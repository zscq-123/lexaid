<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="条文内容 / 条号 / 法律名称" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="法律名称">
          <el-input v-model="query.lawName" placeholder="中英文法律名称" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="法域">
          <el-select v-model="query.jurisdiction" placeholder="全部" clearable style="width: 120px">
            <el-option label="中国" value="CN" />
            <el-option label="美国" value="US" />
            <el-option label="欧盟" value="EU" />
            <el-option label="英国" value="GB" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="query.category" placeholder="全部类别" aria-label="类别" clearable filterable
            :loading="categoriesLoading" :no-data-text="categoriesFailed ? '加载失败，重新展开以重试' : '暂无类别'"
            style="width: 220px" @visible-change="retryCategories">
            <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="query.tag" placeholder="如：正当防卫" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="list-card">
      <el-empty v-if="loading === false && list.length === 0" description="没有找到符合条件的法条" />
      <div v-for="p in list" :key="p.id" class="row" @click="$router.push('/provisions/' + p.id)">
        <div class="row-title">
          <el-tag size="small" type="info" effect="plain">{{ jurisdictionLabel(p.jurisdiction) }}</el-tag>
          <el-tag size="small">{{ p.category }}</el-tag>
          <span class="article">{{ p.lawName }} {{ p.articleNo }}</span>
        </div>
        <div class="row-content">{{ p.content }}</div>
        <div class="row-tags">
          <el-tag v-for="t in p.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listProvisionCategories, listProvisions } from '../../api/content'

const route = useRoute()
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(true)
let lastRequest = 0
const query = reactive({
  keyword: route.query.keyword || '',
  lawName: '',
  jurisdiction: '',
  category: '',
  tag: ''
})

const categories = ref([])
const categoriesLoading = ref(false)
const categoriesFailed = ref(false)
const categoryOptions = computed(() => Array.from(new Set(categories.value
  .filter(row => !query.jurisdiction || row.jurisdiction === query.jurisdiction)
  .map(row => row.category))))

watch(() => query.jurisdiction, () => {
  if (query.category && !categoryOptions.value.includes(query.category)) query.category = ''
})

async function loadCategories() {
  categoriesLoading.value = true
  categoriesFailed.value = false
  try {
    categories.value = await listProvisionCategories()
  } catch {
    categoriesFailed.value = true
  } finally {
    categoriesLoading.value = false
  }
}

function retryCategories(open) {
  if (open && categoriesFailed.value) loadCategories()
}

async function load() {
  const request = ++lastRequest
  loading.value = true
  try {
    const data = await listProvisions({ ...query, page: page.value, size: pageSize.value })
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
  Object.assign(query, { keyword: '', lawName: '', jurisdiction: '', category: '', tag: '' })
  search()
}
function jurisdictionLabel(value) {
  return { CN: '中国', US: '美国', EU: '欧盟', GB: '英国' }[value] || value || '未标注'
}
function changePage(p) {
  page.value = p
  load()
}

onMounted(() => {
  loadCategories()
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
.row {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.row:hover {
  border-color: #2e5fa3;
}
.row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.article {
  font-weight: 600;
  overflow-wrap: anywhere;
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
