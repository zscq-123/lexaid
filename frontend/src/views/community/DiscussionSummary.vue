<template>
  <section id="discussion-summary" class="summary" aria-labelledby="discussion-summary-heading" tabindex="-1">
    <header>
      <h2 id="discussion-summary-heading">AI 观点整理</h2>
      <span v-if="summary?.status === 'READY' && summary.generatedAt" class="when">
        更新于 <time :datetime="summary.generatedAt">{{ summary.generatedAt.replace('T', ' ').slice(0, 16) }}</time>
      </span>
    </header>
    <p v-if="post.visibility !== 'PUBLIC'" class="notice">仅公开讨论会进入观点整理。</p>
    <p v-else-if="failed" class="notice" role="alert">观点整理暂时无法读取。 <el-button link type="primary" @click="load">重试</el-button></p>
    <p v-else-if="!summary" class="notice" role="status">正在读取观点整理…</p>
    <p v-else-if="summary.status === 'INELIGIBLE'" class="notice">公开帖有至少 3 条评论、且有至少 2 位评论者时，会自动整理观点。</p>
    <p v-else-if="summary.status === 'PENDING'" class="notice" role="status">正在整理最新讨论，完成后这里会自动更新。</p>
    <p v-else-if="summary.status === 'UNAVAILABLE'" class="notice" role="status">
      AI 整理暂时不可用，可稍后查看。 <el-button link type="primary" @click="load">刷新状态</el-button>
    </p>
    <template v-else-if="summary.status === 'READY'">
      <p v-if="!groups.length" class="notice">近期讨论尚无可归纳的法律观点。</p>
      <template v-else>
        <h3 class="topic">{{ summary.topic }}</h3>
        <p class="context">依据原帖和最近 {{ summary.sampledComments }} 条评论整理。可打开来源查看发言原文。</p>
        <section v-for="group in groups" :key="group.kind" class="group">
          <h4>{{ group.label }}</h4>
          <ul>
            <li v-for="point in group.items" :key="point.text">
              <p>{{ point.text }}</p>
              <div class="sources">
                <button v-for="id in point.sourceIds" :key="id" type="button" @click="$emit('source', sourceById.get(id))">
                  {{ id === 0 ? '查看原帖' : sourceById.get(id).authorName + '的评论 #' + id }}
                </button>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </template>
    <p v-else class="notice" role="alert">暂时没有可显示的整理结果。</p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getDiscussionSummary } from '../../api/community-posts'
import { useUserStore } from '../../stores/user'

const props = defineProps({ post: { type: Object, required: true } })
defineEmits(['source'])
const user = useUserStore()
const summary = ref(null)
const failed = ref(false)
const sourceById = computed(() => new Map((summary.value?.sources || []).map(s => [s.id, s])))
const kinds = { SUPPORT: '支持观点', OPPOSE: '反对观点', MIXED: '折中观点', OTHER: '其他观点', AGREEMENT: '共识', DISAGREEMENT: '分歧' }
const groups = computed(() => Object.entries(kinds).map(([kind, label]) => ({
  kind, label, items: (summary.value?.points || []).filter(p => p.kind === kind && p.sourceIds?.length &&
    p.sourceIds.every(id => sourceById.value.has(id)))
})).filter(g => g.items.length))
let request
let timer

async function load() {
  clearTimeout(timer)
  request?.abort()
  failed.value = false
  if (!user.token || props.post.visibility !== 'PUBLIC') { summary.value = null; return }
  const current = new AbortController()
  request = current
  try {
    const data = await getDiscussionSummary(props.post.id, current.signal)
    if (!current.signal.aborted) {
      summary.value = data
      if (data.status === 'PENDING' && !document.hidden) timer = setTimeout(load, 15000)
    }
  } catch {
    if (!current.signal.aborted) { summary.value = null; failed.value = true }
  }
}
function visibilityChanged() {
  if (document.hidden) clearTimeout(timer)
  else load()
}
watch([() => props.post.id, () => props.post.commentCount, () => props.post.visibility, () => user.token], () => {
  summary.value = null
  load()
}, { immediate: true })
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', visibilityChanged)
  clearTimeout(timer)
  request?.abort()
})
</script>

<style scoped>
.summary { background: var(--lex-surface); border: 1px solid var(--lex-line); border-radius: var(--lex-radius); padding: clamp(14px, 2.5vw, 22px); scroll-margin-top: calc(var(--toolbar-height, 56px) + 12px); overflow-wrap: anywhere; }
header { display: flex; align-items: baseline; flex-wrap: wrap; justify-content: space-between; gap: 6px 12px; }
h2 { font-size: 1.12rem; color: var(--lex-ink); }
.when, .context, .notice { color: var(--lex-muted); }
.when { font-size: .75rem; }
.notice, .context { margin-top: .55rem; line-height: 1.6; }
.topic { margin-top: 16px; font-size: 1.08rem; }
.context { font-size: .84rem; }
.group { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--lex-line); }
h4 { font-size: .9rem; color: var(--lex-accent); }
ul { display: grid; gap: 12px; list-style: none; margin-top: 6px; }
.sources { display: flex; flex-wrap: wrap; gap: 2px 12px; }
.sources button { border: none; padding: 4px 0; color: var(--lex-accent); background: transparent; cursor: pointer; text-decoration: underline; text-underline-offset: 2px; font-size: .8rem; overflow-wrap: anywhere; }
@media (pointer: coarse) { .sources button { min-height: 44px; } }
</style>
