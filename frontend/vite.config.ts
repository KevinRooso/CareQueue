/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring Boot back end in dev. Proxying keeps the browser on one origin
// (http://localhost:5173), so the UI needs no CORS configuration.
const backend = 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      // OpenAPI JSON and Swagger UI, for convenience while developing.
      '/v3': backend,
      '/swagger-ui': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
