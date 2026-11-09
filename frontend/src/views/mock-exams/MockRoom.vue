<template>
  <main class="room-scroll">
    <div class="mock-page room">
      <div v-if="!session" class="mock-panel">
        <p v-if="loadError" role="alert">考场加载失败。<button class="text-button" @click="sync">重试</button>
          <router-link v-if="canReturn" to="/mock-exams">返回模拟考试</router-link></p>
        <p v-else>正在进入考场…</p>
      </div>
      <template v-else>
        <header class="room-head">
          <div><span class="muted">模拟考试</span><h1>{{ sourceLabels[session.sourceType] }} · {{ paperLabels[session.paperType] }}</h1></div>
          <div class="clock"><span>剩余时间</span><strong>{{ clockLabel(remaining) }}</strong></div>
        </header>
        <p v-if="remaining <= 0" role="status" class="mock-panel">考试时间已到，正在确认交卷结果。<button class="text-button" @click="sync">重试</button></p>
        <div class="room-grid">
          <section v-if="q" class="mock-panel question">
            <div class="question-info"><h2>第 {{ q.position }} 题</h2><span>{{ kindLabels[q.kind] }} · {{ q.maxPoints }} 分</span></div>
            <p class="long-text stem">{{ q.stem }}</p>
            <p v-if="q.sourceLabel?.includes('原卷答题卡') && sourceLink(q.sourceUrl)" class="original-paper">
              <a :href="sourceLink(q.sourceUrl)" target="_blank" rel="noopener noreferrer">阅读原卷</a>
              <span class="muted"> · 打开后考试继续计时</span>
            </p>
            <p v-if="q.kind === 'INDEFINITE'" class="muted">请选择一个或多个答案。</p>
            <label v-if="q.kind === 'SHORT'" class="written">你的作答
              <textarea :value="drafts.valueOf(q)" maxlength="16000" :disabled="!canEdit || submitting" rows="9" @input="drafts.edit(q, $event.target.value)" />
            </label>
            <fieldset v-else class="options" :disabled="!canEdit || submitting" :aria-label="kindLabels[q.kind]">
              <label v-for="o in q.options" :key="o.key" class="answer-option">
                <input :type="q.kind === 'SINGLE' ? 'radio' : 'checkbox'" :name="'mock-q-' + q.position"
                  :checked="selectedKeys(drafts.valueOf(q)).includes(o.key)" @change="selectOption(o.key, $event.target.checked)" />
                <span><b>{{ o.key }}.</b> {{ o.text }}</span>
              </label>
            </fieldset>
            <div class="move"><button :disabled="index === 0" @click="go(index - 1)">上一题</button>
              <button :disabled="index >= session.items.length - 1" @click="go(index + 1)">下一题</button></div>
          </section>
          <aside class="mock-panel card">
            <h2>答题卡</h2>
            <p>已答 {{ answeredCount }} / {{ session.items.length }} 题</p>
            <div v-for="group in groups" :key="group.kind" class="card-group">
              <h3>{{ kindLabels[group.kind] }}</h3>
              <div class="positions"><button v-for="item in group.items" :key="item.position" :aria-label="'第 ' + item.position + ' 题'"
                :aria-current="q?.position === item.position ? 'step' : undefined" :class="{ done: answered(item.kind, drafts.valueOf(item)) }"
                @click="go(item.index)">{{ item.position }}</button></div>
            </div>
            <p v-if="conflicts.length" role="alert">有 {{ conflicts.length }} 道题在其他窗口修改过。</p>
            <div v-for="item in conflicts" :key="'c' + item.position" class="collision">
              <strong>第 {{ item.position }} 题</strong><div class="long-text">服务器答案：{{ item.answer || '空白' }}</div>
              <button @click="drafts.resolve(item, true)">保留本机作答</button><button @click="drafts.resolve(item, false)">采用服务器答案</button>
            </div>
            <p class="muted save-state" role="status">{{ saveLabel }}
              <button v-if="failed && canEdit" class="text-button" @click="drafts.flush">重试保存</button>
            </p>
            <p class="muted submit-rule">{{ remaining <= 0 ? '考试已结束' : session.deadline <= session.earlySubmitAt ? '本卷到时自动交卷' : earlyRemaining > 0 ? '距离可交卷 ' + clockLabel(earlyRemaining) : '已可提前交卷' }}</p>
            <button v-if="session.deadline > session.earlySubmitAt" class="primary" :disabled="!canEdit || earlyRemaining > 0 || conflicts.length > 0 || submitting" @click="handIn">
              {{ submitting ? '正在交卷…' : '提前交卷' }}
            </button>
          </aside>
        </div>
      </template>
    </div>
  </main>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActiveMock, getMock, submitMock } from '../../api/mock-exams'
