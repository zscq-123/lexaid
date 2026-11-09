<template>
  <p v-if="paths.length === 1" class="code-books">{{ paths[0] }}</p>
  <details v-else-if="paths.length > 1" class="code-books">
    <summary>关联法典与编（{{ paths.length }} 项）</summary>
    <ul><li v-for="path in paths" :key="path">{{ path }}</li></ul>
  </details>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ books: Array })
const paths = computed(() => [...new Set((props.books || []).map(book => [book.codeName, book.bookName].filter(Boolean).join(' / ')))])
</script>

<style scoped>
.code-books { margin: 4px 0; color: var(--lex-muted); font-size: .82rem; overflow-wrap: anywhere; }
summary { cursor: pointer; }
ul { padding-left: 1.3em; }
</style>
