import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  server: {
    host: "0.0.0.0",
    port: 18080,
    strictPort: true,
    proxy: {
      "/api": {
        target: process.env.API_PROXY_TARGET || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          "element-plus": ["element-plus"],
          "vue-core": ["vue", "vue-router"],
          icons: ["@element-plus/icons-vue"],
        },
      },
    },
  },
});
