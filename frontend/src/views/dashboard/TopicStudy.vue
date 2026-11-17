<template>
  <section class="topic" aria-labelledby="topic-heading">
    <div class="topic-head">
      <h2 id="topic-heading">围绕一个争点学习</h2>
      <select v-model.number="selected" aria-label="学习专题">
        <option v-for="(item, index) in topics" :key="item.law" :value="index">{{ item.name }}</option>
      </select>
    </div>
    <p>{{ topic.prompt }}</p>
    <dl class="study-outline">
      <dt>关键事实</dt><dd>{{ topic.facts }}</dd>
      <dt>比较与反例</dt><dd>{{ topic.counterexample }}</dd>
      <dt>易错点自查</dt><dd>{{ topic.mistake }}</dd>
    </dl>
    <div class="steps">
      <router-link :to="'/provisions/' + topic.law">读法条<small>{{ topic.article }}</small></router-link>
      <router-link v-if="topic.caseId" :to="'/cases/' + topic.caseId">看案例<small>{{ topic.caseTitle }}</small></router-link>
      <router-link v-else :to="'/provisions/' + topic.compareLaw">对照法条<small>比较另一种主观状态</small></router-link>
      <router-link :to="'/viewpoints/' + topic.viewpoint">比较观点<small>核对作者论证与出处</small></router-link>
      <button type="button" :disabled="starting" @click="practice(topic.question)">做同考点练习<small>{{ starting ? '正在组卷…' : '1 道关联题' }}</small></button>
      <router-link :to="{ path: '/ai-chat', query: { contextType: topic.caseId ? 'CASE' : 'PROVISION', contextId: topic.caseId || topic.law } }">问 AI<small>结合来源解释争点</small></router-link>
    </div>
    <small class="outline-note">以上问题与假设变式用于学习讨论；完成练习后，在错题本记录事实、依据、推理和错因。</small>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useQuestionPractice } from '../../composables/useQuestionPractice'

// These are the stable IDs of the linked teaching materials in data.sql.
const topics = [
  { name: '正当防卫的限度', prompt: '判断防卫是否过当，要从案情中找出哪些事实？',
    law: 4, caseId: 1, question: 1, viewpoint: 1, article: '刑法第二十条', caseTitle: '于欢案',
    facts: '侵害是否正在进行？侵害强度、防卫方式与损害结果分别是什么？',
    counterexample: '假设侵害已经结束，或只改变侵害强度，论证需要怎样调整？', mistake: '是否只凭损害结果下结论，忽略了行为当时的情境？' },
  { name: '合同违约责任', prompt: '约定的交货日期已过，买方可以提出哪些请求？',
    law: 13, caseId: 5, question: 8, viewpoint: 4, article: '民法典第五百七十七条', caseTitle: '买卖合同迟延交货案',
    facts: '合同约定、到期时间、履行情况与具体损失分别有哪些证据？',
    counterexample: '假设继续履行已不可能，原有请求是否需要调整？', mistake: '是否混淆了责任形式与适用条件，遗漏损失的证明？' },
  { name: '安全保障义务', prompt: '顾客在商场受伤，哪些事实会影响责任认定？',
    law: 15, caseId: 4, question: 9, viewpoint: 5, article: '民法典第一千一百九十八条', caseTitle: '商场安全保障义务案',
    facts: '危险来源、提示与清理措施、各方行为及因果关系是什么？',
    counterexample: '假设损害改由第三人造成，责任分析有哪些不同？', mistake: '是否把在经营场所受伤直接等同于经营者承担全部责任？' },
  { name: '盗窃的构成与财物占有', prompt: '设备故障与行为人的认识，在构成分析中各起什么作用？',
    law: 7, caseId: 2, question: 4, viewpoint: 3, article: '刑法第二百六十四条', caseTitle: '许霆案',
    facts: '财物原由谁占有？行为人何时知悉异常，又采取了什么行为？',
    counterexample: '假设行为人并不知道设备异常，哪些构成要素需要重新论证？', mistake: '是否把道德评价当作构成要件，或把一种学说当成唯一结论？' },
  { name: '轻微违法与行政处罚', prompt: '不予处罚、从轻与减轻处罚应分别核对哪些条件？',
    law: 17, caseId: 6, question: 11, viewpoint: 6, article: '行政处罚法第三十三条', caseTitle: '行政处罚教学案例',
    facts: '违法程度、危害后果、改正时间和主观过错有何证据？',
    counterexample: '假设仅改变危害后果或是否及时改正，分析是否相同？', mistake: '是否把初次违法直接等同于不予处罚，而未核对其他条件？' },
  { name: '故意与过失的区分', prompt: '怎样分别描述行为人对结果的认识与意志？',
    law: 2, compareLaw: 3, question: 2, viewpoint: 7, article: '刑法第十四条与第十五条',
    facts: '是否预见结果？希望、放任或轻信能够避免，各有什么事实支持？',
    counterexample: '假设只改变行为人采取的防止措施，主观状态判断是否变化？', mistake: '是否从已经发生的结果倒推主观心态，遗漏行为时的认识？' }
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
.steps { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: .5rem; }
.study-outline { display: grid; grid-template-columns: auto 1fr; gap: .35rem .75rem; margin-bottom: 1rem; font-size: .85rem; line-height: 1.7; }
.study-outline dt { font-weight: 600; }
.study-outline dd { margin: 0; color: var(--lex-muted); }
.outline-note { display: block; margin-top: .8rem; color: var(--lex-muted); line-height: 1.7; }
.steps > * { display: grid; align-content: center; gap: .2rem; min-height: 48px; padding: .5rem .65rem; border: none; border-radius: 8px; text-align: start; color: var(--lex-accent); background: var(--lex-canvas); font: inherit; font-weight: 600; cursor: pointer; }
.steps small { font-size: .78rem; font-weight: 400; color: var(--lex-muted); }
.steps button:disabled { opacity: .65; }
@media (max-width: 650px) { .steps { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
