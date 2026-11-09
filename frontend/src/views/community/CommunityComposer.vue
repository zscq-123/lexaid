<template>
  <el-dialog v-model="open" title="发布帖子" width="min(560px, 94vw)" :close-on-click-modal="false"
    :close-on-press-escape="!saving" :show-close="!saving" :before-close="closeDialog" @closed="reset">
    <form class="compose" @submit.prevent="submit">
      <input ref="fileInput" class="file-input" type="file" accept="image/jpeg,image/png" multiple
        :disabled="saving" @change="addFiles" />
      <template v-if="pictures.length">
        <div class="preview">
          <img :src="selected.url" :alt="'第 ' + (selectedIndex + 1) + ' 张待发布图片'" />
          <span class="position">{{ selectedIndex + 1 }} / {{ pictures.length }}</span>
        </div>
        <div class="thumbs" aria-label="待发布图片">
          <button v-for="(image, i) in pictures" :key="image.url" type="button"
            :class="['thumb', { selected: i === selectedIndex }]" :aria-label="'选择第 ' + (i + 1) + ' 张图片'"
            :aria-pressed="i === selectedIndex" @click="selectedIndex = i">
            <img :src="image.url" alt="" />
          </button>
          <button v-if="pictures.length < 9" type="button" class="thumb add" :disabled="saving"
            aria-label="继续添加图片" @click="fileInput.click()"><el-icon><Plus /></el-icon></button>
        </div>
        <div class="image-tools">
          <small>第一张图片为封面</small>
          <div>
            <el-button text size="small" :disabled="saving || selectedIndex === 0" @click="move(-1)">向前移</el-button>
            <el-button text size="small" :disabled="saving || selectedIndex === pictures.length - 1" @click="move(1)">向后移</el-button>
            <el-button text size="small" type="danger" :disabled="saving" @click="removeImage">移除</el-button>
          </div>
        </div>
      </template>
      <button v-else type="button" class="pick" :disabled="saving" @click="fileInput.click()">
        <el-icon :size="28"><PictureRounded /></el-icon>
        <strong>添加图片</strong>
        <small>可选，最多 9 张 JPG 或 PNG</small>
      </button>
      <p v-if="fileWarning" class="warning" role="alert">{{ fileWarning }}</p>
      <label for="post-caption">帖子正文</label>
      <el-input id="post-caption" v-model="caption" type="textarea" :rows="4" maxlength="2200" show-word-limit
        resize="vertical" :disabled="saving" placeholder="写下你的想法…" />
      <div class="visibility">
        <label for="post-visibility">谁可以看</label>
        <el-select id="post-visibility" v-model="visibility" aria-describedby="post-summary-use" :disabled="saving" style="width: 140px">
          <el-option value="PUBLIC" label="全部用户" />
          <el-option value="FRIENDS" label="仅好友" />
        </el-select>
      </div>
      <small id="post-summary-use" role="status">{{ visibility === 'PUBLIC'
        ? '公开帖收到至少 2 位用户合计 3 条评论后，AI 会整理讨论观点供知识库收录。'
        : '仅好友可见的帖子不参与 AI 观点整理，也不进入讨论知识库。' }}</small>
      <p v-if="sendError" class="warning" role="alert">{{ sendError }}</p>
      <div class="submit">
        <el-button :disabled="saving" @click="requestClose">取消</el-button>
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="!canPublish">发布</el-button>
      </div>
    </form>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, PictureRounded } from '@element-plus/icons-vue'
import { publishPost } from '../../api/community-posts'

const open = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['published'])
const fileInput = ref(null)
const caption = ref('')
const visibility = ref('PUBLIC')
const pictures = ref([])
const selectedIndex = ref(0)
const selected = computed(() => pictures.value[selectedIndex.value])
const saving = ref(false)
const fileWarning = ref('')
const sendError = ref('')
const canPublish = computed(() => !saving.value && Boolean(caption.value.trim() || pictures.value.length))

