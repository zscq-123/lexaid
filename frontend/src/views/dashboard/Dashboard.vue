<template>
  <div class="dashboard">
    <header class="dashboard-heading">
      <div>
        <time class="today" :datetime="dateKey">{{ today }}</time>
        <h1>选择你的备考方向</h1>
        <p class="heading-copy">按考试体系进入学习，题库、资料与进度集中在一个面板。</p>
      </div>
      <router-link class="practice" :to="trackLink('/exam/setup')">
        <el-icon aria-hidden="true"><EditPen /></el-icon>开始练习
      </router-link>
    </header>

    <div class="track-tabs" role="tablist" aria-label="考试类型">
      <button
        v-for="(track, index) in tracks"
        :id="'track-tab-' + track.key"
        :ref="el => setTabRef(el, index)"
        :key="track.key"
        type="button"
        role="tab"
        :aria-selected="activeTrackKey === track.key"
        :aria-controls="'track-panel-' + track.key"
        :tabindex="activeTrackKey === track.key ? 0 : -1"
        :class="{ active: activeTrackKey === track.key }"
        @click="selectTrack(track.key)"
        @keydown.left.prevent="moveTrack(index, -1)"
        @keydown.right.prevent="moveTrack(index, 1)"
        @keydown.home.prevent="moveTrack(index, -index)"
        @keydown.end.prevent="moveTrack(index, tracks.length - 1 - index)"
      >
        <span>{{ track.label }}</span>
        <small>{{ track.tabNote }}</small>
      </button>
    </div>

    <section
      :id="'track-panel-' + activeTrack.key"
      class="track-panel"
      role="tabpanel"
      :aria-labelledby="'track-tab-' + activeTrack.key"
    >
      <div class="track-hero" :class="activeTrack.key">
        <div class="hero-copy">
          <span class="hero-kicker">{{ activeTrack.kicker }}</span>
          <h2>{{ activeTrack.title }}</h2>
          <p>{{ activeTrack.description }}</p>
          <div class="hero-actions">
            <router-link class="hero-primary" :to="trackLink('/exam/setup')">开始刷题</router-link>
            <router-link class="hero-secondary" :to="trackLink('/mock-exams')">进入模拟考试</router-link>
          </div>
        </div>
        <div class="hero-visual" aria-hidden="true">
          <div class="book book-back"></div>
          <div class="book book-front">
            <span>{{ activeTrack.bookMark }}</span>
            <strong>{{ activeTrack.bookTitle }}</strong>
            <small>LEXAID</small>
          </div>
          <div class="hero-seal"><el-icon><Reading /></el-icon></div>
        </div>
      </div>

      <nav class="quick-actions" aria-label="备考快捷入口">
        <router-link v-for="item in quickActions" :key="item.label" :to="trackLink(item.path)">
          <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <el-icon class="chevron" aria-hidden="true"><ArrowRight /></el-icon>
        </router-link>
      </nav>

      <section class="subject-strip" aria-labelledby="subject-heading">
        <div class="section-heading compact">
          <div>
            <span class="section-eyebrow">考试范围</span>
            <h2 id="subject-heading">{{ activeTrack.subjectTitle }}</h2>
          </div>
          <router-link :to="trackLink('/exam/setup')">按科目组卷 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div class="subject-list">
          <span v-for="subject in activeTrack.subjects" :key="subject">{{ subject }}</span>
        </div>
      </section>

      <section class="prep-section" aria-labelledby="prep-heading">
        <div class="section-heading">
          <div>
            <span class="section-eyebrow">重点模块</span>
            <h2 id="prep-heading">{{ activeTrack.moduleTitle }}</h2>
          </div>
          <router-link :to="trackLink('/resources')">查看资料库 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div class="prep-scroll">
          <router-link
            v-for="(module, index) in activeTrack.modules"
            :key="module.title"
            class="prep-card"
            :to="trackLink(module.path)"
          >
            <div class="module-cover" :class="module.tone">
              <span class="module-index">0{{ index + 1 }}</span>
              <span class="module-badge">{{ module.badge }}</span>
              <el-icon aria-hidden="true"><component :is="module.icon" /></el-icon>
              <strong>{{ module.coverTitle }}</strong>
            </div>
            <div class="module-copy">
              <strong>{{ module.title }}</strong>
              <p>{{ module.description }}</p>
            </div>
          </router-link>
        </div>
      </section>
    </section>

    <section aria-labelledby="overview-heading" class="overview-section">
      <div class="section-heading">
        <div>
          <span class="section-eyebrow">学习进度</span>
          <h2 id="overview-heading">我的备考数据</h2>
        </div>
        <router-link to="/stats">查看详情 <el-icon><ArrowRight /></el-icon></router-link>
      </div>
      <div class="metrics">
        <router-link v-for="card in cards" :key="card.label" to="/stats" class="stat-card">
          <div class="stat-label"><el-icon aria-hidden="true"><component :is="card.icon" /></el-icon>{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}<small>{{ card.unit }}</small></div>
        </router-link>
      </div>
    </section>

    <TopicStudy />

    <section class="libraries">
      <div class="section-heading">
        <div>
          <span class="section-eyebrow">常用工具</span>
          <h2>资料检索</h2>
        </div>
      </div>
      <div class="library-grid">
        <router-link v-for="entry in libraries" :key="entry.path" class="library-link" :class="entry.theme" :to="entry.path">
          <el-icon class="library-glyph" aria-hidden="true"><component :is="entry.icon" /></el-icon>
          <span>{{ entry.label }}</span>
          <el-icon class="chevron" aria-hidden="true"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </section>

    <div class="lower-grid">
      <section class="panel reading-panel">
        <div class="panel-heading">
          <div>
            <span class="section-eyebrow">智能推荐</span>
            <h2>{{ recommendedSubject ? '薄弱科目推荐' : '阅读推荐' }}</h2>
          </div>
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
        <span class="section-eyebrow">快捷服务</span>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight, Calendar, ChatDotRound, ChatLineRound, CircleCheck, Clock, Collection,
  EditPen, Finished, FolderOpened, Memo, Notebook, Reading
} from '@element-plus/icons-vue'
import { listProvisions, listViewpoints } from '../../api/content'
import { statsCategory, statsOverview } from '../../api/stats'
import TopicStudy from './TopicStudy.vue'

