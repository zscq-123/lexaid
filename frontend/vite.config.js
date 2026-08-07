import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地开发默认代理到 8080；如后端使用其他端口（本机 8081），设置 VITE_PROXY_TARGET
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
