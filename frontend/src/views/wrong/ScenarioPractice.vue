<template>
  <el-dialog v-model="open" title="情景迁移练习" width="min(640px, calc(100vw - 24px))" destroy-on-close>
    <p v-if="busy && !item" class="status" role="status">正在准备同考点的新情景…</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <article v-if="item" :aria-busy="busy">
      <header class="topic">
        <span>{{ item.origin }} · 第 {{ item.round }} 次练习</span>
        <h2>{{ item.topic }}</h2>
      </header>
      <p class="scenario">{{ item.stem }}</p>
      <fieldset :disabled="busy || Boolean(item.selected)">
        <legend>选择一个答案</legend>
        <label v-for="option in item.options" :key="option.key"
          :class="{ chosen: choice === option.key, correct: item.correctAnswer === option.key }">
          <input type="radio" :name="'scenario-' + item.id" :value="option.key" v-model="choice" />
          <span>{{ option.key }}. {{ option.text }}</span>
        </label>
      </fieldset>
      <section v-if="item.selected" class="feedback" aria-live="polite">
        <h3>{{ item.selected === item.correctAnswer ? '答对了' : '请再核对判断要件' }}</h3>
        <p>你的答案 {{ item.selected }} · 正确答案 {{ item.correctAnswer }}</p>
        <p>{{ item.explanation }}</p>
        <h3>情景对照</h3>
        <p>{{ item.comparison }}</p>
        <h3>原题参考依据</h3>
        <blockquote>{{ item.basis }}</blockquote>
        <details v-if="source"><summary>查看原题</summary><p>{{ source.stem }}</p></details>
      </section>
    </article>
    <template #footer>
      <el-button @click="open = false">关闭</el-button>
      <el-button v-if="!item && error" type="primary" :loading="busy" @click="prepare()">重试</el-button>
      <el-button v-else-if="item?.selected" type="primary" :loading="busy" @click="prepare(item.id)">换个情景</el-button>
      <el-button v-else-if="item" type="primary" :disabled="!choice" :loading="busy" @click="submit">提交答案</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { answerScenario, openScenario } from '../../api/exam'

const open = defineModel({ type: Boolean, default: false })
const props = defineProps({ source: Object })
const item = ref(null)
const choice = ref('')
const error = ref('')
const busy = ref(false)
let controller

async function perform(request) {
  if (busy.value) return
  const active = new AbortController()
  controller = active
  busy.value = true
  error.value = ''
  try {
    const result = await request(active.signal)
    if (!active.signal.aborted) {
      item.value = result
      choice.value = result.selected || ''
    }
  } catch (e) {
    if (!active.signal.aborted) error.value = e.response?.data?.message || '练习暂时无法加载，请重试。'
  } finally {
    if (!active.signal.aborted) busy.value = false
  }
}
function prepare(after) {
  return perform(signal => openScenario(props.source.questionId, after, signal))
}
function submit() {
  if (item.value && choice.value) return perform(signal => answerScenario(item.value.id, choice.value, signal))
}
watch([open, () => props.source?.questionId], ([shown, id]) => {
  controller?.abort()
  busy.value = false
  item.value = null
  choice.value = ''
  error.value = ''
  if (shown && id) prepare()
})
onBeforeUnmount(() => controller?.abort())
</script>

<style scoped>
.topic span, .status { color: var(--lex-muted); }
.topic span { font-size: .8rem; }
.topic h2 { font-size: 1.16rem; margin: .25rem 0 .8rem; }
.scenario, .feedback p, blockquote { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
fieldset { margin: 1rem 0; padding: 0; border: 0; display: grid; gap: .45rem; }
legend { margin-bottom: .5rem; color: var(--lex-muted); }
label { display: flex; align-items: baseline; gap: .6rem; padding: .6rem .75rem; border: 1px solid var(--lex-line); border-radius: .5rem; cursor: pointer; }
label.chosen { border-color: var(--lex-accent); }
label.correct { background: var(--el-color-success-light-9); }
fieldset:disabled label { cursor: default; }
.error { color: var(--el-color-danger); margin: .5rem 0; }
.feedback { border-top: 1px solid var(--lex-line); padding-top: .65rem; }
.feedback h3 { margin: .7rem 0 .2rem; font-size: .94rem; }
blockquote { margin: 0; border-left: 2px solid var(--lex-line); padding-left: .7rem; }
details { margin-top: .8rem; }
summary { cursor: pointer; }
</style>
