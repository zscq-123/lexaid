<template>
  <div class="notes">
    <div class="notes-header">
      <span class="notes-title">学习笔记</span>
      <el-button size="small" type="primary" @click="openAdd">添加笔记</el-button>
    </div>
    <el-empty v-if="notes.length === 0" :image-size="60" description="还没有笔记，点击右上角添加" />
    <div v-for="n in notes" :key="n.id" class="note">
      <div class="note-content">{{ n.content }}</div>
      <div class="note-meta">
        <span>{{ formatTime(n.createdAt) }}</span>
        <span>
          <el-button link type="primary" size="small" @click="openEdit(n)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(n)">删除</el-button>
        </span>
      </div>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑笔记' : '添加笔记'" width="520px">
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="6"
        maxlength="2000"
        show-word-limit
        placeholder="记录你的思考…"
      />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createNote, deleteNote, listNotes, updateNote } from '../api/note'

const props = defineProps({
  targetType: { type: String, required: true },
  targetId: { type: [Number, String], required: true }
})

const notes = ref([])
const dialog = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = reactive({ content: '' })

async function load() {
  notes.value = await listNotes({ targetType: props.targetType, targetId: props.targetId })
}

function openAdd() {
  editing.value = null
  form.content = ''
  dialog.value = true
}

function openEdit(n) {
  editing.value = n
  form.content = n.content
  dialog.value = true
}

async function save() {
  if (!form.content.trim()) {
    ElMessage.warning('请输入笔记内容')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateNote(editing.value.id, {
        targetType: props.targetType,
        targetId: props.targetId,
        content: form.content
      })
      ElMessage.success('笔记已更新')
    } else {
      await createNote({ targetType: props.targetType, targetId: props.targetId, content: form.content })
      ElMessage.success('笔记已保存')
    }
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(n) {
  await ElMessageBox.confirm('确定删除这条笔记吗？', '提示', { type: 'warning' })
  await deleteNote(n.id)
  ElMessage.success('已删除')
  load()
}

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<style scoped>
.notes {
  margin-top: 16px;
}
.notes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.notes-title {
  font-size: 15px;
  font-weight: 600;
  color: #2e5fa3;
}
.note {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fff;
}
.note-content {
  white-space: pre-wrap;
  line-height: 1.7;
  margin-bottom: 6px;
}
.note-meta {
  display: flex;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
}
</style>
