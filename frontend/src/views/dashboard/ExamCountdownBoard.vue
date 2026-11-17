<template>
  <section class="exam-blackboard" :aria-label="board.label + '倒计时'">
    <div class="board-heading">
      <span><i></i> {{ board.title }}</span>
      <small>{{ board.year }} 年度</small>
    </div>
    <div class="board-content">
      <div class="board-subject">
        <b>{{ board.label }}</b>
        <small>官方安排 · 北京时间</small>
      </div>
      <div class="board-countdown" :class="{ pending: board.pending }">
        <strong>{{ board.remaining }}</strong>
        <small>{{ board.detail }}</small>
      </div>
    </div>
    <p class="board-note">官方未公布时不使用估算日期</p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useUserStore } from '../../stores/user'

const props = defineProps({
  track: { type: String, required: true }
})

const officialSchedules = {
  qualification: {
    2026: [
      { name: '客观题', start: '2026-09-12T09:00:00+08:00', end: '2026-09-13T17:30:00+08:00' },
      { name: '主观题', start: '2026-10-18T09:00:00+08:00', end: '2026-10-18T13:00:00+08:00' }
    ]
  },
  master: {
    2027: [
      { name: '全国初试', start: '2026-12-19T08:30:00+08:00', end: '2026-12-20T17:00:00+08:00' }
    ]
  }
}

const userStore = useUserStore()
const now = ref(Date.now())
let timer = null

function countdownText(target) {
  const totalSeconds = Math.max(0, Math.floor((target - now.value) / 1000))
  const days = Math.floor(totalSeconds / 86400)
  const hours = String(Math.floor(totalSeconds % 86400 / 3600)).padStart(2, '0')
  const minutes = String(Math.floor(totalSeconds % 3600 / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${days}天 ${hours}:${minutes}:${seconds}`
}

function formatScheduleTime(raw) {
  const [datePart, timePart] = raw.split('T')
  const [year, month, day] = datePart.split('-').map(Number)
  return `${year}年${month}月${day}日 ${timePart.slice(0, 5)}`
}

function defaultExamYear(track) {
  const schedules = officialSchedules[track]
  const years = Object.keys(schedules).map(Number).sort((a, b) => a - b)
  return years.find(year => new Date(schedules[year].at(-1).end).getTime() > now.value) || years.at(-1) + 1
}

const board = computed(() => {
  const qualification = props.track === 'qualification'
  const yearKey = qualification ? 'legalExamYear' : 'masterExamYear'
  const year = Number(userStore.user?.[yearKey]) || defaultExamYear(props.track)
  const schedule = officialSchedules[props.track][year]
  const base = {
    title: qualification ? '法考日历' : '法硕日历',
    label: qualification ? '法律职业资格考试' : '法律硕士联考',
    year
  }

  if (!schedule) {
    return {
      ...base,
      pending: true,
      remaining: '等待官方公布',
      detail: qualification ? '司法部尚未发布本年度考试公告' : '教育部尚未发布本年度初试安排'
    }
  }

  const next = schedule.find(item => new Date(item.end).getTime() > now.value)
  if (!next) {
    return {
      ...base,
      pending: false,
      remaining: '本年度考试已结束',
      detail: schedule.map(item => `${item.name} ${formatScheduleTime(item.start).split(' ')[0]}`).join(' · ')
    }
  }

  const start = new Date(next.start).getTime()
  return {
    ...base,
    pending: false,
    remaining: start <= now.value ? '考试进行中' : countdownText(start),
    detail: `${next.name} · ${formatScheduleTime(next.start)}`
  }
})

onMounted(() => {
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.exam-blackboard {
  position: relative; overflow: hidden; margin: 0 0 1rem; padding: 1rem 1.35rem .85rem;
  border: .48rem solid #9a6e43; border-radius: .82rem; color: #f8f5dc; background: #173d35;
  box-shadow: inset 0 0 0 2px #0b2b25, inset 0 0 28px #071d1880, 0 6px 18px #1b2b3420;
}
.exam-blackboard::before {
  content: ''; position: absolute; inset: 0; pointer-events: none; opacity: .16;
  background-image: radial-gradient(#fff 0.6px, transparent .7px), linear-gradient(98deg, transparent 46%, #ffffff14 50%, transparent 54%);
  background-size: 9px 9px, 230px 100%;
}
.board-heading, .board-content, .board-note { position: relative; z-index: 1; }
.board-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding-bottom: .55rem; border-bottom: 1px solid #ffffff35; font-size: 1rem; letter-spacing: .11em; }
.board-heading span { display: inline-flex; align-items: center; gap: .5rem; }
.board-heading i { width: .43rem; height: .43rem; border-radius: 50%; background: #efd78e; box-shadow: 0 0 0 3px #efd78e28; }
.board-heading small { color: #d4dfd5; font-size: .7rem; letter-spacing: .02em; }
.board-content { display: grid; grid-template-columns: minmax(13rem, .7fr) minmax(0, 1.3fr); align-items: center; gap: 1.25rem; min-height: 5.7rem; padding: .75rem .1rem .6rem; border-bottom: 1px dashed #ffffff30; }
.board-subject, .board-countdown { display: grid; gap: .22rem; min-width: 0; }
.board-heading span, .board-subject b, .board-countdown strong {
  font-family: 'Kaiti SC', 'STKaiti', 'KaiTi', 'SimKai', cursive; font-weight: 400;
  text-shadow: .4px .3px 0 #fff9, -.35px -.25px 0 #fff5, 0 0 2px #ffffff4d;
}
.board-subject b { color: #fff9dd; font-size: 1.12rem; letter-spacing: .075em; }
.board-subject small, .board-countdown small { color: #c8d8ce; font-size: .72rem; }
.board-countdown { justify-items: end; text-align: right; }
.board-countdown strong { color: #fff5ba; font-size: clamp(1.45rem, 3vw, 2rem); line-height: 1.2; font-variant-numeric: tabular-nums; letter-spacing: .065em; }
.board-countdown.pending strong { color: #e9d59d; font-size: clamp(1.15rem, 2vw, 1.45rem); }
.board-note { margin-top: .45rem; color: #aec4b6; font-size: .67rem; text-align: right; }

@media (max-width: 720px) {
  .exam-blackboard { margin-bottom: .8rem; padding: .82rem .9rem .7rem; border-width: .38rem; }
  .board-content { grid-template-columns: 1fr; gap: .55rem; min-height: 0; padding-block: .7rem; }
  .board-countdown { justify-items: start; text-align: left; }
  .board-countdown strong { font-size: 1.34rem; white-space: nowrap; }
  .board-note { text-align: left; line-height: 1.45; }
}
</style>
