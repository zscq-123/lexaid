<template>
  <main class="profile-page">
    <header class="profile-heading">
      <div>
        <p class="eyebrow">ACCOUNT</p>
        <h1>编辑资料</h1>
        <p>完善备考信息，让练习和学习统计更贴近你的目标。</p>
      </div>
      <el-button class="refresh-button" :loading="refreshing" :disabled="busy" @click="refresh(true)">刷新资料</el-button>
    </header>

    <el-tabs v-model="activeTab" class="profile-tabs">
      <el-tab-pane label="个人资料" name="account">
        <section v-if="countdownItems.length" class="countdown-grid" aria-label="考试倒计时">
          <article v-for="item in countdownItems" :key="item.key" class="countdown-card" :class="item.key">
            <div class="countdown-topline">
              <span>{{ item.label }}</span>
              <span class="countdown-chip">{{ item.estimated ? '预计日期' : '已设置' }}</span>
            </div>
            <strong>{{ item.remaining }}</strong>
            <p v-if="item.date">{{ item.dateLabel }} · 09:00</p>
            <button v-else type="button" @click="openEditor(item.dateField)">设置考试日期</button>
          </article>
        </section>
        <button v-else class="countdown-empty" type="button" @click="openEditor(studyFields[0])">
          <span><b>考试倒计时</b><small>选择备考目标后显示对应考试时间</small></span>
          <el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>

        <section class="profile-group" aria-label="基本资料">
          <button class="profile-row avatar-row" type="button" :disabled="busy" @click="openPicker">
            <span class="row-label">头像</span>
            <span class="row-end">
              <img v-if="preview" :key="preview" :src="preview" class="profile-avatar" alt="待保存的头像" @error="invalidPreview" />
              <UserAvatar v-else :user-id="userStore.user?.id" :version="userStore.user?.avatarVersion"
                :name="userStore.user?.nickname || userStore.user?.username" :size="58" />
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </span>
          </button>
          <input ref="picker" type="file" hidden accept="image/jpeg,image/png" @change="selectFile" />
          <div v-if="file" class="avatar-savebar">
            <span>{{ file.name }}</span>
            <div>
              <el-button text :disabled="busy" @click="cancelAvatar">取消</el-button>
              <el-button type="primary" :loading="userStore.avatarSaving" :disabled="refreshing" @click="saveAvatar">保存头像</el-button>
            </div>
          </div>
          <button v-for="field in identityFields" :key="field.key" class="profile-row" type="button"
            :disabled="busy || field.readonly" @click="openEditor(field)">
            <span class="row-label">{{ field.label }}</span>
            <span class="row-end">
              <span class="row-value" :class="{ placeholder: !displayValue(field) }">{{ displayValue(field) || field.placeholder }}</span>
              <el-icon v-if="!field.readonly" class="row-arrow"><ArrowRight /></el-icon>
            </span>
          </button>
        </section>

        <section class="profile-group" aria-label="账户信息">
          <button v-for="field in accountFields" :key="field.key" class="profile-row" type="button"
            :disabled="busy || field.readonly" @click="openEditor(field)">
            <span class="row-label">{{ field.label }}</span>
            <span class="row-end">
              <span class="row-value" :class="{ placeholder: !displayValue(field) }">{{ displayValue(field) || field.placeholder }}</span>
              <el-icon v-if="!field.readonly" class="row-arrow"><ArrowRight /></el-icon>
            </span>
          </button>
          <button class="profile-row" type="button" :disabled="busy" @click="explainCancellation">
            <span class="row-label danger-label">注销账户</span>
            <span class="row-end"><el-icon class="row-arrow"><ArrowRight /></el-icon></span>
          </button>
        </section>

        <section class="profile-group" aria-label="备考资料">
          <button v-for="field in studyFields" :key="field.key" class="profile-row" type="button"
            :disabled="busy" @click="openEditor(field)">
            <span class="row-label">{{ field.label }}</span>
            <span class="row-end">
              <span class="row-value" :class="{ placeholder: !displayValue(field) }">{{ displayValue(field) || field.placeholder }}</span>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </span>
          </button>
        </section>

        <p v-if="error" role="alert" class="form-error">{{ error }}</p>
        <div class="profile-actions">
          <el-button v-if="userStore.user?.avatarVersion && !file" :disabled="busy" @click="restoreAvatar">恢复默认头像</el-button>
          <el-button plain @click="logout">退出登录</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的收藏" name="favorites" lazy>
        <ProfileFavorites v-if="activeTab === 'favorites'" :key="userStore.token" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="editorOpen" :title="editorField?.label || '编辑资料'" width="min(420px, calc(100vw - 24px))"
      append-to-body destroy-on-close @closed="closeEditor">
      <div v-if="editorField" class="field-editor">
        <el-input v-if="editorField.type === 'text'" v-model="editingValue" :maxlength="editorField.maxlength"
          show-word-limit clearable :placeholder="editorField.placeholder" @keyup.enter="saveField" />
        <el-input v-else-if="editorField.type === 'phone'" v-model="editingValue" inputmode="tel" maxlength="20"
          clearable placeholder="请输入手机号" @keyup.enter="saveField" />
        <el-input-number v-else-if="editorField.type === 'number'" v-model="editingValue" :min="editorField.min"
          :max="editorField.max" controls-position="right" />
        <el-date-picker v-else-if="editorField.type === 'date'" v-model="editingValue" type="date"
          value-format="YYYY-MM-DD" format="YYYY年M月D日" :placeholder="editorField.placeholder" class="editor-select" />
        <el-select v-else v-model="editingValue" class="editor-select" placeholder="请选择">
          <el-option v-for="option in editorField.options" :key="String(option.value)" :label="option.label" :value="option.value" />
        </el-select>
        <p v-if="editorField.hint" class="editor-hint">{{ editorField.hint }}</p>
      </div>
      <template #footer>
        <el-button @click="editorOpen = false">取消</el-button>
        <el-button type="primary" :loading="userStore.profileSaving" @click="saveField">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import UserAvatar from '../../components/UserAvatar.vue'
