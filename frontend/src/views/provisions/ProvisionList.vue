<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="条文内容 / 条号 / 法律名称" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="法律名称">
          <el-input v-model="query.lawName" placeholder="如：中华人民共和国刑法" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 140px">
            <el-option label="刑法" value="刑法" />
            <el-option label="民法" value="民法" />
            <el-option label="行政法" value="行政法" />
          </el-select>
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
      <el-empty v-if="loading === false && list.length === 0" description="没有找到符合条件的法条" />
      <div v-for="p in list" :key="p.id" class="row" @click="$router.push('/provisions/' + p.id)">
        <div class="row-title">
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { listProvisions } from '../../api/content'

const route = useRoute()
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(true)
const query = reactive({
  keyword: route.query.keyword || '',
  lawName: '',
  category: '',
  tag: ''
})

async function load() {
  loading.value = true
  try {
    const data = await listProvisions({ ...query, page: page.value, size: pageSize.value })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}
function reset() {
  Object.assign(query, { keyword: '', lawName: '', category: '', tag: '' })
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
