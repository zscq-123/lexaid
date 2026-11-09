<template>
  <section class="post-detail">
    <el-page-header content="帖子详情" @back="$router.back()">
      <template v-if="post" #extra>
        <el-button size="small" plain type="primary" @click="askAboutPost">针对帖子提问</el-button>
      </template>
    </el-page-header>
    <div v-if="loading" role="status">正在读取帖子…</div>
    <template v-else-if="post">
      <CommunityPost ref="article" :key="post.id" :post="post" detail @contact="openContact"
        @change="changes => Object.assign(post, changes)" @deleted="$router.replace('/community')" />
      <DiscussionSummary :post="post" @source="source => article?.revealSource(source)" />
    </template>
    <p v-else role="alert">帖子已删除、不可查看或加载失败。 <el-button link type="primary" @click="load">重试</el-button></p>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPost } from '../../api/community-posts'
import CommunityPost from './CommunityPost.vue'
import DiscussionSummary from './DiscussionSummary.vue'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const article = ref(null)
const user = useUserStore()
const loading = ref(true)
let postRequest

async function load() {
  postRequest?.abort()
  const request = new AbortController()
  postRequest = request
  post.value = null
  if (!user.token || !route.params.id) { loading.value = false; return }
  loading.value = true
  try {
    const data = await getPost(route.params.id, request.signal)
    if (!request.signal.aborted) post.value = data
  } catch {
    // The page also provides a retry after a failed lookup.
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
watch([post, () => route.hash], async () => {
  if (post.value && route.hash === '#discussion-summary') {
    await nextTick()
    const panel = document.getElementById('discussion-summary')
    panel?.focus({ preventScroll: true })
    panel?.scrollIntoView({ block: 'start' })
  }
}, { flush: 'post' })
function openContact(id) {
  router.push({ path: '/community', query: { member: String(id) } })
}
function askAboutPost() {
  if (post.value) router.push({ path: '/ai-chat', query: { contextType: 'POST', contextId: String(post.value.id) } })
}
watch([() => route.params.id, () => user.token], load, { immediate: true })
onBeforeUnmount(() => postRequest?.abort())
</script>

<style scoped>
.post-detail { max-width: 780px; margin: 0 auto; display: grid; gap: 16px; }
</style>
