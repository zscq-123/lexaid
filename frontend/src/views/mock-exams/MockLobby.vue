<template>
  <div class="exam-lobby mock-page">
    <section class="mock-panel">
      <h2>组建试卷</h2>
      <p v-if="failed" role="alert">试卷信息加载失败。<button class="text-button" @click="load">重试</button></p>
      <p v-else-if="loading" role="status">正在读取题库…</p>
      <form v-else-if="catalog.length" @submit.prevent="start">
        <fieldset>
          <legend>题目来源</legend>
          <label v-for="(label, key) in sourceLabels" :key="key" class="choice">
            <input type="radio" name="source" v-model="source" :value="key" @change="chooseSource" /> {{ label }}
          </label>
        </fieldset>
        <fieldset>
          <legend>试卷类型</legend>
          <label v-for="(label, key) in paperLabels" :key="key" class="choice">
            <input type="radio" name="paper" v-model="paper" :value="key" /> {{ label }}
          </label>
        </fieldset>
        <div v-if="model" class="paper-info">
          <p><strong>{{ totalCount }} 题</strong> · 满分 {{ model.totalPoints }} 分</p>
          <p class="muted">{{ model.sections.map(s => kindLabels[s.kind] + ' ' + s.count + ' 题').join(' · ') }}</p>
        </div>
        <p v-if="!model?.available" role="status" class="muted">此组合的已收录题目不足以组成完整试卷。</p>
        <label for="mock-duration" class="time-label">考试时长</label>
        <div><input id="mock-duration" v-model.number="duration" type="number" required min="1" max="480" step="1" /> 分钟</div>
        <p class="muted rules">开考后锁定考场。{{ duration <= 30 ? '本卷到时自动交卷。' : '满 30 分钟可提前交卷，到时自动交卷。' }}评分以服务器已保存的答案为准，刷新或重新登录后继续计时。</p>
        <button class="primary" type="submit" :disabled="starting || !model?.available || !validDuration">{{ starting ? '正在组卷…' : '开始考试' }}</button>
      </form>
      <p v-else>暂无可用试卷。</p>
    </section>
    <section class="mock-panel">
      <h2>考试记录</h2>
      <p v-if="historyFailed">记录加载失败。<button class="text-button" @click="loadHistory">重试</button></p>
      <p v-else-if="historyLoading" class="muted">正在读取记录…</p>
      <p v-else-if="!history.length" class="muted">暂无考试记录。</p>
      <ul v-else class="history-list">
        <li v-for="s in history" :key="s.id">
          <div><strong>{{ sourceLabels[s.sourceType] }} · {{ paperLabels[s.paperType] }}</strong><br />
            <span class="muted">{{ new Date(s.startedAt).toLocaleString('zh-CN') }}</span></div>
          <router-link :to="s.status === 'ACTIVE' ? roomPath(s.id) : resultPath(s.id)">
            {{ s.status === 'ACTIVE' ? '继续考试' : '查看成绩' }}
          </router-link>
        </li>
      </ul>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getMockCatalog, getMockHistory, startMock } from '../../api/mock-exams'
import { useUserStore } from '../../stores/user'
import { rememberExam, resultPath, roomPath } from './active'
import { kindLabels, paperLabels, sourceLabels } from './format'
const user = useUserStore()
const router = useRouter()
const catalog = ref([]), history = ref([])
const source = ref('MOCK'), paper = ref('OBJECTIVE'), duration = ref(180)
const loading = ref(true), failed = ref(false), starting = ref(false), historyLoading = ref(true), historyFailed = ref(false)
const model = computed(() => catalog.value.find(m => m.sourceType === source.value && m.paperType === paper.value))
const totalCount = computed(() => model.value?.sections.reduce((n,s) => n + s.count, 0) || 0)
const validDuration = computed(() => Number.isInteger(duration.value) && duration.value >= 1 && duration.value <= 480)
watch(model, row => { if (row) duration.value = row.durationMinutes })
function chooseSource() {
  if (!catalog.value.some(m => m.sourceType === source.value && m.paperType === paper.value && m.available)) {
    paper.value = catalog.value.find(m => m.sourceType === source.value && m.available)?.paperType || paper.value
  }
}
async function load() {
  loading.value = true; failed.value = false
  try {
    catalog.value = await getMockCatalog()
    const first = catalog.value.find(m => m.sourceType === 'MOCK' && m.paperType === 'OBJECTIVE' && m.available) ||
      catalog.value.find(m => m.available) || catalog.value[0]
    if (first) { source.value = first.sourceType; paper.value = first.paperType }
  } catch { failed.value = true } finally { loading.value = false }
}
async function loadHistory() {
  historyLoading.value = true; historyFailed.value = false
  try { history.value = await getMockHistory() } catch { historyFailed.value = true } finally { historyLoading.value = false }
}
async function start() {
  if (starting.value || !validDuration.value || !model.value?.available) return
  starting.value = true
  const token = user.token
  try {
    const session = await startMock({ sourceType: source.value, paperType: paper.value, durationMinutes: duration.value })
    if (user.token !== token) return
    rememberExam(user, session.id)
    await router.push(roomPath(session.id))
  } catch { /* API reports a recoverable start error. */ } finally { starting.value = false }
}
onMounted(() => { load(); loadHistory() })
</script>
<style src="./mock.css"></style>
