<template>
  <el-card shadow="never" class="setup-card">
    <template #header><span class="card-title">组卷设置</span></template>
    <ExamCoverage :filters="filters" />
    <el-form :model="form" label-width="100px" style="max-width: 520px">
      <el-form-item label="组卷方式">
        <el-radio-group v-model="form.mode">
          <el-radio value="random">随机组卷</el-radio>
          <el-radio value="entity">按实体组卷</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="法学大类">
        <el-select
          v-model="form.discipline" aria-label="法学大类" placeholder="全部大类" class="setup-select" popper-class="exam-taxonomy"
          :loading="subjectsLoading" :disabled="subjectsFailed" clearable filterable
          no-match-text="没有匹配的法学大类" @change="changeDiscipline"
        >
          <el-option label="全部大类" value="" />
          <el-option v-for="option in disciplines" :key="option.name" :label="option.name"
            :value="option.name" :disabled="!option.count">
            <span>{{ option.name }}</span><span class="option-count">{{ option.count }} 题</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="具体科目">
        <el-select v-model="form.subject" aria-label="具体科目" class="setup-select" popper-class="exam-taxonomy"
          :placeholder="form.discipline ? '全部科目' : '请先选择法学大类'" :disabled="!form.discipline || subjectsFailed"
          clearable filterable no-match-text="没有匹配的科目" @change="changeSubject">
          <el-option label="全部科目" value="" />
          <el-option v-for="option in subjects" :key="option.name" :label="option.name"
            :value="option.name" :disabled="!option.count">
            <span>{{ option.name }}</span><span class="option-count">{{ option.count }} 题</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="codes.length || booksState === 'loading' || booksState === 'error'" label="法典">
        <span v-if="booksState === 'loading'" class="tip" role="status">正在加载法典…</span>
        <span v-else-if="booksState === 'error'" class="tip" role="alert">
          法典目录加载失败。<el-button link type="primary" @click="loadBooks">重新加载</el-button>
        </span>
        <el-select v-else v-model="form.codeName" aria-label="法典" class="setup-select" popper-class="exam-taxonomy"
          placeholder="不限法典" clearable filterable @change="form.bookName = ''">
          <el-option label="不限法典" value="" />
          <el-option v-for="option in codes" :key="option.name" :label="option.name" :value="option.name" :disabled="!option.count">
            <span>{{ option.name }}</span><span class="option-count">{{ option.count }} 题</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.codeName && books.length" label="编">
        <el-select v-model="form.bookName" aria-label="编" class="setup-select" popper-class="exam-taxonomy"
          placeholder="全部编" clearable filterable>
          <el-option label="全部编" value="" />
          <el-option v-for="option in books" :key="option.name" :label="option.name" :value="option.name" :disabled="!option.count">
            <span>{{ option.name }}</span><span class="option-count">{{ option.count }} 题</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" aria-label="难度" placeholder="全部难度" clearable class="setup-select">
          <el-option label="全部难度" value="" />
          <el-option v-for="i in 5" :key="i" :label="i + ' 星'" :value="i" :disabled="!selectedRows.some(row => row.difficulty === i)" />
        </el-select>
      </el-form-item>
      <el-form-item label="题量">
        <el-input-number v-model="form.count" :min="1" :max="50" />
        <p v-if="subjectsFailed" class="tip pool-limit" role="alert">
          法学科目加载失败。<el-button link type="primary" @click="loadSubjects">重新加载</el-button>
        </p>
        <p v-else-if="!subjectsLoading && selectedCount > 0 && selectedCount < form.count" class="tip pool-limit" role="status">
          本次将抽取全部 {{ selectedCount }} 道题。
        </p>
      </el-form-item>
      <template v-if="form.mode === 'entity'">
        <el-form-item label="实体类型">
          <el-select v-model="form.sourceType" class="setup-select">
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
        <el-button type="primary" :loading="loading" :disabled="subjectsLoading || subjectsFailed || !selectedCount" @click="start">开始答题</el-button>
      </el-form-item>
    </el-form>
    <ExamHistory />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createSession, listQuestionSubjects, listCodeBooks } from '../../api/exam'
import ExamHistory from './ExamHistory.vue'
import ExamCoverage from './ExamCoverage.vue'

