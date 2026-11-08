<template>
  <el-dialog v-model="open" title="搜索资料库" class="shell-search-dialog" width="min(620px, calc(100vw - 24px))"
    align-center destroy-on-close modal-class="shell-search-overlay" transition="lex-search"
    @open-auto-focus="focusOnOpen = true" @focus.self="focusKeyword" @close="open = false" @closed="reset">
    <form class="search-form" @submit.prevent="runSearch">
      <el-input ref="input" v-model="keyword" aria-label="搜索关键词" placeholder="搜索法条、案例、学者观点或题目"
        size="large" clearable @input="clearResults">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" native-type="submit" size="large" :disabled="!keyword.trim()" :loading="loading">搜索</el-button>
    </form>
    <div v-if="submitted" class="results" aria-live="polite" :aria-busy="loading">
      <p v-if="loading">正在搜索…</p>
      <p v-else-if="failed">搜索失败，请重试。</p>
      <p v-else-if="!groups.length">没有找到匹配的内容</p>
      <section v-for="group in groups" v-else :key="group.key">
        <h3>{{ group.label }}</h3>
        <router-link v-for="item in group.items" :key="item.id" class="result" :to="target(item)" @click="open = false">
          <strong>{{ item.title }}</strong><span v-if="item.summary">{{ item.summary }}</span>
        </router-link>
      </section>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { globalSearch } from '../api/content'

const open = defineModel({ type: Boolean, default: false })
const keyword = ref('')
const input = ref(null)
const results = ref({})
const submitted = ref(false)
const loading = ref(false)
const failed = ref(false)
let generation = 0
let focusOnOpen = false
const labels = { provisions: '法条', cases: '案例', viewpoints: '学者观点与论文', questions: '题目' }
const groups = computed(() => Object.entries(labels)
  .map(([key, label]) => ({ key, label, items: results.value[key] }))
  .filter(group => group.items?.length))

function focusKeyword() {
  // The dialog trap first focuses its panel; forward that initial focus to the search field.
  if (!focusOnOpen) return
  focusOnOpen = false
  input.value?.focus()
}

function clearResults() {
  generation++
  results.value = {}
  loading.value = false
  submitted.value = false
  failed.value = false
}
function reset() {
  keyword.value = ''
  clearResults()
}
async function runSearch() {
  const term = keyword.value.trim()
  if (!term || loading.value) return
  const version = ++generation
  submitted.value = true
  loading.value = true
  failed.value = false
  try {
    const data = await globalSearch(term)
    if (version === generation) results.value = data
  } catch {
    if (version === generation) failed.value = true
  } finally {
    if (version === generation) loading.value = false
  }
}
function target(item) {
  const paths = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  return paths[item.type] ? paths[item.type] + item.id :
    { path: '/ai-chat', query: { contextType: 'QUESTION', contextId: String(item.id) } }
}
</script>

<style scoped>
.search-form { display: flex; gap: 8px; align-items: center; }
.search-form .el-input { min-width: 0; }
.results { max-height: min(52dvh, 480px); overflow: auto; padding-top: 14px; }
.results > p { padding: 0.7em 0; color: var(--lex-muted); }
.results h3 { font-size: 0.8rem; color: var(--lex-muted); margin: 10px 6px 5px; }
.result { display: grid; gap: 2px; color: var(--lex-ink); padding: 8px 6px; border-radius: 6px; overflow-wrap: anywhere; }
.result strong { font-weight: 590; }
.result span { color: var(--lex-muted); line-height: 1.45; }
.result:focus-visible { outline-offset: -2px; }
@media (hover: hover) { .result:hover { background: var(--el-color-primary-light-9); } }
</style>

<style>
@media (prefers-reduced-motion: no-preference) {
  .shell-search-overlay.lex-search-enter-active,
  .shell-search-overlay.lex-search-leave-active {
    transition: opacity var(--lex-motion-fast, 120ms) var(--lex-ease-out, ease-out);
  }
  .lex-search-enter-active .shell-search-dialog,
  .lex-search-leave-active .shell-search-dialog {
    transition: transform var(--lex-motion-fast, 120ms) var(--lex-ease-out, ease-out);
  }
  .shell-search-overlay.lex-search-leave-active,
  .lex-search-leave-active .shell-search-dialog { transition-duration: 90ms; }
  .shell-search-overlay.lex-search-enter-from,
  .shell-search-overlay.lex-search-leave-to { opacity: 0; }
  .lex-search-enter-from .shell-search-dialog,
  .lex-search-leave-to .shell-search-dialog { transform: translateY(4px); }
}
@media (prefers-reduced-motion: reduce) {
  .shell-search-overlay, .shell-search-dialog { transition-property: none; }
}
</style>
