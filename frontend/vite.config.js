import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Vite 설정: React·Tailwind 플러그인, 개발 서버 포트 3100
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3100,
    // /api 요청을 백엔드(8100)로 전달 (개발용 프록시)
    proxy: {
      "/api": {
        target: "http://localhost:8100",
        changeOrigin: true,
      },
    },
  },
  // 테스트 설정 (jsdom, 결과 XML은 test-results/)
  test: {
    environment: "jsdom",
    reporters: ["default", "junit"],
    outputFile: { junit: "test-results/junit.xml" },
  },
});
