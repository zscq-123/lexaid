<template>
  <section class="friends-panel">
    <div class="toolbar">
      <div class="switcher" aria-label="通讯录视图">
        <button type="button" :class="{ selected: tab === 'contacts' }" @click="switchTab('contacts')">好友 <small>{{ contacts.length }}</small></button>
        <button type="button" :class="{ selected: tab === 'requests' }" @click="switchTab('requests')">新的朋友 <small v-if="incoming.length">{{ incoming.length }}</small></button>
      </div>
      <el-button :icon="Plus" :type="tab === 'find' ? 'primary' : 'default'" @click="switchTab('find')">添加朋友</el-button>
    </div>

    <div v-if="tab === 'contacts'" class="panel-body" v-loading="loading">
      <el-input v-if="contacts.length" v-model="contactFilter" :prefix-icon="Search" clearable
                aria-label="查找通讯录好友" placeholder="查找通讯录好友" maxlength="50" />
      <p v-if="directoryError" class="empty">通讯录加载失败 <el-button link @click="loadDirectory">重试</el-button></p>
      <p v-else-if="!loading && !contacts.length" class="empty">还没有好友</p>
      <p v-else-if="!loading && !filteredContacts.length" class="empty">没有匹配的好友</p>
      <button v-for="person in filteredContacts" :key="person.id" type="button" class="person-row actionable"
              @click="openPerson(person.id)">
        <UserAvatar :user-id="person.id" :version="person.avatarVersion" :name="person.nickname || person.username" />
        <span class="identity"><strong>{{ person.nickname || person.username }}</strong><small>@{{ person.username }}</small></span>
        <el-icon class="chevron"><ArrowRight /></el-icon>
      </button>
    </div>

    <div v-if="tab === 'requests'" class="panel-body" v-loading="loading">
      <p v-if="directoryError" class="empty">申请列表加载失败 <el-button link @click="loadDirectory">重试</el-button></p>
      <p v-else-if="!loading && !requests.length" class="empty">暂无待处理的好友申请</p>
      <template v-for="group in requestGroups" :key="group.title">
        <h3 v-if="group.items.length">{{ group.title }}</h3>
        <div v-for="invite in group.items" :key="invite.requestId" class="person-row">
          <UserAvatar :user-id="invite.person.id" :version="invite.person.avatarVersion" :name="invite.person.nickname || invite.person.username" />
          <div class="identity">
            <strong>{{ invite.person.nickname || invite.person.username }}</strong>
            <small>@{{ invite.person.username }}</small>
            <p v-if="invite.message" class="message">{{ invite.message }}</p>
          </div>
          <div v-if="invite.direction === 'INCOMING'" class="row-actions">
            <el-button size="small" :disabled="busy" @click="respond(invite.requestId, false)">拒绝</el-button>
            <el-button size="small" type="primary" :disabled="busy" @click="respond(invite.requestId, true)">接受</el-button>
          </div>
          <span v-else class="status">等待验证</span>
        </div>
      </template>
    </div>

    <div v-if="tab === 'find'" class="panel-body">
      <form class="search-form" @submit.prevent="search">
        <el-input v-model="query" :prefix-icon="Search" aria-label="搜索用户" maxlength="50"
                  placeholder="输入账号或昵称" clearable @clear="clearSearch" />
        <el-button type="primary" native-type="submit" :disabled="!query.trim()" :loading="searching">搜索</el-button>
      </form>
      <p v-if="searched && !searching && !matches.length" class="empty">没有找到匹配的用户</p>
      <button v-for="person in matches" :key="person.id" type="button" class="person-row actionable"
              @click="openPerson(person.id)">
        <UserAvatar :user-id="person.id" :version="person.avatarVersion" :name="person.nickname || person.username" />
        <span class="identity"><strong>{{ person.nickname || person.username }}</strong><small>@{{ person.username }}</small></span>
        <span class="status">{{ relationText[person.relationship] }}</span>
      </button>
    </div>

    <el-dialog v-model="dialog" title="联系人" width="min(410px, 92vw)" append-to-body
               :close-on-click-modal="!busy" :close-on-press-escape="!busy">
      <template v-if="selected">
        <div class="person-row profile">
          <UserAvatar :user-id="selected.id" :version="selected.avatarVersion" :name="selected.nickname || selected.username" />
          <div class="identity"><strong>{{ selected.nickname || selected.username }}</strong><small>@{{ selected.username }}</small></div>
        </div>
        <template v-if="selected.relationship === 'NONE'">
          <label for="community-friend-note">验证消息</label>
          <el-input id="community-friend-note" v-model="note" type="textarea" :rows="3" maxlength="120"
                    show-word-limit placeholder="介绍一下自己（选填）" />
        </template>
        <p v-else-if="selected.relationship === 'INCOMING'" class="message">{{ currentInvite?.message || '对方发来了好友申请' }}</p>
        <p v-else class="status">{{ relationText[selected.relationship] }}</p>
      </template>
      <template #footer>
        <template v-if="selected?.relationship === 'INCOMING'">
          <el-button :disabled="busy" @click="respond(selected.requestId, false)">拒绝</el-button>
          <el-button type="primary" :disabled="busy" @click="respond(selected.requestId, true)">接受申请</el-button>
        </template>
        <el-button v-else-if="selected?.relationship === 'NONE'" type="primary" :loading="busy" @click="send">发送申请</el-button>
        <el-button v-else-if="selected?.relationship === 'FRIEND'" type="danger" plain :disabled="busy" @click="remove">删除好友</el-button>
        <el-button v-else @click="dialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserAvatar from '../../components/UserAvatar.vue'
