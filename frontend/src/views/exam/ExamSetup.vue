<template>
  <el-card shadow="never" class="setup-card">
    <template #header><span class="card-title">组卷设置</span></template>
    <el-form :model="form" label-width="100px" style="max-width: 520px">
      <el-form-item label="组卷方式">
        <el-radio-group v-model="form.mode">
          <el-radio value="random">随机组卷</el-radio>
          <el-radio value="entity">按实体组卷</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="题目类别">
        <el-select v-model="form.category" placeholder="全部类别" clearable style="width: 240px">
          <el-option label="刑法" value="刑法" />
          <el-option label="民法" value="民法" />
          <el-option label="行政法" value="行政法" />
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" placeholder="全部难度" clearable style="width: 240px">
          <el-option v-for="i in 5" :key="i" :label="i + ' 星'" :value="i" />
        </el-select>
      </el-form-item>
      <el-form-item label="题量">
        <el-input-number v-model="form.count" :min="1" :max="50" />
      </el-form-item>
      <template v-if="form.mode === 'entity'">
        <el-form-item label="实体类型">
          <el-select v-model="form.sourceType" style="width: 240px">
            <el-option label="法条" value="PROVISION" />
            <el-option label="案例" value="CASE" />
            <el-option label="观点" value="VIEWPOINT" />
          </el-select>
        </el-form-item>
        <el-form-item label="实体 ID">
          <el-input-number v-model="form.sourceId" :min="1" />
          <div class="tip">优先选取该实体关联题目，不足时补随机题</div>
        </el-form-item>
      </template>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="start">开始答题</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createSession } from '../../api/exam'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  mode: 'random',
  category: null,
  difficulty: null,
  count: 10,
  sourceType: 'PROVISION',
  sourceId: 4
})

async function start() {
  loading.value = true
  try {
    const payload = {
      category: form.category || undefined,
      difficulty: form.difficulty || undefined,
      count: form.count,
      sourceType: form.mode === 'entity' ? form.sourceType : undefined,
      sourceId: form.mode === 'entity' ? form.sourceId : undefined
    }
    const data = await createSession(payload)
    router.push('/exam/run/' + data.sessionId)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.setup-card {
  max-width: 720px;
  border-radius: 8px;
}
.card-title {
  font-weight: 600;
}
.tip {
  color: #909399;
  font-size: 12px;
}
</style>
