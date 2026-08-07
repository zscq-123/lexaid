<template>
  <div v-loading="loading">
    <el-page-header @back="$router.back()" content="法条详情" />
    <el-card v-if="detail" shadow="never" class="detail-card">
      <div class="head">
        <div>
          <el-tag size="small">{{ detail.category }}</el-tag>
          <span class="title">{{ detail.lawName }} {{ detail.articleNo }}</span>
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
        <span v-if="detail.chapter">章节：{{ detail.chapter }}</span>
        <span v-if="detail.effectiveDate">生效日期：{{ detail.effectiveDate }}</span>
        <span v-if="detail.issuingAuthority">制定机关：{{ detail.issuingAuthority }}</span>
      </div>
      <div class="tags">
        <el-tag v-for="t in detail.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
      </div>
      <div class="content">{{ detail.content }}</div>
    </el-card>
    <el-card v-if="detail" shadow="never" class="detail-card">
      <NotePanel target-type="PROVISION" :target-id="route.params.id" />
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
import { getProvision, getProvisionRelated } from '../../api/content'
import { addFavorite, removeFavorite } from '../../api/favorite'
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
    const [d, r] = await Promise.all([getProvision(id), getProvisionRelated(id)])
    detail.value = d
    related.value = r
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (detail.value.favorite) {
    const list = await (await import('../../api/favorite')).listFavorites({ type: 'PROVISION' })
    const fav = list.find((f) => f.targetId === detail.value.id)
    if (fav) {
      await removeFavorite(fav.id)
      detail.value.favorite = false
      ElMessage.success('已取消收藏')
    }
  } else {
    await addFavorite({ targetType: 'PROVISION', targetId: detail.value.id })
    detail.value.favorite = true
    ElMessage.success('收藏成功')
  }
}

function askAi() {
  router.push({ path: '/ai-chat', query: { contextType: 'PROVISION', contextId: detail.value.id } })
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
.content {
  margin-top: 14px;
  line-height: 2;
  font-size: 15px;
  white-space: pre-wrap;
  border-left: 3px solid #2e5fa3;
  padding-left: 12px;
  color: #303133;
}
</style>
