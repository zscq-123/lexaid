<template>
  <div class="dashboard">
    <header class="dashboard-heading">
      <div>
        <time class="today" :datetime="dateKey">{{ today }}</time>
        <h1>工作台</h1>
      </div>
      <router-link class="practice" to="/exam/setup">
        <el-icon aria-hidden="true"><EditPen /></el-icon>模拟答题
      </router-link>
    </header>

    <section aria-label="学习概览" class="metrics">
      <router-link v-for="card in cards" :key="card.label" to="/stats" class="stat-card">
        <div class="stat-label"><el-icon aria-hidden="true"><component :is="card.icon" /></el-icon>{{ card.label }}</div>
        <div class="stat-value">{{ card.value }}<small>{{ card.unit }}</small></div>
      </router-link>
    </section>

    <TopicStudy />
    <section class="libraries">
      <h2>资料检索</h2>
      <div class="library-grid">
        <router-link v-for="entry in libraries" :key="entry.path" class="library-link" :class="entry.theme" :to="entry.path">
          <el-icon class="library-glyph" aria-hidden="true"><component :is="entry.icon" /></el-icon>
          <span>{{ entry.label }}</span>
          <el-icon class="chevron" aria-hidden="true"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </section>

    <div class="lower-grid">
      <section class="panel reading">
        <div class="panel-heading">
          <h2>{{ recommendedSubject ? '薄弱科目推荐' : '阅读推荐' }}</h2>
          <span class="category">{{ recommendedSubject || '高频考点' }}</span>
        </div>
        <div v-if="readingLoading" class="list-status" role="status">正在加载推荐…</div>
        <div v-else-if="readingError" class="list-status" role="alert">
          推荐暂时无法加载 <el-button link type="primary" @click="loadRecommendations">重试</el-button>
        </div>
        <div v-else-if="!recommendations.length" class="list-status">暂无匹配的推荐资料</div>
        <ul v-else class="reading-list">
          <li v-for="item in recommendations" :key="item.to">
            <router-link :to="item.to" class="reading-link">
              <div class="reading-text">
                <span class="kind">{{ item.typeLabel }}</span>
                <strong>{{ item.title }}</strong>
                <p v-if="item.summary">{{ item.summary }}</p>
              </div>
              <el-icon aria-hidden="true" class="chevron"><ArrowRight /></el-icon>
            </router-link>
          </li>
        </ul>
      </section>
      <section class="panel tools">
        <h2>学习工具</h2>
        <ul>
          <li v-for="entry in tools" :key="entry.path">
            <router-link :to="entry.path" class="tool-link">
              <el-icon class="tool-glyph" aria-hidden="true"><component :is="entry.icon" /></el-icon>
              <span>{{ entry.label }}</span>
              <el-icon class="chevron" aria-hidden="true"><ArrowRight /></el-icon>
            </router-link>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Calendar, ChatDotRound, ChatLineRound, CircleCheck, Clock, Collection, EditPen, Finished, Memo, Notebook, Reading } from '@element-plus/icons-vue'
import { listProvisions, listViewpoints } from '../../api/content'
import { statsCategory, statsOverview } from '../../api/stats'
import TopicStudy from './TopicStudy.vue'

const date = new Date()
const dateKey = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(date)

const overview = ref(null)
const categories = ref([])
const recommendations = ref([])
const readingLoading = ref(true)
const readingError = ref(false)

const cards = computed(() => [
  { label: '累计学习', value: overview.value?.totalStudyMinutes ?? '—', unit: '分钟', icon: Clock },
  { label: '答题总数', value: overview.value?.totalAnswer ?? '—', unit: '题', icon: Finished },
  { label: '答题正确率', value: overview.value?.accuracy ?? '—', unit: '%', icon: CircleCheck },
  { label: '连续学习', value: overview.value?.streakDays ?? '—', unit: '天', icon: Calendar }
])
const libraries = [
  { path: '/provisions', label: '法条检索', icon: Notebook, theme: 'provisions' },
  { path: '/cases', label: '案例查询', icon: Collection, theme: 'cases' },
  { path: '/viewpoints', label: '学者观点', icon: Reading, theme: 'viewpoints' }
]
const tools = [
  { path: '/ai-chat', label: 'AI 问答', icon: ChatDotRound },
  { path: '/notes', label: '学习笔记', icon: Memo },
  { path: '/community', label: '交流天地', icon: ChatLineRound }
]
const weakCategory = computed(() => {
  const measured = categories.value.filter(item => item.total > 0)
  return measured.length ? measured.reduce((a, b) => a.accuracy < b.accuracy ? a : b).category : null
})