import { useUserStore } from '../../stores/user'
import { rememberExam, resultPath, roomPath } from './active'
import { answered, clockLabel, kindLabels, paperLabels, selectedKeys, sourceLabels, sourceLink } from './format'
import { useExamDrafts } from './useExamDrafts'

const route = useRoute(), router = useRouter(), user = useUserStore()
const id = String(route.params.id), token = user.token
const session = ref(null), index = ref(0), now = ref(0), loadError = ref(false), canReturn = ref(false), submitting = ref(false)
let anchor, mounted = true, reading = false, interval, nextCheck = 0, nextExpiry = 0, dialog = false
const remaining = computed(() => (session.value?.deadline ?? 0) - now.value)
const earlyRemaining = computed(() => (session.value?.earlySubmitAt ?? 0) - now.value)
const canEdit = computed(() => !!session.value && session.value.status === 'ACTIVE' && remaining.value > 0)
const drafts = useExamDrafts(session, () => user.user?.id, canEdit, sync)
const { dirtyCount, conflicts, failed, saving, deviceError } = drafts
const saveLabel = computed(() => deviceError.value ? '本机草稿暂时无法保存。' : failed.value ? '未同步到服务器。' :
  saving.value ? '正在保存…' : dirtyCount.value ? '有待同步的作答' : '作答已同步')
