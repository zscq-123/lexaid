<template>
  <div class="question-grade">
    <div class="grade-line">
      <el-tag size="small" :type="tone">{{ label }}</el-tag>
      <span v-if="score != null" class="points">
        {{ status === 'AI_GRADED' ? 'DeepSeek 评分 ' : '' }}{{ score }} / {{ maximum }} 分
      </span>
    </div>
    <p v-if="question.feedback" class="feedback">{{ question.feedback }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ question: { type: Object, required: true } })
const status = computed(() => props.question.gradingStatus ||
  (props.question.type === 'SHORT' ? 'LEGACY_UNGRADED' : 'OBJECTIVE'))
const score = computed(() => props.question.score ??
  (status.value === 'OBJECTIVE' && props.question.isCorrect != null ? Number(props.question.isCorrect) : null))
const maximum = computed(() => props.question.maxScore ?? (props.question.type === 'SHORT' ? 10 : 1))
const labels = {
  PENDING: '等待 DeepSeek 评分', UNAVAILABLE: '评分暂不可用',
  NO_REFERENCE: '缺少参考答案', LEGACY_UNGRADED: '历史作答未评分', BLANK: '未作答'
}
const label = computed(() => {
  if (status.value === 'AI_GRADED') {
    if (score.value === maximum.value) return '满分'
    return score.value > 0 ? '部分得分' : '未得分'
  }
  if (status.value === 'OBJECTIVE') return props.question.isCorrect == null ? '未评分' : props.question.isCorrect ? '正确' : '错误'
  return labels[status.value] ?? '未评分'
})
const tone = computed(() => {
  if (score.value == null) return status.value === 'UNAVAILABLE' ? 'warning' : 'info'
  if (score.value === maximum.value) return 'success'
  return score.value > 0 ? 'warning' : 'danger'
})
</script>

<style scoped>
.question-grade { margin: 10px 0; }
.grade-line { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.points { font-weight: 600; font-variant-numeric: tabular-nums; }
.feedback { margin-top: 4px; line-height: 1.7; color: #495360; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
