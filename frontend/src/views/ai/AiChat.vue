<template>
  <el-card shadow="never" class="chat-card">
    <template #header>
      <div class="chat-header">
        <span>AI 法律问答</span>
        <div class="quick">
          <el-button
            v-for="c in commands"
            :key="c.label"
            size="small"
            :type="contextType === c.type ? 'primary' : 'default'"
            @click="selectCommand(c.type)"
          >{{ c.label }}</el-button>
        </div>
      </div>
    </template>

    <div v-if="contextType" class="context-bar">
      <el-tag closable type="info" @close="clearContext">
        当前针对：{{ contextTitle || contextType }}
        <template v-if="!contextTitle">
          <el-input-number v-model="contextId" :min="1" size="small" style="width: 100px; margin-left: 6px" />
        </template>
      </el-tag>
    </div>

    <div class="messages" ref="listRef">
      <el-empty v-if="messages.length === 0" description="开始提问吧，例如：正当防卫的成立条件是什么？" />
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role === 'USER' ? 'user' : 'assistant']">
        <div class="bubble">{{ m.content }}</div>
        <div v-if="m.refs && m.refs.length" class="refs">
          <el-tag
            v-for="r in m.refs"
            :key="r.type + r.id"
            size="small"
            effect="plain"
            class="ref"
            @click="go(r)"
          >{{ relLabel(r.type) }}：{{ r.title }}</el-tag>
        </div>
      </div>
      <div v-if="sending" class="msg assistant">
        <div class="bubble loading">正在思考…</div>
      </div>
    </div>

    <div class="input-bar">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入你的法律问题，回车发送（Shift+Enter 换行）"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCase, getProvision, getViewpoint } from '../../api/content'
import { chat, chatHistory } from '../../api/ai'

const route = useRoute()
const router = useRouter()
const messages = ref([])
const input = ref('')
const sending = ref(false)
const contextType = ref(null)
const contextId = ref(null)
const contextTitle = ref('')
const listRef = ref(null)

const commands = [
  { label: '全局', type: null },
  { label: '法条', type: 'PROVISION' },
  { label: '案例', type: 'CASE' },
  { label: '观点', type: 'VIEWPOINT' }
]

async function loadHistory() {
  messages.value = await chatHistory()
  scrollBottom()
}

async function initContext() {
  if (route.query.contextType && route.query.contextId) {
    contextType.value = route.query.contextType
    contextId.value = Number(route.query.contextId)
    await loadContextTitle()
  }
}

async function loadContextTitle() {
  try {
    if (contextType.value === 'PROVISION') {
      const d = await getProvision(contextId.value)
      contextTitle.value = d.lawName + ' ' + d.articleNo
    } else if (contextType.value === 'CASE') {
      const d = await getCase(contextId.value)
      contextTitle.value = d.title
    } else if (contextType.value === 'VIEWPOINT') {
      const d = await getViewpoint(contextId.value)
      contextTitle.value = d.title + '（' + d.scholarName + '）'
    }
  } catch (e) {
    contextTitle.value = ''
  }
}

function selectCommand(type) {
  contextType.value = type
  contextId.value = null
  contextTitle.value = ''
}

function clearContext() {
  contextType.value = null
  contextId.value = null
  contextTitle.value = ''
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  const history = messages.value.slice(-10).map((m) => ({
    role: m.role === 'USER' ? 'user' : 'assistant',
    content: m.content
  }))
  messages.value.push({ role: 'USER', content: text, refs: [] })
  sending.value = true
  scrollBottom()
  try {
    const resp = await chat({
      message: text,
      contextType: contextType.value || undefined,
      contextId: contextId.value || undefined,
      history
    })
    messages.value.push({ role: 'ASSISTANT', content: resp.answer, refs: resp.refs || [] })
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function relLabel(t) {
  return { PROVISION: '法条', CASE: '案例', VIEWPOINT: '观点', QUESTION: '题目' }[t] || t
}
function go(r) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (map[r.type]) router.push(map[r.type] + r.id)
}

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

onMounted(() => {
  loadHistory()
  initContext()
})
</script>

<style scoped>
.chat-card {
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 96px);
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.quick {
  display: flex;
  gap: 8px;
}
.context-bar {
  margin-bottom: 8px;
}
.messages {
  flex: 1;
  overflow: auto;
  padding: 8px 4px;
}
.msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}
.msg.user {
  align-items: flex-end;
}
.msg.assistant {
  align-items: flex-start;
}
.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.8;
  white-space: pre-wrap;
}
.user .bubble {
  background: #2e5fa3;
  color: #fff;
}
.assistant .bubble {
  background: #f0f2f5;
  color: #303133;
}
.loading {
  color: #909399 !important;
}
.refs {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 75%;
}
.ref {
  cursor: pointer;
}
.input-bar {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  margin-top: 8px;
}
</style>
