<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="quick">
      <el-col v-for="q in quickEntries" :key="q.path" :span="4">
        <el-card class="quick-card" shadow="hover" @click="$router.push(q.path)">
          <el-icon :size="26" color="#2e5fa3"><component :is="q.icon" /></el-icon>
          <div>{{ q.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="weakCategory" class="weak" shadow="hover">
      <template #header>
        <div class="weak-header">
          <span>薄弱类别推荐（{{ weakCategory }}）</span>
          <el-button link type="primary" @click="$router.push('/exam/setup')">去练习 →</el-button>
        </div>
      </template>
      <el-empty v-if="recommendations.length === 0" :image-size="80" description="暂无推荐" />
      <el-row v-else :gutter="12">
        <el-col v-for="r in recommendations" :key="r.type + r.id" :span="8">
          <el-card class="rec-card" shadow="hover" @click="go(r)">
            <el-tag size="small" :type="tagType(r.type)">{{ typeLabel(r.type) }}</el-tag>
            <div class="rec-title">{{ r.title }}</div>
            <div class="rec-summary">{{ r.summary }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listProvisions, listViewpoints } from '../../api/content'
import { statsCategory, statsOverview } from '../../api/stats'

const router = useRouter()
const overview = ref(null)
const categories = ref([])
const recommendations = ref([])

const cards = computed(() => [
  { label: '累计学习时长（分钟）', value: overview.value?.totalStudyMinutes ?? 0 },
  { label: '答题总数', value: overview.value?.totalAnswer ?? 0 },
  { label: '答题正确率', value: (overview.value?.accuracy ?? 0) + '%' },
  { label: '连续学习天数', value: overview.value?.streakDays ?? 0 }
])

const quickEntries = [
  { path: '/provisions', label: '法条检索', icon: 'Notebook' },
  { path: '/cases', label: '案例查询', icon: 'Collection' },
  { path: '/viewpoints', label: '学者观点', icon: 'Reading' },
  { path: '/exam/setup', label: '模拟答题', icon: 'EditPen' },
  { path: '/notes', label: '学习笔记', icon: 'Memo' },
  { path: '/ai-chat', label: 'AI 问答', icon: 'ChatDotRound' }
]

const weakCategory = computed(() => {
  const withData = categories.value.filter((c) => c.total > 0)
  if (withData.length === 0) return '高频考点'
  return withData.reduce((min, c) => (c.accuracy < min.accuracy ? c : min)).category
})

async function load() {
  overview.value = await statsOverview()
  categories.value = await statsCategory()
  const cat = weakCategory.value
  if (cat === '高频考点') {
    const [p, v] = await Promise.all([
      listProvisions({ tag: '高频考点', size: 3 }),
      listViewpoints({ tag: '案例分析', size: 3 })
    ])
    recommendations.value = [
      ...p.records.map((x) => ({ type: 'PROVISION', id: x.id, title: x.lawName + ' ' + x.articleNo, summary: x.content })),
      ...v.records.map((x) => ({ type: 'VIEWPOINT', id: x.id, title: x.title, summary: x.summary }))
    ]
  } else {
    const [p, v] = await Promise.all([
      listProvisions({ category: cat, size: 3 }),
      listViewpoints({ keyword: cat, size: 3 })
    ])
    recommendations.value = [
      ...p.records.map((x) => ({ type: 'PROVISION', id: x.id, title: x.lawName + ' ' + x.articleNo, summary: x.content })),
      ...v.records.map((x) => ({ type: 'VIEWPOINT', id: x.id, title: x.title, summary: x.summary }))
    ]
  }
}

function go(r) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (map[r.type]) router.push(map[r.type] + r.id)
}
function typeLabel(t) {
  return { PROVISION: '法条', CASE: '案例', VIEWPOINT: '观点', QUESTION: '题目' }[t] || t
}
function tagType(t) {
  return { PROVISION: '', CASE: 'success', VIEWPOINT: 'warning', QUESTION: 'info' }[t] || ''
}

onMounted(load)
</script>

<style scoped>
.stat-card {
  text-align: center;
  border-radius: 8px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #2e5fa3;
}
.stat-label {
  color: #909399;
  margin-top: 4px;
  font-size: 13px;
}
.quick {
  margin-top: 16px;
}
.quick-card {
  text-align: center;
  cursor: pointer;
  border-radius: 8px;
  color: #303133;
}
.quick-card div {
  margin-top: 6px;
}
.weak {
  margin-top: 16px;
  border-radius: 8px;
}
.weak-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rec-card {
  cursor: pointer;
  border-radius: 8px;
  margin-bottom: 8px;
}
.rec-title {
  font-weight: 600;
  margin-top: 8px;
  line-height: 1.5;
}
.rec-summary {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
