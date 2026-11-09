<template>
  <div v-loading="loading || submitting" :element-loading-text="submitting ? '正在保存作答…' : ''">
    <el-card shadow="never" class="run-card">
      <template #header>
        <div class="run-header">
          <span>第 {{ current + 1 }} / {{ questions.length }} 题<span v-if="subjectLabel(currentQuestion)"> · {{ subjectLabel(currentQuestion) }}</span></span>
          <span class="timer"><el-icon><Timer /></el-icon> {{ formatTime(elapsed) }}</span>
        </div>
      </template>
      <div v-if="questions.length" class="paper-progress">
        <span role="status">本卷已答 {{ answeredCount }} / {{ questions.length }} 题</span>
        <el-progress aria-label="本卷作答进度" :percentage="progress" :stroke-width="8" :show-text="false" />
      </div>
      <QuestionCodeBooks :books="currentQuestion?.codeBooks" />

      <template v-if="currentQuestion">
        <div class="stem">
          <el-tag size="small">{{ typeLabel(currentQuestion.type) }}</el-tag>
          <span class="stem-text">{{ currentQuestion.stem }}</span>
        </div>

        <div v-if="currentQuestion.type === 'SINGLE'" class="options">
          <el-radio-group v-model="answerValue" @change="saveAnswer">
            <el-radio v-for="opt in currentQuestion.options" :key="opt.key" :value="opt.key" class="option">
              {{ opt.key }}. {{ opt.text }}
            </el-radio>
          </el-radio-group>
        </div>

        <div v-else-if="currentQuestion.type === 'MULTIPLE'" class="options">
          <el-checkbox-group v-model="answerArr" @change="saveMultiple">
            <el-checkbox v-for="opt in currentQuestion.options" :key="opt.key" :value="opt.key" class="option">
              {{ opt.key }}. {{ opt.text }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div v-else-if="currentQuestion.type === 'JUDGE'" class="options">
          <el-radio-group v-model="answerValue" @change="saveAnswer">
            <el-radio value="true" class="option">正确</el-radio>
            <el-radio value="false" class="option">错误</el-radio>
          </el-radio-group>
        </div>

        <div v-else class="options">
          <el-input
            v-model="answerValue"
            type="textarea"
            :rows="6"
            placeholder="请输入你的作答"
            @blur="saveAnswer"
          />
        </div>
      </template>

      <div class="nav">
        <el-button @click="prev" :disabled="current === 0">上一题</el-button>
        <el-button v-if="current < questions.length - 1" type="primary" @click="next">下一题</el-button>
        <el-button type="success" :loading="submitting" :disabled="confirming || !questions.length" @click="confirmSubmit">交 卷</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="nav-card">
      <template #header>题卡</template>
      <div class="cards">
        <el-button
          v-for="(q, i) in questions"
          :key="q.id"
          :type="isAnswered(q.id) ? 'primary' : 'default'"
          :plain="current !== i"
          size="small"
          @click="current = i"
        >{{ i + 1 }}</el-button>
      </div>
      <div class="legend">
        <span><el-tag size="small" type="primary">已答</el-tag></span>
        <span><el-tag size="small">未答</el-tag></span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Timer } from '@element-plus/icons-vue'
import { getSession, submitSession } from '../../api/exam'
import { subjectLabel } from './subject-label'
import QuestionCodeBooks from './QuestionCodeBooks.vue'

const route = useRoute()
const router = useRouter()
const questions = ref([])
const current = ref(0)
const answers = ref({})
const elapsed = ref(0)
const loading = ref(true)
const submitting = ref(false)
const confirming = ref(false)
let active = true
let timer = null

const currentQuestion = computed(() => questions.value[current.value])
const answeredCount = computed(() => questions.value.filter(q => isAnswered(q.id)).length)
const progress = computed(() => questions.value.length ? Math.round(answeredCount.value * 100 / questions.value.length) : 0)
const answerValue = computed({
  get: () => {
    const q = currentQuestion.value
    if (!q) return ''
    const v = answers.value[q.id]
    if (q.type === 'MULTIPLE') return Array.isArray(v) ? v : []
    return v || ''
  },
  set: (val) => {
    const q = currentQuestion.value
    if (q) answers.value[q.id] = val
  }
})
const answerArr = computed({
  get: () => {
    const q = currentQuestion.value
    if (!q) return []
    const v = answers.value[q.id]
    return Array.isArray(v) ? v : []
  },
  set: (val) => {
    const q = currentQuestion.value
    if (q) answers.value[q.id] = val
  }
})

function saveAnswer() {
  // 单选/判断直接存储字符串
}
function saveMultiple(val) {
  const q = currentQuestion.value
  if (q) answers.value[q.id] = val
}
function isAnswered(id) {
  const v = answers.value[id]
  if (Array.isArray(v)) return v.length > 0
  return typeof v === 'string' && v.trim().length > 0
}
function prev() {
  if (current.value > 0) current.value--
}
function next() {
  if (current.value < questions.value.length - 1) current.value++
}
function formatTime(sec) {
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${m}:${s}`
}
function typeLabel(t) {
  return { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', SHORT: '简答题' }[t] || t
}

async function confirmSubmit() {
  if (confirming.value || submitting.value) return
  const unAnswered = questions.value.length - answeredCount.value
  const hasSubjectiveAnswers = questions.value.some(q => q.type === 'SHORT' && isAnswered(q.id))
  const note = hasSubjectiveAnswers ? '交卷后，DeepSeek 将根据参考答案为简答题评分。' : ''
  confirming.value = true
  try {
    await ElMessageBox.confirm(
      `共 ${questions.value.length} 题，还有 ${unAnswered} 题未作答。${note}确定交卷吗？`,
      '交卷确认', { type: 'warning' }
    )
  } catch { return } finally { confirming.value = false }
  if (!active) return
  submitting.value = true
  const sessionId = route.params.sessionId
  const payload = {
    answers: questions.value.map((q) => {
      let answer = answers.value[q.id]
      if (Array.isArray(answer)) answer = JSON.stringify(answer)
      return { questionId: q.id, answer: answer || '' }
    })
  }
  try {
    await submitSession(sessionId, payload)
    if (active) await router.push('/exam/result/' + sessionId)
  } catch {
    // The API client reports the error; keep the answers available for another submission.
  } finally { submitting.value = false }
}

onMounted(async () => {
  try {
    const data = await getSession(route.params.sessionId)
    if (!active) return
    questions.value = data
    timer = setInterval(() => elapsed.value++, 1000)
  } finally { loading.value = false }
})

onBeforeUnmount(() => {
  active = false
  clearInterval(timer)
})
</script>

<style scoped>
.run-card,
.nav-card {
  border-radius: 8px;
}
.nav-card {
  margin-top: 12px;
}
.run-header {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
}
.paper-progress {
  display: grid;
  gap: 6px;
  font-size: 14px;
  color: var(--lex-muted);
  font-variant-numeric: tabular-nums;
}
.timer {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #2e5fa3;
}
.stem {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin: 14px 0;
}
.stem-text {
  font-size: 15px;
  line-height: 1.8;
}
.options {
  margin: 10px 0 16px;
}
.option {
  display: flex;
  margin-bottom: 10px;
  height: auto;
  white-space: normal;
}
.nav {
  margin-top: 12px;
}
.cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.legend {
  margin-top: 10px;
  display: flex;
  gap: 12px;
}
</style>
