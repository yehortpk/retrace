import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The API runs on :8080. Proxying it keeps the browser on one origin in dev, exactly as the packaged
// jar does in production, so no CORS is configured anywhere and the session and XSRF cookies just work.
const backend = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/v3/api-docs': backend,
      '/swagger-ui': backend,
    },
  },
});
