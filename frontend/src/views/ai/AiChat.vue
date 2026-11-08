<template>
  <el-card shadow="never" class="chat-card">
    <template #header>
      <div class="chat-header">
        <h1>DeepSeek 法律问答</h1>
        <div class="scope-picker">
          <label for="chat-scope">检索范围</label>
          <el-select id="chat-scope" :model-value="scope" :disabled="sending" aria-label="检索范围" @update:model-value="selectScope">
            <el-option label="全站" value="ALL" />
            <el-option-group v-for="group in sourceGroups" :key="group.label" :label="group.label">
              <el-option v-for="type in group.types" :key="type" :label="sourceLabels[type]" :value="type" />
            </el-option-group>
          </el-select>
        </div>
      </div>
    </template>

    <div v-if="hasFocus" class="context-bar">
      <span>{{ focusFailed ? '来源暂不可用' : '当前来源：' + (focused?.title || '加载中…') }}</span>
      <el-button v-if="focusFailed" link type="primary" @click="loadFocus">重试</el-button>
      <el-button link :disabled="sending" @click="selectScope('ALL')">解除限定</el-button>
    </div>
    <div ref="listRef" class="messages">
      <p v-if="historyLoading" class="notice" role="status">正在加载问答记录…</p>
      <p v-if="historyFailed" class="notice" role="alert">
        问答记录加载失败 <el-button link type="primary" :disabled="sending" @click="loadHistory">重试</el-button>
      </p>
      <el-empty v-if="!historyLoading && !historyFailed && !messages.length" description="例如：站内有哪些关于正当防卫的案例？" />
      <div v-for="(m, i) in messages" :key="m.id ?? i + ':' + m.role" :class="['msg', m.role === 'USER' ? 'user' : 'assistant']">
        <div class="bubble">{{ m.content }}</div>
        <div v-if="m.refs?.length" class="refs" aria-label="站内来源">
          <button v-for="(r, index) in m.refs" :key="r.type + ':' + r.id" type="button" class="source-button"
            @click="openSource(r, questionFor(i))">
            <span class="source-type">【{{ index + 1 }}】{{ sourceLabels[r.type] || '站内来源' }}</span>
            <span>{{ r.title }}</span>
          </button>
        </div>
      </div>
      <div v-if="sending" class="msg assistant"><div class="bubble loading" role="status">正在查询站内内容…</div></div>
    </div>

    <div class="input-bar">
      <el-input ref="editor" v-model="input" type="textarea" :rows="2" resize="none" maxlength="4000"
        aria-label="提问" placeholder="输入问题，回车发送（Shift+Enter 换行）" @keydown.enter.exact="sendOnEnter" />
      <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">发送</el-button>
    </div>

    <el-dialog v-model="sourceOpen" title="站内来源" width="min(560px, 92vw)" destroy-on-close>
      <p v-if="sourceLoading" role="status">正在读取来源…</p>
      <template v-else-if="source">
        <p class="source-type">{{ sourceLabels[source.type] || '站内来源' }}</p>
        <h2 class="source-title">{{ source.title }}</h2>
        <p class="excerpt">{{ source.summary }}</p>
      </template>
      <p v-else role="alert">{{ sourceError }}</p>
      <template #footer>
        <template v-if="source">
          <el-button v-if="originalPage(source)" @click="visitOriginal">打开原文</el-button>
          <el-button type="primary" :disabled="sending" @click="focusSource">针对这条内容提问</el-button>
        </template>
        <el-button v-else-if="!sourceLoading" @click="reloadSource">重试</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chat, chatHistory, getChatSource } from '../../api/ai'
import { useUserStore } from '../../stores/user'
import { sourceGroups, sourceLabels, originalPage } from './site-sources'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const messages = ref([])
const input = ref('')
const editor = ref(null)
const listRef = ref(null)
const historyLoading = ref(false)
const historyFailed = ref(false)
const sending = ref(false)
const focused = ref(null)
const focusFailed = ref(false)
const scope = computed(() => Object.hasOwn(sourceLabels, route.query.contextType) ? route.query.contextType : 'ALL')
const hasFocus = computed(() => route.query.contextId != null)
const sourceOpen = ref(false)
const sourceLoading = ref(false)
const sourceError = ref('')
const source = ref(null)
let sourceRequest = null
let sourceVersion = 0
let focusVersion = 0
let historyVersion = 0
let sendVersion = 0
const canSend = computed(() => !!input.value.trim() && !historyLoading.value && !sending.value && (!hasFocus.value || !!focused.value))

function selectScope(type, id) {
  const query = { ...route.query }
  if (type === 'ALL') delete query.contextType
  else query.contextType = type
  if (id == null) delete query.contextId
  else query.contextId = String(id)
  return router.replace({ path: '/ai-chat', query })
}

async function loadFocus() {
  const version = ++focusVersion
  focused.value = null
  focusFailed.value = false
  if (!hasFocus.value || !user.token) return
  const id = Number(route.query.contextId)
  if (scope.value === 'ALL' || !Number.isSafeInteger(id) || id < 1) {
    focusFailed.value = true
    return
  }
  try {
    const result = await getChatSource(scope.value, id)
    if (version === focusVersion) {
      focused.value = result
      focusFailed.value = !result
    }
  } catch {
    if (version === focusVersion) focusFailed.value = true
  }
}