const tracks = [
  {
    key: 'qualification',
    label: '法律职业资格考试',
    tabNote: '法考客观题与主观题',
    kicker: '2027 法考备考中心',
    title: '按考试阶段，完成系统训练',
    description: '从客观题基础巩固到主观题案例表达，串联法条、真题、错题与模拟考试。',
    bookMark: '法考',
    bookTitle: '通关学习手册',
    subjectTitle: '法律职业资格考试科目',
    moduleTitle: '法考训练专区',
    subjects: ['刑法', '民法', '行政法', '商经法', '理论法', '刑诉法', '民诉法', '三国法'],
    modules: [
      { badge: '每日训练', coverTitle: '客观题突破', title: '客观题专项练习', description: '按科目与难度组卷，及时核对解析。', path: '/exam/setup', icon: EditPen, tone: 'blue' },
      { badge: '案例思维', coverTitle: '主观题训练', title: '案例分析与法条定位', description: '围绕事实、依据和推理组织答案。', path: '/dashboard', icon: Reading, tone: 'orange' },
      { badge: '考场模式', coverTitle: '全真模拟', title: '限时模拟考试', description: '锁定计时，交卷后查看成绩与解析。', path: '/mock-exams', icon: Notebook, tone: 'violet' },
      { badge: '复盘巩固', coverTitle: '错题强化', title: '错题与薄弱点复习', description: '追踪错因，生成同考点迁移练习。', path: '/wrong-book', icon: CircleCheck, tone: 'green' }
    ]
  },
  {
    key: 'master',
    label: '法律硕士联考',
    tabNote: '专业基础课与综合课',
    kicker: '法律硕士联考学习中心',
    title: '基础课与综合课，分阶段推进',
    description: '覆盖民法、刑法、法理、宪法与中国法制史，适合按科目打底并持续复盘。',
    bookMark: '法硕',
    bookTitle: '联考核心笔记',
    subjectTitle: '法律硕士联考科目',
    moduleTitle: '法硕学习专区',
    subjects: ['民法学', '刑法学', '法理学', '宪法学', '中国法制史'],
    modules: [
      { badge: '专业基础课', coverTitle: '民法与刑法', title: '专业基础课训练', description: '梳理构成要件、请求权与高频考点。', path: '/exam/setup', icon: Collection, tone: 'orange' },
      { badge: '专业综合课', coverTitle: '理论法专题', title: '法理·宪法·法制史', description: '用专题框架串联概念、制度与史实。', path: '/provisions', icon: Reading, tone: 'blue' },
      { badge: '阶段检测', coverTitle: '联考模拟', title: '阶段组卷与计时', description: '按当前题库完成阶段性练习与复盘。', path: '/mock-exams', icon: Notebook, tone: 'violet' },
      { badge: '知识沉淀', coverTitle: '复习笔记', title: '笔记与标签整理', description: '归纳主观题表达，建立个人知识线索。', path: '/notes', icon: Memo, tone: 'green' }
    ]
  }
]

