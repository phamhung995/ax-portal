import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: 'dist-library',
    lib: { entry: 'src/index.js', formats: ['es'], fileName: 'prism', cssFileName: 'prism' },
    rollupOptions: { external: ['vue', 'lucide-vue-next'] },
  },
})
