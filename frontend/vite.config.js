import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { devProxy } from './devProxy.js';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Listen on all interfaces so the browser on the host can reach the container.
    host: true,
    port: 5173,
    strictPort: true,
    // File change events do not cross Docker bind mounts on Windows and macOS.
    watch: { usePolling: true },
    proxy: devProxy(process.env.API_PROXY_TARGET),
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
    },
  },
});