const route = useRoute()
const router = useRouter()
const validTrack = value => tracks.some(track => track.key === value)
const storedTrack = localStorage.getItem('lexaid_exam_track')
const activeTrackKey = ref(validTrack(route.query.track) ? route.query.track : validTrack(storedTrack) ? storedTrack : 'qualification')
const tabRefs = ref([])
const activeTrack = computed(() => tracks.find(track => track.key === activeTrackKey.value) || tracks[0])
const quickActions = [
  { path: '/exam/setup', label: '每日练习', icon: EditPen },
  { path: '/mock-exams', label: '模拟考试', icon: Notebook },
  { path: '/wrong-book', label: '错题本', icon: CircleCheck },
  { path: '/resources', label: '学习资料', icon: FolderOpened }
]

function trackLink(path) {
  return { path, query: { track: activeTrackKey.value } }
}
function setTabRef(element, index) {
  if (element) tabRefs.value[index] = element
}
function selectTrack(key, updateRoute = true) {
  if (!validTrack(key)) return
  activeTrackKey.value = key
  localStorage.setItem('lexaid_exam_track', key)
  if (updateRoute && route.path === '/dashboard' && route.query.track !== key) {
    router.replace({ query: { ...route.query, track: key } })
  }
}
async function moveTrack(index, delta) {
  const nextIndex = Math.max(0, Math.min(tracks.length - 1, index + delta))
  selectTrack(tracks[nextIndex].key)
  await nextTick()
  tabRefs.value[nextIndex]?.focus()
}
watch(() => route.query.track, value => {
  if (validTrack(value) && value !== activeTrackKey.value) selectTrack(value, false)
})

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
  { label: '实际作答（含重复）', value: overview.value?.totalAnswer ?? '—', unit: '次', icon: Finished },
  { label: '答题正确率', value: overview.value?.accuracy ?? '—', unit: '%', icon: CircleCheck },
  { label: '连续学习', value: overview.value?.streakDays ?? '—', unit: '天', icon: Calendar }
])
const libraries = [
  { path: '/provisions', label: '法条检索', icon: Notebook, theme: 'provisions' },
  { path: '/cases', label: '案例查询', icon: Collection, theme: 'cases' },
  { path: '/viewpoints', label: '学者观点', icon: Reading, theme: 'viewpoints' },
  { path: '/resources', label: '学习资源', icon: FolderOpened, theme: 'resources' }
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
  if (route.path === '/dashboard' && route.query.track !== activeTrackKey.value) {
    router.replace({ query: { ...route.query, track: activeTrackKey.value } })
  }
  statsOverview().then(data => { overview.value = data }).catch(() => {})
  loadRecommendations()
})
</script>

