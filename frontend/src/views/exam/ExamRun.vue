<template>
  <div v-loading="loading">
    <el-card shadow="never" class="run-card">
      <template #header>
        <div class="run-header">
          <span>第 {{ current + 1 }} / {{ questions.length }} 题</span>
          <span class="timer"><el-icon><Timer /></el-icon> {{ formatTime(elapsed) }}</span>
        </div>
      </template>
      <el-progress :percentage="progress" :stroke-width="8" />

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
        <el-button v-else type="success" @click="confirmSubmit">交 卷</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSession, submitSession } from '../../api/exam'

const route = useRoute()
const router = useRouter()
const questions = ref([])
const current = ref(0)
const answers = ref({})
const elapsed = ref(0)
const loading = ref(true)
let timer = null

const currentQuestion = computed(() => questions.value[current.value])
const progress = computed(() => (questions.value.length ? ((current.value + 1) / questions.value.length) * 100 : 0))
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
  return v !== undefined && v !== null && v !== ''
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
  const unAnswered = questions.value.filter((q) => !isAnswered(q.id)).length
  await ElMessageBox.confirm(
    `共 ${questions.value.length} 题，还有 ${unAnswered} 题未作答，确定交卷吗？`,
    '交卷确认',
    { type: 'warning' }
  )
  const payload = {
    answers: questions.value.map((q) => {
      let answer = answers.value[q.id]
      if (Array.isArray(answer)) answer = JSON.stringify(answer)
      return { questionId: q.id, answer: answer || '' }
    })
  }
  const result = await submitSession(route.params.sessionId, payload)
  sessionStorage.setItem('lexaid_result_' + route.params.sessionId, JSON.stringify(result))
  router.push('/exam/result/' + route.params.sessionId)
}

onMounted(async () => {
  questions.value = await getSession(route.params.sessionId)
  loading.value = false
  timer = setInterval(() => elapsed.value++, 1000)
})

onBeforeUnmount(() => clearInterval(timer))
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