function addFiles(event) {
  const files = Array.from(event.target.files ?? [])
  event.target.value = ''
  fileWarning.value = ''
  if (!files.length) return
  if (files.length + pictures.value.length > 9) fileWarning.value = '每条帖子最多可添加 9 张图片。'
  else if (files.some((f) => !['image/jpeg', 'image/png'].includes(f.type) || !f.size))
    fileWarning.value = '请选择非空的 JPG 或 PNG 图片。'
  else if (files.some((f) => f.size > 4 * 1024 ** 2)) fileWarning.value = '每张图片不能超过 4 MB。'
  else if ([...pictures.value.map((p) => p.file), ...files].reduce((n, f) => n + f.size, 0) > 20 * 1024 ** 2)
    fileWarning.value = '图片总大小不能超过 20 MB。'
  if (fileWarning.value) return
  const firstNew = pictures.value.length
  pictures.value.push(...files.map((file) => ({ file, url: URL.createObjectURL(file) })))
  selectedIndex.value = firstNew
}

function move(offset) {
  const target = selectedIndex.value + offset
  if (target < 0 || target >= pictures.value.length) return
  const [item] = pictures.value.splice(selectedIndex.value, 1)
  pictures.value.splice(target, 0, item)
  selectedIndex.value = target
}

function removeImage() {
  const [item] = pictures.value.splice(selectedIndex.value, 1)
  URL.revokeObjectURL(item.url)
  selectedIndex.value = Math.max(0, Math.min(selectedIndex.value, pictures.value.length - 1))
  fileWarning.value = ''
}

function reset() {
  for (const item of pictures.value) URL.revokeObjectURL(item.url)
  pictures.value = []
  selectedIndex.value = 0
  caption.value = ''
  visibility.value = 'PUBLIC'
  fileWarning.value = ''
  sendError.value = ''
}

async function closeDialog(done) {
  if (saving.value) return
  if (caption.value.trim() || pictures.value.length) {
    try {
      await ElMessageBox.confirm('放弃这次编辑的帖子？', '放弃编辑', { confirmButtonText: '放弃', cancelButtonText: '继续编辑' })
    } catch { return }
  }
  done()
}
function requestClose() { closeDialog(() => { open.value = false }) }

async function submit() {
  if (!canPublish.value) return
  saving.value = true
  sendError.value = ''
  try {
    const post = await publishPost({ caption: caption.value.trim(), visibility: visibility.value, files: pictures.value.map((p) => p.file) })
    open.value = false
    emit('published', post)
  } catch (err) {
    sendError.value = err.response?.data?.message || '发布失败，请检查网络后重试。'
  } finally { saving.value = false }
}
onBeforeUnmount(reset)
</script>

<style scoped>
.compose { display: grid; gap: 12px; color: #263750; }
.file-input { display: none; }
label { font-weight: 600; }
.preview { position: relative; height: clamp(170px, 31vh, 310px); background: #f0f3f7; border-radius: 10px; overflow: hidden; }
.preview img { width: 100%; height: 100%; object-fit: contain; }
.position { position: absolute; top: 10px; right: 10px; background: #1c293fcb; color: white; padding: 3px 9px; border-radius: 14px; font-size: 12px; }
.thumbs { display: flex; gap: 6px; overflow-x: auto; padding: 2px; }
.thumb { flex: 0 0 45px; height: 45px; padding: 2px; border: 2px solid transparent; background: transparent; border-radius: 6px; cursor: pointer; }
.thumb img { display: block; width: 100%; height: 100%; object-fit: cover; border-radius: 3px; }
.thumb.selected { border-color: #2e5fa3; }
.thumb.add { border: 1px dashed #aeb9c8; color: #2e5fa3; }
.image-tools, .visibility { display: flex; align-items: center; gap: 10px; justify-content: space-between; flex-wrap: wrap; }
.image-tools { margin-top: -6px; }
small { color: #697a90; font-size: 12px; }
.pick { width: 100%; display: grid; justify-items: center; gap: 7px; padding: 22px; background: #f6f9fd; border: 1px dashed #b9c9dc; border-radius: 9px; color: #345c90; cursor: pointer; }
.warning { font-size: 13px; color: #b13838; }
.submit { display: flex; justify-content: flex-end; gap: 4px; padding-top: 4px; }
</style>