<style scoped>
.dashboard {
  max-width: 76rem;
  margin: 0 auto;
  padding: clamp(1.15rem, 2vw, 1.7rem);
  color: var(--lex-ink, #182438);
  background: #fff;
  border: 1px solid #e8edf3;
  border-radius: 1.5rem;
  box-shadow: 0 12px 36px #1634540a;
}
.dashboard-heading, .panel-heading, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.dashboard-heading { margin-bottom: 1.25rem; }
.today, .heading-copy, .stat-label, .category, .reading-text p, .list-status { color: var(--lex-muted, #58687b); }
.today, .section-eyebrow { font-size: .74rem; letter-spacing: .055em; font-weight: 650; }
.today { display: block; margin-bottom: .2rem; }
.section-eyebrow { color: #76869a; text-transform: uppercase; }
h1 { margin: .1rem 0 .18rem; font-size: clamp(1.85rem, 3vw, 2.45rem); font-weight: 720; line-height: 1.16; letter-spacing: -.048em; }
h2 { font-size: 1.18rem; line-height: 1.35; font-weight: 680; letter-spacing: -.025em; color: var(--lex-ink, #182438); }
.heading-copy { max-width: 38rem; font-size: .86rem; }
.practice { display: inline-flex; align-items: center; justify-content: center; gap: .45rem; min-height: 2.55rem; padding: .45rem 1rem; color: white; background: #2f83e7; border-radius: .82rem; font-weight: 650; white-space: nowrap; box-shadow: 0 8px 18px #2f83e72c; }

.track-tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 2rem; margin-bottom: 1rem; border-bottom: 1px solid #eef1f5; }
.track-tabs button {
  position: relative; display: grid; justify-items: start; gap: .12rem; padding: .8rem .25rem .92rem; border: 0;
  color: #6f7b8e; background: transparent; text-align: left; cursor: pointer;
  touch-action: manipulation; user-select: none; -webkit-user-select: none; -webkit-touch-callout: none;
}
.track-tabs button::after { content: ''; position: absolute; left: 0; right: 100%; bottom: -1px; height: 3px; border-radius: 3px; background: #2f8ff4; }
.track-tabs button span { font-size: clamp(1rem, 1.8vw, 1.24rem); font-weight: 650; letter-spacing: -.025em; }
.track-tabs button small { font-size: .73rem; color: #a0aaba; }
.track-tabs button.active { color: #17243a; }
.track-tabs button.active small { color: #68809c; }
.track-tabs button.active::after { right: 0; }

.track-panel { min-width: 0; }
.track-hero {
  --hero-accent: #ff7c37; --hero-strong: #214f85;
  position: relative; min-height: 15.5rem; overflow: hidden; display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(13rem, .7fr);
  align-items: center; gap: 1.5rem; padding: clamp(1.4rem, 3vw, 2.3rem); border: 1px solid #e0ebf7; border-radius: 1.35rem;
  background: radial-gradient(circle at 86% 16%, #fff 0 4%, transparent 4.3%),
    linear-gradient(122deg, #f1f7ff 0%, #e6f2ff 58%, #fff1df 100%);
}
.track-hero.master { --hero-accent: #735ce8; --hero-strong: #59429a; background: radial-gradient(circle at 86% 16%, #fff 0 4%, transparent 4.3%), linear-gradient(122deg, #f7f4ff 0%, #edf1ff 56%, #fff4de 100%); }
.track-hero::before, .track-hero::after { content: ''; position: absolute; border-radius: 50%; border: 1px solid #fff9; }
.track-hero::before { width: 15rem; height: 15rem; right: -3rem; top: -6rem; }
.track-hero::after { width: 8rem; height: 8rem; right: 18%; bottom: -5rem; }
.hero-copy { position: relative; z-index: 2; max-width: 42rem; }
.hero-kicker { display: inline-flex; align-items: center; min-height: 1.75rem; padding: 0 .65rem; border-radius: 999px; color: var(--hero-strong); background: #ffffffb8; font-size: .75rem; font-weight: 700; box-shadow: inset 0 0 0 1px #fff; }
.hero-copy h2 { max-width: 17ch; margin: .75rem 0 .5rem; font-size: clamp(1.65rem, 3.2vw, 2.5rem); line-height: 1.15; font-weight: 760; letter-spacing: -.05em; }
.hero-copy p { max-width: 40rem; color: #52657d; font-size: .92rem; line-height: 1.75; }
.hero-actions { display: flex; flex-wrap: wrap; gap: .65rem; margin-top: 1.1rem; }
.hero-actions a { min-height: 2.45rem; display: inline-flex; align-items: center; justify-content: center; padding: .45rem 1rem; border-radius: .75rem; font-weight: 650; }
.hero-primary { color: #fff; background: var(--hero-accent); box-shadow: 0 8px 18px color-mix(in srgb, var(--hero-accent) 28%, transparent); }
.hero-secondary { color: var(--hero-strong); background: #ffffffc9; box-shadow: inset 0 0 0 1px #ffffffde; }
.hero-visual { position: relative; z-index: 1; min-height: 12rem; align-self: stretch; }
.book { position: absolute; right: 12%; width: 8.7rem; height: 11.4rem; border-radius: .7rem 1rem 1rem .7rem; box-shadow: 0 16px 30px #25476c26; }
.book-back { top: 12%; transform: rotate(10deg); background: #fff; border: 1px solid #fff; }
.book-front { top: 6%; display: flex; flex-direction: column; padding: 1rem; color: #fff; background: linear-gradient(155deg, var(--hero-strong), var(--hero-accent)); transform: rotate(-4deg); }
.book-front::before { content: ''; position: absolute; inset: .45rem auto .45rem .45rem; width: 2px; border-radius: 2px; background: #ffffff4d; }
.book-front span { font-size: .75rem; font-weight: 720; }
.book-front strong { margin: auto 0; font-size: 1.35rem; line-height: 1.25; letter-spacing: .05em; }
.book-front small { font-size: .62rem; letter-spacing: .14em; opacity: .8; }
.hero-seal { position: absolute; right: 0; bottom: 4%; display: grid; place-items: center; width: 4.6rem; height: 4.6rem; border: .35rem solid #fff; border-radius: 50%; color: #fff; background: var(--hero-accent); box-shadow: 0 8px 20px #2447692b; font-size: 1.8rem; }

.quick-actions { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .7rem; margin: .95rem 0 1.7rem; }
.quick-actions a { min-width: 0; display: flex; align-items: center; gap: .55rem; min-height: 3.2rem; padding: .65rem .8rem; border: 1px solid transparent; border-radius: 1rem; color: #526174; background: #f6f8fb; font-weight: 600; }
.quick-actions a > .el-icon:first-child { width: 1.8rem; height: 1.8rem; flex: none; border-radius: .55rem; color: #3186e6; background: #e6f2ff; }
.chevron { flex: none; color: #8c9aab; font-size: .83rem; margin-left: auto; }

.subject-strip { margin-bottom: 1.8rem; padding: 1.1rem 1.15rem 1.2rem; border: 1px solid #ebeff4; border-radius: 1.1rem; background: #fbfcfe; }
.section-heading { margin-bottom: .8rem; }
.section-heading.compact { margin-bottom: .7rem; }
.section-heading > div { min-width: 0; }
.section-heading h2 { margin-top: .12rem; }
.section-heading > a { display: inline-flex; align-items: center; gap: .2rem; flex: none; color: #77869a; font-size: .78rem; }
.subject-list { display: flex; flex-wrap: wrap; gap: .5rem; }
.subject-list span { padding: .44rem .75rem; border: 1px solid #e0e7ef; border-radius: 999px; color: #43556c; background: #fff; font-size: .8rem; font-weight: 580; }

.prep-section { margin-bottom: 1.85rem; }
.prep-scroll { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .85rem; }
.prep-card { min-width: 0; color: var(--lex-ink); }
.module-cover { position: relative; min-height: 10.5rem; overflow: hidden; display: flex; flex-direction: column; justify-content: flex-end; padding: 1rem; border-radius: 1rem; color: #fff; background: #378ee9; }
.module-cover::after { content: ''; position: absolute; width: 7rem; height: 7rem; top: -2rem; right: -1.5rem; border: 1.25rem solid #ffffff1f; border-radius: 50%; }
.module-cover.orange { background: linear-gradient(145deg, #ff9a3e, #f36f45); }
.module-cover.blue { background: linear-gradient(145deg, #2e9bef, #3371de); }
.module-cover.violet { background: linear-gradient(145deg, #6f6ee8, #8959c8); }
.module-cover.green { background: linear-gradient(145deg, #37a982, #2f8178); }
.module-cover > .el-icon { position: relative; z-index: 1; margin-bottom: .8rem; font-size: 2rem; }
.module-cover strong { position: relative; z-index: 1; max-width: 7em; font-size: 1.2rem; line-height: 1.3; letter-spacing: .02em; }
.module-index { position: absolute; right: .65rem; bottom: -.35rem; color: #ffffff18; font-size: 5.5rem; font-weight: 800; line-height: 1; }
.module-badge { position: absolute; z-index: 1; top: .7rem; left: .7rem; padding: .25rem .55rem; border-radius: .5rem; background: #fff; color: #4d6685; font-size: .68rem; font-weight: 720; }
.module-copy { padding: .72rem .18rem 0; }
.module-copy > strong { display: block; font-size: .94rem; font-weight: 660; }
.module-copy p { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; margin-top: .22rem; color: #7a8899; font-size: .77rem; line-height: 1.55; }

.overview-section { margin: 1.8rem 0; }
.metrics, .panel { background: #fff; border: 1px solid #e5eaf0; border-radius: 1rem; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 1rem 0; }
.stat-card { display: block; min-width: 0; padding: .18rem clamp(.9rem, 2vw, 1.55rem); color: var(--lex-ink, #182438); }
.stat-card + .stat-card { border-inline-start: 1px solid #e7ebf0; }
.stat-label { display: flex; align-items: center; gap: .4rem; font-size: .76rem; font-weight: 560; }
.stat-value { margin-top: .4rem; font-size: clamp(1.5rem, 2.5vw, 2rem); font-weight: 690; line-height: 1.1; letter-spacing: -.045em; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.stat-value small { margin-left: .25rem; color: var(--lex-muted, #58687b); font-size: .72rem; font-weight: 480; letter-spacing: 0; }

.libraries { margin: 1.8rem 0; }
.library-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .78rem; }
.library-link { display: flex; align-items: center; gap: .7rem; padding: 1rem clamp(.65rem, 1.5vw, 1.05rem); color: var(--lex-ink, #182438); background: #fbfcfe; border: 1px solid #e6ebf1; border-radius: 1rem; font-size: .92rem; font-weight: 620; }
.library-glyph { border-radius: .65rem; width: 2.4rem; height: 2.4rem; font-size: 1.25rem; background: var(--glyph-bg); color: var(--glyph); }
.provisions { --glyph: #285b90; --glyph-bg: #e9f1fa; }
.cases { --glyph: #76572e; --glyph-bg: #f6efe1; }
.viewpoints { --glyph: #476b5a; --glyph-bg: #e9f2ed; }
.resources { --glyph: #6b4f8b; --glyph-bg: #f0ebf7; }

.lower-grid { display: grid; grid-template-columns: minmax(0, 1.75fr) minmax(14rem, 1fr); gap: 1rem; align-items: start; }
.panel { min-width: 0; padding: 1.1rem 1.2rem .35rem; }
.panel-heading { flex-wrap: wrap; gap: .35rem; }
.category { font-size: .75rem; }
.reading-list, .tools ul { list-style: none; margin: .55rem 0 0; padding: 0; }
.reading-list li + li, .tools li + li { border-top: 1px solid #e7ebf0; }
.reading-link, .tool-link { display: flex; align-items: center; gap: .75rem; padding: .8rem 0; color: var(--lex-ink, #182438); }
.reading-text { min-width: 0; overflow-wrap: anywhere; }
.kind { display: inline-block; color: #2f83e7; font-size: .68rem; font-weight: 650; }
.reading-text strong, .reading-text p { display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden; }
.reading-text strong { margin: .12rem 0 .13rem; -webkit-line-clamp: 2; font-size: .93rem; line-height: 1.45; font-weight: 620; }
.reading-text p { -webkit-line-clamp: 1; font-size: .79rem; line-height: 1.55; }
.tool-link { font-size: .9rem; font-weight: 560; }
.tool-glyph { width: 1.85rem; height: 1.85rem; border-radius: .48rem; font-size: 1.05rem; color: #2f83e7; background: #edf5fe; }
.list-status { min-height: 5rem; display: flex; gap: .5rem; align-items: center; justify-content: center; font-size: .82rem; }

.dashboard a, .dashboard button { touch-action: manipulation; }
.dashboard a:focus-visible, .dashboard button:focus-visible { outline: 3px solid #2f8ff4; outline-offset: 3px; }
.dashboard a:active, .dashboard button:active { opacity: .78; }
@media (hover: hover) and (pointer: fine) {
  .practice:hover, .hero-primary:hover { filter: brightness(1.05); }
  .quick-actions a:hover, .library-link:hover { border-color: #b9ccdf; background: #f5f9fe; }
  .prep-card:hover .module-cover { transform: translateY(-3px); box-shadow: 0 12px 24px #234a7221; }
  .stat-card:hover .stat-label, .reading-link:hover strong, .tool-link:hover { color: #2f83e7; }
}
@media (prefers-reduced-motion: no-preference) {
  .track-tabs button::after, .practice, .hero-actions a, .quick-actions a, .library-link, .module-cover { transition: transform var(--lex-motion-fast) var(--lex-ease-out), opacity var(--lex-motion-fast), box-shadow var(--lex-motion-fast), right var(--lex-motion-enter) var(--lex-ease-out); }
  .practice:active, .hero-actions a:active, .quick-actions a:active, .library-link:active, .prep-card:active .module-cover { transform: scale(.975); }
}

@media (max-width: 980px) {
  .track-hero { grid-template-columns: minmax(0, 1.35fr) minmax(10rem, .65fr); }
  .prep-scroll { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .lower-grid { grid-template-columns: minmax(0, 1.4fr) minmax(12rem, 1fr); }
  .library-link { gap: .5rem; }
}
@media (max-width: 720px) {
  .dashboard { max-width: none; min-height: 100%; margin: -12px; padding: 1rem 12px 1.6rem; border: 0; border-radius: 0; box-shadow: none; }
  .dashboard-heading { align-items: flex-start; margin-bottom: .75rem; }
  .dashboard-heading h1 { font-size: 1.45rem; }
  .heading-copy { display: none; }
  .practice { min-height: 2.3rem; padding-inline: .75rem; font-size: .78rem; }
  .track-tabs { gap: .55rem; margin-inline: -12px; padding-inline: 12px; }
  .track-tabs button { padding-block: .7rem .8rem; }
  .track-tabs button span { font-size: .94rem; }
  .track-tabs button small { font-size: .65rem; }
  .track-hero { min-height: 17rem; grid-template-columns: minmax(0, 1fr) 7rem; gap: .25rem; padding: 1.25rem; border-radius: 1.15rem; }
  .hero-copy h2 { max-width: 12ch; margin-top: .6rem; font-size: 1.72rem; }
  .hero-copy p { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; font-size: .82rem; }
  .hero-actions { gap: .45rem; }
  .hero-actions a { min-height: 2.3rem; padding-inline: .75rem; font-size: .77rem; }
  .hero-visual { min-height: 10rem; margin-right: -1rem; }
  .book { right: -5%; width: 6.2rem; height: 8.5rem; }
  .book-front { padding: .72rem; }
  .book-front strong { font-size: 1rem; }
  .hero-seal { right: -8%; bottom: 0; width: 3.6rem; height: 3.6rem; border-width: .25rem; font-size: 1.4rem; }
  .quick-actions { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .5rem; margin-bottom: 1.35rem; }
  .quick-actions a { min-height: 2.8rem; padding: .5rem .65rem; border-radius: .8rem; font-size: .8rem; }
  .quick-actions a > .el-icon:first-child { width: 1.65rem; height: 1.65rem; }
  .subject-strip { margin-bottom: 1.35rem; padding: .9rem; }
  .section-heading > a { font-size: .72rem; }
  .prep-scroll {
    grid-template-columns: none; grid-auto-flow: column; grid-auto-columns: minmax(10.4rem, 72%); gap: .7rem;
    overflow-x: auto; overscroll-behavior-inline: contain; scroll-snap-type: x proximity; scrollbar-width: none;
    margin-inline: -12px; padding: 0 12px .35rem; -webkit-overflow-scrolling: touch;
  }
  .prep-scroll::-webkit-scrollbar { display: none; }
  .prep-card { scroll-snap-align: start; }
  .module-cover { min-height: 9.5rem; }
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .9rem 0; }
  .stat-card:nth-child(3) { border-left: 0; }
  .lower-grid { grid-template-columns: 1fr; }
  .library-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .library-link { flex-direction: column; gap: .45rem; align-items: center; padding: .75rem .4rem; font-size: .82rem; }
  .library-link .chevron { display: none; }
}
@media (max-width: 390px) {
  .dashboard-heading .practice .el-icon { display: none; }
  .track-tabs button small { display: none; }
  .track-hero { grid-template-columns: minmax(0, 1fr) 5.4rem; padding: 1.05rem; }
  .hero-kicker { font-size: .68rem; }
  .hero-copy h2 { font-size: 1.5rem; }
  .hero-secondary { display: none !important; }
  .book { right: -18%; width: 5.6rem; height: 7.6rem; }
  .book-front strong { font-size: .86rem; }
  .hero-seal { right: -18%; }
}
</style>
