<template>
  <section class="post-detail">
    <el-page-header content="帖子详情" @back="$router.back()">
      <template v-if="post" #extra>
        <el-button size="small" plain type="primary" @click="askAboutPost">针对帖子提问</el-button>
      </template>
    </el-page-header>
    <div v-if="loading" role="status">正在读取帖子…</div>
    <CommunityPost v-else-if="post" :key="post.id" :post="post" @contact="openContact"
      @change="changes => Object.assign(post, changes)" @deleted="$router.replace('/community')" />
    <p v-else role="alert">帖子已删除、不可查看或加载失败。 <el-button link type="primary" @click="load">重试</el-button></p>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPost } from '../../api/community-posts'
import CommunityPost from './CommunityPost.vue'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const loading = ref(true)
let sequence = 0

async function load() {
  const request = ++sequence
  post.value = null
  loading.value = true
  try {
    const data = await getPost(route.params.id)
    if (request === sequence) post.value = data
  } catch {
    // The page also provides a retry after a failed lookup.
  } finally {
    if (request === sequence) loading.value = false
  }
}
function openContact(id) {
  router.push({ path: '/community', query: { member: String(id) } })
}
function askAboutPost() {
  if (post.value) router.push({ path: '/ai-chat', query: { contextType: 'POST', contextId: String(post.value.id) } })
}
watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.post-detail { max-width: 780px; margin: 0 auto; display: grid; gap: 16px; }
</style>
