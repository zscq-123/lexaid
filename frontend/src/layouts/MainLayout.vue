<template>
  <div class="layout" @keydown.esc="closeMenu">
    <button class="nav-overlay" :class="{ open: menuOpen }" tabindex="-1" :disabled="!menuOpen"
      :aria-hidden="!menuOpen || undefined" aria-label="关闭主菜单" @click="closeMenu"></button>
    <el-aside id="primary-navigation" width="224px" class="aside" :class="{ open: menuOpen, 'drawer-motion': drawerMotion }"
      :inert="isNarrow && !menuOpen || undefined" :role="menuOpen ? 'dialog' : undefined"
      :aria-modal="menuOpen || undefined" aria-label="主导航" @keydown.tab="keepFocusInDrawer">
      <div class="brand-row">
        <router-link class="brand" to="/dashboard" aria-label="LexAid 法律学习，返回工作台" @click="closeMenu">
          <span class="brand-mark"><el-icon :size="23"><Reading /></el-icon></span>
          <span class="brand-type">LexAid<span>法律学习</span></span>
        </router-link>
        <button ref="closeButton" class="mobile-close icon-button" aria-label="收起主菜单" @click="closeMenu">
          <el-icon><Close /></el-icon>
        </button>
      </div>
      <nav class="navigation" aria-label="应用导航">
        <div class="menu" role="menu" aria-label="应用模块" @keydown="moveNavFocus">
          <router-link to="/dashboard" role="menuitem" class="el-menu-item" :class="{ 'is-active': activePath === '/dashboard' }" @click="closeMenu">
            <el-icon><Grid /></el-icon><span>工作台</span>
          </router-link>
          <div v-for="section in visibleSections" :key="section.label" role="group" :aria-label="section.label">
            <div class="el-menu-item-group__title" aria-hidden="true">{{ section.label }}</div>
            <router-link v-for="item in section.items" :key="item.path" :to="item.path" role="menuitem" class="el-menu-item"
              :class="{ 'is-active': activePath === item.path }" :aria-current="activePath === item.path ? 'page' : undefined" @click="closeMenu">
              <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
            </router-link>
          </div>
        </div>
      </nav>
    </el-aside>
    <div class="workspace" :inert="menuOpen || undefined">
      <el-header class="header">
        <button ref="menuButton" class="menu-toggle icon-button" aria-label="主菜单" aria-controls="primary-navigation"
          :aria-expanded="menuOpen" @click="openMenu"><el-icon><Menu /></el-icon></button>
        <router-link class="mobile-brand" to="/dashboard">LexAid</router-link>
        <button class="search-launch" aria-label="搜索资料库" aria-haspopup="dialog" aria-keyshortcuts="Meta+K Control+K" @click="searchOpen = true">
          <el-icon><Search /></el-icon><span>搜索资料库</span>
          <kbd aria-hidden="true">{{ shortcut }}</kbd>
        </button>
        <div class="right">
          <router-link to="/ai-chat" class="ai-shortcut"><el-icon><ChatDotRound /></el-icon><span>AI 问答</span></router-link>
          <el-dropdown @command="onCommand">
            <button type="button" class="user" :aria-label="displayName + '，账户菜单'">
              <UserAvatar :user-id="userStore.user?.id" :version="userStore.user?.avatarVersion"
                :name="displayName" :size="29" />
              <span class="user-name">{{ displayName }}</span>
              <el-icon class="user-chevron" :size="12"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <h1 v-if="pageHeading" class="page-heading">{{ pageHeading }}</h1>
        <router-view />
      </el-main>
      <nav v-if="showPhoneTabs" class="phone-tabs" aria-label="快捷导航">
        <router-link v-for="item in phoneTabs" :key="item.path" :to="item.path"
          :aria-current="activePath === item.path ? 'page' : undefined">
          <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </div>
    <ShellSearch v-model="searchOpen" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown, ChatDotRound, ChatLineRound, CircleCheck, Close, Collection, EditPen,
  DataAnalysis, Grid, Memo, Menu, Notebook, Reading, Search, TrendCharts, User
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import UserAvatar from '../components/UserAvatar.vue'
import ShellSearch from './ShellSearch.vue'

