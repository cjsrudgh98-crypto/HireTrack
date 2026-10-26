// README · 포트폴리오용 스크린샷 → docs/screenshots/*.png
//   준비: 백엔드(8080) · 프론트(5173) 실행 → node demo/seed-demo.mjs
//   실행: node demo/screenshots.mjs
import { chromium } from 'playwright-core';
import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(here, '../..');
const OUT = join(here, '..', 'screenshots');
mkdirSync(OUT, { recursive: true });

const APP = process.env.APP_URL || 'http://localhost:5173';
const state = JSON.parse(readFileSync(join(here, 'out', 'demo_state.json'), 'utf8'));
const { ids, token } = state;
const executablePath = [process.env.BROWSER_PATH, 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium'].find((p) => p && existsSync(p));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await chromium.launch({ executablePath, headless: true });

async function open(viewport, colorScheme = 'light') {
  const ctx = await browser.newContext({ viewport, colorScheme, locale: 'ko-KR', timezoneId: 'Asia/Seoul', deviceScaleFactor: 1 });
  await ctx.addInitScript((t) => localStorage.setItem('hiretrack.token', t), token);
  return ctx.newPage();
}
async function shot(page, name, { full = false } = {}) {
  await sleep(800);
  await page.screenshot({ path: join(OUT, `${name}.png`), fullPage: full });
  console.log('saved', name);
}

// ── 데스크톱 ──
const page = await open({ width: 1280, height: 800 });
await page.goto(APP + '/');
await page.locator('.card').first().waitFor();
await shot(page, 'board');

await page.getByRole('button', { name: '+ 공고 등록' }).click();
await page.locator('form.modal').getByLabel(/회사명/).fill('하이어트랙');
await page.locator('form.modal').getByLabel(/직무명/).fill('백엔드 개발자');
await shot(page, 'posting-form');

await page.goto(`${APP}/postings/${ids['클라우드웍스']}?tab=essay`);
await page.getByText('지원 동기와 입사 후 포부').first().waitFor();
await shot(page, 'essay', { full: true });

await page.goto(`${APP}/postings/${ids['핀테크원']}?tab=essay`);
await page.getByText('지원 동기를 작성해 주세요').first().waitFor();
await shot(page, 'essay-submitted', { full: true });

await page.goto(`${APP}/postings/${ids['모빌리티플러스']}`);
await page.getByRole('tab', { name: '정보' }).waitFor();
await shot(page, 'detail', { full: true });

await page.goto(`${APP}/postings/${ids['클라우드웍스']}?tab=history`);
await page.getByText('상태 변경 이력').waitFor();
await shot(page, 'history');

await page.goto(APP + '/dashboard');
await page.locator('.recharts-surface').first().waitFor({ timeout: 15000 });
await shot(page, 'dashboard', { full: true });

await page.goto(APP + '/library');
await page.getByText('지원 동기 · 대규모 트래픽').first().waitFor();
await page.getByPlaceholder(/키워드/).fill('장애');
await shot(page, 'library');

await page.goto(APP + '/settings');
await page.getByText('캘린더 구독').waitFor();
await shot(page, 'settings', { full: true });

const dark = await open({ width: 1280, height: 800 }, 'dark');
await dark.goto(APP + '/dashboard');
await dark.locator('.recharts-surface').first().waitFor({ timeout: 15000 });
await shot(dark, 'dashboard-dark', { full: true });
await dark.goto(APP + '/');
await dark.locator('.card').first().waitFor();
await shot(dark, 'board-dark');

// ── 모바일 ──
const m = await open({ width: 390, height: 844 });
await m.goto(APP + '/');
await m.locator('.card').first().waitFor();
await shot(m, 'mobile-board');
await m.goto(`${APP}/postings/${ids['클라우드웍스']}?tab=essay`);
await m.getByText('지원 동기와 입사 후 포부').first().waitFor();
await shot(m, 'mobile-essay');
await m.goto(APP + '/dashboard');
await m.locator('.recharts-surface').first().waitFor({ timeout: 15000 });
await shot(m, 'mobile-dashboard');
await browser.close();

// ── 크롬 확장 프로그램 팝업 ──
// 실제 확장 프로그램을 로드해 팝업 → 서비스 워커 → 백엔드(parse-html, 확장 전용 토큰)까지 그대로 동작시킨다.
// "지금 보고 있는 탭 읽기"(activeTab 은 사용자 클릭이 있어야 허용)만 사람인 형식 예시 페이지로 대신한다.
const EXT = join(ROOT, 'extension');
const EXT_ID = 'kkdekpdfgncihpjpjbllcmmpodengigh';
const fixture = readFileSync(join(ROOT, 'src/test/resources/fixtures/saramin.html'), 'utf8');
const extCtx = await chromium.launchPersistentContext('', {
  executablePath, headless: true, viewport: { width: 360, height: 600 }, deviceScaleFactor: 1, locale: 'ko-KR',
  args: [`--disable-extensions-except=${EXT}`, `--load-extension=${EXT}`],
});
const loginPage = await extCtx.newPage();
await loginPage.goto(`chrome-extension://${EXT_ID}/popup/index.html`);
await loginPage.evaluate(async ([apiUrl, appUrl]) => {
  await chrome.runtime.sendMessage({ type: 'saveSettings', apiUrl, appUrl });
}, ['http://localhost:8080', APP]);
const login = await loginPage.evaluate((email) => chrome.runtime.sendMessage({ type: 'login', email, password: 'password123' }), state.email);
if (!login?.ok) throw new Error('확장 로그인 실패: ' + JSON.stringify(login));
await loginPage.close();

const popup = await extCtx.newPage();
await popup.addInitScript((html) => {
  const page = { url: 'https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=12345', title: '사람인 채용 공고', html, truncated: false };
  chrome.tabs.query = async () => [{ id: 1, url: page.url }];
  chrome.scripting.executeScript = async () => [{ result: page }];
}, fixture);
await popup.goto(`chrome-extension://${EXT_ID}/popup/index.html`);
await popup.locator('#posting-form:not([hidden])').waitFor({ timeout: 10000 });
await sleep(600);
await popup.screenshot({ path: join(OUT, 'extension-popup.png'), fullPage: true });
console.log('saved extension-popup');
await extCtx.close();
