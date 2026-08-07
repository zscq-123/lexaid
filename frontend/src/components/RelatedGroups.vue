<template>
  <div v-if="hasAny" class="related">
    <el-divider content-position="left">相关内容</el-divider>
    <el-row v-for="group in groups" :key="group.key" :gutter="12" class="group">
      <el-col :span="24">
        <div class="group-title">{{ group.label }}</div>
        <el-empty v-if="group.items.length === 0" :image-size="60" description="暂无" />
        <el-row v-else :gutter="12">
          <el-col v-for="item in group.items" :key="item.type + item.id" :span="8">
            <el-card class="item" shadow="hover" @click="go(item)">
              <div class="item-title">{{ item.title }}</div>
              <div class="item-summary">{{ item.summary }}</div>
            </el-card>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  groups: { type: Object, default: () => ({}) }
})
const router = useRouter()

const groups = computed(() => [
  { key: 'provisions', label: '相关法条', items: props.groups.provisions || [] },
  { key: 'cases', label: '相关案例', items: props.groups.cases || [] },
  { key: 'viewpoints', label: '学者观点', items: props.groups.viewpoints || [] },
  { key: 'questions', label: '相关题目', items: props.groups.questions || [] }
])
const hasAny = computed(() => groups.value.some((g) => g.items.length > 0))

function go(item) {
  if (item.type === 'PROVISION') router.push(`/provisions/${item.id}`)
  else if (item.type === 'CASE') router.push(`/cases/${item.id}`)
  else if (item.type === 'VIEWPOINT') router.push(`/viewpoints/${item.id}`)
}
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
  cursor: pointer;
  margin-bottom: 12px;
  border-radius: 8px;
}
.item-title {
  font-weight: 600;
  margin-bottom: 6px;
  color: #303133;
  line-height: 1.5;
}
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
