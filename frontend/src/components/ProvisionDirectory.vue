<template>
  <el-form-item label="法典目录">
    <el-select-v2 :model-value="selected" aria-label="法典目录" placeholder="选择或搜索法典" filterable clearable
      :options="selectedOption" :loading="loadingCodes" :no-data-text="codeError ? '加载失败，请重新展开' : '暂无法律目录'"
      style="width: 240px" @visible-change="openCodes" @change="chooseCode" />
  </el-form-item>
  <el-form-item v-if="code" label="编章范围">
    <el-cascader ref="picker" :model-value="path" :options="nodes" :props="treeProps"
      aria-label="编章范围" placeholder="全部编章" filterable clearable
      popper-class="provision-outline-menu" style="width: 280px"
      @visible-change="retryTree" @expand-change="showNextLevel" @change="choosePath">
      <template #header>
        <div class="outline-header">
          <span class="outline-hint">点标题展开下一级，点圆圈选择整个层级。</span>
          <el-button v-if="path" link type="primary" @click="choosePath('')">全部编章</el-button>
        </div>
      </template>
      <template #empty>{{ treeError ? '加载失败，请重新展开' : loadingTree ? '正在读取目录…' : '无下级目录' }}</template>
    </el-cascader>
  </el-form-item>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { listProvisionCodes, getProvisionOutline } from '../api/content'

const props = defineProps({ jurisdiction: String, code: String, path: String })
const emit = defineEmits(['select'])
const treeProps = { label: 'label', value: 'path', children: 'children', emitPath: false, checkStrictly: true }
const codes = ref([])
const nodes = ref([])
const picker = ref(null)
const loadingCodes = ref(false)
const loadingTree = ref(false)
const codeError = ref(false)
const treeError = ref(false)
const selected = computed(() => props.code ? JSON.stringify([props.jurisdiction, props.code]) : '')
let codeRequest, treeRequest

watch(() => props.jurisdiction, () => {
  codeRequest?.abort()
  codes.value = []
  codeError.value = false
  loadingCodes.value = false
})

async function openCodes(open) {
  if (!open || loadingCodes.value || codes.value.length) return
  const request = new AbortController()
  codeRequest = request
  loadingCodes.value = true
  codeError.value = false
  try {
    const rows = await listProvisionCodes(props.jurisdiction, request.signal)
    if (!request.signal.aborted) codes.value = rows.map(row => ({
      value: JSON.stringify([row.jurisdiction, row.lawName]), label: row.lawName
    }))
  } catch {
    if (!request.signal.aborted) codeError.value = true
  } finally {
    if (!request.signal.aborted) loadingCodes.value = false
  }
}
const selectedOption = computed(() => selected.value && !codes.value.some(o => o.value === selected.value)
  ? [{ value: selected.value, label: props.code }, ...codes.value] : codes.value)
function chooseCode(value) {
  const [jurisdiction, code] = value ? JSON.parse(value) : [props.jurisdiction, '']
  emit('select', { jurisdiction, code, path: '' })
}

async function loadTree() {
  treeRequest?.abort()
  nodes.value = []
  treeError.value = false
  loadingTree.value = Boolean(props.code)
  if (!props.code) return
  const request = new AbortController()
  treeRequest = request
  try {
    const tree = await getProvisionOutline(props.jurisdiction, props.code, request.signal)
    if (!request.signal.aborted) nodes.value = tree
  } catch {
    if (!request.signal.aborted) treeError.value = true
  } finally {
    if (!request.signal.aborted) loadingTree.value = false
  }
}
watch(() => [props.jurisdiction, props.code], loadTree, { immediate: true })
function retryTree(open) { if (open && treeError.value) loadTree() }
async function showNextLevel() {
  await nextTick()
  const panel = picker.value?.cascaderPanelRef?.$el
  if (panel) panel.scrollLeft = panel.scrollWidth
}
function choosePath(path) {
  picker.value?.togglePopperVisible(false)
  emit('select', { jurisdiction: props.jurisdiction, code: props.code, path: path || '' })
}
onBeforeUnmount(() => { codeRequest?.abort(); treeRequest?.abort() })
</script>

<style>
.provision-outline-menu .el-cascader-panel { max-width: min(1000px, calc(100vw - 26px)); overflow-x: auto; }
.provision-outline-menu .el-cascader-menu { flex: 0 0 auto; }
.provision-outline-menu .el-cascader__suggestion-panel { max-width: calc(100vw - 26px); }
.provision-outline-menu .el-cascader__suggestion-item { height: auto; min-height: 36px; line-height: 1.5; padding-block: 6px; }
.provision-outline-menu .el-cascader__suggestion-item > span { white-space: normal; overflow-wrap: anywhere; }
.provision-outline-menu .outline-header { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.provision-outline-menu .outline-hint { color: var(--lex-muted); font-size: .8rem; }
</style>
