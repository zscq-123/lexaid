<template>
  <details class="quality">
    <summary>题库质量与可练范围<span v-if="counts">：{{ available }} 道可练，{{ held }} 道待复核</span></summary>
    <p>可练题已通过题型、答案结构、解析和分类检查。这是基础筛查，不代表全部经过法律专家复核。</p>
    <p>任务指令、机器标注题与未分类题暂不参与抽题；原始题目和历史作答保留。导入题库须两名审核人确认答案、法律版本与解析后再开放。</p>
    <ul v-if="counts"><li v-for="row in counts.filter(r => r.reason !== 'ELIGIBLE')" :key="row.reason">{{ labels[row.reason] || row.reason }}：{{ row.count }}</li></ul>
    <el-button v-else link type="primary" @click="load">{{ failed ? '重试质量统计' : '读取质量统计' }}</el-button>
  </details>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../../api/request'
const counts = ref(null)
const failed = ref(false)
const available = computed(() => counts.value?.find(r => r.reason === 'ELIGIBLE')?.count || 0)
const held = computed(() => counts.value?.reduce((n, r) => n + (r.reason === 'ELIGIBLE' ? 0 : r.count), 0) || 0)
const labels = { MISSING_CONTENT: '缺少题干或答案', INVALID_TYPE: '题型待修订', INVALID_ANSWER: '答案格式异常', INVALID_CHOICES: '选项与答案不匹配', MISSING_EXPLANATION: '缺少教学解析', TASK_INSTRUCTION: '解析含任务指令', UNCLASSIFIED: '法学科目待判定', BENCHMARK_REVIEW: '基准题待教学复核', DATASET_REVIEW: '导入题待教学复核' }
async function load() {
  try { counts.value = (await request.get('/exam/quality')).data; failed.value = false }
  catch { failed.value = true }
}
onMounted(load)
</script>
<style scoped>
.quality { margin-bottom: 20px; font-size: .85rem; line-height: 1.8; color: var(--lex-muted); }
summary { cursor: pointer; color: var(--lex-accent); }
ul { padding-left: 20px; }
</style>
