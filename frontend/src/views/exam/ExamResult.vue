<template>
  <div v-loading="loading">
    <el-card shadow="never" class="result-card">
      <div class="summary">
        <div class="chart" ref="chartRef"></div>
        <div class="summary-info">
          <div class="score">{{ result.score }} / {{ result.total }}</div>
          <div class="score-label">得分（正确率 {{ accuracy }}%）</div>
          <el-button type="primary" class="retry" @click="$router.push('/exam/setup')">重新练习</el-button>
          <el-button @click="$router.push('/wrong-book')">查看错题本</el-button>
        </div>
      </div>
    </el-card>

    <el-card v-for="(d, i) in result.details" :key="d.questionId" shadow="never" class="detail-card">
      <div class="q-head">
        <span class="q-index">第 {{ i + 1 }} 题</span>
        <el-tag :type="tagType(d.isCorrect)">{{ tagLabel(d.isCorrect) }}</el-tag>
        <el-tag size="small" type="info">{{ typeLabel(d.type) }}</el-tag>
      </div>
      <div class="q-stem">{{ d.stem }}</div>
      <div v-if="d.type === 'JUDGE'" class="q-body">你的答案：{{ d.userAnswer === 'true' ? '正确' : '错误' }}；正确答案：{{ d.correctAnswer }}</div>
      <div v-else class="q-body">
        <div>你的答案：{{ formatAnswer(d) }}</div>
        <div v-if="d.type !== 'SHORT'">正确答案：{{ d.correctAnswer }}</div>
      </div>
      <div v-if="d.type === 'SHORT'" class="q-body">参考答案：{{ d.correctAnswer }}</div>
      <el-divider content-position="left">解析</el-divider>
      <div class="q-analysis">{{ d.analysis }}</div>
      <div v-if="d.related.length" class="q-related">
        <el-tag v-for="r in d.related" :key="r.type + r.id" class="rel-tag" size="small" effect="plain" @click="go(r)">
          {{ relLabel(r.type) }}：{{ r.title }} →
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getSessionResult } from '../../api/exam'

const route = useRoute()
const router = useRouter()
const result = ref({ score: 0, total: 0, details: [] })
const chartRef = ref(null)
const loading = ref(true)

const accuracy = computed(() => {
  if (!result.value.total) return 0
  return Math.round((result.value.score / result.value.total) * 100)
})

async function load() {
  loading.value = true
  try {
    const cached = sessionStorage.getItem('lexaid_result_' + route.params.sessionId)
    if (cached) {
      result.value = JSON.parse(cached)
    } else {
      result.value = await getSessionResult(route.params.sessionId)
    }
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    series: [
      {
        type: 'pie',
        radius: ['58%', '80%'],
        avoidLabelOverlap: false,
        label: { show: false },
        data: [
          { value: result.value.score, name: '正确', itemStyle: { color: '#2e5fa3' } },
          { value: result.value.total - result.value.score, name: '错误', itemStyle: { color: '#e6a23c' } }
        ]
      }
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '42%',
        style: { text: accuracy.value + '%', fontSize: 22, fontWeight: 700, fill: '#303133' }
      }
    ]
  })
}

function tagType(c) {
  if (c === null || c === undefined) return 'info'
  return c ? 'success' : 'danger'
}
function tagLabel(c) {
  if (c === null || c === undefined) return '待自评'
  return c ? '正确' : '错误'
}
function typeLabel(t) {
  return { SINGLE: '单选', MULTIPLE: '多选', JUDGE: '判断', SHORT: '简答' }[t] || t
}
function formatAnswer(d) {
  if (d.type === 'SHORT') return d.userAnswer || '（未作答）'
  return d.userAnswer || '（未作答）'
}
function relLabel(t) {
  return { PROVISION: '法条', CASE: '案例', VIEWPOINT: '观点', QUESTION: '题目' }[t] || t
}
function go(r) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (map[r.type]) router.push(map[r.type] + r.id)
}

onMounted(load)
</script>

<style scoped>
.result-card,
.detail-card {
  border-radius: 8px;
}
.detail-card {
  margin-top: 12px;
}
.summary {
  display: flex;
  align-items: center;
  gap: 24px;
  justify-content: center;
  padding: 8px 0;
}
.chart {
  width: 180px;
  height: 180px;
}
.score {
  font-size: 32px;
  font-weight: 700;
  color: #2e5fa3;
}
.score-label {
  color: #909399;
  margin-bottom: 12px;
}
.retry {
  margin-right: 8px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.q-index {
  font-weight: 600;
}
.q-stem {
  margin: 10px 0;
  line-height: 1.8;
  font-size: 15px;
}
.q-body {
  color: #606266;
  line-height: 1.9;
}
.q-analysis {
  line-height: 1.9;
  color: #303133;
}
.q-related {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.rel-tag {
  cursor: pointer;
}
</style>
