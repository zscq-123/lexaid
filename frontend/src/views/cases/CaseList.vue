<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题 / 案情 / 裁判要旨" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="案由">
          <el-input v-model="query.caseType" placeholder="如：故意伤害" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="法院">
          <el-input v-model="query.court" placeholder="如：北京市" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="年份">
          <el-input-number v-model="query.year" :min="2000" :max="2030" controls-position="right" placeholder="全部" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="list-card">
      <el-empty v-if="list.length === 0" description="没有找到符合条件的案例" />
      <div v-for="c in list" :key="c.id" class="row" @click="$router.push('/cases/' + c.id)">
        <div class="row-title">
          <el-tag size="small">{{ c.caseType }}</el-tag>
          <span class="title">{{ c.title }}</span>
          <span class="meta">{{ c.court }} · {{ c.judgeDate }} · {{ c.procedure }}</span>
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
import { onMounted, reactive, ref } from 'vue'
import { listCases } from '../../api/content'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const query = reactive({ keyword: '', caseType: '', court: '', year: null })

async function load() {
  const data = await listCases({ ...query, page: page.value, size: pageSize.value })
  list.value = data.records
  total.value = data.total
}
function search() {
  page.value = 1
  load()
}
function reset() {
  Object.assign(query, { keyword: '', caseType: '', court: '', year: null })
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
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.title {
  font-weight: 600;
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
