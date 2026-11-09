<template>
  <el-config-provider :locale="zhCn">
    <router-view :key="$route.meta.mockRoom ? $route.path : undefined" />
  </el-config-provider>
</template>

<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style src="./styles/theme.css"></style>
