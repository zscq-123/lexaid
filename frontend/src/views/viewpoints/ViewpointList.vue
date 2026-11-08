<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="作者">
          <el-input v-model="query.scholar" placeholder="如：张明楷" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="题名 / 摘要 / DOI" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="语言">
          <el-select v-model="query.languageCode" clearable placeholder="全部" style="width: 110px">
            <el-option label="中文" value="zh" />
            <el-option label="英文" value="en" />
            <el-option label="待核实" value="und" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="query.tag" placeholder="如：正当防卫" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.papersOnly">仅看论文</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="没有找到符合条件的论文或观点" />
      <div v-for="v in list" :key="v.id" class="row" @click="$router.push('/viewpoints/' + v.id)">
        <template v-if="v.sourceDataset">
          <h2 class="paper-title">
            <router-link class="title" :to="'/viewpoints/' + v.id" @click.stop>{{ v.title }}</router-link>
          </h2>
          <div class="paper-byline">{{ [v.scholarName, publicationLine(v)].filter(Boolean).join(' · ') }}</div>
        </template>
        <div v-else class="row-title">
          <el-tag size="small" type="warning">{{ v.scholarName }}</el-tag>
          <router-link class="title" :to="'/viewpoints/' + v.id" @click.stop>{{ v.title }}</router-link>
        </div>
        <div v-if="v.summaryKind === 'metadata_only'" class="row-content">未收录作者摘要</div>
        <div v-else-if="v.summary" class="row-content"><span v-if="v.summaryKind === 'author_abstract'">作者摘要 · </span>{{ v.summary }}</div>
        <div v-if="!v.sourceDataset" class="row-meta">
          {{ [v.source, v.publishYear].filter(Boolean).join(' · ') }}
        </div>
        <div v-if="v.tags.length" class="row-tags">
          <el-tag v-for="t in v.tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
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
import { onMounted, reactive, ref } from 'vue'
import { listViewpoints } from '../../api/content'
import { publicationLine } from './paper-text'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(true)
const initialFilters = { scholar: '', keyword: '', tag: '', languageCode: '', papersOnly: false }
const query = reactive({ ...initialFilters })
let lastRequest = 0

async function load() {
  const request = ++lastRequest
  loading.value = true
  try {
    const data = await listViewpoints({ ...query, page: page.value, size: pageSize.value })
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
function changePage(p) {
  page.value = p
  load()
}
onMounted(load)
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
  color: inherit;
  text-decoration: none;
  overflow-wrap: anywhere;
}
.title:hover {
  text-decoration: underline;
}
.paper-title {
  margin: 0 0 4px;
  font: 600 19px/1.55 Georgia, 'Songti SC', 'Noto Serif CJK SC', SimSun, serif;
}
.paper-byline {
  color: #606266;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.65;
  margin-bottom: 8px;
}
.row-content {
  color: #606266;
  line-height: 1.7;
  font-size: 13px;
  margin-bottom: 6px;
}
.row-meta {
  color: #909399;
  font-size: 12px;
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