const sections = [
  { label: '资料库', items: [
    { path: '/provisions', label: '法条', icon: Notebook },
    { path: '/cases', label: '案例', icon: Collection },
    { path: '/viewpoints', label: '学者观点', icon: Reading }
  ] },
  { label: '学习', items: [
    { path: '/exam/setup', label: '模拟答题', icon: EditPen },
    { path: '/mock-exams', label: '模拟考试', icon: Notebook },
    { path: '/wrong-book', label: '错题本', icon: CircleCheck },
    { path: '/notes', label: '学习笔记', icon: Memo },
    { path: '/stats', label: '学习统计', icon: TrendCharts }
  ] },
  { label: '交流与个人', items: [
    { path: '/community', label: '交流天地', icon: ChatLineRound },
    { path: '/ai-chat', label: 'AI 问答', icon: ChatDotRound },
    { path: '/profile', label: '个人中心', icon: User }
  ] },
  { label: '管理', adminOnly: true, items: [
    { path: '/admin', label: '用户数据', icon: DataAnalysis }
  ] }
]
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const visibleSections = computed(() => sections.filter(section => !section.adminOnly || userStore.user?.role === 'ADMIN'))
const menuOpen = ref(false)
const menuButton = ref(null)
const closeButton = ref(null)
const drawerMotion = ref(false)
const searchOpen = ref(false)
const displayName = computed(() => userStore.user?.nickname || userStore.user?.username || '用户')
const activePath = computed(() => route.path.startsWith('/exam/') ? '/exam/setup' : '/' + route.path.split('/')[1])
const phoneTabs = [
  { path: '/dashboard', label: '工作台', icon: Grid },
  { path: '/exam/setup', label: '答题', icon: EditPen },
  { path: '/community', label: '交流', icon: ChatLineRound },
  { path: '/profile', label: '我的', icon: User }
]
const showPhoneTabs = computed(() => route.path === '/exam/setup' ||
  (route.path.split('/').length === 2 && !['/ai-chat', '/admin'].includes(route.path)))
const pageTitles = {
  '/provisions': '法条检索', '/cases': '案例查询', '/viewpoints': '学者观点',
  '/mock-exams': '模拟考试', '/wrong-book': '错题本', '/notes': '学习笔记', '/stats': '学习统计'
}
const pageHeading = computed(() => pageTitles[route.path])
const shortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K'
const narrow = window.matchMedia('(max-width: 720px), (max-width: 900px) and (max-height: 500px)')
const isNarrow = ref(narrow.matches)

function openMenu() {
  drawerMotion.value = true
  menuOpen.value = true
  nextTick(() => {
    if (menuOpen.value) closeButton.value?.focus({ preventScroll: true })
  })
}
function closeMenu() {
  if (!menuOpen.value) return
  menuOpen.value = false
  nextTick(() => {
    if (!menuOpen.value && narrow.matches) menuButton.value?.focus({ preventScroll: true })
  })
}
function viewportChanged() {
  const nav = document.getElementById('primary-navigation')
  const focusWasInNav = nav?.contains(document.activeElement)
  const wasOpen = menuOpen.value
  isNarrow.value = narrow.matches
  drawerMotion.value = false
  menuOpen.value = false
  nextTick(() => {
    if (narrow.matches && focusWasInNav) menuButton.value?.focus()
    if (!narrow.matches && wasOpen) nav?.querySelector('.is-active')?.focus()
  })
}
function keepFocusInDrawer(event) {
  if (!menuOpen.value) return
  const links = event.currentTarget.querySelectorAll('a[href], button:not(:disabled)')
  const first = links[0]
  const last = links[links.length - 1]
  if (event.shiftKey ? event.target !== first : event.target !== last) return
  event.preventDefault()
  const next = event.shiftKey ? last : first
  next.focus()
}
function moveNavFocus(event) {
  if (event.key === ' ' && event.target.matches('[role="menuitem"]')) {
    event.preventDefault()
    event.target.click()
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  const items = Array.from(event.currentTarget.querySelectorAll('[role="menuitem"]'))
  const index = items.indexOf(event.target)
  if (index < 0) return
  event.preventDefault()
  const position = { Home: 0, End: items.length - 1, ArrowDown: index + 1, ArrowUp: index - 1 }[event.key]
  items[(position + items.length) % items.length].focus()
}
async function keyShortcut(event) {
  if (event.defaultPrevented || event.isComposing || event.key.toLowerCase() !== 'k' || (!event.metaKey && !event.ctrlKey)) return
  if (document.querySelector('.el-overlay:not(.shell-search-overlay):not([style*="display: none"])') && !searchOpen.value) return
  event.preventDefault()
  if (menuOpen.value) {
    closeMenu()
    await nextTick()
  }
  searchOpen.value = true
}
watch(() => route.path, () => {
  closeMenu()
  if (narrow.matches) document.querySelector('.workspace > main')?.scrollTo(0, 0)
})
onMounted(() => {
  narrow.addEventListener('change', viewportChanged)
  window.addEventListener('keydown', keyShortcut)
  if (route.path !== '/profile') userStore.fetchMe().catch(() => {})
})
onBeforeUnmount(() => {
  narrow.removeEventListener('change', viewportChanged)
  window.removeEventListener('keydown', keyShortcut)
})
function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') router.push('/profile')
}
</script>

