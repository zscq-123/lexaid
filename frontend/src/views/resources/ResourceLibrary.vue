<template>
  <section class="resource-library" aria-label="资源库">
    <header class="page-head">
      <div class="heading-copy">
        <p class="eyebrow">学习资料</p>
        <h1>资源库</h1>
        <p class="lede">上传并整理法考、课程和案例资料。个人资源仅自己可见，公开资源会出现在社区资源中。</p>
      </div>
      <el-button type="primary" size="large" :icon="UploadFilled" @click="openUpload">上传资源</el-button>
    </header>

    <nav class="scope-tabs" aria-label="资源范围">
      <button type="button" :aria-current="tab === 'community' ? 'page' : undefined" @click="switchTab('community')">
        <el-icon><Share /></el-icon>社区资源
      </button>
      <button type="button" :aria-current="tab === 'mine' ? 'page' : undefined" @click="switchTab('mine')">
        <el-icon><Lock /></el-icon>我的资源
      </button>
    </nav>

    <div class="toolbar">
      <el-input v-model="keyword" class="search-input" aria-label="搜索资源" clearable
        placeholder="搜索标题、说明或文件名" @input="onKeywordInput" @keyup.enter="searchNow"
        @clear="searchNow">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="filters">
        <el-select v-model="category" aria-label="资源分类" placeholder="全部分类">
          <el-option label="全部分类" value="" />
          <el-option v-for="item in CATEGORIES" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="format" aria-label="资源格式" placeholder="全部格式">
          <el-option label="全部格式" value="" />
          <el-option v-for="item in FORMATS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="sort" aria-label="资源排序">
          <el-option label="最新上传" value="latest" />
          <el-option label="下载最多" value="popular" />
        </el-select>
        <el-button text :icon="Refresh" :loading="loading" aria-label="刷新资源" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="list-summary">
      <span>共 {{ total }} 份资源</span>
      <span v-if="loading" role="status">正在加载…</span>
    </div>

    <div v-if="loading && !records.length" class="state-card" role="status">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="error" class="state-card">
      <p role="alert">资源加载失败</p>
      <el-button @click="load">重试</el-button>
    </div>
    <div v-else-if="!records.length" class="state-card empty">
      <el-icon class="empty-icon" :size="34"><FolderOpened /></el-icon>
      <p>{{ emptyText }}</p>
      <el-button v-if="tab === 'mine'" type="primary" plain @click="openUpload">上传第一份资源</el-button>
    </div>
    <div v-else class="resource-grid" :aria-busy="loading">
      <article v-for="item in records" :key="item.id" class="resource-card">
        <div class="file-badge" :class="'ext-' + item.fileExtension" aria-hidden="true">
          {{ formatLabel(item.fileExtension) }}
        </div>
        <div class="card-body">
          <div class="card-head">
            <h2 :title="item.title">{{ item.title }}</h2>
            <el-tag v-if="tab === 'mine'" size="small" :type="item.visibility === 'PUBLIC' ? 'success' : 'info'"
              effect="plain">{{ item.visibility === 'PUBLIC' ? '社区公开' : '仅自己' }}</el-tag>
          </div>
          <p class="description" :class="{ muted: !item.description }">{{ item.description || '暂无资源说明' }}</p>
          <div class="meta">
            <span class="category">{{ item.category }}</span>
            <span>{{ formatSize(item.fileSize) }}</span>
            <span>{{ item.downloadCount }} 次下载</span>
            <time v-if="item.createdAt" :datetime="item.createdAt">{{ formatDate(item.createdAt) }}</time>
          </div>
          <div class="card-footer">
            <span v-if="tab === 'community'" class="author">
              <UserAvatar :user-id="item.ownerId" :version="item.ownerAvatarVersion"
                :name="item.ownerName" :size="22" />
              <span>{{ item.ownerName }}</span>
            </span>
            <span v-else class="file-name" :title="item.fileName">{{ item.fileName }}</span>
            <div class="actions">
              <el-button link type="primary" :loading="downloadingId === item.id" @click="downloadFile(item)">
                <el-icon><Download /></el-icon>下载
              </el-button>
              <template v-if="item.owned">
                <el-button link @click="openEdit(item)">编辑</el-button>
                <el-button link type="danger" @click="remove(item)">删除</el-button>
              </template>
            </div>
          </div>
        </div>
      </article>
    </div>
    <el-pagination v-if="total > pageSize && !error" class="pager" layout="prev, pager, next, total"
      background :pager-count="5" :current-page="page" :page-size="pageSize" :total="total"
      @current-change="changePage" />

    <el-dialog v-model="uploadOpen" title="上传学习资源" width="min(580px, 94vw)"
      :close-on-click-modal="false" :close-on-press-escape="!uploading" :show-close="!uploading"
      @closed="resetUpload">
      <form class="resource-form" @submit.prevent="submitUpload">
        <input ref="fileInput" class="file-input" type="file" :accept="ACCEPT" :disabled="uploading"
          @change="selectFile" />
        <button v-if="!uploadFile" type="button" class="file-picker" :disabled="uploading"
          @click="fileInput?.click()">
          <el-icon :size="30"><UploadFilled /></el-icon>
          <strong>选择学习资源文件</strong>
          <small>支持 PDF、DOCX、PPTX、XLSX、TXT、MD；文件大小不限</small>
        </button>
        <div v-else class="selected-file">
          <span class="selected-icon" :class="'ext-' + extensionOf(uploadFile.name)">
            {{ formatLabel(extensionOf(uploadFile.name)) }}
          </span>
          <div class="selected-copy">
            <strong :title="uploadFile.name">{{ uploadFile.name }}</strong>
            <small>{{ formatSize(uploadFile.size) }}</small>
          </div>
          <el-button text :disabled="uploading" @click="fileInput?.click()">更换</el-button>
          <el-button text type="danger" :disabled="uploading" @click="uploadFile = null">移除</el-button>
        </div>
        <p v-if="fileError" class="warning" role="alert">{{ fileError }}</p>

        <el-form label-position="top" class="metadata">
          <el-form-item label="资源标题" required>
            <el-input v-model="form.title" maxlength="120" show-word-limit :disabled="uploading"
              placeholder="例如：2026 民法总则讲义" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="资源分类">
              <el-select v-model="form.category" :disabled="uploading">
                <el-option v-for="item in CATEGORIES" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="可见范围">
              <el-radio-group v-model="form.visibility" :disabled="uploading">
                <el-radio value="PRIVATE">仅自己</el-radio>
                <el-radio value="PUBLIC">社区公开</el-radio>
              </el-radio-group>
            </el-form-item>
          </div>
          <el-form-item label="资源说明">
            <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit
              :disabled="uploading" placeholder="简单说明资料内容、适用科目或使用方式（可选）" />
          </el-form-item>
        </el-form>

        <p class="hint">{{ form.visibility === 'PUBLIC'
          ? '公开后，所有登录用户都能在「社区资源」中看到并下载这份资料。'
          : '仅自己可见，其他用户无法访问。' }}</p>
        <el-progress v-if="uploading && uploadProgress > 0" :percentage="uploadProgress" :stroke-width="8" />
        <p v-if="uploadError" class="warning" role="alert">{{ uploadError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="uploading" @click="uploadOpen = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="uploading">上传</el-button>
        </div>
      </form>
    </el-dialog>

    <el-dialog v-model="editOpen" title="编辑资源信息" width="min(580px, 94vw)"
      :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <form class="resource-form" @submit.prevent="saveEdit">
        <el-form label-position="top" class="metadata">
          <el-form-item label="资源标题" required>
            <el-input v-model="editForm.title" maxlength="120" show-word-limit :disabled="saving" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="资源分类">
              <el-select v-model="editForm.category" :disabled="saving">
                <el-option v-for="item in CATEGORIES" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="可见范围">
              <el-radio-group v-model="editForm.visibility" :disabled="saving">
                <el-radio value="PRIVATE">仅自己</el-radio>
                <el-radio value="PUBLIC">社区公开</el-radio>
              </el-radio-group>
            </el-form-item>
          </div>
          <el-form-item label="资源说明">
            <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="1000" show-word-limit
              :disabled="saving" />
          </el-form-item>
        </el-form>
        <p class="hint">文件内容保持不变。如需更换文件，请删除后重新上传。</p>
        <p v-if="editError" class="warning" role="alert">{{ editError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="editOpen = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Download, FolderOpened, Lock, Refresh, Search, Share, UploadFilled } from '@element-plus/icons-vue'
import { deleteResource, downloadResource, listResources, updateResource, uploadResource } from '../../api/resources'
import UserAvatar from '../../components/UserAvatar.vue'

const CATEGORIES = ['法考资料', '课程讲义', '案例研习', '复习笔记', '真题试卷', '其他']
const FORMATS = [
  { value: 'pdf', label: 'PDF' },
  { value: 'docx', label: 'DOCX' },
  { value: 'pptx', label: 'PPTX' },
  { value: 'xlsx', label: 'XLSX' },
  { value: 'txt', label: 'TXT' },
  { value: 'md', label: 'Markdown' }
]
const BADGE_LABELS = { ...Object.fromEntries(FORMATS.map(item => [item.value, item.label])), md: 'MD' }
const ACCEPT = '.pdf,.docx,.pptx,.xlsx,.txt,.md'
const pageSize = 12

const tab = ref('community')
const keyword = ref('')
const category = ref('')
const sort = ref('latest')
const format = ref('')
const records = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const error = ref(false)
let activeRequest
let keywordTimer

const uploadOpen = ref(false)
const fileInput = ref(null)
const uploadFile = ref(null)
const form = reactive({ title: '', description: '', category: '法考资料', visibility: 'PRIVATE' })
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadError = ref('')
const fileError = ref('')

const editOpen = ref(false)
const editing = ref(null)
const editForm = reactive({ title: '', description: '', category: '其他', visibility: 'PRIVATE' })
const saving = ref(false)
const editError = ref('')
const downloadingId = ref(null)

const emptyText = computed(() => {
  if (keyword.value.trim() || category.value || format.value) return '没有找到符合条件的资源'
  return tab.value === 'mine'
    ? '你还没有上传资源，上传的资料会保存在这里'
    : '社区还没有公开资源，上传时可以勾选「社区公开」'
})

async function load() {
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  loading.value = true
  error.value = false
  try {
    const result = await listResources({
      scope: tab.value,
      keyword: keyword.value.trim(),
      category: category.value,
      format: format.value,
      sort: sort.value,
      page: page.value,
      size: pageSize
    }, request.signal)
    if (request.signal.aborted) return
    records.value = result.records
    total.value = result.total
  } catch {
    if (!request.signal.aborted) error.value = true
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

function onKeywordInput() {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 350)
}

function searchNow() {
  clearTimeout(keywordTimer)
  page.value = 1
  load()
}

function switchTab(next) {
  if (tab.value === next) return
  tab.value = next
  page.value = 1
  records.value = []
  total.value = 0
  load()
}

function changePage(next) {
  if (page.value === next) return
  page.value = next
  load()
}

watch([category, format, sort], () => {
  page.value = 1
  load()
})

function openUpload() {
  uploadFile.value = null
  form.title = ''
  form.description = ''
  form.category = '法考资料'
  form.visibility = 'PRIVATE'
  uploadProgress.value = 0
  uploadError.value = ''
  fileError.value = ''
  uploadOpen.value = true
}

function selectFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  fileError.value = ''
  if (!file) return
  const extension = extensionOf(file.name)
  if (!FORMATS.some(item => item.value === extension)) {
    fileError.value = '仅支持 PDF、DOCX、PPTX、XLSX、TXT 或 MD 文件'
    return
  }
  uploadFile.value = file
  if (!form.title.trim()) form.title = file.name.replace(/\.[^.]+$/, '').slice(0, 120)
}

function resetUpload() {
  if (uploading.value) return
  uploadFile.value = null
  form.title = ''
  form.description = ''
  form.category = '法考资料'
  form.visibility = 'PRIVATE'
  uploadProgress.value = 0
  uploadError.value = ''
  fileError.value = ''
}

async function submitUpload() {
  if (!uploadFile.value) {
    fileError.value = '请选择要上传的学习资源文件'
    return
  }
  if (!form.title.trim()) {
    uploadError.value = '请填写资源标题'
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  uploadError.value = ''
  try {
    const created = await uploadResource({
      title: form.title.trim(),
      description: form.description.trim(),
      category: form.category,
      visibility: form.visibility,
      file: uploadFile.value
    }, event => {
      if (event.total) uploadProgress.value = Math.min(100, Math.round(event.loaded * 100 / event.total))
    })
    uploadOpen.value = false
    ElMessage.success(created.visibility === 'PUBLIC' ? '资源已上传并公开到社区' : '资源已保存到「我的资源」')
    tab.value = 'mine'
    page.value = 1
    records.value = []
    total.value = 0
    await load()
  } catch (err) {
    uploadError.value = err.response?.data?.message || '上传失败，请检查网络后重试'
  } finally {
    uploading.value = false
  }
}

function openEdit(item) {
  editing.value = item
  editForm.title = item.title
  editForm.description = item.description || ''
  editForm.category = item.category
  editForm.visibility = item.visibility
  editError.value = ''
  editOpen.value = true
}

async function saveEdit() {
  if (!editForm.title.trim()) {
    editError.value = '请填写资源标题'
    return
  }
  saving.value = true
  editError.value = ''
  try {
    await updateResource(editing.value.id, {
      title: editForm.title.trim(),
      description: editForm.description.trim(),
      category: editForm.category,
      visibility: editForm.visibility
    })
    editOpen.value = false
    ElMessage.success(editForm.visibility === 'PUBLIC' ? '资料信息已更新并公开到社区' : '资料信息已更新')
    await load()
  } catch (err) {
    editError.value = err.response?.data?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`删除「${item.title}」后不可恢复，确定继续吗？`, '删除资源',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteResource(item.id)
    ElMessage.success('资源已删除')
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    // The API client already displays the error.
  }
}

async function downloadFile(item) {
  if (downloadingId.value) return
  downloadingId.value = item.id
  try {
    const blob = await downloadResource(item.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    item.downloadCount += 1
  } catch {
    // The API client already displays the error.
  } finally {
    downloadingId.value = null
  }
}

function extensionOf(name) {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
}

function formatLabel(extension) {
  return BADGE_LABELS[extension] || (extension ? extension.toUpperCase() : 'FILE')
}

function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${unit === 0 || value >= 10 ? Math.round(value) : value.toFixed(1)} ${units[unit]}`
}

function formatDate(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
onBeforeUnmount(() => {
  activeRequest?.abort()
  clearTimeout(keywordTimer)
})
</script>

<style scoped>
.resource-library {
  max-width: 960px;
  margin: 0 auto;
  padding-bottom: 32px;
  color: #25374d;
}
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 5px 0 20px;
}
.heading-copy { max-width: 640px; }
.eyebrow {
  color: #2e5fa3;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: .12em;
}
h1 {
  margin: 4px 0 7px;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 680;
  line-height: 1.2;
  letter-spacing: -.035em;
}
.lede { color: #5a6b80; line-height: 1.7; }
.scope-tabs {
  display: flex;
  gap: 26px;
  border-bottom: 1px solid #d9e1eb;
}
.scope-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 2px 12px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #606f82;
  font: inherit;
  cursor: pointer;
}
.scope-tabs button[aria-current="page"] {
  border-color: #2e5fa3;
  color: #224e89;
  font-weight: 650;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 0 8px;
  flex-wrap: wrap;
}
.search-input { width: min(320px, 100%); }
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.filters .el-select { width: 126px; }
.list-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
  color: #77879b;
  font-size: 12px;
}
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}
.resource-card {
  display: flex;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid #dde4ed;
  border-radius: 14px;
}
.file-badge, .selected-icon {
  --badge: #5c6b7d;
  --badge-bg: #eff2f6;
  display: grid;
  place-items: center;
  flex: none;
  color: var(--badge);
  background: var(--badge-bg);
  font-weight: 700;
  letter-spacing: .02em;
}
.file-badge {
  width: 58px;
  height: 68px;
  border-radius: 10px;
  font-size: 12px;
}
.ext-pdf { --badge: #b54646; --badge-bg: #fbecec; }
.ext-docx { --badge: #2c5f9c; --badge-bg: #eaf1fa; }
.ext-pptx { --badge: #a65d24; --badge-bg: #fcf0e6; }
.ext-xlsx { --badge: #2f7150; --badge-bg: #e9f4ee; }
.ext-txt { --badge: #5c6b7d; --badge-bg: #eff2f6; }
.ext-md { --badge: #65509a; --badge-bg: #f0ecfa; }
.card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.card-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.card-head h2 {
  display: -webkit-box;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #24364d;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow-wrap: anywhere;
}
.description {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 5px;
  color: #5f7085;
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow-wrap: anywhere;
}
.description.muted { color: #9aa8b8; }
.meta {
  display: flex;
  align-items: center;
  gap: 5px 10px;
  flex-wrap: wrap;
  margin-top: 9px;
  color: #8493a6;
  font-size: 12px;
}
.category {
  padding: 1px 7px;
  border-radius: 10px;
  background: #edf3fa;
  color: #315d92;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 10px;
}
.author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #6d7e92;
  font-size: 12px;
}
.author > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-name {
  min-width: 0;
  overflow: hidden;
  color: #8493a6;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
  margin-left: auto;
}
.actions .el-button + .el-button { margin-left: 0; }
.actions .el-button .el-icon { margin-right: 2px; }
.state-card {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 12px;
  min-height: 220px;
  margin-top: 8px;
  padding: 28px;
  background: #fff;
  border: 1px solid #dde4ed;
  border-radius: 14px;
}
.state-card p { color: #52677f; font-size: 14px; }
.state-card.empty p { color: #6d7e92; }
.empty-icon { color: #7b99bf; }
.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
.file-input { display: none; }
.resource-form { display: grid; gap: 12px; color: #263750; }
.file-picker {
  display: grid;
  justify-items: center;
  gap: 7px;
  width: 100%;
  padding: 24px;
  color: #345c90;
  background: #f6f9fd;
  border: 1px dashed #b9c9dc;
  border-radius: 10px;
  cursor: pointer;
}
.file-picker:disabled { cursor: not-allowed; opacity: .7; }
.file-picker strong { font-size: 14px; }
.file-picker small, .selected-copy small { color: #74869b; font-size: 12px; }
.selected-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e1e7ef;
  border-radius: 10px;
}
.selected-icon {
  width: 42px;
  height: 50px;
  border-radius: 8px;
  font-size: 10px;
}
.selected-copy {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.selected-copy strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.metadata { margin-top: 2px; }
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}
.hint { color: #6f8094; font-size: 12px; line-height: 1.6; }
.warning { color: #b13838; font-size: 13px; }
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding-top: 2px;
}
@media (hover: hover) {
  .resource-card { transition: border-color 120ms ease, box-shadow 120ms ease; }
  .resource-card:hover { border-color: #b6c7d9; box-shadow: 0 4px 14px #263a520d; }
}
@media (max-width: 820px) {
  .resource-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .page-head { align-items: flex-start; flex-direction: column; gap: 12px; }
  .page-head .el-button { align-self: stretch; }
  .scope-tabs { gap: 18px; }
  .toolbar { align-items: stretch; }
  .search-input { width: 100%; }
  .filters { width: 100%; }
  .filters .el-select { flex: 1 1 110px; width: auto; }
  .filters .el-button { margin-left: auto; }
  .form-grid { grid-template-columns: 1fr; }
  .selected-file { flex-wrap: wrap; }
}
@media (max-width: 480px) {
  .scope-tabs { gap: 12px; }
  .resource-card { padding: 13px; gap: 11px; }
  .file-badge { width: 48px; height: 58px; }
  .card-footer { align-items: flex-start; flex-direction: column; }
  .actions { width: 100%; justify-content: flex-end; }
}
</style>
