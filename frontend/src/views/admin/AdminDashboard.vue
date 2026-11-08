<template>
  <section class="admin-page" aria-labelledby="admin-title">
    <header class="admin-header">
      <div>
        <div class="eyebrow">ADMIN CONSOLE</div>
        <h1 id="admin-title">用户数据</h1>
        <p>掌握用户增长、活跃和学习投入，快速定位每位用户的使用情况。</p>
      </div>
      <div class="header-actions">
        <span v-if="lastUpdated" class="updated">更新于 {{ formatClock(lastUpdated) }}</span>
        <el-button :loading="loadingSummary || loadingUsers" @click="refreshAll">
          <el-icon><Refresh /></el-icon>刷新数据
        </el-button>
      </div>
    </header>

    <div v-loading="loadingSummary" class="metric-grid" aria-label="用户概览">
      <article v-for="card in metricCards" :key="card.label" class="metric-card">
        <div class="metric-icon" :class="card.tone"><el-icon><component :is="card.icon" /></el-icon></div>
        <div class="metric-copy">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.note }}</small>
        </div>
      </article>
    </div>

    <div class="insight-strip" aria-label="内容参与概览">
      <div><span>累计学习</span><strong>{{ formatDuration(overview?.totalStudyMinutes) }}</strong></div>
      <div><span>答题总量</span><strong>{{ number(overview?.totalAnswers) }}</strong></div>
      <div><span>学习笔记</span><strong>{{ number(overview?.totalNotes) }}</strong></div>
      <div><span>社区动态</span><strong>{{ number(overview?.totalPosts) }}</strong></div>
    </div>

    <div class="chart-grid">
      <el-card shadow="never" class="panel chart-panel">
        <template #header>
          <div class="panel-title"><div><strong>增长与活跃趋势</strong><span>近 {{ trendDays }} 天</span></div></div>
        </template>
        <div ref="trendRef" class="chart" aria-label="用户注册和活跃趋势图"></div>
      </el-card>
      <el-card shadow="never" class="panel chart-panel">
        <template #header>
          <div class="panel-title"><div><strong>学习行为分布</strong><span>全部历史记录</span></div></div>
        </template>
        <div ref="activityRef" class="chart" aria-label="学习行为分布图"></div>
      </el-card>
    </div>

    <el-card shadow="never" class="panel users-panel">
      <template #header>
        <div class="users-heading">
          <div class="panel-title">
            <div><strong>用户列表</strong><span>共 {{ number(total) }} 位用户</span></div>
          </div>
          <div class="filters" role="search" aria-label="筛选用户">
            <el-input v-model="filters.keyword" clearable placeholder="搜索账号或昵称" aria-label="搜索账号或昵称"
              @keyup.enter="applyFilters" @clear="applyFilters">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="filters.role" placeholder="全部角色" aria-label="筛选角色" @change="applyFilters">
              <el-option label="全部角色" value="" />
              <el-option label="普通用户" value="USER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
            <el-select v-model="filters.activity" aria-label="筛选活跃状态" @change="applyFilters">
              <el-option label="全部状态" value="all" />
              <el-option label="近 7 天活跃" value="active7" />
              <el-option label="近 30 天注册" value="new30" />
              <el-option label="超过 30 天未活跃" value="inactive30" />
            </el-select>
            <el-select v-model="filters.sort" aria-label="排序字段" @change="applyFilters">
              <el-option label="最近活跃" value="lastActive" />
              <el-option label="注册时间" value="created" />
              <el-option label="学习时长" value="studyTime" />
              <el-option label="答题数量" value="answers" />
            </el-select>
            <el-button class="search-button" type="primary" @click="applyFilters">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loadingUsers" :data="users" class="users-table" row-key="id"
        empty-text="暂无符合条件的用户" @row-click="openUser">
        <el-table-column label="用户" min-width="230">
          <template #default="{ row }">
            <div class="user-cell">
              <UserAvatar :user-id="row.id" :version="row.avatarVersion" :name="displayName(row)" :size="38" />
              <div><strong>{{ displayName(row) }}</strong><span>@{{ row.username }} · ID {{ row.id }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="126">
          <template #default="{ row }">
            <span class="status-pill" :class="activityState(row).tone"><i></i>{{ activityState(row).label }}</span>
          </template>
        </el-table-column>
        <el-table-column label="学习投入" min-width="150">
          <template #default="{ row }"><strong>{{ formatDuration(row.studyMinutes) }}</strong><span class="cell-sub">累计时长</span></template>
        </el-table-column>
        <el-table-column label="答题表现" min-width="140">
          <template #default="{ row }"><strong>{{ number(row.answerCount) }} 题</strong><span class="cell-sub">正确率 {{ row.accuracy }}%</span></template>
        </el-table-column>
        <el-table-column label="内容沉淀" min-width="180">
          <template #default="{ row }"><span>{{ row.noteCount }} 笔记 · {{ row.favoriteCount }} 收藏</span><span class="cell-sub">{{ row.postCount }} 条社区动态</span></template>
        </el-table-column>
        <el-table-column label="注册时间" width="126">
          <template #default="{ row }"><span>{{ formatDate(row.createdAt) }}</span><span class="cell-sub">{{ roleLabel(row.role) }}</span></template>
        </el-table-column>
        <el-table-column width="74" align="right">
          <template #default="{ row }"><el-button text type="primary" @click.stop="openUser(row)">详情</el-button></template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > pageSize" class="pager" background layout="prev, pager, next, total"
        :current-page="page" :page-size="pageSize" :total="total" @current-change="changePage" />
    </el-card>

    <el-drawer v-model="drawerOpen" :size="drawerSize" :with-header="false" destroy-on-close class="user-drawer">
      <div v-loading="detailLoading" class="drawer-content">
        <template v-if="detail">
          <div class="drawer-topbar">
            <span>用户详情</span>
            <el-button text circle aria-label="关闭用户详情" @click="drawerOpen = false"><el-icon><Close /></el-icon></el-button>
          </div>
          <div class="profile-card">
            <UserAvatar :user-id="detail.profile.id" :version="detail.profile.avatarVersion"
              :name="displayName(detail.profile)" :size="62" />
            <div class="profile-copy">
              <div><h2>{{ displayName(detail.profile) }}</h2><el-tag :type="detail.profile.role === 'ADMIN' ? 'warning' : 'info'" size="small">{{ roleLabel(detail.profile.role) }}</el-tag></div>
              <p>@{{ detail.profile.username }} · 用户 ID {{ detail.profile.id }}</p>
              <span>注册于 {{ formatFullTime(detail.profile.createdAt) }}</span>
            </div>
          </div>
          <div class="detail-metrics">
            <div><strong>{{ formatDuration(detail.profile.studyMinutes) }}</strong><span>学习时长</span></div>
            <div><strong>{{ number(detail.profile.answerCount) }}</strong><span>答题数量</span></div>
            <div><strong>{{ detail.profile.accuracy }}%</strong><span>答题正确率</span></div>
          </div>
          <section class="drawer-section">
            <div class="section-heading"><h3>行为构成</h3><span>累计记录</span></div>
            <div class="count-grid">
              <div v-for="item in detailCounts" :key="item.key"><span>{{ item.label }}</span><strong>{{ number(item.value) }}</strong></div>
            </div>
          </section>
          <section class="drawer-section recent-section">
            <div class="section-heading"><h3>最近活动</h3><span>{{ activityState(detail.profile).label }}</span></div>
            <el-timeline v-if="detail.recentActivities.length" class="activity-timeline">
              <el-timeline-item v-for="(item, index) in detail.recentActivities" :key="index"
                :timestamp="formatFullTime(item.occurredAt)" placement="top" :color="timelineColor(item.category)">
                <strong>{{ actionLabel(item.actionType) }}</strong>
                <p>{{ item.title || '完成了一次操作' }}</p>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="该用户还没有使用记录" :image-size="72" />
          </section>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Close, Plus, Refresh, Search, Timer, TrendCharts, UserFilled } from '@element-plus/icons-vue'
