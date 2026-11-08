<template>
  <section v-loading="loading" class="favorites" aria-label="我的收藏">
    <div class="filters">
      <el-select v-model="selectedFolder" aria-label="收藏分区" class="folder-picker" popper-class="favorite-folder-options" filterable>
        <el-option label="全部分区" :value="ALL" />
        <el-option label="未分区" :value="UNFILED" />
        <el-option v-for="folder in folders" :key="folder.id" :label="folder.name" :value="folder.id" />
      </el-select>
      <el-select v-model="selectedType" aria-label="收藏类型" class="type-picker">
        <el-option label="全部类型" :value="ALL" />
        <el-option v-for="(label, type) in typeLabels" :key="type" :label="label" :value="type" />
      </el-select>
      <el-button type="primary" plain :disabled="busy" @click="openFolderEditor()">新建分区</el-button>
    </div>

    <div v-if="loadFailed" class="retry">
      收藏加载失败。<el-button link type="primary" @click="load">重试</el-button>
    </div>
    <template v-else-if="!loading">
      <div class="list-summary">
        <span>共 {{ filtered.length }} 条收藏</span>
        <div v-if="currentFolder">
          <el-button link :disabled="busy" @click="openFolderEditor(currentFolder)">重命名</el-button>
          <el-button link type="danger" :disabled="busy" @click="removeFolder">删除分区</el-button>
        </div>
      </div>
      <el-empty v-if="!filtered.length" :image-size="64" :description="emptyDescription" />
      <ul v-else class="favorite-list">
        <li v-for="item in pageItems" :key="item.id" class="favorite-row">
          <div class="entry">
            <router-link v-if="detailLink(item)" class="entry-title" :to="detailLink(item)">{{ item.title }}</router-link>
            <span v-else class="entry-title">{{ item.title }}</span>
            <div class="entry-meta">
              <span>{{ typeLabels[item.targetType] || item.targetType }}</span>
              <span>{{ folderNames.get(item.folderId) || '未分区' }}</span>
              <time v-if="item.createdAt" :datetime="item.createdAt">{{ item.createdAt.slice(0, 10) }}</time>
            </div>
          </div>
          <div class="row-actions">
            <el-dropdown trigger="click" :disabled="busy" @command="destination => moveTo(item, destination)">
              <el-button link type="primary" :disabled="busy">移动</el-button>
              <template #dropdown>
                <el-dropdown-menu class="move-menu">
                  <el-dropdown-item :command="UNFILED" :disabled="item.folderId == null">未分区</el-dropdown-item>
                  <el-dropdown-item v-for="folder in folders" :key="folder.id" :command="folder.id" :disabled="item.folderId === folder.id">
                    {{ folder.name }}
                  </el-dropdown-item>
                  <el-dropdown-item command="create" divided>新建分区</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link :disabled="busy" @click="unfavorite(item)">取消收藏</el-button>
          </div>
        </li>
      </ul>
      <el-pagination v-if="filtered.length > pageSize" v-model:current-page="page" :page-size="pageSize"
        :total="filtered.length" :pager-count="5" layout="prev, pager, next" class="pager" />
    </template>

    <el-dialog v-model="editorOpen" :title="editingFolder ? '重命名分区' : '新建分区'" width="min(380px, 92vw)"
      :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
      <el-form @submit.prevent="saveFolder">
        <el-form-item label="分区名称" :error="nameError">
          <el-input v-model="folderName" aria-label="分区名称" placeholder="例如：民法复习" @input="nameError = ''" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy" @click="editorOpen = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="saveFolder">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  createFavoriteFolder, deleteFavoriteFolder, listFavoriteFolders, listFavorites,
  moveFavorite, removeFavorite, renameFavoriteFolder
} from '../../api/favorite'

const ALL = 'all'
const UNFILED = 'unfiled'
const typeLabels = { PROVISION: '法条', CASE: '案例', VIEWPOINT: '学者观点 / 论文', QUESTION: '题目' }
const detailPaths = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
const folders = ref([])
const items = ref([])
const loading = ref(true)
const loadFailed = ref(false)
const busy = ref(false)
const selectedFolder = ref(ALL)
const route = useRoute()
const selectedType = ref(ALL)
const pageSize = 10
const page = ref(1)
const editorOpen = ref(false)
const editingFolder = ref(null)
const moveAfterCreate = ref(null)
const folderName = ref('')
const nameError = ref('')
let active = true
let latestLoad = 0

const folderNames = computed(() => new Map(folders.value.map(folder => [folder.id, folder.name])))
const currentFolder = computed(() => folders.value.find(folder => folder.id === selectedFolder.value))
const filtered = computed(() => items.value.filter(item => {
  const folder = folderNames.value.has(item.folderId) ? item.folderId : UNFILED
  return (selectedFolder.value === ALL || selectedFolder.value === folder)
    && (selectedType.value === ALL || item.targetType === selectedType.value)
}))
const pageItems = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const emptyDescription = computed(() => {
  if (!items.value.length) return '还没有收藏，可在法条、案例或学者观点详情页点击「收藏」。'
  return selectedFolder.value !== ALL && selectedType.value === ALL ? '这个分区还没有收藏' : '没有符合条件的收藏'
})

