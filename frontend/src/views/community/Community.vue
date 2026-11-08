<template>
  <section class="community" aria-label="交流天地">
    <header class="page-head">
      <h1>交流天地</h1>
      <el-button type="primary" :icon="EditPen" @click="composerOpen = true">发布帖子</el-button>
    </header>
    <nav class="sections" aria-label="交流天地导航">
      <button type="button" :aria-current="tab === 'feed' ? 'page' : undefined" @click="showFeed">
        <el-icon><ChatDotRound /></el-icon>动态
      </button>
      <button type="button" :aria-current="tab === 'friends' ? 'page' : undefined" @click="showFriends()">
        <el-icon><User /></el-icon>通讯录
      </button>
    </nav>

    <div v-if="tab === 'feed'" class="feed">
      <div class="toolbar">
        <div class="scopes" role="group" aria-label="信息流范围">
          <button v-for="item in scopes" :key="item.id" type="button" :aria-pressed="scope === item.id"
            @click="setScope(item.id)">{{ item.label }}</button>
        </div>
        <el-button text :icon="Refresh" :loading="loading" aria-label="刷新动态" @click="load">刷新</el-button>
      </div>
      <div v-if="loading" class="status-card" role="status"><el-skeleton :rows="3" animated /></div>
      <div v-else-if="error" class="status-card">
        <p role="alert">动态加载失败</p>
        <el-button @click="load">重试</el-button>
      </div>
      <template v-else>
        <div v-if="posts.length === 0" class="status-card">
          <el-icon class="empty-icon" :size="32"><ChatDotRound /></el-icon>
          <p>{{ emptyLabel }}</p>
          <el-button v-if="scope === 'friends'" @click="showFriends()">打开通讯录</el-button>
          <el-button v-else type="primary" plain @click="composerOpen = true">发布第一条帖子</el-button>
        </div>
        <CommunityPost v-for="post in posts" :key="post.id" :post="post" @contact="showFriends"
          @change="(changes) => updatePost(post.id, changes)" @deleted="onDeleted" />
      </template>
      <el-pagination v-if="total > pageSize && !error" layout="prev, pager, next" background :pager-count="5"
        :current-page="page" :page-size="pageSize" :total="total" @current-change="changePage" />
    </div>
    <FriendsPanel v-else :target-user-id="targetUserId" @target-opened="targetUserId = null" @changed="invalidateFeed" />
    <CommunityComposer v-model="composerOpen" @published="onPublished" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, EditPen, Refresh, User } from '@element-plus/icons-vue'
import { listPosts } from '../../api/community-posts'
import CommunityComposer from './CommunityComposer.vue'
import CommunityPost from './CommunityPost.vue'
import FriendsPanel from './FriendsPanel.vue'

const tab = ref('feed')
const route = useRoute()
const targetUserId = ref(null)
const composerOpen = ref(false)
const scopes = [{ id: 'all', label: '全部' }, { id: 'friends', label: '好友' }, { id: 'mine', label: '我的' }]
const scope = ref('all')
const emptyLabel = computed(() => ({ all: '还没有动态', friends: '还没有好友动态', mine: '你还没有发布帖子' })[scope.value])
const posts = ref([])
const pageSize = 10
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const error = ref(false)
let requestId = 0

async function load() {
  const id = ++requestId
  loading.value = true
  error.value = false
  try {
    const result = await listPosts({ scope: scope.value, page: page.value, size: pageSize })
    if (id !== requestId || tab.value !== 'feed') return
    const last = Math.max(1, Math.ceil(result.total / pageSize))
    if (page.value > last) {
      page.value = last
      await load()
      return
    }
    posts.value = result.records
    total.value = result.total
  } catch {
    if (id === requestId) error.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}

function changePage(next) {
  if (page.value === next) return
  page.value = next
  load()
}
function setScope(next) {
  if (scope.value === next) return
  scope.value = next
  page.value = 1
  total.value = 0
  load()
}
function showFeed() {
  if (tab.value === 'feed') return
  tab.value = 'feed'
  load()
}
function showFriends(id = null) {
  requestId++
  targetUserId.value = id
  tab.value = 'friends'
}
function invalidateFeed() {
  posts.value = []
  total.value = 0
  page.value = 1
}
function updatePost(id, changes) {
  const index = posts.value.findIndex((post) => post.id === id)
  if (index >= 0) posts.value[index] = { ...posts.value[index], ...changes }
}
function onDeleted() { load() }
function onPublished() {
  tab.value = 'feed'
  scope.value = 'mine'
  page.value = 1
  total.value = 0
  ElMessage.success('帖子已发布')
  load()
}
watch(() => route.query.member, id => {
  const value = Number(id)
  if (Number.isSafeInteger(value) && value > 0) showFriends(value)
}, { immediate: true })
onMounted(() => { if (tab.value === 'feed') load() })
</script>

<style scoped>
.community { max-width: 780px; margin: 0 auto; color: #25374d; padding-bottom: 28px; }
.page-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 5px 0 18px; }
h1 { font-size: clamp(23px, 3vw, 30px); font-weight: 680; line-height: 1.25; }
.sections { display: flex; gap: 26px; border-bottom: 1px solid #d9e1eb; }
.sections button, .scopes button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; font: inherit; cursor: pointer; }
.sections button { padding: 9px 2px 12px; border: none; border-bottom: 2px solid transparent; background: transparent; color: #606f82; }
.sections button[aria-current="page"] { border-color: #2e5fa3; color: #224e89; font-weight: 650; }
.feed { display: grid; gap: 14px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; padding-top: 16px; }
.scopes { display: flex; flex-wrap: wrap; gap: 6px; }
.scopes button { border: 1px solid transparent; border-radius: 16px; padding: 5px 16px; background: transparent; color: #5b6b82; }
.scopes button[aria-pressed="true"] { background: #e5edf8; color: #244f8a; border-color: #d3e1f3; font-weight: 600; }
.status-card { display: grid; justify-items: center; align-content: center; gap: 12px; min-height: 215px; padding: 28px; border: 1px solid #dde4ed; border-radius: 12px; background: #fff; }
.status-card p { font-size: 15px; color: #52677f; }
.empty-icon { color: #7b99bf; }
.feed > :deep(.el-pagination) { justify-self: center; }
@media (max-width: 600px) {
  .page-head { padding-top: 0; }
  .scopes { gap: 1px; }
  .scopes button { padding-inline: 11px; }
}
</style>
