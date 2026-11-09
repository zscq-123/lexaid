<template>
  <div v-if="hasAny" class="related">
    <el-divider content-position="left">相关内容</el-divider>
    <el-row v-for="group in groups" :key="group.key" :gutter="12" class="group">
      <el-col :span="24">
        <div class="group-title">{{ group.label }}</div>
        <el-empty v-if="group.items.length === 0" :image-size="60" description="暂无" />
        <el-row v-else :gutter="12">
          <el-col v-for="item in group.items" :key="item.type + item.id" :xs="24" :sm="12" :md="8">
            <component :is="item.type === 'QUESTION' ? 'button' : RouterLink" class="item"
              v-bind="item.type === 'QUESTION' ? { type: 'button', disabled: starting } : { to: paths[item.type] + item.id }"
              @click="item.type === 'QUESTION' && practice(item.id)">
              <span class="item-title">{{ item.title }}</span>
              <span class="item-summary">{{ item.summary }}</span>
              <span v-if="item.type === 'QUESTION'" class="practice-label">练习此题</span>
            </component>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuestionPractice } from '../composables/useQuestionPractice'

const props = defineProps({
  groups: { type: Object, default: () => ({}) }
})
const { practice, starting } = useQuestionPractice()
const paths = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }

const groups = computed(() => [
  { key: 'provisions', label: '相关法条', items: props.groups.provisions || [] },
  { key: 'cases', label: '相关案例', items: props.groups.cases || [] },
  { key: 'viewpoints', label: '学者观点', items: props.groups.viewpoints || [] },
  { key: 'questions', label: '相关题目', items: props.groups.questions || [] }
])
const hasAny = computed(() => groups.value.some((g) => g.items.length > 0))

</script>

<style scoped>
.group {
  margin-bottom: 8px;
}
.group-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #2e5fa3;
}
.item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 14px;
  border: 1px solid var(--lex-line);
  border-radius: 10px;
  background: var(--lex-surface);
  cursor: pointer;
  margin-bottom: 12px;
  border-radius: 8px;
}
.item-title {
  display: block;
  font-weight: 600;
  margin-bottom: 6px;
  color: #303133;
  line-height: 1.5;
}
.practice-label { display: inline-block; margin-top: .5rem; color: var(--lex-accent); }
.item-summary {
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