const recommendedSubject = ref('')
async function readings(subject) {
  const term = subject?.replace(/学$/, '')
  const [p, v] = await Promise.all([
    listProvisions({ ...(term ? { category: term } : { tag: '高频考点' }), size: 3 }),
    listViewpoints({ ...(term ? { keyword: term } : { tag: '案例分析' }), size: 3 })
  ])
  return [
    ...p.records.map(x => ({ typeLabel: '法条', to: '/provisions/' + x.id, title: x.lawName + ' ' + x.articleNo, summary: x.content })),
    ...v.records.map(x => ({ typeLabel: '学者观点', to: '/viewpoints/' + x.id, title: x.title, summary: x.summary }))
  ]
}

async function loadRecommendations() {
  readingLoading.value = true
  readingError.value = false
  try {
    categories.value = await statsCategory()
    const cat = weakCategory.value
    const matches = cat && !['待判定', '跨学科综合'].includes(cat) ? await readings(cat) : []
    recommendedSubject.value = matches.length ? cat : ''
    recommendations.value = matches.length ? matches : await readings()
  } catch {
    readingError.value = true
  } finally {
    readingLoading.value = false
  }
}

onMounted(() => {
  statsOverview().then(data => { overview.value = data }).catch(() => {})
  loadRecommendations()
})
</script>