import ProfileFavorites from './ProfileFavorites.vue'

const genderOptions = [
  { label: '暂不填写', value: 'UNSPECIFIED' },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' }
]
const goalOptions = [
  { label: '暂未决定', value: 'UNDECIDED' },
  { label: '法律职业资格考试', value: 'LEGAL_EXAM' },
  { label: '法律硕士联考', value: 'LAW_MASTER' },
  { label: '两项都准备', value: 'BOTH' }
]
const stageOptions = [
  { label: '基础学习', value: 'FOUNDATION' },
  { label: '强化练习', value: 'INTENSIVE' },
  { label: '冲刺复习', value: 'SPRINT' },
  { label: '准备应考', value: 'EXAM_READY' },
  { label: '已完成考试', value: 'FINISHED' }
]
const statusOptions = [
  { label: '尚未开始', value: 'NOT_STARTED' },
  { label: '正在备考', value: 'PREPARING' },
  { label: '暂时停考', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' }
]
const thisYear = new Date().getFullYear()
const identityFields = [
  { key: 'nickname', label: '昵称', type: 'text', maxlength: 50, placeholder: '请填写昵称' },
  { key: 'gender', label: '性别', type: 'select', options: genderOptions, placeholder: '请选择' }
]
const accountFields = [
  { key: 'username', label: '登录账号', readonly: true, placeholder: '—' },
  { key: 'phone', label: '手机号', type: 'phone', placeholder: '请完善资料', hint: '仅用于完善个人资料，不会公开显示。' }
]
const studyFields = [
  { key: 'examGoal', label: '备考目标', type: 'select', options: goalOptions, placeholder: '请完善资料' },
  { key: 'legalExamAttempts', label: '法考参与次数', type: 'number', min: 0, max: 99, placeholder: '请完善资料', suffix: '次' },
  { key: 'masterExamAttempts', label: '法硕参与次数', type: 'number', min: 0, max: 99, placeholder: '请完善资料', suffix: '次' },
  { key: 'legalExamYear', label: '法考参考年份', type: 'number', min: Math.max(2000, thisYear - 10), max: thisYear + 10, placeholder: '请完善资料', suffix: '年' },
  { key: 'masterExamYear', label: '法硕参考年份', type: 'number', min: Math.max(2000, thisYear - 10), max: thisYear + 10, placeholder: '请完善资料', suffix: '年' },
  { key: 'legalExamDate', label: '法考日期', type: 'date', placeholder: '请选择考试日期', hint: '未设置时，将按参考年份显示预计日期。' },
  { key: 'masterExamDate', label: '法硕联考日期', type: 'date', placeholder: '请选择考试日期', hint: '未设置时，将按参考年份显示预计日期。' },
  { key: 'legalExamStage', label: '法考备考阶段', type: 'select', options: stageOptions, placeholder: '请完善资料' },
  { key: 'masterExamStage', label: '法硕备考阶段', type: 'select', options: stageOptions, placeholder: '请完善资料' },
  { key: 'preparationStatus', label: '备考状态', type: 'select', options: statusOptions, placeholder: '请完善资料' }
]
const profileDefaults = {
  nickname: '', gender: 'UNSPECIFIED', phone: '', examGoal: 'UNDECIDED',
  legalExamAttempts: null, masterExamAttempts: null, legalExamYear: null, masterExamYear: null,
  legalExamDate: '', masterExamDate: '',
  legalExamStage: '', masterExamStage: '', preparationStatus: ''
}

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const activeTab = computed({
  get: () => route.query.tab === 'favorites' ? 'favorites' : 'account',
  set(tab) {
    const query = { ...route.query }
    if (tab === 'favorites') query.tab = tab
    else delete query.tab
    router.replace({ query })
  }
})
const draft = ref({ ...profileDefaults })
const picker = ref(null)
const file = ref(null)
const preview = ref('')
const error = ref('')
const refreshing = ref(false)
const editorOpen = ref(false)
const editorField = ref(null)
const editingValue = ref(null)
const now = ref(Date.now())
const busy = computed(() => refreshing.value || userStore.avatarSaving || userStore.profileSaving)
let active = true
let countdownTimer = null

function estimatedDate(kind) {
  const year = Number(draft.value[kind === 'legal' ? 'legalExamYear' : 'masterExamYear'])
  if (!year) return ''
  if (kind === 'legal') return `${year}-09-20`
  return `${year - 1}-12-20`
}
function countdownText(date) {
  if (!date) return '等待设置日期'
  const target = new Date(`${date}T09:00:00`).getTime()
  if (!Number.isFinite(target)) return '日期无效'
  const delta = target - now.value
  const today = new Date().toLocaleDateString('zh-CN')
  if (new Date(`${date}T12:00:00`).toLocaleDateString('zh-CN') === today) return '今天考试'
  if (delta <= 0) return `已过 ${Math.max(1, Math.floor(Math.abs(delta) / 86400000))} 天`
  const totalSeconds = Math.floor(delta / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = String(Math.floor(totalSeconds % 86400 / 3600)).padStart(2, '0')
  const minutes = String(Math.floor(totalSeconds % 3600 / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${days}天 ${hours}:${minutes}:${seconds}`
}
function formatDate(date) {
  if (!date) return ''
  const [year, month, day] = date.split('-').map(Number)
  return year && month && day ? `${year}年${month}月${day}日` : date
}
function makeCountdown(kind) {
  const legal = kind === 'legal'
  const customDate = draft.value[legal ? 'legalExamDate' : 'masterExamDate']
  const date = customDate || estimatedDate(kind)
  return {
    key: kind,
    label: legal ? '法考倒计时' : '法硕联考倒计时',
    date,
    dateLabel: formatDate(date),
    estimated: !customDate,
    remaining: countdownText(date),
    dateField: studyFields.find(field => field.key === (legal ? 'legalExamDate' : 'masterExamDate'))
  }
}
const countdownItems = computed(() => {
  const goal = draft.value.examGoal
  if (goal === 'LEGAL_EXAM') return [makeCountdown('legal')]
  if (goal === 'LAW_MASTER') return [makeCountdown('master')]
  if (goal === 'BOTH') return [makeCountdown('legal'), makeCountdown('master')]
  return []
})

function syncDraft(user) {
  if (!user) return
  draft.value = Object.fromEntries(Object.keys(profileDefaults).map(key => [key, user[key] ?? profileDefaults[key]]))
}
function optionLabel(field, value) {
  return field.options?.find(option => option.value === value)?.label || ''
}
function displayValue(field) {
  const value = field.key === 'username' ? userStore.user?.username : draft.value[field.key]
  if (value === null || value === undefined || value === '') return ''
  if (field.options) return optionLabel(field, value)
  if (field.type === 'date') return formatDate(value)
  return `${value}${field.suffix || ''}`
}
function openEditor(field) {
  if (busy.value || field.readonly) return
  error.value = ''
  editorField.value = field
  editingValue.value = draft.value[field.key]
  editorOpen.value = true
}
function closeEditor() {
  editorField.value = null
  editingValue.value = null
}
async function saveField() {
  const field = editorField.value
  if (!field || userStore.profileSaving) return
  const value = typeof editingValue.value === 'string' ? editingValue.value.trim() : editingValue.value
  if (field.key === 'nickname' && !value) {
    ElMessage.warning('昵称不能为空')
    return
  }
  const next = { ...draft.value, [field.key]: value }
  try {
    if (await userStore.saveProfile(next) && active) {
      syncDraft(userStore.user)
      editorOpen.value = false
      ElMessage.success('资料已保存')
    }
  } catch (err) {
    if (active) ElMessage.error(err.response?.data?.message || '资料保存失败，请重试。')
  }
}
function openPicker() {
  if (!busy.value) picker.value?.click()
}
function cancelAvatar() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  file.value = null
  error.value = ''
}
function selectFile(event) {
  const image = event.target.files?.[0]
  event.target.value = ''
  if (!image || busy.value) return
  cancelAvatar()
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
  cancelAvatar()
  error.value = '无法读取这张图片，请重新选择。'
}
async function updateAvatar(image) {
  const token = userStore.token
  error.value = ''
  try {
    if (await userStore.saveAvatar(image) && active) {
      cancelAvatar()
      ElMessage.success(image ? '头像已保存' : '已恢复默认头像')
    }
  } catch (err) {
    if (active && userStore.token === token) error.value = err.response?.data?.message || '头像更新失败，请重试。'
  }
}
function saveAvatar() {
  if (!busy.value && file.value) updateAvatar(file.value)
}
async function restoreAvatar() {
  if (busy.value || !userStore.user?.avatarVersion) return
  try {
    await ElMessageBox.confirm('将使用昵称首字作为头像。', '恢复默认头像', { confirmButtonText: '恢复默认', cancelButtonText: '取消' })
  } catch { return }
  if (active) await updateAvatar(null)
}
async function refresh(notify = false) {
  if (busy.value) return
  refreshing.value = true
  try {
    if (await userStore.fetchMe() && active) {
      syncDraft(userStore.user)
      if (notify) ElMessage.success('资料已刷新')
    }
  } catch { /* API client displays the request failure. */ }
  finally { refreshing.value = false }
}
function explainCancellation() {
  ElMessageBox.alert('为保护答题记录、笔记与交流内容，暂不支持一键注销。如确需注销，请联系管理员核验后处理。', '账户注销', {
    confirmButtonText: '我知道了'
  })
}
function logout() {
  userStore.logout()
  router.push('/login')
}

watch(() => userStore.user, syncDraft, { immediate: true, deep: true })
watch(() => userStore.token, cancelAvatar)
onMounted(() => {
  refresh()
  countdownTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => {
  active = false
  cancelAvatar()
  window.clearInterval(countdownTimer)
})
</script>

<style scoped>
.profile-page { width: min(100%, 920px); margin: 0 auto; padding-bottom: 24px; }
.profile-heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.profile-heading h1 { color: var(--lex-ink); font-size: clamp(1.65rem, 3vw, 2.2rem); line-height: 1.15; letter-spacing: -.04em; }
.profile-heading p:not(.eyebrow) { margin-top: 7px; color: var(--lex-muted); }
.eyebrow { margin-bottom: 5px; color: var(--lex-accent); font-size: .7rem; font-weight: 720; letter-spacing: .13em; }
.profile-tabs { --el-tabs-header-height: 43px; }
.profile-tabs :deep(.el-tabs__header) { margin-bottom: 18px; }
.profile-tabs :deep(.el-tabs__item) { font-size: .94rem; font-weight: 620; }
.countdown-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-bottom: 14px; }
.countdown-card { position: relative; overflow: hidden; min-height: 138px; padding: 20px 22px; border: 1px solid #dce6f0; border-radius: 17px; background: linear-gradient(135deg, #fff 25%, #f2f7fc); box-shadow: 0 5px 20px #23364d0a; }
.countdown-card::after { content: ''; position: absolute; right: -26px; bottom: -50px; width: 150px; height: 150px; border-radius: 50%; background: #dceafb; opacity: .55; }
.countdown-card.master { border-color: #e9e2d6; background: linear-gradient(135deg, #fff 25%, #fbf7ef); }
.countdown-card.master::after { background: #f5e5c6; }
.countdown-topline { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #3b5878; font-size: .82rem; font-weight: 650; }
.countdown-card.master .countdown-topline { color: #7d5b27; }
.countdown-chip { padding: 2px 7px; border-radius: 9px; background: #e5effa; font-size: .68rem; font-weight: 600; }
.countdown-card.master .countdown-chip { background: #f5ead6; }
.countdown-card strong { position: relative; z-index: 1; display: block; margin-top: 13px; color: #1e4f83; font-size: clamp(1.32rem, 3vw, 1.8rem); font-variant-numeric: tabular-nums; letter-spacing: -.035em; }
.countdown-card.master strong { color: #895f20; }
.countdown-card p, .countdown-card button { position: relative; z-index: 1; margin-top: 5px; color: var(--lex-muted); font-size: .78rem; }
.countdown-card button { padding: 0; border: 0; background: transparent; color: var(--lex-accent); cursor: pointer; }
.countdown-empty { display: flex; align-items: center; justify-content: space-between; width: 100%; margin-bottom: 14px; padding: 17px 20px; border: 1px dashed #cbd6e2; border-radius: 15px; background: #fff; color: var(--lex-ink); text-align: left; cursor: pointer; }
.countdown-empty span { display: grid; gap: 2px; }
.countdown-empty small { color: var(--lex-muted); font-size: .78rem; font-weight: 400; }
.profile-group { overflow: hidden; margin-bottom: 14px; border: 1px solid #e0e6ed; border-radius: 17px; background: #fff; box-shadow: 0 5px 20px #23364d0a; }
.profile-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; width: 100%; min-height: 68px; padding: 12px 22px; border: 0; border-bottom: 1px solid #edf0f4; background: transparent; color: var(--lex-ink); text-align: left; cursor: pointer; }
.profile-row:last-child { border-bottom: 0; }
.profile-row:disabled { cursor: default; opacity: 1; }
.row-label { flex: 0 0 auto; font-size: .96rem; font-weight: 570; }
.danger-label { color: #a33434; }
.row-end { display: flex; align-items: center; justify-content: flex-end; gap: 10px; min-width: 0; color: #667487; }
.row-value { overflow: hidden; max-width: min(48vw, 420px); font-size: .94rem; text-overflow: ellipsis; white-space: nowrap; }
.row-value.placeholder { color: #aeb7c2; }
.row-arrow { flex: none; color: #7a8796; font-size: 18px; }
.avatar-row { min-height: 92px; }
.profile-avatar { width: 58px; height: 58px; object-fit: cover; border-radius: 50%; }
.avatar-savebar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 22px; border-bottom: 1px solid #edf0f4; background: #f7f9fc; color: var(--lex-muted); font-size: .82rem; }
.avatar-savebar > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.avatar-savebar > div { display: flex; flex: none; }
.profile-actions { display: flex; gap: 10px; justify-content: flex-end; }
.form-error { margin: -2px 0 12px; color: #a33434; font-size: .84rem; }
.field-editor { min-height: 78px; padding: 5px 0; }
.editor-select { width: 100%; }
.editor-hint { margin-top: 9px; color: var(--lex-muted); font-size: .8rem; }
@media (hover: hover) {
  .profile-row:not(:disabled):hover { background: #f8fafc; }
}
@media (max-width: 720px) {
  .profile-page { width: auto; margin: -12px; padding: 0 0 18px; background: #f5f6f8; }
  .profile-heading { align-items: center; margin: 0; padding: 19px 20px 12px; background: #fff; }
  .profile-heading h1 { font-size: 1.46rem; }
  .profile-heading p, .eyebrow { display: none; }
  .refresh-button { padding-inline: 11px; }
  .profile-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 20px; background: #fff; }
  .profile-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: #edf0f4; }
  .profile-tabs :deep(.el-tabs__content) { padding-top: 12px; }
  .countdown-grid { gap: 10px; margin: 0 12px 12px; }
  .countdown-card { min-height: 126px; padding: 16px; border: 0; border-radius: 15px; }
  .countdown-card strong { margin-top: 11px; font-size: 1.2rem; white-space: nowrap; }
  .countdown-topline { align-items: flex-start; }
  .countdown-chip { white-space: nowrap; }
  .countdown-empty { width: calc(100% - 24px); margin: 0 12px 12px; }
  .profile-group { margin: 0 12px 12px; border: 0; border-radius: 15px; box-shadow: 0 2px 13px #273c5510; }
  .profile-row { min-height: 65px; padding: 11px 18px; gap: 14px; }
  .avatar-row { min-height: 94px; }
  .row-label { font-size: 1rem; font-weight: 520; }
  .row-value { max-width: 53vw; font-size: .97rem; }
  .row-value.placeholder { color: #c0c7cf; }
  .avatar-savebar { padding-inline: 18px; }
  .profile-actions { padding: 0 12px; }
  .form-error { padding: 0 12px; }
  .profile-tabs :deep(.el-tab-pane:has(.favorites-card)) { padding: 0 12px; }
}
@media (max-width: 430px) {
  .countdown-grid { grid-template-columns: 1fr; }
}
</style>
