<template>
  <el-card shadow="never" class="list-card">
    <div class="head">
      <el-select v-model="typeFilter" placeholder="全部类型" clearable style="width: 180px" @change="load">
        <el-option label="法条" value="PROVISION" />
        <el-option label="案例" value="CASE" />
        <el-option label="观点" value="VIEWPOINT" />
        <el-option label="题目" value="QUESTION" />
      </el-select>
    </div>
    <el-empty v-if="notes.length === 0" description="还没有笔记" />
    <div v-for="n in notes" :key="n.id" class="row">
      <div class="row-head">
        <el-tag size="small">{{ typeLabel(n.targetType) }}</el-tag>
        <span class="target" @click="go(n)">{{ n.targetTitle }}</span>
        <span class="time">{{ formatTime(n.createdAt) }}</span>
      </div>
      <div class="content">{{ n.content }}</div>
      <div class="actions">
        <el-button link type="primary" size="small" @click="openEdit(n)">编辑</el-button>
        <el-button link type="danger" size="small" @click="remove(n)">删除</el-button>
      </div>
    </div>
    <el-dialog v-model="dialog" :title="editing ? '编辑笔记' : '新建笔记'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="关联类型">
          <el-select v-model="form.targetType" style="width: 200px">
            <el-option label="法条" value="PROVISION" />
            <el-option label="案例" value="CASE" />
            <el-option label="观点" value="VIEWPOINT" />
            <el-option label="题目" value="QUESTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联 ID">
          <el-input-number v-model="form.targetId" :min="1" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="记录学习心得…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createNote, deleteNote, listNotes, updateNote } from '../../api/note'

const router = useRouter()
const notes = ref([])
const typeFilter = ref('')
const dialog = ref(false)
const editing = ref(null)
const form = reactive({ targetType: 'PROVISION', targetId: 1, content: '' })

async function load() {
  const params = {}
  if (typeFilter.value) params.targetType = typeFilter.value
  notes.value = await listNotes(params)
}

function openEdit(n) {
  editing.value = n
  form.targetType = n.targetType
  form.targetId = n.targetId
  form.content = n.content
  dialog.value = true
}

async function save() {
  if (!form.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  if (editing.value) {
    await updateNote(editing.value.id, { ...form })
    ElMessage.success('已更新')
  } else {
    await createNote({ ...form })
    ElMessage.success('已保存')
  }
  dialog.value = false
  load()
}

async function remove(n) {
  await ElMessageBox.confirm('确定删除这条笔记吗？', '提示', { type: 'warning' })
  await deleteNote(n.id)
  ElMessage.success('已删除')
  load()
}

function go(n) {
  const map = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  if (map[n.targetType]) router.push(map[n.targetType] + n.targetId)
}
function typeLabel(t) {
  return { PROVISION: '法条', CASE: '案例', VIEWPOINT: '观点', QUESTION: '题目' }[t] || t
}
function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<style scoped>
.list-card {
  border-radius: 8px;
}
.head {
  margin-bottom: 12px;
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
.target {
  font-weight: 600;
  cursor: pointer;
  color: #2e5fa3;
}
.time {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
.content {
  margin-top: 8px;
  line-height: 1.8;
  white-space: pre-wrap;
}
.actions {
  margin-top: 6px;
}
</style>
