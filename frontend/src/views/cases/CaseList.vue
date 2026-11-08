<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题 / 案情 / 裁判要旨" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="法域">
          <el-select v-model="query.jurisdiction" aria-label="法域" placeholder="全部" clearable style="width: 120px">
            <el-option label="中国" value="CN" />
            <el-option label="美国" value="US" />
            <el-option label="欧盟" value="EU" />
            <el-option label="英国" value="GB" />
            <el-option label="欧洲人权法院" value="ECHR" />
            <el-option label="国际" value="INTL" />
          </el-select>
        </el-form-item>
        <el-form-item label="案由">
          <el-input v-model="query.caseType" placeholder="如：故意伤害" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="法院">
          <el-cascader
            ref="courtPicker"
            v-model="query.courtNode"
            :options="courtOptions"
            :props="courtPickerProps"
            :filter-method="matchCourt"
            :placeholder="courtsLoading ? '正在加载法院…' : '选择地区或搜索法院'"
            aria-label="法院"
            class="court-picker"
            popper-class="case-court-menu"
            clearable
            filterable
            @change="courtPicker?.togglePopperVisible(false)"
            @expand-change="revealCourtLevel"
            @visible-change="retryCourts"
          >
            <template #header>
              <span class="court-hint">点地区名称展开，点左侧圆圈选择整个地区。</span>
            </template>
            <template #empty>
              <p class="court-empty">{{ courtsLoading ? '正在加载法院…' : courtsFailed ? '加载失败，重新展开以重试' : '没有匹配的地区或法院' }}</p>
            </template>
          </el-cascader>
        </el-form-item>
        <el-form-item label="年份">
          <el-input-number v-model="query.year" :min="1600" :max="2030" controls-position="right" placeholder="全部" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="没有找到符合条件的案例" />
      <div v-for="c in list" :key="c.id" class="row" @click="$router.push('/cases/' + c.id)">
        <div class="row-title">
          <el-tag size="small" type="info" effect="plain">{{ jurisdictionLabel(c.jurisdiction) }}</el-tag>
          <el-tag size="small">{{ c.caseType }}</el-tag>
          <span class="title">{{ c.title }}</span>
          <span class="meta">{{ [c.court || '审理法院未注明', c.judgeDate, c.procedure].filter(Boolean).join(' · ') }}</span>
        </div>
        <div class="row-content">{{ c.summary }}</div>
        <div class="row-tags">
          <el-tag v-for="t in c.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
        </div>
      </div>
      <el-pagination
        v-if="total > pageSize"
        class="pager"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { listCaseCourts, listCases } from '../../api/content'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(true)
const initialFilters = { keyword: '', jurisdiction: '', caseType: '', courtNode: null, year: null }
const query = reactive({ ...initialFilters })
const courts = ref([])
const courtPicker = ref(null)
const courtsLoading = ref(false)
const courtsFailed = ref(false)
const courtPickerProps = { checkStrictly: true, emitPath: false }
const courtOptions = computed(() => {
  if (!query.jurisdiction) return courts.value
  return courts.value.find(root => root.value === query.jurisdiction)?.children || []
})
let lastRequest = 0

function containsCourt(nodes, value) {
  return nodes.some(node => node.value === value || (node.children?.length && containsCourt(node.children, value)))
}

watch(() => query.jurisdiction, () => {
  if (query.courtNode && !containsCourt(courtOptions.value, query.courtNode)) query.courtNode = null
})

function matchCourt(node, keyword) {
  const path = node.pathLabels.join(' ').toLowerCase()
  return keyword.trim().toLowerCase().split(/\s+/).every(word => path.includes(word))
}

async function loadCourts() {
  if (courtsLoading.value) return
  courtsLoading.value = true
  courtsFailed.value = false
  try {
    courts.value = await listCaseCourts()
  } catch {
    courtsFailed.value = true
  } finally {
    courtsLoading.value = false
  }
}

function retryCourts(open) {
  if (open && courtsFailed.value) loadCourts()
}

async function revealCourtLevel() {
  await nextTick()
  const panel = courtPicker.value?.cascaderPanelRef?.$el
  if (panel) panel.scrollLeft = panel.scrollWidth
}

async function load() {
  const request = ++lastRequest
  loading.value = true
  try {
    const data = await listCases({ ...query, page: page.value, size: pageSize.value })
    if (request === lastRequest) {
      list.value = data.records
      total.value = data.total
    }
  } finally {
    if (request === lastRequest) loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}
function reset() {
  Object.assign(query, initialFilters)
  search()
}
function jurisdictionLabel(value) {
  return { CN: '中国', US: '美国', EU: '欧盟', GB: '英国', ECHR: '欧洲人权法院', INTL: '国际' }[value] || value || '未标注'
}
function changePage(p) {
  page.value = p
  load()
}
onMounted(() => {
  loadCourts()
  load()
})
</script>

<style scoped>
.filter-card {
  border-radius: 8px;
  margin-bottom: 12px;
}
.list-card {
  border-radius: 8px;
}
.court-picker { width: clamp(200px, 26vw, 320px); }
.court-hint { font-size: 12px; color: #606266; }
.court-empty { padding: 8px 14px; font-size: 13px; color: #606266; }
:global(.case-court-menu .el-cascader-panel) {
  max-width: min(960px, calc(100vw - 28px));
  overflow-x: auto;
}
:global(.case-court-menu .el-cascader-menu) { flex: 0 0 auto; }
:global(.case-court-menu .el-cascader__suggestion-panel) { max-width: calc(100vw - 28px); }
:global(.case-court-menu .el-cascader__suggestion-item) {
  min-height: 34px;
  height: auto;
  line-height: 1.5;
  padding-block: 6px;
}
:global(.case-court-menu .el-cascader__suggestion-item > span) {
  white-space: normal;
  overflow-wrap: anywhere;
}
.row {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;
}
.row:hover {
  border-color: #2e5fa3;
}
.row-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.title {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.meta {
  color: #909399;
  font-size: 12px;
}
.row-content {
  color: #606266;
  line-height: 1.7;
  font-size: 13px;
  margin-bottom: 6px;
}
.row-tags {
  display: flex;
  gap: 6px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