import echarts from '../../charts'
import { adminOverview, adminUser, adminUsers } from '../../api/admin'
import UserAvatar from '../../components/UserAvatar.vue'

const trendDays = 14
const overview = ref(null)
const users = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loadingSummary = ref(false)
const loadingUsers = ref(false)
const lastUpdated = ref(null)
const trendRef = ref(null)
const activityRef = ref(null)
const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const narrowMedia = window.matchMedia('(max-width: 720px)')
const isNarrow = ref(narrowMedia.matches)
const drawerSize = computed(() => isNarrow.value ? '100%' : '540px')
let trendChart
let activityChart
let chartObserver
let userRequest = 0
let detailRequest = 0

const filters = reactive({ keyword: '', role: '', activity: 'all', sort: 'lastActive' })

const metricCards = computed(() => [
  { label: '用户总量', value: number(overview.value?.totalUsers), note: '已注册账号', icon: UserFilled, tone: 'blue' },
  { label: '近 7 天活跃', value: number(overview.value?.activeUsers7Days), note: activeRate.value + '% 用户活跃', icon: TrendCharts, tone: 'green' },
  { label: '近 7 天新增', value: number(overview.value?.newUsers7Days), note: '今日新增 ' + number(overview.value?.newUsersToday), icon: Plus, tone: 'violet' },
  { label: '人均学习时长', value: averageMinutes.value + ' 分钟', note: '基于全部注册用户', icon: Timer, tone: 'amber' }
])
const activeRate = computed(() => overview.value?.totalUsers
  ? Math.round(overview.value.activeUsers7Days * 1000 / overview.value.totalUsers) / 10 : 0)
