<template>
  <div v-loading="loading" :class="{ 'paper-view': isPaper }">
    <div class="view-toolbar">
      <el-page-header @back="$router.back()" :content="isPaper ? '论文详情' : '观点详情'" />
      <div v-if="detail" class="actions">
        <el-button :type="detail.favorite ? 'warning' : 'default'" @click="toggleFavorite">
          <el-icon><StarFilled v-if="detail.favorite" /><Star v-else /></el-icon>&nbsp;
          {{ detail.favorite ? '已收藏' : '收藏' }}
        </el-button>
        <el-button type="primary" @click="askAi">针对本内容提问</el-button>
      </div>
    </div>
    <PaperDocument v-if="detail && isPaper" :paper="detail" :source-link="sourceLink" />
    <el-card v-else-if="detail" shadow="never" class="detail-card">
      <div>
        <el-tag size="small" type="warning">{{ detail.scholarName }}</el-tag>
        <span class="title">{{ detail.title }}</span>
      </div>
      <div class="meta">
        <span v-if="detail.source">出处：{{ detail.source }}</span>
        <span v-if="detail.publishYear">发表年份：{{ detail.publishYear }}</span>
        <span v-if="detail.doi">DOI：{{ detail.doi }}</span>
      </div>
      <div class="tags">
        <el-tag v-for="t in detail.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
      </div>
      <div v-if="detail.summary" class="section">
        <div class="section-title">观点摘要</div>
        <div class="content">{{ detail.summary }}</div>
      </div>
      <div v-if="detail.content" class="section">
        <div class="section-title">观点正文</div>
        <div class="content">{{ detail.content }}</div>
      </div>
    </el-card>
    <el-card v-if="detail" shadow="never" class="detail-card">
      <NotePanel :key="detail.id" target-type="VIEWPOINT" :target-id="detail.id" />
    </el-card>
    <el-card v-if="hasRelated" shadow="never" class="detail-card">
      <RelatedGroups :groups="related" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { getViewpoint, getViewpointRelated } from '../../api/content'
import { addFavorite, listFavorites, removeFavorite } from '../../api/favorite'
import NotePanel from '../../components/NotePanel.vue'
import RelatedGroups from '../../components/RelatedGroups.vue'
import PaperDocument from './PaperDocument.vue'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const related = ref(null)
const loading = ref(true)
const isPaper = computed(() => ['author_abstract', 'metadata_only'].includes(detail.value?.summaryKind))
const hasRelated = computed(() => Object.values(related.value || {}).some(items => Array.isArray(items) && items.length))
const sourceLink = computed(() => {
  const url = detail.value?.sourceUrl
  return /^https?:\/\//i.test(url ?? '') ? url : null
})

watch(() => route.params.id, async (id, _, onCleanup) => {
  if (!id) return
  const controller = new AbortController()
  const { signal } = controller
  onCleanup(() => controller.abort())
  detail.value = null
  related.value = null
  loading.value = true
  try {
    const [d, r] = await Promise.all([getViewpoint(id, signal), getViewpointRelated(id, signal)])
    if (!signal.aborted) {
      detail.value = d
      related.value = r
    }
  } catch (error) {
    if (!signal.aborted) throw error
  } finally {
    if (!signal.aborted) loading.value = false
  }
}, { immediate: true })

async function toggleFavorite() {
  if (detail.value.favorite) {
    const favs = await listFavorites({ type: 'VIEWPOINT' })
    const fav = favs.find((f) => f.targetId === detail.value.id)
    if (fav) {
      await removeFavorite(fav.id)
      detail.value.favorite = false
      ElMessage.success('已取消收藏')
    }
  } else {
    await addFavorite({ targetType: 'VIEWPOINT', targetId: detail.value.id })
    detail.value.favorite = true
    ElMessage.success('收藏成功')
  }
}

function askAi() {
  router.push({ path: '/ai-chat', query: { contextType: 'VIEWPOINT', contextId: detail.value.id } })
}

</script>

<style scoped>
.detail-card {
  margin-top: 12px;
  border-radius: 8px;
}
.paper-view {
  max-width: 880px;
  margin-inline: auto;
}
.view-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.title {
  font-size: 20px;
  font-weight: 700;
  overflow-wrap: anywhere;
}
.el-tag + .title {
  margin-left: 8px;
}
.meta {
  color: #909399;
  font-size: 13px;
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  overflow-wrap: anywhere;
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
  overflow-wrap: anywhere;
}
</style>
