import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

declare const process: { env: Record<string, string | undefined> };

// В разработке все запросы /api уходят на локальный backend, поэтому cookie сессии
// и CSRF работают в рамках одного origin (docs/API.md, раздел «Сессия и CSRF»).
const backend = process.env.FOODFLOW_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
