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
    // Both company sign-in and Super Admin use the verified local restore by default.
    // Set HRMS_ISOLATION_FRONTEND=0 to inspect the legacy backend on port 8080.
    proxy: {
      '/api': process.env.HRMS_ISOLATION_FRONTEND === '0' ? 'http://localhost:8080' : 'http://127.0.0.1:8081',
    },
  },
})
