import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    // 开发环境请求转发到 test 子域（与 .env.development 中 VITE_APP_ENV=test 一致）
    proxy: {
      '/api': {
        target: 'https://test.guokegames.online',
        changeOrigin: true,
        secure: true
      }
    }
  }
})
