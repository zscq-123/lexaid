<template>
  <span ref="element" class="avatar" role="img" :aria-label="name ? name + '的头像' : '用户头像'"
    :style="{ width: size + 'px', height: size + 'px', fontSize: Math.max(12, size * 0.4) + 'px' }">
    <img v-if="src && !failed" :src="src" alt="" @error="failed = true" />
    <span v-else aria-hidden="true">{{ initial }}</span>
  </span>
</template>

<script>
import { getAvatarBlob } from '../api/auth'

// Keep one URL per signed-in session, avatar and version, only while someone displays it.
const displayed = new Map()

function acquireAvatar(token, id, version) {
  const key = JSON.stringify([token, String(id), version])
  let entry = displayed.get(key)
  if (!entry) {
    const controller = new AbortController()
    entry = { users: 0, controller, url: '' }
    displayed.set(key, entry)
    entry.ready = getAvatarBlob(id, version, controller.signal).then(blob => {
      if (blob && !controller.signal.aborted) entry.url = URL.createObjectURL(blob)
      return entry.url
    }).catch(error => {
      if (displayed.get(key) === entry) displayed.delete(key)
      throw error
    })
  }
  entry.users++
  return {
    ready: entry.ready,
    release() {
      if (--entry.users > 0) return
      if (displayed.get(key) === entry) displayed.delete(key)
      entry.controller.abort()
      if (entry.url) URL.revokeObjectURL(entry.url)
    }
  }
}
</script>

<script setup>
import { computed, ref, watch } from 'vue'
import { useUserStore } from '../stores/user'
import { useNearViewport } from '../composables/useNearViewport'

const props = defineProps({
  userId: [Number, String],
  version: String,
  name: { type: String, default: '' },
  size: { type: Number, default: 36 }
})
const store = useUserStore()
const initial = computed(() => Array.from((props.name || '').trim())[0] || '用')
const version = computed(() => {
  if (store.user && String(store.user.id) === String(props.userId) && 'avatarVersion' in store.user) {
    return store.user.avatarVersion
  }
  return props.version
})
const src = ref('')
const failed = ref(false)
const element = ref(null)
const nearby = useNearViewport(computed(() => version.value ? element.value : null))
watch([() => store.token, () => props.userId, version, nearby], async ([token, id, v, visible], _, onCleanup) => {
  src.value = ''
  failed.value = false
  if (!visible || !token || !id || !v) return
  let active = true
  const avatar = acquireAvatar(token, id, v)
  onCleanup(() => {
    active = false
    avatar.release()
  })
  try {
    const url = await avatar.ready
    if (active) src.value = url
  } catch {
    if (active) failed.value = true
  }
}, { immediate: true })
</script>

<style scoped>
.avatar { display: inline-grid; place-items: center; flex: none; overflow: hidden; border-radius: 50%;
  background: #e5edf8; color: #335a8a; line-height: 1; font-weight: 650; vertical-align: middle; }
.avatar img { display: block; width: 100%; height: 100%; object-fit: cover; }
</style>
