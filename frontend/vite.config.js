import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // The isolation frontend can use the separate loopback backend without changing the default proxy.
    proxy: {
      '/api': process.env.HRMS_ISOLATION_FRONTEND === '1' ? 'http://127.0.0.1:8081' : 'http://localhost:8080',
    },
  },
})
