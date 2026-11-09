<template>
  <div class="mock-page">
    <router-link to="/mock-exams">返回模拟考试</router-link>
    <p v-if="failed" role="alert" class="mock-panel">成绩读取失败。<button class="text-button" @click="load()">重试</button></p>
    <p v-if="!session && !failed" class="mock-panel">正在读取成绩…</p>
    <template v-if="session">
      <section class="mock-panel result-summary">
        <h2>{{ sourceLabels[session.sourceType] }} · {{ paperLabels[session.paperType] }}成绩</h2>
        <p v-if="session.score != null"><strong class="score">{{ session.score }}</strong> / {{ session.totalPoints }} 分</p>
        <p v-else>已评 {{ session.earnedPoints }} 分，满分 {{ session.totalPoints }} 分。</p>
        <p v-if="pending" class="muted" role="status">有 {{ pending }} 道主观题正在依据参考答案评分。</p>
        <p v-if="unavailable" class="muted">有 {{ unavailable }} 道题暂时无法评分。
          <button class="text-button" :disabled="busy" @click="load(true)">重试主观题评分</button></p>
      </section>
      <section v-for="group in groups" :key="group.kind" class="mock-panel">
        <h2>{{ kindLabels[group.kind] }} · {{ group.points }} / {{ group.maximum }} 分</h2>
        <details v-for="q in group.items" :key="q.position" class="question-detail">
          <summary>第 {{ q.position }} 题 · {{ gradeLabel(q) }}</summary>
          <p class="long-text">{{ q.stem }}</p>
          <ul v-if="q.options?.length" class="result-options"><li v-for="o in q.options" :key="o.key">{{ o.key }}. {{ o.text }}</li></ul>
          <div class="long-text"><b>你的答案</b>　{{ displayAnswer(q, q.answer) || '未作答' }}</div>
          <div class="long-text"><b>参考答案</b>　{{ displayAnswer(q, q.referenceAnswer) || '未提供' }}</div>
          <p v-if="q.feedback" class="long-text"><b>评分反馈</b>　{{ q.feedback }}</p>
          <p v-if="q.explanation" class="long-text"><b>解析</b>　{{ q.explanation }}</p>
          <p v-if="q.sourceLabel" class="muted">题目来源　<a v-if="sourceLink(q.sourceUrl)" :href="sourceLink(q.sourceUrl)" target="_blank" rel="noopener noreferrer">{{ q.sourceLabel }}</a><span v-else>{{ q.sourceLabel }}</span></p>
        </details>
      </section>
    </template>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMock, retryMockGrading } from '../../api/mock-exams'
import { useUserStore } from '../../stores/user'
import { rememberExam, roomPath } from './active'
import { kindLabels, paperLabels, selectedKeys, sourceLabels, sourceLink } from './format'
const route = useRoute(), router = useRouter(), user = useUserStore()
const session = ref(null), busy = ref(false), failed = ref(false)
let generation = 0, timer
const pending = computed(() => session.value?.items.filter(i => i.gradeStatus === 'PENDING').length || 0)
const unavailable = computed(() => session.value?.items.filter(i => i.gradeStatus === 'UNAVAILABLE').length || 0)
const groups = computed(() => {
  const list = new Map()
  for (const item of session.value?.items || []) {
    if (!list.has(item.kind)) list.set(item.kind, { kind: item.kind, items: [], points: 0, maximum: 0 })
    const group = list.get(item.kind)
    group.items.push(item); group.points += item.points ?? 0; group.maximum += item.maxPoints
  }
  return Array.from(list.values())
})
function displayAnswer(q, value) { return q.kind === 'SHORT' ? value : selectedKeys(value).join('、') }
function gradeLabel(item) {
  if (item.gradeStatus === 'PENDING') return '待评分'
  if (item.gradeStatus === 'UNAVAILABLE') return '评分暂不可用'
  return (item.points ?? 0) + ' / ' + item.maxPoints + ' 分'
}
async function load(retry = false) {
  clearTimeout(timer)
  const request = ++generation, token = user.token
  busy.value = true; failed.value = false
  try {
    const data = await (retry ? retryMockGrading(route.params.id) : getMock(route.params.id))
    if (generation !== request || token !== user.token) return
    if (data.status === 'ACTIVE') { rememberExam(user, data.id); await router.replace(roomPath(data.id)); return }
    session.value = data
    if (pending.value) timer = setTimeout(load, 5500)
  } catch { if (generation === request) failed.value = true }
  finally { if (generation === request) busy.value = false }
}
watch(() => route.params.id, () => { session.value = null; load() }, { immediate: true })
onBeforeUnmount(() => { generation++; clearTimeout(timer) })
</script>
<style src="./mock.css"></style>
<style scoped>
.result-summary { margin-top: 12px; }
.score { font-size: 1.8rem; font-variant-numeric: tabular-nums; }
.question-detail { border-top: 1px solid var(--lex-line); padding: 10px 0; }
.question-detail summary { cursor: pointer; font-weight: 600; }
.question-detail p, .question-detail div { margin: 8px 0; }
.result-options { list-style: none; overflow-wrap: anywhere; }
</style>