const averageMinutes = computed(() => overview.value?.totalUsers
  ? Math.round(overview.value.totalStudyMinutes / overview.value.totalUsers) : 0)
const detailCounts = computed(() => Object.entries(detail.value?.activityCounts || {})
  .map(([key, value]) => ({ key, value, label: actionLabel(key) })))

async function loadOverview() {
  loadingSummary.value = true
  try {
    overview.value = await adminOverview(trendDays)
    await nextTick()
    renderCharts()
    lastUpdated.value = new Date()
  } finally {
    loadingSummary.value = false
  }
}

async function loadUsers() {
  const request = ++userRequest
  loadingUsers.value = true
  try {
    const result = await adminUsers({ ...filters, page: page.value, size: pageSize, order: 'desc' })
    if (request !== userRequest) return
    users.value = result.records
    total.value = result.total
  } finally {
    if (request === userRequest) loadingUsers.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadUsers()])
}

function applyFilters() {
  page.value = 1
  loadUsers()
}

function changePage(value) {
  page.value = value
  loadUsers()
}

async function openUser(row) {
  drawerOpen.value = true
  detail.value = null
  detailLoading.value = true
  const request = ++detailRequest
  try {
    const result = await adminUser(row.id)
    if (request === detailRequest) detail.value = result
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

function renderCharts() {
  const trend = overview.value?.trend || []
  const distribution = overview.value?.activityDistribution || []
  const common = { animation: false, textStyle: { fontFamily: 'inherit', color: '#536276' } }
  trendChart ??= echarts.init(trendRef.value, null, { renderer: 'svg' })
  trendChart.setOption({
    ...common,
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'line' } },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, data: ['新增用户', '活跃用户'] },
    grid: { left: 36, right: 16, top: 42, bottom: 26 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map(item => item.date.slice(5)), axisLine: { lineStyle: { color: '#dce3eb' } }, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f5' } } },
    series: [
      { name: '新增用户', type: 'line', smooth: true, symbolSize: 6, data: trend.map(item => item.registrations), lineStyle: { width: 2, color: '#376ea8' }, itemStyle: { color: '#376ea8' }, areaStyle: { color: '#dce9f5', opacity: .55 } },
      { name: '活跃用户', type: 'line', smooth: true, symbolSize: 6, data: trend.map(item => item.activeUsers), lineStyle: { width: 2, color: '#42a07b' }, itemStyle: { color: '#42a07b' } }
    ]
  })
  activityChart ??= echarts.init(activityRef.value, null, { renderer: 'svg' })
  activityChart.setOption({
    ...common,
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 62, right: 20, top: 12, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f5' } } },
    yAxis: { type: 'category', inverse: true, data: distribution.map(item => actionLabel(item.actionType)), axisLine: { show: false }, axisTick: { show: false } },
    series: [{ type: 'bar', barMaxWidth: 18, data: distribution.map(item => item.count), itemStyle: { color: '#567faa', borderRadius: [0, 5, 5, 0] }, label: { show: true, position: 'right', color: '#536276' } }]
  })
}

