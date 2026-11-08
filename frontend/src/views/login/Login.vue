<template>
  <main class="login-page">
    <div class="auth-panel">
      <div class="cover">
        <img class="cover-photo" :src="readingRoom" alt="" />
        <div class="cover-brand">
          <el-icon aria-hidden="true"><Reading /></el-icon>
          <span class="brand-title">LexAid</span>
          <span class="brand-sub">法律学习系统</span>
        </div>
      </div>
      <section class="auth-form" aria-labelledby="account-title">
        <h1 id="account-title">{{ tab === 'login' ? '账户登录' : '创建账户' }}</h1>
        <el-radio-group v-model="tab" class="mode-switch" aria-label="账户操作" :disabled="loading">
          <el-radio-button value="login">登录</el-radio-button>
          <el-radio-button value="register">注册</el-radio-button>
        </el-radio-group>

        <el-form v-if="tab === 'login'" :model="loginForm" label-position="top" @submit.prevent="doLogin">
          <el-form-item label="用户名" for="login-username">
            <el-input id="login-username" v-model="loginForm.username" placeholder="用户名" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" for="login-password">
            <el-input id="login-password" v-model="loginForm.password" type="password" placeholder="密码" autocomplete="current-password" show-password />
          </el-form-item>
          <el-button native-type="submit" type="primary" class="submit" :loading="loading">登录</el-button>
        </el-form>
        <el-form v-else :model="regForm" label-position="top" @submit.prevent="doRegister">
          <el-form-item label="用户名" for="register-username">
            <el-input id="register-username" v-model="regForm.username" maxlength="50" placeholder="2 至 50 个字符" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" for="register-password">
            <el-input id="register-password" v-model="regForm.password" type="password" maxlength="50" placeholder="6 至 50 个字符" autocomplete="new-password" show-password />
          </el-form-item>
          <el-form-item label="昵称（选填）" for="register-nickname">
            <el-input id="register-nickname" v-model="regForm.nickname" maxlength="50" placeholder="昵称" autocomplete="nickname" />
          </el-form-item>
          <el-button native-type="submit" type="primary" class="submit" :loading="loading">创建账户</el-button>
        </el-form>
      </section>
    </div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Reading } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/user'
import readingRoom from '../../assets/reading-room.jpg'

const router = useRouter()
const userStore = useUserStore()
const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', nickname: '' })

async function doLogin() {
  if (loading.value) return
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')
    await router.push('/dashboard')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (loading.value) return
  if (regForm.username.trim().length < 2 || regForm.password.length < 6) {
    ElMessage.warning('用户名至少需要 2 个字符，密码至少需要 6 个字符')
    return
  }
  loading.value = true
  try {
    await userStore.register(regForm)
    ElMessage.success('注册成功，请登录')
    loginForm.username = regForm.username
    loginForm.password = regForm.password
    regForm.password = ''
    tab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  padding: clamp(1rem, 5vw, 4rem);
  display: grid;
  place-items: center;
  background: radial-gradient(ellipse at 16% 8%, #e7edf3, transparent 64%), var(--lex-canvas, #f4f6f9);
  color: var(--lex-ink, #182438);
}
.auth-panel {
  display: grid;
  grid-template-columns: 0.94fr 1fr;
  width: min(100%, 53rem);
  overflow: hidden;
  border: 1px solid var(--lex-line, #dce2e9);
  border-radius: 1.5rem;
  background: var(--lex-surface, #fff);
  box-shadow: 0 1.5rem 4.5rem -2.2rem #15283f55, 0 2px 8px #15283f07;
}
.cover { position: relative; min-width: 0; min-height: 29.5rem; isolation: isolate; display: flex; align-items: flex-end; background: #334254; }
.cover-photo, .cover::after { position: absolute; inset: 0; width: 100%; height: 100%; }
.cover-photo { z-index: -2; object-fit: cover; object-position: 53% 48%; }
.cover::after { content: ''; z-index: -1; background: linear-gradient(0deg, #0e1826dc, #111d2933 44%, transparent 72%); }
.cover-brand { display: grid; gap: .2rem; padding: 2rem 2.2rem; color: #fff; }
.cover-brand .el-icon { margin-bottom: .3rem; font-size: 1.65rem; }
.brand-title { font-size: 2.35rem; line-height: 1.1; font-weight: 680; letter-spacing: -.05em; }
.brand-sub { font-size: .95rem; font-weight: 500; }
.auth-form { min-width: 0; align-self: center; padding: clamp(1.75rem, 4.2vw, 3rem); }
.auth-form h1 { margin: 0 0 1.35rem; font-size: clamp(1.5rem, 3vw, 1.9rem); line-height: 1.24; font-weight: 680; letter-spacing: -.035em; }
.mode-switch { width: 100%; padding: 3px; margin-bottom: 1.7rem; border-radius: 10px; background: #edf0f4; }
.mode-switch :deep(.el-radio-button) { flex: 1; }
.mode-switch :deep(.el-radio-button__inner) { width: 100%; padding: .52rem; border: 0; background: transparent; color: var(--lex-muted, #58687b); box-shadow: none; border-radius: 7px; }
.mode-switch :deep(.el-radio-button.is-active .el-radio-button__inner) { background: white; color: var(--lex-ink, #182438); box-shadow: 0 1px 4px #17283f25; font-weight: 620; }
.auth-form :deep(.el-form-item__label) { height: auto; line-height: 1.5; margin-bottom: 5px; font-size: .85rem; font-weight: 560; }
.auth-form :deep(.el-input__wrapper) { min-height: 2.55rem; padding-inline: .75rem; }
.submit { width: 100%; height: 2.55rem; margin-top: .45rem; font-weight: 600; }
@media (max-width: 620px) {
  .login-page { align-items: center; }
  .auth-panel { max-width: 27rem; grid-template-columns: 1fr; border-radius: 1.125rem; }
  .cover { min-height: 7.25rem; }
  .cover-brand { grid-template-columns: auto auto; gap: .1rem .65rem; align-items: center; padding: 1.2rem 1.55rem; }
  .cover-brand .el-icon { grid-row: span 2; margin: 0; }
  .brand-title { font-size: 1.6rem; }
  .brand-sub { grid-column: 2; font-size: .8rem; }
  .cover-photo { object-position: 50% 48%; }
  .auth-form { padding: 1.65rem; }
}
</style>
