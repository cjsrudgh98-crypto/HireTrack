import { defineConfig, devices } from '@playwright/test';

/**
 * E2E: 실제 백엔드 + 프론트를 띄우고 브라우저로 핵심 흐름을 검증한다.
 * - 이미 떠 있는 서버가 있으면 그대로 쓴다(로컬 개발 중). 없으면 webServer 가 띄운다(CI).
 * - PW_CHANNEL=msedge 처럼 설치된 브라우저를 쓰면 브라우저 다운로드 없이 로컬에서 돌릴 수 있다.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 7_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}) },
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : [
        {
          // CI: mvn package 로 만든 jar. 로컬에서 이미 8080 이 떠 있으면 재사용
          command: 'java -jar ../target/hiretrack.jar',
          url: 'http://localhost:8080/actuator/health',
          reuseExistingServer: true,
          timeout: 120_000,
          env: { JWT_SECRET: 'e2e-secret-e2e-secret-e2e-secret-0123456789', DB_URL: 'jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1' },
        },
        {
          command: 'npm run preview -- --port 5173 --strictPort',
          url: 'http://localhost:5173',
          reuseExistingServer: true,
        },
      ],
});