function displayName(user) { return user.nickname || user.username || '未命名用户' }
function roleLabel(role) { return role === 'ADMIN' ? '管理员' : '普通用户' }
function actionLabel(action) {
  return { VIEW: '浏览', SEARCH: '搜索', ANSWER: '答题', CHAT: 'AI 提问', NOTE: '笔记', EXAM: '答题', FAVORITE: '收藏', POST: '动态', STUDY: '学习' }[action] || action
}
function activityState(user) {
  if (!user.lastActiveAt) return { label: '暂无行为', tone: 'muted' }
  const days = (Date.now() - parseTime(user.lastActiveAt).getTime()) / 86400000
  if (days <= 7) return { label: '活跃', tone: 'active' }
  if (days <= 30) return { label: '一般', tone: 'warm' }
  return { label: '沉默', tone: 'muted' }
}
function timelineColor(category) { return { POST: '#8b6dc0', CHAT: '#4f7fb2', ANSWER: '#42a07b', NOTE: '#d28b3b' }[category] || '#8190a2' }
function parseTime(value) { return new Date(String(value).replace(' ', 'T')) }
function formatDate(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', year: 'numeric' }).format(parseTime(value)) : '—' }
function formatFullTime(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(parseTime(value)) : '暂无记录' }
function formatClock(value) { return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(value) }
function formatDuration(minutes = 0) {
  const value = Number(minutes) || 0
  return value >= 60 ? `${Math.floor(value / 60)} 小时 ${value % 60} 分` : `${value} 分钟`
}
function number(value = 0) { return new Intl.NumberFormat('zh-CN').format(Number(value) || 0) }
function viewportChanged(event) { isNarrow.value = event.matches }

onMounted(() => {
  narrowMedia.addEventListener('change', viewportChanged)
  chartObserver = new ResizeObserver(entries => entries.forEach(({ target }) => echarts.getInstanceByDom(target)?.resize()))
  chartObserver.observe(trendRef.value)
  chartObserver.observe(activityRef.value)
  refreshAll()
})
onBeforeUnmount(() => {
  narrowMedia.removeEventListener('change', viewportChanged)
  chartObserver?.disconnect()
  trendChart?.dispose()
  activityChart?.dispose()
})
</script>

<style scoped>
.admin-page { max-width: 1500px; margin: 0 auto; padding-bottom: 32px; }
.admin-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin: 2px 0 22px; }
.eyebrow { color: #55708f; font-size: .68rem; font-weight: 760; letter-spacing: .16em; margin-bottom: 5px; }
.admin-header h1 { font-size: clamp(1.7rem, 3vw, 2.15rem); line-height: 1.15; letter-spacing: -.04em; font-weight: 740; }
.admin-header p { color: var(--lex-muted); margin-top: 7px; }
.header-actions { display: flex; align-items: center; gap: 12px; flex: none; }
.updated { color: #7a8797; font-size: .78rem; }
.metric-grid { min-height: 132px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.metric-card { display: flex; align-items: flex-start; gap: 14px; min-width: 0; padding: 20px; border: 1px solid var(--lex-line); border-radius: 15px; background: #fff; box-shadow: 0 3px 12px #263a5208; }
.metric-icon { width: 40px; height: 40px; display: grid; place-items: center; flex: none; border-radius: 11px; font-size: 1.15rem; }
.metric-icon.blue { color: #2e659c; background: #e9f1fa; }
.metric-icon.green { color: #238163; background: #e7f5ef; }
.metric-icon.violet { color: #7557a5; background: #f0ebf8; }
.metric-icon.amber { color: #a76721; background: #fbf0df; }
.metric-copy { min-width: 0; display: flex; flex-direction: column; }
.metric-copy > span { color: #667589; font-size: .78rem; }
.metric-copy strong { color: #1a2c43; font-size: clamp(1.45rem, 2vw, 1.85rem); line-height: 1.25; margin: 4px 0 3px; letter-spacing: -.035em; }
.metric-copy small { color: #8190a1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.insight-strip { display: grid; grid-template-columns: repeat(4, 1fr); margin: 14px 0; border: 1px solid #d9e2eb; border-radius: 13px; background: linear-gradient(100deg, #eef3f8, #f7f9fb); }
.insight-strip > div { display: flex; align-items: baseline; justify-content: center; gap: 10px; padding: 14px 16px; border-right: 1px solid #dbe3eb; }
.insight-strip > div:last-child { border-right: 0; }
.insight-strip span { color: #6c7b8e; font-size: .78rem; }
.insight-strip strong { color: #294a6d; font-size: .96rem; }
.chart-grid { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(340px, .8fr); gap: 14px; margin-bottom: 14px; }
.panel { border-color: var(--lex-line); }
.panel :deep(.el-card__header) { padding: 16px 18px 13px; }
.panel-title { display: flex; align-items: center; justify-content: space-between; }
.panel-title > div { display: flex; align-items: baseline; gap: 10px; }
.panel-title strong { font-size: .98rem; }
.panel-title span { color: #8290a1; font-size: .76rem; font-weight: 450; }
.chart-panel :deep(.el-card__body) { padding: 12px 18px 16px; }
.chart { height: 250px; }
.users-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.filters { display: flex; align-items: center; gap: 8px; }
.filters .el-input { width: 190px; }
.filters .el-select { width: 126px; }
.filters .el-select:nth-of-type(4) { width: 116px; }
.users-panel :deep(.el-card__body) { padding: 0 18px 17px; }
.users-table { --el-table-border-color: #e8edf2; --el-table-header-bg-color: #f8fafc; --el-table-row-hover-bg-color: #f5f8fb; cursor: pointer; }
.users-table :deep(th.el-table__cell) { color: #718094; font-size: .75rem; font-weight: 650; }
.users-table :deep(td.el-table__cell) { padding: 13px 0; }
.user-cell { display: flex; align-items: center; gap: 11px; min-width: 0; }
.user-cell > div { min-width: 0; display: flex; flex-direction: column; }
.user-cell strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-cell span, .cell-sub { display: block; color: #8290a1; font-size: .75rem; margin-top: 2px; }
.status-pill { display: inline-flex; align-items: center; gap: 6px; padding: 4px 8px; border-radius: 999px; font-size: .73rem; font-weight: 600; }
.status-pill i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.status-pill.active { color: #247b5d; background: #e8f5ef; }
.status-pill.warm { color: #9d6726; background: #fbf0df; }
.status-pill.muted { color: #728093; background: #edf1f5; }
.pager { justify-content: flex-end; margin-top: 16px; }
.drawer-content { min-height: 100%; padding: 4px 4px 28px; }
.drawer-topbar { display: flex; align-items: center; justify-content: space-between; color: #617187; font-size: .8rem; font-weight: 650; text-transform: uppercase; letter-spacing: .08em; }
.profile-card { display: flex; align-items: center; gap: 16px; padding: 22px 0 20px; border-bottom: 1px solid #e6ebf0; }
.profile-copy { min-width: 0; }
.profile-copy > div { display: flex; align-items: center; gap: 9px; }
.profile-copy h2 { font-size: 1.35rem; letter-spacing: -.025em; }
.profile-copy p { color: #53657a; margin: 3px 0; }
.profile-copy > span { color: #8995a4; font-size: .77rem; }
.detail-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1px; margin: 18px 0 24px; overflow: hidden; border: 1px solid #e1e7ed; border-radius: 12px; background: #e1e7ed; }
.detail-metrics div { display: flex; flex-direction: column; gap: 3px; padding: 15px 10px; text-align: center; background: #f8fafc; }
.detail-metrics strong { color: #294f78; font-size: 1rem; }
.detail-metrics span { color: #7c8999; font-size: .72rem; }
.drawer-section { margin-top: 24px; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.section-heading h3 { font-size: .95rem; }
.section-heading span { color: #8a96a5; font-size: .74rem; }
.count-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.count-grid > div { display: flex; justify-content: space-between; align-items: center; padding: 9px 11px; border-radius: 9px; background: #f1f4f7; }
.count-grid span { color: #637287; font-size: .76rem; }
.count-grid strong { color: #294d72; }
.activity-timeline { padding: 4px 0 0 3px; }
.activity-timeline :deep(.el-timeline-item__timestamp) { color: #919cab; font-size: .71rem; }
.activity-timeline strong { font-size: .83rem; }
.activity-timeline p { color: #637287; font-size: .79rem; margin-top: 2px; line-height: 1.45; }

@media (max-width: 1180px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
  .users-heading { align-items: flex-start; flex-direction: column; }
  .filters { width: 100%; flex-wrap: wrap; }
  .filters .el-input { flex: 1; min-width: 220px; }
}
@media (max-width: 720px), (max-width: 900px) and (max-height: 500px) {
  .admin-header { align-items: flex-start; flex-direction: column; margin-top: 0; }
  .header-actions { width: 100%; justify-content: space-between; }
  .metric-grid { grid-template-columns: 1fr 1fr; gap: 9px; }
  .metric-card { padding: 14px; gap: 10px; }
  .metric-icon { width: 34px; height: 34px; }
  .metric-copy strong { font-size: 1.2rem; }
  .insight-strip { grid-template-columns: 1fr 1fr; }
  .insight-strip > div:nth-child(2) { border-right: 0; }
  .insight-strip > div:nth-child(-n + 2) { border-bottom: 1px solid #dbe3eb; }
  .chart-grid { grid-template-columns: minmax(0, 1fr); }
  .chart { height: 230px; }
  .filters > * { flex: 1; min-width: calc(50% - 4px) !important; width: auto !important; }
  .filters .el-input { flex-basis: 100%; }
  .search-button { flex-basis: 100%; }
  .users-panel :deep(.el-card__body) { padding-inline: 12px; }
  .users-table :deep(.el-table__body-wrapper) { overflow-x: auto; }
}
@media (max-width: 430px) {
  .metric-grid { grid-template-columns: 1fr; }
  .updated { display: none; }
  .count-grid { grid-template-columns: 1fr 1fr; }
}
</style>
