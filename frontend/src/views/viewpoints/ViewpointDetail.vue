<template>
  <div v-loading="loading">
    <el-page-header @back="$router.back()" content="观点详情" />
    <el-card v-if="detail" shadow="never" class="detail-card">
      <div class="head">
        <div>
          <el-tag size="small" type="warning">{{ detail.scholarName }}</el-tag>
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
        <span v-if="detail.source">出处：{{ detail.source }}</span>
        <span v-if="detail.publishYear">发表年份：{{ detail.publishYear }}</span>
      </div>
      <div class="tags">
        <el-tag v-for="t in detail.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
      </div>
      <div class="section">
        <div class="section-title">观点摘要</div>
        <div class="content">{{ detail.summary }}</div>
      </div>
      <div v-if="detail.content" class="section">
        <div class="section-title">观点正文</div>
        <div class="content">{{ detail.content }}</div>
      </div>
    </el-card>
    <el-card v-if="detail" shadow="never" class="detail-card">
      <NotePanel target-type="VIEWPOINT" :target-id="route.params.id" />
    </el-card>
    <el-card v-if="related" shadow="never" class="detail-card">
      <RelatedGroups :groups="related" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { getViewpoint, getViewpointRelated } from '../../api/content'
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
    const [d, r] = await Promise.all([getViewpoint(id), getViewpointRelated(id)])
    detail.value = d
    related.value = r
  } finally {
    loading.value = false
  }
}

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
}
.meta {
  color: #909399;
  font-size: 13px;
  margin-top: 10px;
  display: flex;
  gap: 20px;
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
