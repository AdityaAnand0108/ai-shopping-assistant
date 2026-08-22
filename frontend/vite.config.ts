import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies /api to Spring Boot so the browser sees one origin.
// The backend does send CORS headers for :5173, but proxying avoids a preflight
// on every request and keeps the frontend code free of absolute URLs — the same
// relative paths work unchanged when the built bundle is served by the backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
