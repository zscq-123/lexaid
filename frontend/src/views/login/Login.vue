<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="brand">
        <el-icon :size="30" color="#2e5fa3"><Reading /></el-icon>
        <div class="brand-title">LexAid 法律学习系统</div>
        <div class="brand-sub">法条 · 案例 · 观点 · 题目，一站式学习</div>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-width="0" @keyup.enter="doLogin">
            <el-form-item>
              <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="loginForm.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="doLogin">登 录</el-button>
          </el-form>
          <div class="hint">演示账号：demo / 123456</div>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-width="0" @keyup.enter="doRegister">
            <el-form-item>
              <el-input v-model="regForm.username" placeholder="用户名（2~50 字符）" :prefix-icon="User" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="regForm.password" type="password" placeholder="密码（至少 6 位）" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-input v-model="regForm.nickname" placeholder="昵称（可选）" :prefix-icon="UserFilled" />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="doRegister">注 册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: 'demo', password: '123456' })
const regForm = reactive({ username: '', password: '', nickname: '' })

async function doLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!regForm.username || !regForm.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.register(regForm)
    ElMessage.success('注册成功，请登录')
    loginForm.username = regForm.username
    loginForm.password = regForm.password
    tab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2e5fa3 0%, #4a7fc7 100%);
}
.login-card {
  width: 400px;
  border-radius: 8px;
  padding: 12px 8px;
}
.brand {
  text-align: center;
  margin-bottom: 12px;
}
.brand-title {
  font-size: 20px;
  font-weight: 700;
  color: #2e5fa3;
  margin-top: 6px;
}
.brand-sub {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}
.submit {
  width: 100%;
}
.hint {
  margin-top: 12px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}
</style>
