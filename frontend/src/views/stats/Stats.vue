<template>
  <div>
    <el-row :gutter="16" class="summary-cards">
      <el-col v-for="c in cards" :key="c.label" :span="6" :xs="12">
        <el-card shadow="hover" class="stat-card">
          <div class="value">{{ c.value }}</div>
          <div class="label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="charts">
      <el-col :span="24" :md="14">
        <el-card shadow="never" class="chart-card">
          <template #header>近 30 天答题趋势</template>
          <div ref="lineRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="24" :md="10">
        <el-card shadow="never" class="chart-card">
          <template #header>类别正确率</template>
          <div ref="barRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="records-card">
      <template #header>学习足迹</template>
      <el-table :data="records" stripe>
        <el-table-column prop="createdAt" label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ actionLabel(row.actionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="180">
          <template #default="{ row }">
            <span v-if="row.targetTitle" class="target" @click="go(row)">{{ row.targetTitle }}</span>
            <span v-else>{{ row.detail || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="160" />
      </el-table>
      <el-pagination
        v-if="total > pageSize"
        class="pager"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import echarts from '../../charts'
import { statsCategory, statsDaily, statsOverview, statsRecords } from '../../api/stats'

const router = useRouter()
const overview = ref(null)
const daily = ref([])
const categories = ref([])
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const lineRef = ref(null)
const barRef = ref(null)
let lineChart = null
let barChart = null
let chartObserver
let recordsRequest = 0

const cards = computed(() => [
  { label: '累计学习时长（分钟）', value: overview.value?.totalStudyMinutes ?? 0 },
  { label: '答题总数', value: overview.value?.totalAnswer ?? 0 },
  { label: '正确率', value: (overview.value?.accuracy ?? 0) + '%' },
  { label: '连续学习天数', value: overview.value?.streakDays ?? 0 }
])

async function loadSummary() {
  overview.value = await statsOverview()
  daily.value = await statsDaily(30)
  categories.value = await statsCategory()
  renderCharts()
}

async function loadRecords() {
  const request = ++recordsRequest
  const data = await statsRecords({ page: page.value, size: pageSize.value })
  if (request !== recordsRequest) return
  records.value = data.records
  total.value = data.total
}

function renderCharts() {
  const chartOptions = {
    // CSS fades in the chart with its axes, symbols and labels at their final positions.
    animation: false,
    tooltip: { trigger: 'axis', confine: true, transitionDuration: 0, axisPointer: { animation: false } }
  }
  if (lineRef.value) {
    lineChart ??= echarts.init(lineRef.value, null, { renderer: 'svg' })
    lineChart.setOption({
      ...chartOptions,
      legend: { top: 0, left: 'center', data: ['答题数', '正确数'] },
      grid: { left: 40, right: 16, top: 42, bottom: 32 },
      xAxis: { type: 'category', axisLabel: { hideOverlap: true }, data: daily.value.map((d) => d.date.slice(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '答题数', type: 'line', smooth: true, data: daily.value.map((d) => d.answerCount), itemStyle: { color: '#2e5fa3' } },
        { name: '正确数', type: 'line', smooth: true, data: daily.value.map((d) => d.correctCount), itemStyle: { color: '#67c23a' } }
      ]
    })
  }
  if (barRef.value) {
    barChart ??= echarts.init(barRef.value, null, { renderer: 'svg' })
    barChart.setOption({
      ...chartOptions,
      grid: { left: 40, right: 16, top: 36, bottom: 28 },
      xAxis: { type: 'category', data: categories.value.map((c) => c.category) },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        {
          name: '正确率',
          type: 'bar',
          data: categories.value.map((c) => c.accuracy),
          itemStyle: { color: '#2e5fa3', borderRadius: [4, 4, 0, 0] },
          label: { show: true, formatter: '{c}%', position: 'top' }
        }
      ]
    })
  }
}

function actionLabel(a) {
  return { VIEW: '浏览', SEARCH: '搜索', ANSWER: '答题', CHAT: 'AI 提问', NOTE: '笔记' }[a] || a
}
function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}
function go(row) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (row.targetType && map[row.targetType]) router.push(map[row.targetType] + row.targetId)
}
function changePage(p) {
  page.value = p
  loadRecords()
}

onMounted(() => {
  chartObserver = new ResizeObserver(entries => {
    for (const { target } of entries) {
      const chart = echarts.getInstanceByDom(target)
      if (chart && (chart.getWidth() !== target.clientWidth || chart.getHeight() !== target.clientHeight)) {
        chart.resize()
      }
    }
  })
  chartObserver.observe(lineRef.value)
  chartObserver.observe(barRef.value)
  loadSummary()
  loadRecords()
})
onBeforeUnmount(() => {
  chartObserver?.disconnect()
  lineChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  border-radius: 8px;
}
.summary-cards { row-gap: 12px; }
.value {
  font-size: 26px;
  font-weight: 700;
  color: #2e5fa3;
}
.label {
  color: #909399;
  margin-top: 4px;
  font-size: 13px;
}
.charts {
  margin-top: 16px;
  row-gap: 16px;
}
.chart-card {
  border-radius: 8px;
}
.chart {
  height: 300px;
}
@media (prefers-reduced-motion: no-preference) {
  .chart:has(svg) { animation: chart-reveal var(--lex-motion-enter, 180ms) var(--lex-ease-out, ease-out); }
  @keyframes chart-reveal { from { opacity: .65; } to { opacity: 1; } }
}
.records-card {
  margin-top: 16px;
  border-radius: 8px;
}
.target {
  color: #2e5fa3;
  cursor: pointer;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
