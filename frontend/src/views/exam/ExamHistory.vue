<template>
  <details class="history" @toggle="onToggle">
    <summary>答题记录</summary>
    <div v-loading="loading" class="entries">
      <p v-if="failed">记录加载失败 <el-button link type="primary" @click="load(page)">重新加载</el-button></p>
      <p v-else-if="!loading && !sessions.length">暂无答题记录</p>
      <ul v-else>
        <li v-for="session in sessions" :key="session.id">
          <span>{{ subjectLabel(session) || '综合练习' }} · {{ session.total }} 题</span>
          <time>{{ session.startTime?.replace('T', ' ').slice(0, 16) }}</time>
          <router-link :to="(session.endTime ? '/exam/result/' : '/exam/run/') + session.id">
            {{ session.endTime ? '查看成绩' : '进入答题' }}
          </router-link>
        </li>
      </ul>
      <el-pagination v-if="total > pageSize" :page-size="pageSize" :total="total" :current-page="page"
        layout="prev, pager, next" :pager-count="5" @current-change="load" />
    </div>
  </details>
</template>

<script setup>
import { ref } from 'vue'
import { examHistory } from '../../api/exam'
import { subjectLabel } from './subject-label'

const sessions = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const failed = ref(false)
const pageSize = 8
let lastRequest = 0

function onToggle(event) {
  if (event.currentTarget.open) load(1)
}

async function load(number) {
  const request = ++lastRequest
  page.value = number
  loading.value = true
  failed.value = false
  try {
    const data = await examHistory({ page: number, size: pageSize })
    if (request === lastRequest) {
      sessions.value = data.records
      total.value = data.total
    }
  } catch {
    if (request === lastRequest) failed.value = true
  } finally {
    if (request === lastRequest) loading.value = false
  }
}
</script>

<style scoped>
.history { margin-top: 12px; border-top: 1px solid #ebeef5; }
summary { padding-top: 12px; cursor: pointer; font-weight: 600; }
.entries { min-height: 48px; margin-top: 8px; }
.entries li { padding: 10px 0; display: flex; flex-wrap: wrap; align-items: baseline; gap: 5px 14px; }
.entries time { color: #606266; font-size: 12px; }
.entries a { margin-inline-start: auto; }
</style>