<style scoped>
.layout {
  --toolbar-height: 56px;
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  height: 100%;
  overflow: hidden;
  background: var(--lex-canvas);
}
.aside {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(165deg, #e5edf5 0%, #edf1f6 52%, #e9edf3 100%);
  border-right: 1px solid #d6dfe9;
}
.brand-row { display: flex; align-items: center; padding: 26px 20px 22px; }
.brand { display: flex; align-items: center; gap: 11px; color: var(--lex-ink); width: fit-content; }
.brand-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 37px;
  height: 37px;
  border-radius: 11px;
  color: white;
  background: linear-gradient(145deg, #477eb7, #204c7d);
  box-shadow: inset 0 1px 1px #ffffff45, 0 2px 4px #25436a1c;
}
.brand-type { font-size: 1.26rem; line-height: 1.06; font-weight: 690; letter-spacing: -0.036em; }
.brand-type > span { display: block; font-size: .7rem; line-height: 1.55; font-weight: 570; letter-spacing: .06em; color: var(--lex-muted); margin-top: 3px; }
.navigation { padding: 0 10px 20px; flex: 1; min-height: 0; overflow-y: auto; }
.menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #3a475a;
  --el-menu-active-color: #204f84;
  --el-menu-hover-bg-color: #dfe7f0;
  --el-menu-item-height: 37px;
  border-right: none;
}
.menu :deep(.el-menu-item) {
  display: flex;
  align-items: center;
  height: var(--el-menu-item-height);
  padding: 0 13px !important;
  border-radius: 8px;
  margin-bottom: 2px;
  font-size: .875rem;
  font-weight: 510;
  color: var(--el-menu-text-color);
  transition: color .12s, background-color .12s;
}
.menu :deep(.el-menu-item > .el-icon) { width: 18px; margin-right: 10px; font-size: 18px; }
.menu :deep(.el-menu-item.is-active) { background: #d4e2f1; color: #1e4f83; font-weight: 650; }
.menu :deep(.el-menu-item-group__title) {
  padding: 17px 13px 6px !important;
  color: #596a7f;
  font-size: .705rem;
  letter-spacing: .055em;
  font-weight: 600;
}
.workspace { position: relative; height: 100%; min-width: 0; overflow: hidden; }
.header {
  position: absolute;
  z-index: 8;
  inset: 0 0 auto;
  height: var(--toolbar-height);
  background: rgba(244,246,249,.88);
  -webkit-backdrop-filter: saturate(150%) blur(18px);
  backdrop-filter: saturate(150%) blur(18px);
  box-shadow: 0 1px 0 #586e8912;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 clamp(18px, 2.4vw, 38px);
}
.search-launch, .user, .icon-button {
  display: inline-flex;
  align-items: center;
  border: 0;
  cursor: pointer;
  color: var(--lex-ink);
  background: transparent;
}
.search-launch {
  gap: 8px;
  min-width: 0;
  width: min(40%, 360px);
  height: 33px;
  padding: 0 10px;
  border: 1px solid #d7dfe8;
  border-radius: 8px;
  color: #516174;
  text-align: left;
  background: #ffffffbd;
}
.search-launch kbd { margin-left: auto; font: 11px/1.3 var(--el-font-family); color: #68788c; }
.right { display: flex; align-items: center; gap: 15px; margin-left: auto; }
.ai-shortcut { display: inline-flex; align-items: center; gap: 6px; padding: 5px 8px; border-radius: 7px; color: #335a88; font-size: .82rem; font-weight: 560; }
.user { gap: 7px; border-radius: 18px; padding: 3px 5px 3px 3px; font-size: .83rem; font-weight: 550; }
.user-name { max-width: 14ch; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-chevron { color: var(--lex-muted); }
.icon-button { flex: none; justify-content: center; font-size: 1.2rem; min-width: 36px; min-height: 36px; border-radius: 8px; }
.mobile-brand, .mobile-close, .menu-toggle, .nav-overlay, .phone-tabs { display: none; }
.main { height: 100%; min-width: 0; overflow: auto; padding: calc(var(--toolbar-height) + 20px) clamp(18px, 2.4vw, 38px) 20px; scroll-padding-top: calc(var(--toolbar-height) + 12px); }
.page-heading { margin: 0 0 1rem; color: var(--lex-ink); font-size: clamp(1.5rem, 2.1vw, 2rem); line-height: 1.25; font-weight: 680; letter-spacing: -.035em; }
@media (hover: hover) {
  .menu :deep(.el-menu-item:not(.is-active):hover) { background: #dfe7f0; }
  .search-launch:hover { border-color: #a7b8cb; background: #fff; }
  .ai-shortcut:hover, .user:hover, .icon-button:hover { background: #dee7f1; }
}
.search-launch:active, .icon-button:active, .ai-shortcut:active, .user:active { background: #d8e3ef; }
@media (max-height: 760px) {
  .brand-row { padding-top: 17px; padding-bottom: 14px; }
  .menu :deep(.el-menu-item-group__title) { padding-top: 11px !important; }
}
@media (max-width: 720px), (max-width: 900px) and (max-height: 500px) {
  .layout { grid-template-columns: minmax(0, 1fr); height: 100dvh; --toolbar-height: calc(50px + env(safe-area-inset-top)); }
  .workspace { display: flex; flex-direction: column; min-height: 0; }
  .header { padding: env(safe-area-inset-top) max(10px, env(safe-area-inset-right)) 0 max(10px, env(safe-area-inset-left)); gap: 4px; }
  .mobile-brand { display: block; font-size: 1.04rem; font-weight: 670; letter-spacing: -.035em; color: var(--lex-ink); }
  .menu-toggle, .mobile-close { display: inline-flex; }
  .mobile-close { margin-left: auto; }
  .right { margin-left: 0; gap: 0; }
  .user-name, .user-chevron, .ai-shortcut, .search-launch span, .search-launch kbd { display: none; }
  .search-launch { width: 44px; height: 44px; border: 0; background: transparent; font-size: 1.12rem; margin-left: auto; justify-content: center; }
  .icon-button, .user { min-width: 44px; min-height: 44px; justify-content: center; }
  .aside {
    position: fixed;
    z-index: 18;
    inset: 0 auto 0 0;
    width: min(278px, calc(100vw - 48px));
    box-shadow: 12px 0 38px #13274326;
    visibility: hidden;
    pointer-events: none;
    transform: translateX(-100%);
  }
  .aside.drawer-motion {
    transition: transform var(--lex-motion-fast, 120ms) var(--lex-ease-out, ease-out),
      visibility 0s linear var(--lex-motion-fast, 120ms);
  }
  .aside.open {
    visibility: visible;
    pointer-events: auto;
    transform: translateX(0);
    transition: transform var(--lex-motion-enter, 180ms) var(--lex-ease-out, ease-out);
  }
  .aside .brand-row { padding: max(16px, env(safe-area-inset-top)) 16px 18px max(16px, env(safe-area-inset-left)); }
  .aside .navigation { padding-bottom: max(20px, env(safe-area-inset-bottom)); }
  .aside .menu { --el-menu-item-height: 44px; }
  .nav-overlay {
    position: fixed; display: block; inset: 0; border: 0; z-index: 17; background: #1c2c405c;
    opacity: 0; visibility: hidden; pointer-events: none;
    transition: opacity var(--lex-motion-fast, 120ms) ease-out, visibility 0s linear var(--lex-motion-fast, 120ms);
  }
  .nav-overlay.open {
    opacity: 1; visibility: visible; pointer-events: auto;
    transition: opacity var(--lex-motion-enter, 180ms) ease-out;
  }
  .main { flex: 1; height: auto; min-height: 0; padding: calc(var(--toolbar-height) + 12px) max(12px, env(safe-area-inset-right)) max(12px, env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left)); }
  .workspace:has(.phone-tabs) .main { padding-bottom: 12px; }
  .phone-tabs {
    display: grid; flex: none; grid-template-columns: repeat(4, minmax(0, 1fr));
    padding: 3px env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
    border-top: 1px solid var(--lex-line); background: var(--lex-surface);
  }
  .phone-tabs a { display: grid; align-content: center; justify-items: center; gap: 2px; min-height: 48px; color: var(--lex-muted); font-size: .7rem; }
  .phone-tabs .el-icon { font-size: 19px; }
  .phone-tabs a[aria-current="page"] { color: var(--lex-accent); font-weight: 700; }
  .phone-tabs a:active { background: var(--el-color-primary-light-9); }
  .workspace:has(input:focus, textarea:focus) .phone-tabs { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .aside.drawer-motion, .aside.drawer-motion :deep(*), .nav-overlay { transition: none !important; }
}
@media (prefers-reduced-transparency: reduce), (prefers-contrast: more) {
  .header { background: var(--lex-canvas); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
@media (prefers-contrast: more) {
  .aside, .header, .search-launch { border-color: #8797a9; }
  .menu :deep(.el-menu-item.is-active) { color: #143756; outline: 1px solid #577796; outline-offset: -1px; }
}
</style>
