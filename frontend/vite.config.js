import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    // Listen on all interfaces so the browser on the host can reach the container.
    host: true,
    port: 5173,
    strictPort: true,
    // File change events do not cross Docker bind mounts on Windows and macOS.
    watch: { usePolling: true },
  },
});
