<template>
  <div v-loading="loading">
    <el-alert v-if="loadFailed" class="load-error" type="warning" :closable="false">
      <template #title>成绩更新失败 <el-button link type="primary" :disabled="refreshing" @click="refresh()">重新加载</el-button></template>
    </el-alert>
    <template v-if="result">
      <el-card shadow="never" class="result-card">
        <div class="summary">
          <div class="chart" role="img" :aria-label="rate == null ? '暂无得分率' : '得分率 ' + rate + '%'"
            :style="{ '--score-rate': (rate ?? 0) + '%' }">
            <span class="chart-label"><strong>{{ rate == null ? '—' : rate + '%' }}</strong>得分率</span>
          </div>
          <div class="summary-info">
            <div class="score">{{ maximum > 0 ? result.score + ' / ' + maximum + ' 分' : '暂无已评分题目' }}</div>
            <div class="score-label">已评分 {{ gradedTotal }} / {{ result.total }} 题</div>
            <p v-if="pendingCount" class="score-note" role="status">{{ pendingCount }} 道简答题等待 DeepSeek 评分</p>
            <p v-if="unavailableCount" class="score-note">
              {{ unavailableCount }} 道题评分暂不可用
              <el-button link type="primary" :loading="retrying" :disabled="refreshing" @click="refresh(true)">重新评分</el-button>
            </p>
            <div class="links">
              <el-button type="primary" @click="$router.push('/exam/setup')">重新练习</el-button>
              <el-button @click="$router.push('/wrong-book')">查看错题本</el-button>
              <el-button @click="router.push({ path: '/ai-chat', query: { contextType: 'EXAM', contextId: route.params.sessionId } })">AI 复盘本次作答</el-button>
            </div>
          </div>
        </div>
      </el-card>

      <el-card v-for="(d, i) in result.details" :key="d.questionId" shadow="never" class="detail-card">
        <div class="q-head">
          <span class="q-index">第 {{ i + 1 }} 题</span>
          <el-tag size="small" type="info">{{ typeLabel(d.type) }}</el-tag>
          <span v-if="subjectLabel(d)" class="question-subject">{{ subjectLabel(d) }}</span>
        </div>
        <QuestionCodeBooks :books="d.codeBooks" />
        <div class="q-stem">{{ d.stem }}</div>
        <div class="q-body">
          <div>你的答案：{{ formatAnswer(d) }}</div>
          <div>{{ d.type === 'SHORT' ? '参考答案' : '正确答案' }}：{{ d.correctAnswer || '未收录' }}</div>
        </div>
        <QuestionGrade :question="d" />
        <template v-if="d.analysis">
          <el-divider content-position="left">解析</el-divider>
          <div class="q-analysis">{{ d.analysis }}</div>
        </template>
        <div v-if="d.related?.length" class="q-related">
          <el-tag v-for="r in d.related" :key="r.type + r.id" class="rel-tag" size="small" effect="plain" @click="go(r)">
            {{ relLabel(r.type) }}：{{ r.title }} →
          </el-tag>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QuestionGrade from '../../components/QuestionGrade.vue'
import { getSessionResult, retryGrading } from '../../api/exam'
import { subjectLabel } from './subject-label'
import QuestionCodeBooks from './QuestionCodeBooks.vue'

const route = useRoute()
const router = useRouter()
const result = ref(null)
const loading = ref(true)
const refreshing = ref(false)
const retrying = ref(false)
const loadFailed = ref(false)
let latestRequest = 0
let pollTimer

const gradedTotal = computed(() => result.value?.gradedTotal ?? result.value?.details?.filter(d => d.isCorrect != null).length ?? 0)
const maximum = computed(() => result.value?.maxScore ?? gradedTotal.value)
const rate = computed(() => maximum.value > 0 ? Math.round(result.value.score * 100 / maximum.value) : null)
const pendingCount = computed(() => result.value?.details?.filter(d => d.gradingStatus === 'PENDING').length ?? 0)
const unavailableCount = computed(() => result.value?.details?.filter(d => d.gradingStatus === 'UNAVAILABLE').length ?? 0)

async function refresh(retry = false) {
  const sessionId = route.params.sessionId
  if (sessionId == null) return
  const request = ++latestRequest
  clearTimeout(pollTimer)
  loading.value = result.value == null
  refreshing.value = true
  retrying.value = retry
  loadFailed.value = false
  try {
    const data = await (retry ? retryGrading(sessionId) : getSessionResult(sessionId))
    if (request !== latestRequest) return
    result.value = data
    if (pendingCount.value) pollTimer = setTimeout(() => refresh(), 3000)
  } catch {
    if (request === latestRequest) loadFailed.value = true
  } finally {
    if (request === latestRequest) {
      loading.value = false
      refreshing.value = false
      retrying.value = false
    }
  }
}

watch(() => route.params.sessionId, () => {
  result.value = null
  refresh()
}, { immediate: true })

onBeforeUnmount(() => {
  ++latestRequest
  clearTimeout(pollTimer)
})

function typeLabel(t) {
  return { SINGLE: '单选', MULTIPLE: '多选', JUDGE: '判断', SHORT: '简答' }[t] || t
}
function formatAnswer(d) {
  if (!d.userAnswer?.trim()) return '（未作答）'
  if (d.type === 'JUDGE') return d.userAnswer === 'true' ? '正确' : '错误'
  return d.userAnswer
}
function relLabel(t) {
  return { PROVISION: '法条', CASE: '案例', VIEWPOINT: '观点', QUESTION: '题目' }[t] || t
}
function go(r) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (map[r.type]) router.push(map[r.type] + r.id)
}
</script>

<style scoped>
.result-card, .detail-card { border-radius: 8px; }
.detail-card { margin-top: 12px; }
.load-error { margin-bottom: 12px; }
.summary { display: flex; align-items: center; flex-wrap: wrap; gap: 24px; justify-content: center; padding: 8px 0; }
.chart { position: relative; display: grid; place-items: center; width: 150px; height: 150px; flex: none; }
.chart::before {
  content: ''; position: absolute; inset: 9%; border-radius: 50%;
  background: radial-gradient(closest-side, var(--lex-surface) 77%, transparent 79%),
    conic-gradient(#2e5fa3 var(--score-rate), #e5eaf0 0);
}
.chart-label { position: relative; text-align: center; font-size: 12px; color: var(--lex-muted); }
.chart-label strong { display: block; font-size: 24px; line-height: 1.2; color: var(--lex-ink); }
.score { font-size: 30px; font-weight: 700; color: #2e5fa3; }
.score-label, .score-note { color: #606266; }
.score-note { margin-top: 6px; font-size: 14px; }
.links { margin-top: 12px; }
.q-head, .q-related { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.q-index { font-weight: 600; }
.question-subject { color: var(--lex-muted); font-size: .82rem; }
.q-stem { margin: 10px 0; line-height: 1.8; font-size: 15px; }
.q-body, .q-analysis { line-height: 1.9; color: #606266; }
.q-body, .q-analysis, .q-stem { white-space: pre-wrap; overflow-wrap: anywhere; }
.q-related { margin-top: 10px; }
.rel-tag { cursor: pointer; }
</style>
