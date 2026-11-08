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
        <el-select
          v-model="form.category"
          aria-label="题目类别"
          :placeholder="categoriesFailed ? '类别加载失败，点击重试' : '全部类别'"
          :loading="categoriesLoading"
          :no-data-text="categoriesFailed ? '加载失败，重新展开以重试' : '暂无符合条件的类别'"
          no-match-text="没有匹配的题目类别"
          clearable
          filterable
          class="setup-select"
          @visible-change="retryCategories"
        >
          <el-option v-for="option in categoryOptions" :key="option.category" :label="option.category" :value="option.category">
            <span>{{ option.category }}</span><span class="option-count">{{ option.count }} 题</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" aria-label="难度" placeholder="全部难度" clearable class="setup-select">
          <el-option v-for="i in 5" :key="i" :label="i + ' 星'" :value="i" :disabled="!supportsDifficulty(i)" />
        </el-select>
      </el-form-item>
      <el-form-item label="题量">
        <el-input-number v-model="form.count" :min="1" :max="50" />
        <p v-if="selectedCount != null && selectedCount < form.count" class="tip pool-limit">
          当前条件共有 {{ selectedCount }} 道题，本次将抽取全部 {{ selectedCount }} 道。
        </p>
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
    <ExamHistory />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createSession, listQuestionCategories } from '../../api/exam'
import ExamHistory from './ExamHistory.vue'

const router = useRouter()
const loading = ref(false)
const categoryCounts = ref([])
const categoriesLoading = ref(false)
const categoriesFailed = ref(false)
const form = reactive({
  mode: 'random',
  category: null,
  difficulty: null,
  count: 10,
  sourceType: 'PROVISION',
  sourceId: 4
})

const categoryOptions = computed(() => {
  const counts = new Map()
  for (const { category, difficulty, count } of categoryCounts.value) {
    if (form.difficulty && difficulty !== form.difficulty) continue
    counts.set(category, (counts.get(category) || 0) + count)
  }
  return Array.from(counts, ([category, count]) => ({ category, count }))
    .sort((a, b) => a.category.localeCompare(b.category, 'zh-CN'))
})
const selectedCount = computed(() => categoryOptions.value.find(option => option.category === form.category)?.count)

function supportsDifficulty(level) {
  return !form.category || categoryCounts.value.some(row => row.category === form.category && row.difficulty === level)
}

async function loadCategories() {
  if (categoriesLoading.value) return
  categoriesLoading.value = true
  categoriesFailed.value = false
  try {
    categoryCounts.value = await listQuestionCategories()
  } catch {
    categoriesFailed.value = true
  } finally {
    categoriesLoading.value = false
  }
}

function retryCategories(open) {
  if (open && categoriesFailed.value) loadCategories()
}

onMounted(loadCategories)

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
.setup-select { width: 240px; max-width: 100%; }
.option-count { float: right; margin-left: 18px; color: #606266; font-size: 12px; font-weight: 400; }
.pool-limit { width: 100%; }
.tip {
  color: #909399;
  font-size: 12px;
}
</style>
