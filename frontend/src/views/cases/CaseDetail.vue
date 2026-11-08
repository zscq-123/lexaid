<template>
  <div v-loading="loading">
    <el-page-header @back="$router.back()" content="案例详情" />
    <el-card v-if="detail" shadow="never" class="detail-card">
      <div class="head">
        <div>
          <el-tag size="small" type="info" effect="plain">{{ jurisdictionLabel(detail.jurisdiction) }}</el-tag>
          <el-tag size="small">{{ detail.caseType }}</el-tag>
          <span class="title">{{ detail.title }}</span>
        </div>
        <div class="actions">
          <el-button :type="detail.favorite ? 'warning' : 'default'" @click="toggleFavorite">
            <el-icon><StarFilled v-if="detail.favorite" /><Star v-else /></el-icon>&nbsp;
            {{ detail.favorite ? '已收藏' : '收藏' }}
          </el-button>
          <el-button type="primary" @click="askAi">针对本内容提问</el-button>
        </div>
      </div>
      <div class="meta">
        <span v-if="detail.caseNo">案号：{{ detail.caseNo }}</span>
        <span v-if="detail.languageCode">语言：{{ detail.languageCode }}</span>
        <span v-if="detail.status">状态：{{ detail.status }}</span>
        <span>审理法院：{{ detail.court || '未注明' }}</span>
        <span v-if="detail.judgeDate">裁判日期：{{ detail.judgeDate }}</span>
        <span v-if="detail.procedure">程序：{{ detail.procedure }}</span>
      </div>
      <div v-if="detail.sourceDataset || detail.licenseName" class="source">
        来源：{{ detail.sourceDataset || '未标注' }}
        <span v-if="detail.licenseName"> · 许可：{{ detail.licenseName }}</span>
        <a v-if="detail.sourceUrl" :href="detail.sourceUrl" target="_blank" rel="noopener noreferrer">查看原文</a>
      </div>
      <div class="tags">
        <el-tag v-for="t in detail.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
      </div>
      <div class="section">
        <div class="section-title">案情摘要</div>
        <div class="content">{{ detail.summary }}</div>
      </div>
      <div class="section">
        <div class="section-title">裁判要旨</div>
        <div class="content">{{ detail.holding }}</div>
      </div>
      <div v-if="detail.content && detail.content !== detail.summary" class="section">
        <div class="section-title">案例全文 / 数据集正文</div>
        <div class="content">{{ detail.content }}</div>
      </div>
    </el-card>
    <el-card v-if="detail" shadow="never" class="detail-card">
      <NotePanel target-type="CASE" :target-id="route.params.id" />
    </el-card>
    <el-card v-if="related" shadow="never" class="detail-card">
      <RelatedGroups :groups="related" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { getCase, getCaseRelated } from '../../api/content'
import { addFavorite, listFavorites, removeFavorite } from '../../api/favorite'
import NotePanel from '../../components/NotePanel.vue'
import RelatedGroups from '../../components/RelatedGroups.vue'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const related = ref(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    const [d, r] = await Promise.all([getCase(id), getCaseRelated(id)])
    detail.value = d
    related.value = r
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (detail.value.favorite) {
    const favs = await listFavorites({ type: 'CASE' })
    const fav = favs.find((f) => f.targetId === detail.value.id)
    if (fav) {
      await removeFavorite(fav.id)
      detail.value.favorite = false
      ElMessage.success('已取消收藏')
    }
  } else {
    await addFavorite({ targetType: 'CASE', targetId: detail.value.id })
    detail.value.favorite = true
    ElMessage.success('收藏成功')
  }
}

function askAi() {
  router.push({ path: '/ai-chat', query: { contextType: 'CASE', contextId: detail.value.id } })
}

function jurisdictionLabel(value) {
  return { CN: '中国', US: '美国', EU: '欧盟', GB: '英国', ECHR: '欧洲人权法院', INTL: '国际' }[value] || value || '未标注'
}

onMounted(load)
</script>

<style scoped>
.detail-card {
  margin-top: 12px;
  border-radius: 8px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 20px;
  font-weight: 700;
  margin-left: 8px;
  overflow-wrap: anywhere;
}
.meta {
  color: #909399;
  font-size: 13px;
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}
.source {
  margin-top: 10px;
  color: #606266;
  font-size: 13px;
}
.source a {
  margin-left: 10px;
  color: #2e5fa3;
}
.tags {
  margin-top: 10px;
  display: flex;
  gap: 6px;
}
.section {
  margin-top: 14px;
}
.section-title {
  font-weight: 600;
  color: #2e5fa3;
  margin-bottom: 6px;
}
.content {
  line-height: 1.9;
  white-space: pre-wrap;
}
</style>