watch(() => route.query.folder, value => {
  const id = Number(value)
  selectedFolder.value = Number.isSafeInteger(id) && id > 0 ? id : ALL
}, { immediate: true })
watch([selectedFolder, selectedType], () => { page.value = 1 })
watch(() => filtered.value.length, length => {
  page.value = Math.min(page.value, Math.max(1, Math.ceil(length / pageSize)))
})

function detailLink(item) {
  if (!detailPaths[item.targetType] || item.title === '内容已删除') return null
  return detailPaths[item.targetType] + item.targetId
}

async function load() {
  const version = ++latestLoad
  loading.value = true
  loadFailed.value = false
  try {
    const [folderList, favorites] = await Promise.all([listFavoriteFolders(), listFavorites()])
    if (!active || version !== latestLoad) return
    folders.value = folderList
    items.value = favorites
    if (typeof selectedFolder.value === 'number' && !currentFolder.value) selectedFolder.value = ALL
  } catch {
    if (active && version === latestLoad) loadFailed.value = true
  } finally {
    if (active && version === latestLoad) loading.value = false
  }
}

async function change(action) {
  if (!active || busy.value) return
  busy.value = true
  try {
    await action()
  } catch {
    // The API client displays the error. Reload in case another window changed the collection.
  } finally {
    if (active) {
      await load()
      busy.value = false
    }
  }
}

function openFolderEditor(folder = null, item = null) {
  editingFolder.value = folder
  moveAfterCreate.value = item
  folderName.value = folder?.name || ''
  nameError.value = ''
  editorOpen.value = true
}

async function saveFolder() {
  const name = folderName.value.trim()
  if (!name || [...name].length > 40) {
    nameError.value = '请输入 1～40 个字符'
    return
  }
  if (['全部分区', '未分区'].includes(name) || folders.value.some(f => f.id !== editingFolder.value?.id && f.name === name)) {
    nameError.value = '该分区名称已在使用'
    return
  }
  await change(async () => {
    const folder = editingFolder.value
      ? await renameFavoriteFolder(editingFolder.value.id, name)
      : await createFavoriteFolder(name)
    if (!active) return
    editorOpen.value = false
    if (moveAfterCreate.value) await moveFavorite(moveAfterCreate.value.id, folder.id)
  })
}

async function removeFolder() {
  const folder = currentFolder.value
  if (!folder || busy.value) return
  try {
    await ElMessageBox.confirm('删除「' + folder.name + '」后，其中的收藏会移到「未分区」。', '删除分区',
      { confirmButtonText: '删除分区', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  await change(async () => {
    await deleteFavoriteFolder(folder.id)
    if (active) selectedFolder.value = UNFILED
  })
}

function moveTo(item, destination) {
  if (destination === 'create') return openFolderEditor(null, item)
  return change(() => moveFavorite(item.id, destination === UNFILED ? null : destination))
}

function unfavorite(item) {
  return change(() => removeFavorite(item.id))
}

onMounted(load)
onBeforeUnmount(() => { active = false })
</script>

<style scoped>
.favorites { min-height: 150px; }
.filters, .list-summary, .row-actions, .entry-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; }
.filters { margin-bottom: 16px; }
.folder-picker { width: min(210px, 100%); }
.type-picker { width: 156px; }
.filters > .el-button { margin-left: auto; }
.retry { color: #606266; }
.list-summary { justify-content: space-between; color: #606266; font-size: 13px; }
.favorite-list { list-style: none; }
.favorite-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 0; border-bottom: 1px solid #ebeef5; }
.entry { min-width: 0; }
.entry-title { font-size: 15px; line-height: 1.6; font-weight: 600; overflow-wrap: anywhere; }
a.entry-title:hover { text-decoration: underline; }
.row-actions { flex-shrink: 0; }
.entry-meta { margin-top: 4px; font-size: 12px; color: #606266; overflow-wrap: anywhere; }
.move-menu { max-height: 300px; overflow: auto; max-width: min(300px, 90vw); overflow-wrap: anywhere; }
.move-menu :deep(.el-dropdown-menu__item) { white-space: normal; }
:global(.favorite-folder-options .el-select-dropdown) { max-width: calc(100vw - 32px); }
:global(.favorite-folder-options .el-select-dropdown__item) {
  height: auto;
  min-height: 34px;
  line-height: 1.5;
  padding-block: 6px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.pager { margin-top: 12px; justify-content: flex-end; }
@media (max-width: 600px) {
  .filters > .el-button { margin-left: 0; }
  .favorite-row { flex-wrap: wrap; }
  .entry { flex-basis: 100%; }
}
</style>
