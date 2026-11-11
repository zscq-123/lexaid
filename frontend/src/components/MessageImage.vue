<template>
  <div class="message-image">
    <button type="button" class="image-button" :disabled="!url" :aria-label="failed ? '图片加载失败' : '查看图片消息'"
      @click="preview = true">
      <img v-if="url" :src="url" alt="图片消息" @error="imageFailed" />
      <span v-else class="placeholder">{{ failed ? '图片加载失败' : '正在加载图片…' }}</span>
    </button>
    <el-dialog v-model="preview" title="图片消息" width="min(760px, 94vw)" append-to-body destroy-on-close>
      <img v-if="url" :src="url" alt="图片消息预览" class="preview-image" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { loadMessageAttachment } from '../api/messages'

const props = defineProps({
  messageId: { type: Number, required: true }
})
const url = ref('')
const failed = ref(false)
const preview = ref(false)
let active = true

function imageFailed() {
  failed.value = true
  if (url.value) {
    URL.revokeObjectURL(url.value)
    url.value = ''
  }
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
  if (url.value) URL.revokeObjectURL(url.value)
})
</script>

<style scoped>
.message-image { display: flex; justify-content: flex-start; }
.image-button {
  display: block;
  width: min(240px, 62vw);
  padding: 0;
  border: 0;
  border-radius: 10px;
  background: #e9eef5;
  cursor: zoom-in;
  overflow: hidden;
}
.image-button:disabled { cursor: default; }
.image-button img {
  display: block;
  width: 100%;
  height: auto;
  max-height: 260px;
  object-fit: cover;
}
.placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 120px;
  color: #8190a2;
  font-size: 12px;
}
.preview-image { display: block; width: 100%; max-height: 70dvh; object-fit: contain; }
</style>
