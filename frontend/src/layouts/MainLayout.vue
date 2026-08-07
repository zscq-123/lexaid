<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo" @click="$router.push('/dashboard')">
        <el-icon :size="22"><Reading /></el-icon>
        <span>LexAid 法律学习</span>
      </div>
      <div class="search">
        <el-input
          v-model="keyword"
          placeholder="全局搜索法条、案例、观点、题目"
          clearable
          @keyup.enter="doSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="right">
        <el-button text @click="$router.push('/ai-chat')">
          <el-icon><ChatDotRound /></el-icon>&nbsp;AI 问答
        </el-button>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.user?.nickname || userStore.user?.username || '用户' }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container class="body">
      <el-aside width="220px" class="aside">
        <el-menu
          :default-active="route.path"
          router
          class="menu"
        >
          <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon>工作台</el-menu-item>
          <el-menu-item index="/provisions"><el-icon><Notebook /></el-icon>法条</el-menu-item>
          <el-menu-item index="/cases"><el-icon><Collection /></el-icon>案例</el-menu-item>
          <el-menu-item index="/viewpoints"><el-icon><Reading /></el-icon>学者观点</el-menu-item>
          <el-menu-item index="/exam/setup"><el-icon><EditPen /></el-icon>模拟答题</el-menu-item>
          <el-menu-item index="/wrong-book"><el-icon><CircleCheck /></el-icon>错题本</el-menu-item>
          <el-menu-item index="/notes"><el-icon><Memo /></el-icon>学习笔记</el-menu-item>
          <el-menu-item index="/stats"><el-icon><TrendCharts /></el-icon>学习统计</el-menu-item>
          <el-menu-item index="/ai-chat"><el-icon><ChatDotRound /></el-icon>AI 问答</el-menu-item>
          <el-menu-item index="/profile"><el-icon><User /></el-icon>个人中心</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')

function doSearch() {
  if (keyword.value.trim()) {
    router.push({ path: '/provisions', query: { keyword: keyword.value.trim() } })
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 20px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #2e5fa3;
  cursor: pointer;
  white-space: nowrap;
}
.search {
  flex: 1;
  max-width: 480px;
}
.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #303133;
}
.body {
  height: calc(100% - 56px);
}
.aside {
  border-right: 1px solid #ebeef5;
  background: #fff;
}
.menu {
  border-right: none;
}
.main {
  background: #f7f8fa;
  overflow: auto;
  padding: 20px;
}
</style>