const q = computed(() => session.value?.items[index.value])
const answeredCount = computed(() => session.value.items.filter(i => answered(i.kind, drafts.valueOf(i))).length)
const groups = computed(() => {
  const grouped = new Map()
  session.value.items.forEach((item, index) => {
    if (!grouped.has(item.kind)) grouped.set(item.kind, [])
    grouped.get(item.kind).push({ ...item, index })
  })
  return Array.from(grouped, ([kind, items]) => ({ kind, items }))
})
function go(i) { index.value = i; drafts.flush() }
function selectOption(key, checked) {
  const item = q.value
  const keys = new Set(item.kind === 'SINGLE' ? [] : selectedKeys(drafts.valueOf(item)))
  if (checked) keys.add(key); else keys.delete(key)
  drafts.edit(item, JSON.stringify([...keys].sort()))
}
function updateTime() {
  if (!anchor) return
  // Monotonic time survives wall-clock adjustments. Recheck with the server on wake.
  now.value = anchor.time + performance.now() - anchor.at
  if (performance.now() >= nextCheck || remaining.value <= 0 && performance.now() >= nextExpiry) sync()
}
async function finish(s) {
  drafts.clear(s.id)
  if (dialog) ElMessageBox.close()
  rememberExam(user, null)
  await router.replace(resultPath(s.id))
}
async function sync() {
  if (reading || !mounted || user.token !== token) return
  reading = true
  try {
    const s = await getMock(id)
    if (!mounted || user.token !== token) return
    if (s.status !== 'ACTIVE') { await finish(s); return }
    anchor = { time: s.serverTime, at: performance.now() }; now.value = s.serverTime
    if (!session.value) {
      session.value = s
      drafts.restore()
      rememberExam(user, s.id)
    } else {
      // Keep any more recent local acknowledgement, even if this poll started before the save.
      s.items.forEach(item => {
        const local = session.value.items.find(q => q.position === item.position)
        if (local && item.revision > local.revision) { local.revision = item.revision; local.answer = item.answer }
      })
    }
    loadError.value = false
    if (!failed.value || navigator.onLine) drafts.flush()
  } catch {
    if (!mounted || user.token !== token) return
    loadError.value = true
    if (!session.value) {
      // A foreign or missing attempt must not trap this account on a dead route.
      try {
        const active = await getActiveMock()
        if (!mounted || user.token !== token) return
        rememberExam(user, active?.id)
        if (active?.id && String(active.id) !== id) await router.replace(roomPath(active.id))
        canReturn.value = !active
      } catch { /* Retry once the connection returns. */ }
    }
  } finally {
    nextCheck = performance.now() + 30000
    if (remaining.value <= 0) nextExpiry = performance.now() + 4500
    reading = false
  }
}
async function handIn() {
  if (submitting.value || dialog || !canEdit.value || earlyRemaining.value > 0) return
  dialog = true
  try { await ElMessageBox.confirm('已答 ' + answeredCount.value + ' / ' + session.value.items.length + ' 题。交卷后不能修改答案。', '提前交卷', { confirmButtonText: '确认交卷', cancelButtonText: '继续考试' }) }
  catch { return } finally { dialog = false }
  if (!mounted || !canEdit.value) return
  submitting.value = true
  try {
    if (!await drafts.flush()) { ElMessage.warning('请先同步本机作答，再交卷。'); return }
    const s = await submitMock(id)
    if (mounted && user.token === token) await finish(s)
  } catch { if (mounted) sync() } finally { submitting.value = false }
}
function beforeUnload(e) {
  if (session.value?.status === 'ACTIVE') { e.preventDefault(); e.returnValue = '' }
}
function visible() { if (!document.hidden) sync() }
onMounted(() => {
  sync(); interval = setInterval(updateTime, 400)
  window.addEventListener('beforeunload', beforeUnload); window.addEventListener('online', sync); document.addEventListener('visibilitychange', visible)
})
onBeforeUnmount(() => {
  mounted = false; clearInterval(interval); drafts.stop()
  window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('online', sync); document.removeEventListener('visibilitychange', visible)
})
</script>
<style src="./mock.css"></style>
<style scoped>
.room-scroll { height: 100%; overflow-y: auto; }
.room { padding: max(14px, env(safe-area-inset-top)) 14px max(24px, env(safe-area-inset-bottom)); }
.room-head { display: flex; justify-content: space-between; gap: 14px; align-items: center; margin-bottom: 16px; }
.room-head h1 { font-size: clamp(1.12rem, 3vw, 1.55rem); }
.clock { display: grid; text-align: right; flex: none; }
.clock strong { font-size: 1.2rem; font-variant-numeric: tabular-nums; }
.room-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(210px, 270px); gap: 14px; align-items: start; }
.question-info { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.question-info span { color: var(--lex-muted); }
.stem { font-size: 1rem; line-height: 1.75; margin-bottom: 14px; }
.answer-option { display: flex; align-items: baseline; gap: 9px; border: 1px solid var(--lex-line); border-radius: 8px; padding: 10px; margin: 8px 0; cursor: pointer; }
.answer-option:has(:checked) { border-color: var(--lex-accent); background: #edf4fc; }
.answer-option span { overflow-wrap: anywhere; white-space: pre-wrap; }
.original-paper { margin: 8px 0 14px; }
.move { display: flex; justify-content: space-between; gap: 12px; margin-top: 18px; }
.written { display: grid; gap: 6px; }
textarea { width: 100%; resize: vertical; border: 1px solid var(--lex-line); border-radius: 8px; padding: 10px; }
.card-group { margin: 12px 0; }
.card-group h3 { margin-bottom: 5px; font-size: .9rem; }
.positions { display: flex; flex-wrap: wrap; gap: 5px; }
.positions button { font-size: .78rem; padding: 4px; min-width: 30px; font-variant-numeric: tabular-nums; }
.positions button.done { background: #deebf8; }
.positions button[aria-current] { outline: 2px solid var(--lex-accent); }
.collision, .save-state, .submit-rule { margin-bottom: 9px; }
@media(max-width:680px) { .room-grid { grid-template-columns: minmax(0,1fr); gap: 0; } }
</style>