const router = useRouter()
const loading = ref(false)
const counts = ref([])
const subjectsLoading = ref(false)
const subjectsFailed = ref(false)
const codeCounts = ref([])
const booksState = ref('idle')
const form = reactive({
  mode: 'random',
  discipline: '',
  subject: '',
  codeName: '',
  bookName: '',
  difficulty: null,
  count: 10,
  sourceType: 'PROVISION',
  sourceId: 4
})
const filters = computed(() => ({
  discipline: form.discipline || undefined,
  subject: form.subject || undefined,
  codeName: form.codeName || undefined,
  bookName: form.bookName || undefined,
  difficulty: form.difficulty || undefined
}))

function optionsFor(rows, field) {
  const totals = new Map()
  for (const row of rows) {
    const count = !form.difficulty || row.difficulty === form.difficulty ? row.count : 0
    totals.set(row[field], (totals.get(row[field]) || 0) + count)
  }
  return Array.from(totals, ([name, count]) => ({ name, count }))
}

const disciplines = computed(() => optionsFor(counts.value, 'discipline'))
const disciplineRows = computed(() => form.discipline ? counts.value.filter(row => row.discipline === form.discipline) : counts.value)
const subjects = computed(() => optionsFor(disciplineRows.value, 'subject'))
const scopeRows = computed(() => form.subject ? disciplineRows.value.filter(row => row.subject === form.subject) : disciplineRows.value)
const codeRows = computed(() => codeCounts.value.filter(row => (!form.discipline || row.discipline === form.discipline) && (!form.subject || row.subject === form.subject)))
const codeTotals = computed(() => codeRows.value.filter(row => row.bookName == null))
const codes = computed(() => optionsFor(codeTotals.value, 'codeName'))
const bookRows = computed(() => codeRows.value.filter(row => row.codeName === form.codeName && row.bookName != null))
function bookOrder(name) {
  const heading = /^第([一二三四五六七八九十]+|\d+)编/.exec(name)?.[1]
  if (!heading) return Infinity
  if (/^\d+$/.test(heading)) return Number(heading)
  const digits = '〇一二三四五六七八九'
  const [tens, units] = heading.split('十')
  return units === undefined ? digits.indexOf(tens)
    : (tens ? digits.indexOf(tens) : 1) * 10 + (units ? digits.indexOf(units) : 0)
}
const books = computed(() => optionsFor(bookRows.value, 'bookName')
  .sort((a, b) => bookOrder(a.name) - bookOrder(b.name)))
const selectedRows = computed(() => {
  if (form.bookName) return bookRows.value.filter(row => row.bookName === form.bookName)
  if (form.codeName) return codeTotals.value.filter(row => row.codeName === form.codeName)
  return scopeRows.value
})
const selectedCount = computed(() => selectedRows.value
  .filter(row => !form.difficulty || row.difficulty === form.difficulty)
  .reduce((total, row) => total + row.count, 0))

function changeDiscipline() {
  form.subject = ''
  changeSubject()
}
function changeSubject() {
  form.codeName = ''
  form.bookName = ''
}
async function loadBooks() {
  if (booksState.value === 'loading') return
  booksState.value = 'loading'
  try {
    codeCounts.value = await listCodeBooks()
    booksState.value = 'ready'
  } catch {
    booksState.value = 'error'
  }
}

async function loadSubjects() {
  if (subjectsLoading.value) return
  subjectsLoading.value = true
  subjectsFailed.value = false
  try {
    counts.value = await listQuestionSubjects()
  } catch {
    subjectsFailed.value = true
  } finally {
    subjectsLoading.value = false
  }
}

onMounted(loadSubjects)
onMounted(loadBooks)

async function start() {
  loading.value = true
  try {
    const payload = {
      ...filters.value,
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
.setup-select { width: 100%; max-width: 290px; }
:global(.exam-taxonomy .el-select-dropdown__item) {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  white-space: normal; height: auto; min-height: 36px; padding-block: 6px; line-height: 1.4;
}
.option-count { flex: none; color: var(--lex-muted); font-size: 12px; font-weight: 400; }
.pool-limit { width: 100%; }
.tip {
  color: #909399;
  font-size: 12px;
}
</style>
