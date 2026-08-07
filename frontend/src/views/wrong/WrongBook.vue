<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-radio-group v-model="all" @change="reload">
        <el-radio-button :value="false">仅错题</el-radio-button>
        <el-radio-button :value="true">全部</el-radio-button>
      </el-radio-group>
    </el-card>

    <el-card shadow="never" class="list-card">
      <el-empty v-if="list.length === 0" description="暂无记录，去答题吧" />
      <div v-for="(q, i) in list" :key="q.questionId" class="row">
        <div class="row-head">
          <span class="idx">{{ i + 1 }}</span>
          <el-tag size="small">{{ typeLabel(q.type) }}</el-tag>
          <el-tag size="small" :type="q.isCorrect === null || q.isCorrect === undefined ? 'info' : q.isCorrect ? 'success' : 'danger'">
            {{ q.isCorrect === null || q.isCorrect === undefined ? '待自评' : q.isCorrect ? '正确' : '错误' }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">{{ q.category }}</el-tag>
        </div>
        <div class="stem">{{ q.stem }}</div>
        <div class="answer">
          <span>你的答案：{{ q.userAnswer || '（未作答）' }}</span>
          <span v-if="q.type !== 'SHORT'">正确答案：{{ q.correctAnswer }}</span>
          <span v-else>参考答案：{{ q.correctAnswer }}</span>
        </div>
        <div v-if="q.analysis" class="analysis">
          <div class="analysis-title">解析</div>
          {{ q.analysis }}
        </div>
        <div v-if="q.related.length" class="related">
          <el-tag v-for="r in q.related" :key="r.type + r.id" size="small" effect="plain" class="rel" @click="go(r)">
            {{ relLabel(r.type) }}：{{ r.title }} →
          </el-tag>
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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { wrongQuestions } from '../../api/exam'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const all = ref(false)

async function load() {
  const data = await wrongQuestions({ page: page.value, size: pageSize.value, all: all.value })
  list.value = data.records
  total.value = data.total
}
function reload() {
  page.value = 1
  load()
}
function changePage(p) {
  page.value = p
  load()
}
function typeLabel(t) {
  return { SINGLE: '单选', MULTIPLE: '多选', JUDGE: '判断', SHORT: '简答' }[t] || t
}
function relLabel(t) {
  return { PROVISION: '法条', CASE: '案例', VIEWPOINT: '观点', QUESTION: '题目' }[t] || t
}
function go(r) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (map[r.type]) router.push(map[r.type] + r.id)
}

onMounted(load)
</script>

<style scoped>
.filter-card,
.list-card {
  border-radius: 8px;
}
.list-card {
  margin-top: 12px;
}
.row {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 10px;
}
.row-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.idx {
  font-weight: 700;
  color: #2e5fa3;
}
.stem {
  margin: 10px 0 6px;
  font-size: 15px;
  line-height: 1.8;
}
.answer {
  display: flex;
  gap: 24px;
  color: #606266;
  font-size: 13px;
}
.analysis {
  margin-top: 8px;
  background: #f7f8fa;
  padding: 8px 10px;
  border-radius: 6px;
  line-height: 1.8;
  color: #606266;
}
.analysis-title {
  font-weight: 600;
  color: #2e5fa3;
}
.related {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.rel {
  cursor: pointer;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
