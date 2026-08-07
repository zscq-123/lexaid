<template>
  <el-card shadow="never" class="profile-card">
    <el-descriptions title="个人信息" :column="1" border>
      <el-descriptions-item label="用户名">{{ userStore.user?.username }}</el-descriptions-item>
      <el-descriptions-item label="昵称">{{ userStore.user?.nickname }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ userStore.user?.role === 'ADMIN' ? '管理员' : '学习者' }}</el-descriptions-item>
    </el-descriptions>
    <div class="actions">
      <el-button @click="refresh">刷新信息</el-button>
      <el-button type="danger" plain @click="logout">退出登录</el-button>
    </div>
    <el-alert
      class="tip"
      title="提示：在详情页可收藏内容、记录笔记，并通过 AI 针对该内容提问。"
      type="info"
      :closable="false"
      show-icon
    />
  </el-card>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()

async function refresh() {
  await userStore.fetchMe()
  ElMessage.success('信息已刷新')
}
function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.profile-card {
  max-width: 640px;
  border-radius: 8px;
}
.actions {
  margin-top: 16px;
}
.tip {
  margin-top: 16px;
}
</style>
