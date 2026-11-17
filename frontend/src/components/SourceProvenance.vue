<template>
  <section class="provenance" aria-label="来源与核验信息">
    <p>来源：{{ source.sourceDataset || source.source || '未标注' }}
      <a v-if="link" :href="link" target="_blank" rel="noopener noreferrer">打开来源页面 ↗</a>
      <span v-else> · 未收录可访问的原文链接</span>
    </p>
    <p v-if="kind === 'PROVISION'">版本标识：{{ source.lawName }} · {{ source.articleNo }} · 公布 {{ source.promulgationDate || '未注明' }} · 生效 {{ source.effectiveDate || '未注明' }}；失效日期未收录。</p>
    <p v-else-if="kind === 'CASE'">原文定位：案号 {{ source.caseNo || '未注明' }} · {{ source.court || '法院未注明' }} · {{ source.judgeDate || '裁判日期未注明' }}</p>
    <p v-else>原文定位：{{ source.source || '出版物未注明' }} · {{ source.publishYear || '年份未注明' }}；引用页码需核对原文。</p>
    <p class="verification">{{ kind === 'PROVISION' ? '效力待核验。日期与状态来自收录记录，不能据此确认当前有效版本。' : '出处待核验。摘要与教学整理不等于完整原文，请核对引用位置。' }}</p>
  </section>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ source: { type: Object, required: true }, kind: { type: String, required: true } })
const link = computed(() => {
  try { const url = new URL(props.source.sourceUrl); return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : null }
  catch { return null }
})
</script>
<style scoped>
.provenance { margin: 12px 0; padding: 12px 14px; border: 1px solid var(--lex-line); border-radius: 8px; font-size: .82rem; line-height: 1.8; color: var(--lex-muted); overflow-wrap: anywhere; }
a { margin-left: 8px; color: var(--lex-accent); }
.verification { color: var(--lex-ink); }
</style>
