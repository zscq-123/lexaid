<template>
  <el-select :model-value="modelValue" filterable remote clearable :remote-method="search"
    :loading="loading" placeholder="输入法律名称、案名或观点关键词" aria-label="选择学习内容"
    style="width: 100%" @update:model-value="$emit('update:modelValue', $event || null)"
    @visible-change="open => open && !options.length && search(lastQuery)">
    <el-option v-for="item in options" :key="item.id" :value="item.id" :label="title(item)" />
    <template #empty><p class="picker-empty">{{ loading ? '正在搜索…' : failed ? '加载失败，请重新输入关键词' : '未找到匹配内容，请更换关键词' }}</p></template>
  </el-select>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { listProvisions, listCases, listViewpoints } from '../api/content'
const props = defineProps({ modelValue: Number, type: { type: String, required: true } })
const emit = defineEmits(['update:modelValue'])
const options = ref([])
const loading = ref(false)
const failed = ref(false)
let version = 0
let timer
let lastQuery = ''
function title(item) {
  return props.type === 'PROVISION' ? `${item.lawName} ${item.articleNo} · ${item.effectiveDate || '日期未注明'}`
    : props.type === 'VIEWPOINT' ? `${item.title} · ${item.scholarName || '作者未注明'}` : item.title
}
function search(keyword) {
  lastQuery = keyword
  const request = ++version
  clearTimeout(timer)
  options.value = []
  loading.value = true
  failed.value = false
  timer = setTimeout(async () => {
    try {
      const fetch = { PROVISION: listProvisions, CASE: listCases, VIEWPOINT: listViewpoints }[props.type]
      const data = await fetch({ keyword: keyword.trim() || undefined, page: 1, size: 20 })
      if (request === version) options.value = data.records
    } catch {
      if (request === version) { options.value = []; failed.value = true }
    } finally {
      if (request === version) loading.value = false
    }
  }, 250)
}
watch(() => props.type, () => { ++version; clearTimeout(timer); lastQuery = ''; options.value = []; loading.value = false; emit('update:modelValue', null) })
onBeforeUnmount(() => { ++version; clearTimeout(timer) })
</script>

<style scoped>
.picker-empty { padding: 12px; color: var(--lex-muted); text-align: center; }
</style>
