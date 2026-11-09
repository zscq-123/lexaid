<template>
  <section class="coverage" aria-labelledby="coverage-heading">
    <div class="coverage-head">
      <h2 id="coverage-heading">我的做题进度</h2>
      <span class="scope">{{ scope }}</span>
    </div>
    <div class="readout" aria-live="polite">
      <p v-if="state === 'loading'" class="hint">正在读取进度…</p>
      <p v-else-if="state === 'error'" class="hint">
        进度加载失败。<el-button link type="primary" @click="load">重试</el-button>
      </p>
      <p v-else-if="!progress.total" class="hint">当前范围暂无题目。</p>
      <template v-else>
        <div class="numbers">
          <span>已做 <strong>{{ progress.answered }}</strong> / {{ progress.total }} 题</span>
          <span class="remaining">剩余 {{ progress.total - progress.answered }} 题</span>
        </div>
        <progress :value="progress.answered" :max="progress.total" aria-label="做题进度"
          :aria-valuetext="`已做 ${progress.answered} 道，共 ${progress.total} 道`" />
        <p class="hint">交卷后更新，重复作答只计一题。</p>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getExamProgress } from '../../api/exam'

const props = defineProps({ filters: { type: Object, required: true } })
const state = ref('loading')
const progress = ref(null)
let currentRequest

const scope = computed(() => {
  const { discipline, subject, codeName, bookName, difficulty } = props.filters
  return [discipline, subject, codeName, bookName, difficulty && `${difficulty} 星`].filter(Boolean).join(' / ') || '全部题库'
})

async function load() {
  currentRequest?.abort()
  const request = new AbortController()
  currentRequest = request
  state.value = 'loading'
  try {
    const data = await getExamProgress(props.filters, request.signal)
    if (request.signal.aborted) return
    progress.value = data
    state.value = 'ready'
  } catch {
    if (!request.signal.aborted) state.value = 'error'
  }
}

watch(() => props.filters, load, { immediate: true })
onBeforeUnmount(() => currentRequest?.abort())
</script>

<style scoped>
.coverage { margin-bottom: 20px; padding: 14px 16px; border-radius: 12px; background: var(--lex-canvas); }
.coverage-head, .numbers { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 12px; align-items: baseline; }
.coverage-head { margin-bottom: 8px; }
.readout { min-height: 68px; }
h2 { font-size: 1rem; color: var(--lex-ink); }
.scope, .hint, .remaining { color: var(--lex-muted); font-size: .8rem; }
.scope { overflow-wrap: anywhere; }
.numbers { font-variant-numeric: tabular-nums; }
.numbers strong { font-size: 1.25rem; color: var(--lex-accent); }
progress { display: block; width: 100%; height: 7px; margin: 7px 0; border: 0; border-radius: 4px; background: var(--lex-line); color: var(--lex-accent); appearance: none; }
progress::-webkit-progress-bar { background: var(--lex-line); border-radius: 4px; }
progress::-webkit-progress-value { background: var(--lex-accent); border-radius: 4px; }
progress::-moz-progress-bar { background: var(--lex-accent); border-radius: 4px; }
</style>
