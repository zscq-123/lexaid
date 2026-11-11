<template>
  <div class="message-voice" :class="{ mine }">
    <audio v-if="url" ref="audio" :src="url" preload="metadata" @loadedmetadata="onLoaded"
      @timeupdate="onTime" @ended="onEnded" @error="onError"></audio>
    <button type="button" class="voice-button" :disabled="!url" :aria-label="playing ? '暂停语音' : '播放语音'"
      @click="toggle">
      <el-icon :size="18"><VideoPause v-if="playing" /><VideoPlay v-else /></el-icon>
    </button>
    <span class="voice-bars" aria-hidden="true">
      <i v-for="(height, index) in bars" :key="index" :class="{ played: index < playedBars }"
        :style="{ height: height + 'px' }"></i>
    </span>
    <span class="voice-duration">{{ statusText }}</span>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { loadMessageAttachment } from '../api/messages'

const props = defineProps({
  messageId: { type: Number, required: true },
  durationSeconds: { type: Number, default: 0 },
  mine: { type: Boolean, default: false }
})
const bars = [7, 12, 17, 10, 20, 14, 8, 16, 22, 12, 18, 9, 15, 20, 11, 7]
const audio = ref(null)
const url = ref('')
const playing = ref(false)
const current = ref(0)
const total = ref(props.durationSeconds || 0)
const failed = ref(false)
let active = true

const playedBars = computed(() => {
  if (!total.value) return 0
  return Math.round(Math.min(1, current.value / total.value) * bars.length)
})
const statusText = computed(() => {
  if (failed.value) return '加载失败'
  if (!url.value) return '加载中'
  return formatDuration(total.value)
})

function formatDuration(seconds) {
  const value = Math.max(0, Math.round(Number(seconds) || 0))
  return Math.floor(value / 60) + ':' + String(value % 60).padStart(2, '0')
}

async function toggle() {
  if (!audio.value || !url.value) return
  try {
    if (audio.value.paused) {
      await audio.value.play()
      playing.value = true
    } else {
      audio.value.pause()
      playing.value = false
    }
  } catch {
    failed.value = true
  }
}

function onLoaded() {
  if (audio.value && Number.isFinite(audio.value.duration) && audio.value.duration > 0) {
    total.value = audio.value.duration
  }
}

function onTime() {
  if (!audio.value) return
  current.value = audio.value.currentTime
  playing.value = !audio.value.paused && !audio.value.ended
}

function onEnded() {
  playing.value = false
  current.value = 0
  if (audio.value) audio.value.currentTime = 0
}

function onError() {
  failed.value = true
  playing.value = false
}

onMounted(async () => {
  try {
    const blob = await loadMessageAttachment(props.messageId)
    if (active && blob instanceof Blob) url.value = URL.createObjectURL(blob)
  } catch {
    if (active) failed.value = true
  }
})

onBeforeUnmount(() => {
  active = false
  if (audio.value) {
    audio.value.pause()
    audio.value.src = ''
  }
  if (url.value) URL.revokeObjectURL(url.value)
})
</script>

<style scoped>
.message-voice {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 168px;
  max-width: 280px;
  padding: 8px 11px;
  border-radius: 12px 12px 12px 4px;
  background: #eef2f7;
  color: #30465d;
}
.message-voice.mine { border-radius: 12px 12px 4px 12px; background: #2e5fa3; color: #fff; }
.voice-button {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  background: #fff;
  color: #2e5fa3;
  cursor: pointer;
}
.mine .voice-button { background: #ffffff29; color: #fff; }
.voice-button:disabled { cursor: wait; opacity: .65; }
.voice-bars { display: inline-flex; align-items: center; gap: 2px; flex: 1; height: 24px; }
.voice-bars i { width: 3px; min-height: 3px; border-radius: 2px; background: #aab8c8; }
.mine .voice-bars i { background: #ffffff70; }
.voice-bars i.played { background: #2e5fa3; }
.mine .voice-bars i.played { background: #fff; }
.voice-duration { flex: none; font-size: 11px; font-variant-numeric: tabular-nums; opacity: .8; }
</style>
