<template>
  <section class="topic" aria-labelledby="topic-heading">
    <div class="topic-head">
      <h2 id="topic-heading">围绕一个争点学习</h2>
      <select v-model.number="selected" aria-label="学习专题">
        <option v-for="(item, index) in topics" :key="item.law" :value="index">{{ item.name }}</option>
      </select>
    </div>
    <p>{{ topic.prompt }}</p>
    <div class="steps">
      <router-link :to="'/provisions/' + topic.law">读法条<small>{{ topic.article }}</small></router-link>
      <router-link :to="'/cases/' + topic.caseId">看案例<small>{{ topic.caseTitle }}</small></router-link>
      <button type="button" :disabled="starting" @click="practice(topic.question)">做同考点练习<small>{{ starting ? '正在组卷…' : '1 道关联题' }}</small></button>
      <router-link :to="{ path: '/ai-chat', query: { contextType: 'CASE', contextId: topic.caseId } }">问 AI<small>结合本案解释争点</small></router-link>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useQuestionPractice } from '../../composables/useQuestionPractice'

// These are the stable IDs of the linked teaching materials in data.sql.
const topics = [
  { name: '正当防卫的限度', prompt: '判断防卫是否过当，要从案情中找出哪些事实？',
    law: 4, caseId: 1, question: 1, article: '刑法第二十条', caseTitle: '于欢案' },
  { name: '合同违约责任', prompt: '约定的交货日期已过，买方可以提出哪些请求？',
    law: 13, caseId: 5, question: 8, article: '民法典第五百七十七条', caseTitle: '买卖合同迟延交货案' },
  { name: '安全保障义务', prompt: '顾客在商场受伤，哪些事实会影响责任认定？',
    law: 15, caseId: 4, question: 9, article: '民法典第一千一百九十八条', caseTitle: '商场安全保障义务案' }
]
const selected = ref(0)
const topic = computed(() => topics[selected.value])
const { practice, starting } = useQuestionPractice()
</script>

<style scoped>
.topic { margin: 1.35rem 0; padding: 1.1rem 1.2rem; border: 1px solid var(--lex-line); border-radius: var(--lex-radius); background: var(--lex-surface); }
.topic-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: .6rem; }
h2 { font-size: 1.12rem; }
select { font: inherit; color: var(--lex-accent); border: 1px solid var(--lex-line); border-radius: 6px; padding: .25rem .4rem; background: var(--lex-surface); }
p { margin: .65rem 0 .85rem; }
.steps { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .5rem; }
.steps > * { display: grid; align-content: center; gap: .2rem; min-height: 48px; padding: .5rem .65rem; border: none; border-radius: 8px; text-align: start; color: var(--lex-accent); background: var(--lex-canvas); font: inherit; font-weight: 600; cursor: pointer; }
.steps small { font-size: .78rem; font-weight: 400; color: var(--lex-muted); }
.steps button:disabled { opacity: .65; }
@media (max-width: 650px) { .steps { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