<style scoped>
.dashboard { max-width: 74rem; margin: 0 auto; color: var(--lex-ink, #182438); }
.dashboard-heading, .panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.dashboard-heading { margin-bottom: 1.4rem; }
.today, .stat-label, .category, .reading-text p, .list-status { color: var(--lex-muted, #58687b); }
.today { font-size: .82rem; letter-spacing: .01em; }
h1 { margin: .15rem 0 0; font-size: clamp(2rem, 3vw, 2.4rem); font-weight: 690; line-height: 1.16; letter-spacing: -.045em; }
h2 { font-size: 1.12rem; line-height: 1.35; font-weight: 650; letter-spacing: -.018em; color: var(--lex-ink, #182438); }
.practice { display: inline-flex; align-items: center; justify-content: center; gap: .45rem; min-height: 2.45rem; padding: .4rem .95rem; color: white; background: var(--lex-accent, #275b97); border-radius: .68rem; font-weight: 570; white-space: nowrap; }
.metrics, .panel { background: var(--lex-surface, white); border: 1px solid var(--lex-line, #dce2e9); border-radius: var(--lex-radius, 14px); }
.metrics { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); padding: .95rem 0; }
.stat-card { display: block; padding: .17rem clamp(.9rem,2vw,1.55rem); min-width: 0; color: var(--lex-ink, #182438); }
.stat-card + .stat-card { border-inline-start: 1px solid var(--lex-line, #dce2e9); }
.stat-label { display: flex; align-items: center; gap: .4rem; font-size: .8rem; font-weight: 540; }
.stat-value { margin-top: .4rem; font-size: clamp(1.55rem,2.5vw,2rem); font-weight: 650; line-height: 1.1; letter-spacing: -.045em; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.stat-value small { margin-left: .25rem; color: var(--lex-muted, #58687b); font-size: .74rem; font-weight: 480; letter-spacing: 0; }
.libraries { margin: 1.65rem 0; }
.library-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: .78rem; margin-top: .7rem; }
.library-link { display: flex; align-items: center; gap: .7rem; padding: 1.15rem clamp(.65rem,1.5vw,1.15rem); color: var(--lex-ink, #182438); background: var(--lex-surface, white); border: 1px solid var(--lex-line, #dce2e9); border-radius: var(--lex-radius, 14px); font-size: .96rem; font-weight: 600; }
.library-glyph { border-radius: .65rem; width: 2.4rem; height: 2.4rem; font-size: 1.25rem; background: var(--glyph-bg); color: var(--glyph); }
.provisions { --glyph: #285b90; --glyph-bg: #e9f1fa; }
.cases      { --glyph: #76572e; --glyph-bg: #f6efe1; }
.viewpoints { --glyph: #476b5a; --glyph-bg: #e9f2ed; }
.chevron { flex: none; color: #708299; font-size: .86rem; margin-left: auto; }
.lower-grid { display: grid; grid-template-columns: minmax(0,1.75fr) minmax(14rem,1fr); gap: 1rem; align-items: start; }
.panel { min-width: 0; padding: 1.1rem 1.2rem .35rem; }
.panel-heading { flex-wrap: wrap; gap: .35rem; }
.category { font-size: .78rem; }
.reading-list, .tools ul { list-style: none; margin: .55rem 0 0; padding: 0; }
.reading-list li + li, .tools li + li { border-top: 1px solid var(--lex-line, #dce2e9); }
.reading-link, .tool-link { display: flex; align-items: center; gap: .75rem; padding: .78rem 0; color: var(--lex-ink, #182438); }
.reading-text { min-width: 0; overflow-wrap: anywhere; }
.kind { display: inline-block; color: var(--lex-accent, #275b97); font-size: .7rem; font-weight: 600; }
.reading-text strong, .reading-text p { display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden; }
.reading-text strong { margin: .12rem 0 .13rem; -webkit-line-clamp: 2; font-size: .95rem; line-height: 1.45; font-weight: 600; }
.reading-text p { -webkit-line-clamp: 1; font-size: .82rem; line-height: 1.55; }
.tool-link { font-size: .92rem; font-weight: 540; }
.tool-glyph { width: 1.85rem; height: 1.85rem; border-radius: .48rem; font-size: 1.05rem; color: var(--lex-accent, #275b97); background: #edf2f7; }
.list-status { min-height: 5rem; display: flex; gap: .5rem; align-items: center; justify-content: center; font-size: .84rem; }
@media (hover: hover) {
  .practice:hover { filter: brightness(1.08); }
  .library-link:hover { border-color: #a1b7cc; }
  .stat-card:hover .stat-label, .reading-link:hover strong, .tool-link:hover { color: var(--lex-accent, #275b97); }
}
.dashboard a:focus-visible { outline: 2px solid var(--lex-accent, #275b97); outline-offset: 2px; border-radius: .4rem; }
.dashboard a:active { opacity: .76; }
@media (prefers-reduced-motion: no-preference) {
  .practice, .library-link { transition: transform var(--lex-motion-fast) var(--lex-ease-out); }
  .practice:active, .library-link:active { transform: scale(.985); }
}
@media (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .library-link .chevron { transition: transform var(--lex-motion-fast) var(--lex-ease-out); }
  .library-link:is(:hover, :focus-visible) .chevron { transform: translateX(3px); }
}
@media (max-width: 980px) {
  .lower-grid { grid-template-columns: minmax(0,1.4fr) minmax(12rem,1fr); }
  .library-link { gap: .5rem; }
}
@media (max-width: 720px) {
  .metrics { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 1rem 0; }
  .stat-card:nth-child(3) { border-left: 0; }
  .lower-grid { grid-template-columns: 1fr; }
  .library-link { flex-direction: column; gap: .5rem; align-items: flex-start; padding: .8rem; }
  .library-link .chevron { display: none; }
}
@media (max-width: 460px) {
  .library-grid { gap: .42rem; }
  .library-link { font-size: .84rem; align-items: center; padding: .7rem .25rem; }
  .dashboard-heading { margin-bottom: 1rem; }
}
</style>
