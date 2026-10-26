/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 개발 중에는 /api 요청을 Spring Boot(8080)로 넘겨서 같은 출처처럼 동작하게 한다.
// changeOrigin: false — Host 를 그대로 넘겨야 백엔드가 Origin(5173)과 같은 출처로 보고 CORS 검사를 하지 않는다.
// (운영의 Nginx `proxy_set_header Host $host` 와 같은 동작. true 로 두면 확장 프로그램용 CORS 설정에 걸려 403)
const apiProxy = { '/api': { target: 'http://localhost:8080', changeOrigin: false } };

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: apiProxy,
  },
  preview: {
    port: 5173,
    proxy: apiProxy,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // 날짜 계산 테스트가 실행 환경 시간대에 흔들리지 않도록 서비스 기준 시간대로 고정
    env: { TZ: 'Asia/Seoul' },
  },
});