import {
  listFriends, listInvitations, findPeople, getPerson, sendInvitation, answerInvitation, deleteFriend
} from '../../api/community-friends'

const props = defineProps({ targetUserId: { type: [Number, String], default: null } })
const emit = defineEmits(['target-opened', 'changed'])
const tab = ref('contacts'), contacts = ref([]), requests = ref([]), contactFilter = ref('')
const query = ref(''), matches = ref([]), searched = ref(false), searching = ref(false)
const dialog = ref(false), selected = ref(null), note = ref(''), loading = ref(false), busy = ref(false)
const directoryError = ref(false)
let directorySequence = 0, searchSequence = 0, profileSequence = 0
const relationText = { NONE: '添加好友', FRIEND: '已是好友', INCOMING: '待我处理', OUTGOING: '等待验证' }
const filteredContacts = computed(() => {
  const filter = contactFilter.value.trim().toLowerCase()
  return contacts.value.filter(p => [p.nickname, p.username].some(s => s?.toLowerCase().includes(filter)))
})
const incoming = computed(() => requests.value.filter(i => i.direction === 'INCOMING'))
const requestGroups = computed(() => [
  { title: '收到的申请', items: incoming.value },
  { title: '已发出的申请', items: requests.value.filter(i => i.direction === 'OUTGOING') }
])
const currentInvite = computed(() => requests.value.find(i => i.requestId === selected.value?.requestId))

async function loadDirectory() {
  const seq = ++directorySequence
  loading.value = true
  directoryError.value = false
  try {
    const [people, invitations] = await Promise.all([listFriends(), listInvitations()])
    if (seq === directorySequence) {
      contacts.value = people
      requests.value = invitations
    }
  } catch {
    if (seq === directorySequence) directoryError.value = true
  } finally {
    if (seq === directorySequence) loading.value = false
  }
}

function switchTab(value) {
  tab.value = value
  if (value !== 'find') loadDirectory()
}

function clearSearch() {
  ++searchSequence
  matches.value = []
  searched.value = false
  searching.value = false
}

