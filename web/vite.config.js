import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/webapi': {
        target: 'http://localhost:8883',
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8883',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus'],
          vendor: ['axios'],
        },
      },
    },
  },
})
