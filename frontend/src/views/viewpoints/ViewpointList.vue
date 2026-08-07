<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="学者">
          <el-input v-model="query.scholar" placeholder="如：张明楷" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="观点标题 / 摘要" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="query.tag" placeholder="如：正当防卫" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="list-card">
      <el-empty v-if="list.length === 0" description="没有找到符合条件的观点" />
      <div v-for="v in list" :key="v.id" class="row" @click="$router.push('/viewpoints/' + v.id)">
        <div class="row-title">
          <el-tag size="small" type="warning">{{ v.scholarName }}</el-tag>
          <span class="title">{{ v.title }}</span>
        </div>
        <div class="row-content">{{ v.summary }}</div>
        <div class="row-meta">
          <span>{{ v.source }} · {{ v.publishYear }}</span>
        </div>
        <div class="row-tags">
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

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const query = reactive({ scholar: '', keyword: '', tag: '' })

async function load() {
  const data = await listViewpoints({ ...query, page: page.value, size: pageSize.value })
  list.value = data.records
  total.value = data.total
}
function search() {
  page.value = 1
  load()
}
function reset() {
  Object.assign(query, { scholar: '', keyword: '', tag: '' })
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
