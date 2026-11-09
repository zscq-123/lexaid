<template>
  <section class="library" aria-label="讨论知识库">
    <form class="filter" role="search" aria-label="搜索讨论知识库" @submit.prevent="search">
      <el-input v-model="draft" aria-label="讨论关键词" placeholder="搜索讨论关键词" maxlength="100" clearable
        @clear="search" />
      <el-button type="primary" native-type="submit">搜索</el-button>
    </form>
    <p v-if="loading" class="notice" role="status">正在读取讨论知识库…</p>
    <p v-else-if="failed" class="notice" role="alert">讨论知识库加载失败 <el-button link type="primary" @click="load">重试</el-button></p>
    <p v-else-if="!records.length" class="notice">{{ keyword ? '没有找到匹配的讨论' : '暂无整理完成的公开讨论' }}</p>
    <ul v-else aria-label="已整理的讨论">
      <li v-for="item in records" :key="item.postId">
        <router-link :to="{ path: '/community/posts/' + item.postId, hash: '#discussion-summary' }">
          <strong>{{ item.topic }}</strong>
          <span>{{ item.commentCount }} 条评论 · 本次整理 {{ item.sampledComments }} 条</span>
          <el-icon aria-hidden="true"><ArrowRight /></el-icon>
        </router-link>
      </li>
    </ul>
    <el-pagination v-if="total > size && !failed" :current-page="page" :page-size="size" :total="total"
      layout="prev, pager, next" :pager-count="5" @current-change="changePage" />
  </section>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { listDiscussionSummaries } from '../../api/community-posts'
import { useUserStore } from '../../stores/user'

const user = useUserStore()
const size = 10
const draft = ref('')
const keyword = ref('')
const page = ref(1)
const records = ref([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)
let request

async function load() {
  request?.abort()
  const current = new AbortController()
  request = current
  records.value = []
  total.value = 0
  failed.value = false
  loading.value = Boolean(user.token)
  if (!user.token) return
  try {
    const data = await listDiscussionSummaries({ keyword: keyword.value, page: page.value, size }, current.signal)
    if (current.signal.aborted) return
    const last = Math.max(1, Math.ceil(data.total / size))
    if (page.value > last) {
      page.value = last
      return load()
    }
    records.value = data.records
    total.value = data.total
  } catch {
    if (!current.signal.aborted) failed.value = true
  } finally {
    if (!current.signal.aborted) loading.value = false
  }
}

function search() {
  keyword.value = draft.value.trim()
  page.value = 1
  load()
}
function changePage(value) {
  page.value = value
  load()
}
watch(() => user.token, () => { page.value = 1; load() }, { immediate: true })
onBeforeUnmount(() => request?.abort())
</script>

<style scoped>
.library { display: grid; gap: 16px; padding-top: 18px; }
.filter { display: flex; align-items: center; gap: 8px; max-width: 30rem; }
.filter .el-input { min-width: 0; }
ul, .notice { background: var(--lex-surface); border: 1px solid var(--lex-line); border-radius: var(--lex-radius); }
ul { list-style: none; margin: 0; padding: 0 16px; }
li + li { border-top: 1px solid var(--lex-line); }
a { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 4px 12px; padding: 15px 0; color: var(--lex-ink); }
a strong { overflow-wrap: anywhere; font-size: 1rem; }
a span, .notice { color: var(--lex-muted); }
a span { grid-row: 2; font-size: .8rem; }
a .el-icon { grid-column: 2; grid-row: 1 / span 2; align-self: center; }
.notice { padding: 22px 16px; }
.el-pagination { justify-self: center; }
</style>