async function loadHistory() {
  const version = ++historyVersion
  if (!user.token) return
  historyLoading.value = true
  historyFailed.value = false
  try {
    const result = await chatHistory()
    if (version === historyVersion) {
      messages.value = result
      scrollBottom()
    }
  } catch {
    if (version === historyVersion) historyFailed.value = true
  } finally {
    if (version === historyVersion) historyLoading.value = false
  }
}

function questionFor(index) {
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i].role === 'USER') return messages.value[i].content
  }
  return undefined
}

function openSource(item, question) {
  sourceRequest = { type: item.type, id: item.id, question }
  sourceOpen.value = true
  reloadSource()
}

async function reloadSource() {
  if (!sourceRequest) return
  const version = ++sourceVersion
  const { type, id, question } = sourceRequest
  source.value = null
  sourceLoading.value = true
  try {
    const result = await getChatSource(type, id, question)
    if (version === sourceVersion) {
      source.value = result
      if (!result) sourceError.value = '这条来源已删除或当前不可查看。'
    }
  } catch (err) {
    if (version === sourceVersion) {
      sourceError.value = [403, 404].includes(err.response?.status)
        ? '这条来源已删除或当前不可查看。' : '来源加载失败，请重试。'
    }
  } finally {
    if (version === sourceVersion) sourceLoading.value = false
  }
}

function visitOriginal() {
  const path = source.value && originalPage(source.value)
  if (path) router.push(path)
}

async function focusSource() {
  if (!source.value) return
  const { type, id } = source.value
  sourceOpen.value = false
  await selectScope(type, id)
  await nextTick()
  editor.value?.focus()
}

function sendOnEnter(event) {
  if (!event.isComposing && event.keyCode !== 229) {
    event.preventDefault()
    send()
  }
}

async function send() {
  if (!canSend.value) return
  const text = input.value.trim()
  const version = ++sendVersion
  input.value = ''
  sending.value = true
  messages.value.push({ role: 'USER', content: text })
  scrollBottom()
  try {
    const resp = await chat({ message: text, contextType: scope.value === 'ALL' ? undefined : scope.value,
      contextId: hasFocus.value ? focused.value.id : undefined })
    if (version === sendVersion) messages.value.push({ role: 'ASSISTANT', content: resp.answer, refs: resp.refs || [] })
  } catch {
    if (version === sendVersion) {
      messages.value.pop()
      input.value = input.value || text
    }
  } finally {
    if (version === sendVersion) {
      sending.value = false
      scrollBottom()
    }
  }
}

function scrollBottom() {
  nextTick(() => { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight })
}

watch([scope, () => route.query.contextId, () => user.token], loadFocus, { immediate: true })
watch(() => user.token, () => {
  ++sendVersion
  ++sourceVersion
  messages.value = []
  input.value = ''
  sending.value = false
  sourceOpen.value = false
  source.value = null
  loadHistory()
}, { immediate: true })

onBeforeUnmount(() => {
  ++historyVersion
  ++focusVersion
  ++sendVersion
  ++sourceVersion
})
</script>

<style scoped>
.chat-card { border-radius: 8px; display: flex; flex-direction: column; height: calc(100dvh - 96px); min-height: 320px; }
.chat-card :deep(.el-card__body) { display: flex; flex: 1; flex-direction: column; min-height: 0; }
.chat-header, .scope-picker, .context-bar, .input-bar { display: flex; align-items: center; gap: 10px; }
.chat-header { justify-content: space-between; flex-wrap: wrap; }
.chat-header h1 { font-size: 18px; margin: 0; }
.scope-picker { color: #606266; font-size: 13px; }
.scope-picker .el-select { width: 152px; }
.context-bar { flex-wrap: wrap; padding-bottom: 8px; color: #49586b; font-size: 13px; overflow-wrap: anywhere; }
.messages { flex: 1; overflow: auto; padding: 8px 4px; }
.notice { color: #606266; font-size: 14px; }
.msg { display: flex; flex-direction: column; margin-bottom: 12px; }
.user { align-items: flex-end; }
.assistant { align-items: flex-start; }
.bubble { max-width: min(82%, 780px); padding: 10px 14px; border-radius: 8px; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.user .bubble { background: #2e5fa3; color: #fff; }
.assistant .bubble { background: #f0f2f5; color: #292c30; }
.loading, .source-type { color: #626d7a; }
.refs { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 6px; max-width: 92%; }
.source-button { display: inline-flex; gap: 8px; max-width: 100%; align-items: baseline; text-align: start; overflow-wrap: anywhere; border: 1px solid #d5e0ee; border-radius: 5px; background: #fff; font: inherit; font-size: 13px; line-height: 1.5; color: #2e5fa3; padding: 4px 8px; cursor: pointer; }
.source-button:hover { background: #eef4fa; }
.source-type { font-size: 12px; }
.source-button .source-type { flex-shrink: 0; }
.source-title { margin: 6px 0 12px; font-size: 18px; line-height: 1.45; color: #25374d; overflow-wrap: anywhere; }
.excerpt { max-height: 52dvh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.75; }
.input-bar { align-items: flex-end; }
@media (max-width: 600px) {
  .chat-card { height: calc(100dvh - 80px); }
  .chat-card :deep(.el-card__header), .chat-card :deep(.el-card__body) { padding: 12px; }
  .bubble, .refs { max-width: 100%; }
}
</style>
