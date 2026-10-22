import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// Backend base URL for the dev proxy. Override with VITE_API_TARGET
// (e.g. .env.local: VITE_API_TARGET=http://192.168.1.10:8080).
const API_TARGET = process.env.VITE_API_TARGET ?? 'http://localhost:8080/skm'

// https://vite.dev/config/
export default defineConfig({
  base: '/skm/',
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
      },
      '/health': {
        target: API_TARGET,
        changeOrigin: true,
      },
      '/uploads': {
        target: API_TARGET,
        changeOrigin: true,
      },
    },
  },
})