async function search() {
  const term = query.value.trim()
  if (!term) return clearSearch()
  const seq = ++searchSequence
  searching.value = true
  try {
    const people = await findPeople(term)
    if (seq === searchSequence) {
      matches.value = people
      searched.value = true
    }
  } catch {
    if (seq === searchSequence) {
      matches.value = []
      searched.value = false
    }
  } finally {
    if (seq === searchSequence) searching.value = false
  }
}

async function openPerson(id) {
  const seq = ++profileSequence
  try {
    const person = await getPerson(id)
    if (seq === profileSequence) {
      selected.value = person
      note.value = ''
      dialog.value = true
    }
  } catch {
    // The API interceptor has already explained the failed contact lookup.
    if (seq === profileSequence) dialog.value = false
  }
}

async function act(operation, success) {
  if (busy.value) return
  busy.value = true
  try {
    await operation()
    ElMessage.success(success)
    dialog.value = false
    emit('changed')
  } catch (error) {
    // Keep the verification message editable for retry after a network error.
    if (error.response && [404, 409].includes(error.response.status)) dialog.value = false
  } finally {
    await loadDirectory()
    if (tab.value === 'find' && searched.value) await search()
    busy.value = false
  }
}
const send = () => act(() => sendInvitation(selected.value.id, note.value), '好友申请已发送')
const respond = (id, accept) => act(() => answerInvitation(id, accept), accept ? '已添加到通讯录' : '已拒绝申请')
async function remove() {
  const person = selected.value
  try {
    await ElMessageBox.confirm(`删除与「${person.nickname || person.username}」的好友关系？`, '删除好友', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  await act(() => deleteFriend(person.id), '已删除好友')
}

watch(() => props.targetUserId, async id => {
  if (!id) return
  emit('target-opened')
  await openPerson(id)
}, { immediate: true })
onMounted(loadDirectory)
</script>

<style scoped>
.friends-panel { width: min(100%, 780px); margin-inline: auto; background: white; border: 1px solid #dce4ec; border-radius: 12px; overflow: hidden; }
.toolbar { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #e7ebf0; gap: 8px; }
.switcher { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.switcher button { background: transparent; border: 0; color: #657281; font: inherit; font-size: 14px; cursor: pointer; border-radius: 8px; padding: 8px 10px; }
.switcher .selected { color: #285286; background: #eef4fa; font-weight: 600; }
.switcher small { font-variant-numeric: tabular-nums; }
.panel-body { padding: 16px 20px 20px; min-height: 110px; }
.panel-body h3 { font-size: 14px; color: #566578; margin: 20px 0 5px; }
.panel-body h3:first-child { margin-top: 0; }
.empty { padding: 24px 8px; text-align: center; color: #677789; font-size: 14px; }
.person-row { display: flex; align-items: center; gap: 12px; width: 100%; text-align: left; border: 0; border-bottom: 1px solid #eef1f4; padding: 12px 0; background: none; box-sizing: border-box; }
.person-row:last-child { border-bottom: 0; }
.actionable { cursor: pointer; font: inherit; }
.actionable:hover { background: #f5f8fb; }
.identity { display: flex; flex-direction: column; flex: 1; min-width: 0; gap: 3px; overflow-wrap: anywhere; }
.identity strong { font-size: 15px; color: #243448; }
.identity small,.status,.chevron { font-size: 13px; color: #637487; }
.message { white-space: pre-wrap; overflow-wrap: anywhere; margin: 4px 0; font-size: 14px; }
.row-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.row-actions .el-button { margin: 0; }
.search-form { display: flex; gap: 8px; }
.profile { margin-bottom: 14px; border-bottom: 0; padding: 0; }
label { display: block; color: #4b596a; margin-bottom: 7px; font-size: 13px; }
@media (max-width: 550px) {
  .toolbar, .panel-body { padding-inline: 12px; }
  .toolbar { flex-wrap: wrap; }
  .person-row { flex-wrap: wrap; }
}
</style>
