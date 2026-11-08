<template>
  <el-card shadow="never" class="profile-card">
    <h1>个人中心</h1>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="个人资料" name="account">
        <section class="avatar-editor" aria-label="头像设置">
          <img v-if="preview" :key="preview" :src="preview" class="draft-avatar" alt="待保存的头像" @error="invalidPreview" />
          <UserAvatar v-else :user-id="userStore.user?.id" :version="userStore.user?.avatarVersion"
            :name="userStore.user?.nickname || userStore.user?.username" :size="76" />
          <div class="editor-controls">
            <h2>个人头像</h2>
            <small>JPG 或 PNG，最大 4 MB</small>
            <input ref="picker" type="file" hidden accept="image/jpeg,image/png" @change="selectFile" />
            <div class="avatar-actions">
              <el-button size="small" :disabled="busy" @click="picker.click()">
                {{ file || userStore.user?.avatarVersion ? '更换头像' : '选择图片' }}
              </el-button>
              <template v-if="file">
                <el-button type="primary" size="small" :loading="userStore.avatarSaving" :disabled="refreshing" @click="save">保存头像</el-button>
                <el-button text size="small" :disabled="busy" @click="cancel">取消</el-button>
              </template>
              <el-button v-else-if="userStore.user?.avatarVersion" text size="small" :disabled="busy" @click="restore">恢复默认头像</el-button>
            </div>
            <p v-if="error" role="alert" class="error">{{ error }}</p>
          </div>
        </section>
        <el-descriptions title="个人信息" :column="1" border>
          <el-descriptions-item label="用户名">{{ userStore.user?.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ userStore.user?.nickname }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ userStore.user?.role === 'ADMIN' ? '管理员' : '学习者' }}</el-descriptions-item>
        </el-descriptions>
        <div class="actions">
          <el-button :loading="refreshing" :disabled="userStore.avatarSaving" @click="refresh(true)">刷新信息</el-button>
          <el-button type="danger" plain @click="logout">退出登录</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="我的收藏" name="favorites" lazy>
        <ProfileFavorites v-if="activeTab === 'favorites'" :key="userStore.token" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import UserAvatar from '../../components/UserAvatar.vue'
import ProfileFavorites from './ProfileFavorites.vue'

const router = useRouter()
const route = useRoute()
const activeTab = computed({
  get: () => route.query.tab === 'favorites' ? 'favorites' : 'account',
  set(tab) {
    const query = { ...route.query }
    if (tab === 'favorites') query.tab = tab
    else delete query.tab
    router.replace({ query })
  }
})
const userStore = useUserStore()
const picker = ref(null)
const file = ref(null)
const preview = ref('')
const error = ref('')
const refreshing = ref(false)
const busy = computed(() => userStore.avatarSaving || refreshing.value)
let active = true

function cancel() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  file.value = null
  error.value = ''
}

function selectFile(event) {
  const image = event.target.files?.[0]
  event.target.value = ''
  if (!image || busy.value) return
  cancel()
  if (!['image/jpeg', 'image/png'].includes(image.type) || !image.size) {
    error.value = '请选择有效的 JPG 或 PNG 图片。'
    return
  }
  if (image.size > 4 * 1024 * 1024) {
    error.value = '头像图片不能超过 4 MB。'
    return
  }
  file.value = image
  preview.value = URL.createObjectURL(image)
}

function invalidPreview(event) {
  if (event.target.src !== preview.value) return
  cancel()
  error.value = '无法读取这张图片，请重新选择。'
}

async function updateAvatar(image) {
  const token = userStore.token
  error.value = ''
  try {
    if (await userStore.saveAvatar(image) && active) {
      cancel()
      ElMessage.success(image ? '头像已保存' : '已恢复默认头像')
    }
  } catch (err) {
    if (active && userStore.token === token) error.value = err.response?.data?.message || '头像更新失败，请重试。'
  }
}

function save() {
  if (!busy.value && file.value) updateAvatar(file.value)
}

async function restore() {
  if (busy.value || !userStore.user?.avatarVersion) return
  const token = userStore.token
  try {
    await ElMessageBox.confirm('将使用昵称首字作为头像。', '恢复默认头像', { confirmButtonText: '恢复默认', cancelButtonText: '取消' })
  } catch { return }
  if (active && token === userStore.token) await updateAvatar(null)
}

async function refresh(notify = false) {
  if (busy.value) return
  refreshing.value = true
  try {
    if (await userStore.fetchMe() && active && notify) ElMessage.success('信息已刷新')
  } catch { /* The API client reports the failure. */ }
  finally { refreshing.value = false }
}
function logout() {
  userStore.logout()
  router.push('/login')
}
watch(() => userStore.token, cancel)
onMounted(() => refresh())
onBeforeUnmount(() => { active = false; cancel() })
</script>

<style scoped>
.profile-card {
  max-width: 840px;
  border-radius: 8px;
}
h1 { color: #25374d; font-size: 24px; margin-bottom: 18px; }
.avatar-editor { display: flex; align-items: center; gap: 18px; padding: 0 0 24px; }
.draft-avatar { width: 76px; height: 76px; flex: none; object-fit: cover; border-radius: 50%; }
.editor-controls { min-width: 0; }
.editor-controls h2 { font-size: 16px; color: #303133; margin-bottom: 4px; }
.editor-controls small { color: #606266; font-size: 13px; }
.avatar-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.avatar-actions .el-button { margin-left: 0; }
.error { color: #a33434; margin-top: 6px; font-size: 13px; }
.actions {
  margin-top: 16px;
}
</style>
