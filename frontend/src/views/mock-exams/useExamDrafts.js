import { computed, ref } from 'vue'
import { saveMockAnswer } from '../../api/mock-exams'

export function useExamDrafts(session, getUserId, canEdit, onClosed) {
  const drafts = ref({})
  const saving = ref(false), failed = ref(false), deviceError = ref(false)
  let timer, pending, stopped = false
  const key = (id = session.value?.id) => getUserId() != null && id ? 'lexaid_mock_draft_' + getUserId() + '_' + id : ''
  const dirtyCount = computed(() => Object.keys(drafts.value).length)
  const conflicts = computed(() => session.value?.items.filter(q => drafts.value[q.position]?.conflict) || [])
  const valueOf = q => drafts.value[q.position]?.value ?? q.answer ?? ''

  function persist() {
    const k = key()
    if (!k) return
    try {
      if (dirtyCount.value) {
        const serialized = JSON.stringify(drafts.value)
        // Keep a tab's unsent answers across refresh even if a second tab syncs first.
        sessionStorage.setItem(k, serialized)
        localStorage.setItem(k, serialized)
      } else {
        sessionStorage.removeItem(k)
        localStorage.removeItem(k)
      }
      deviceError.value = false
    } catch { deviceError.value = true }
  }
  function restore() {
    let saved = {}
    try { saved = JSON.parse(sessionStorage.getItem(key()) || localStorage.getItem(key()) || '{}') || {} } catch { deviceError.value = true }
    drafts.value = {}
    for (const q of session.value.items) {
      const entry = saved[q.position]
      if (entry && typeof entry.value === 'string' && Number.isInteger(entry.base) && entry.value !== (q.answer || '')) {
        drafts.value[q.position] = { value: entry.value, base: entry.base, conflict: entry.base !== q.revision }
      }
    }
    persist()
  }
  function clear(id) {
    drafts.value = {}
    const k = key(id)
    if (k) try { sessionStorage.removeItem(k); localStorage.removeItem(k) } catch { deviceError.value = true }
  }
  function edit(q, value) {
    if (!canEdit.value || stopped) return
    const old = drafts.value[q.position]
    drafts.value[q.position] = { value, base: old?.base ?? q.revision, conflict: old?.conflict || false }
    failed.value = false
    persist()
    clearTimeout(timer)
    timer = setTimeout(flush, q.kind === 'SHORT' ? 420 : 0)
  }
  function resolve(q, keepOwn) {
    if (!canEdit.value) return
    const d = drafts.value[q.position]
    if (!d?.conflict) return
    if (keepOwn) { d.base = q.revision; d.conflict = false }
    else delete drafts.value[q.position]
    persist()
    flush()
  }
  async function drain() {
    saving.value = true; failed.value = false
    try {
      while (!stopped && canEdit.value) {
        const q = session.value.items.find(q => drafts.value[q.position] && !drafts.value[q.position].conflict)
        if (!q) break
        const sending = { ...drafts.value[q.position] }
        const reply = await saveMockAnswer(session.value.id, q.position, { answer: sending.value, revision: sending.base + 1 })
        if (stopped) return
        if (reply.status !== 'ACTIVE') { onClosed(); return }
        q.answer = reply.answer ?? ''; q.revision = reply.revision
        const latest = drafts.value[q.position]
        if (!latest) continue
        if (reply.accepted || q.answer === sending.value) {
          if (latest.value === sending.value) delete drafts.value[q.position]
          else latest.base = q.revision
        } else if (latest.value === q.answer) delete drafts.value[q.position]
        else latest.conflict = true
        persist()
      }
    } catch { failed.value = true } finally { saving.value = false }
  }
  async function flush() {
    clearTimeout(timer)
    if (!pending && dirtyCount.value && canEdit.value && !stopped) pending = drain().finally(() => { pending = null })
    if (pending) await pending
    return !dirtyCount.value
  }
  function stop() { stopped = true; clearTimeout(timer) }
  return { dirtyCount, conflicts, valueOf, saving, failed, deviceError, edit, resolve, flush, restore, stop, clear }
}
