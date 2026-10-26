// HireTrack 시연 영상 녹화
//   준비: 백엔드(8080) · 프론트(5173) 실행 → node demo/seed-demo.mjs → node demo/screenshots.mjs (확장 팝업 이미지)
//   실행: node demo/record-demo.mjs        → out/frames/*.jpg + out/frames.txt (ffmpeg concat 목록)
//   이어서: node demo/make-video.mjs       → HireTrack-demo.mp4 / .gif
// Playwright recordVideo 대신 CDP screencast 프레임을 직접 모은다 (화질·브라우저 다운로드 문제 없음).
// 화면 위에 단계 자막과 마우스 커서를 그려서 소리 없이 봐도 무엇을 하는지 알 수 있게 한다.
import { chromium } from 'playwright-core';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, 'out');
const FRAMES = join(OUT, 'frames');
rmSync(FRAMES, { recursive: true, force: true });
mkdirSync(FRAMES, { recursive: true });

const APP = process.env.APP_URL || 'http://localhost:5173';
const state = JSON.parse(readFileSync(join(OUT, 'demo_state.json'), 'utf8'));
const popupPng = readFileSync(join(here, '..', 'screenshots', 'extension-popup.png')).toString('base64');
const VIEW = { width: 1280, height: 720 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const executablePath = [process.env.BROWSER_PATH, 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium'].find((p) => p && existsSync(p));
const browser = await chromium.launch({ executablePath, headless: true });

// ── 화면 위 연출: 자막 · 커서 · 클릭 파문 · 타이틀 카드 (페이지가 바뀌어도 다시 붙는다) ──
const OVERLAY = () => {
  const css = `
    #demo-cursor{position:fixed;z-index:2147483647;width:22px;height:22px;margin:-3px 0 0 -3px;pointer-events:none;
      background:no-repeat center/contain url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M3 2l7 19 2.5-7.5L20 11z' fill='%23111' stroke='white' stroke-width='1.5'/%3E%3C/svg%3E");
      transition:transform .08s}
    #demo-cursor.down{transform:scale(.8)}
    #demo-ripple{position:fixed;z-index:2147483646;width:38px;height:38px;margin:-19px 0 0 -19px;border-radius:50%;
      border:3px solid #3758f9;pointer-events:none;opacity:0}
    #demo-ripple.on{animation:demo-r .5s ease-out}
    @keyframes demo-r{from{opacity:.95;transform:scale(.3)}to{opacity:0;transform:scale(1.5)}}
    #demo-caption{position:fixed;z-index:2147483645;left:50%;bottom:36px;transform:translateX(-50%);max-width:92%;
      padding:11px 24px;border-radius:999px;background:rgba(13,19,48,.92);border:1px solid rgba(91,120,255,.55);color:#fff;
      font:600 20px/1.35 'Pretendard','Malgun Gothic',sans-serif;box-shadow:0 8px 30px rgba(0,0,0,.35);pointer-events:none;
      transition:opacity .3s;white-space:nowrap}
    #demo-caption b{color:#8ea2ff;margin-right:10px}
    @media (max-width:600px){#demo-caption{bottom:28px;font-size:15px;white-space:normal;text-align:center;border-radius:16px;padding:9px 14px}}
    #demo-title{position:fixed;inset:0;z-index:2147483644;display:flex;flex-direction:column;align-items:center;justify-content:center;
      gap:14px;background:radial-gradient(circle at 15% 10%,rgba(55,88,249,.65),transparent 55%),
      radial-gradient(circle at 90% 90%,rgba(20,184,166,.45),transparent 55%),#0b1023;color:#fff;
      font-family:'Pretendard','Malgun Gothic',sans-serif;transition:opacity .6s}
    body:has(#demo-title) #demo-cursor, body:has(#demo-title) #demo-caption{display:none}
    #demo-title .logo{width:72px;height:72px;border-radius:18px;background:#3758f9;display:grid;place-items:center;font:800 40px/1 Arial,sans-serif;
      box-shadow:0 10px 40px rgba(55,88,249,.6)}
    #demo-title h1{margin:0;font-size:68px;letter-spacing:-1px}
    #demo-title p{margin:0;font-size:25px;opacity:.92}
    #demo-title small{margin-top:16px;font-size:17px;opacity:.7}
    #demo-figure{position:fixed;inset:0;z-index:2147483640;display:flex;align-items:center;justify-content:center;gap:48px;
      background:rgba(11,16,35,.78);transition:opacity .4s;font-family:'Pretendard','Malgun Gothic',sans-serif;color:#fff}
    #demo-figure img{border-radius:12px;box-shadow:0 20px 60px rgba(0,0,0,.5);max-height:600px}
    #demo-figure ul{margin:0;padding-left:22px;font-size:21px;line-height:1.9;max-width:520px}
    #demo-figure h2{margin:0 0 10px;font-size:30px}`;
  const mount = () => {
    if (document.getElementById('demo-cursor')) return;
    const style = document.createElement('style'); style.textContent = css; document.head.appendChild(style);
    for (const id of ['demo-cursor', 'demo-ripple', 'demo-caption']) {
      const el = document.createElement('div'); el.id = id; document.body.appendChild(el);
    }
    const saved = JSON.parse(sessionStorage.getItem('demo-state') || '{}');
    const cursor = document.getElementById('demo-cursor');
    cursor.style.left = (saved.x ?? 640) + 'px'; cursor.style.top = (saved.y ?? 360) + 'px';
    const cap = document.getElementById('demo-caption');
    if (saved.caption) cap.innerHTML = saved.caption; else cap.style.opacity = '0';
    document.addEventListener('mousemove', (e) => {
      cursor.style.left = e.clientX + 'px'; cursor.style.top = e.clientY + 'px';
      const s = JSON.parse(sessionStorage.getItem('demo-state') || '{}'); s.x = e.clientX; s.y = e.clientY;
      sessionStorage.setItem('demo-state', JSON.stringify(s));
    }, true);
    document.addEventListener('mousedown', (e) => {
      cursor.classList.add('down');
      const r = document.getElementById('demo-ripple'); r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px';
      r.classList.remove('on'); void r.offsetWidth; r.classList.add('on');
    }, true);
    document.addEventListener('mouseup', () => cursor.classList.remove('down'), true);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount); else mount();
};

async function newContext(viewport = VIEW, extra = {}) {
  const ctx = await browser.newContext({ viewport, locale: 'ko-KR', timezoneId: 'Asia/Seoul', ...extra });
  await ctx.addInitScript((t) => localStorage.setItem('hiretrack.token', t), state.token);
  await ctx.addInitScript(OVERLAY);
  return ctx;
}

// ── CDP screencast 로 프레임 수집 ──
const frames = [];
let frameNo = 0;
async function startCast(page) {
  const cdp = await page.context().newCDPSession(page);
  cdp.on('Page.screencastFrame', async (f) => {
    const file = `frames/${String(frameNo++).padStart(5, '0')}.jpg`;
    writeFileSync(join(OUT, file), Buffer.from(f.data, 'base64'));
    frames.push({ file, t: f.metadata.timestamp });
    try { await cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }); } catch {}
  });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 88, maxWidth: 1280, maxHeight: 844, everyNthFrame: 2 });
  return cdp;
}

// ── 연출 도우미 ──
async function caption(page, html) {
  await page.evaluate((h) => {
    const cap = document.getElementById('demo-caption');
    if (cap) { cap.innerHTML = h; cap.style.opacity = h ? '1' : '0'; }
    const s = JSON.parse(sessionStorage.getItem('demo-state') || '{}'); s.caption = h;
    sessionStorage.setItem('demo-state', JSON.stringify(s));
  }, html);
}
async function point(page, locator) {
  const el = locator.first();
  await el.scrollIntoViewIfNeeded().catch(() => {});
  const box = await el.boundingBox();
  if (!box) throw new Error('요소를 찾을 수 없음: ' + locator);
  return { x: box.x + box.width / 2, y: box.y + Math.min(box.height / 2, 40) };
}
async function moveTo(page, locator, { click = true, steps = 22 } = {}) {
  const { x, y } = await point(page, locator);
  await page.mouse.move(x, y, { steps });
  await sleep(220);
  if (click) { await page.mouse.down(); await sleep(90); await page.mouse.up(); }
  await sleep(350);
}
async function type(page, locator, text, delay = 55) {
  await moveTo(page, locator);
  await locator.first().pressSequentially(text, { delay });
}
async function scrollPage(page, dy, ms = 1400) {
  await page.evaluate(async ([d, t]) => {
    const el = document.scrollingElement;
    const start = el.scrollTop; const t0 = performance.now();
    await new Promise((res) => {
      const step = (now) => { const p = Math.min(1, (now - t0) / t); el.scrollTop = start + d * (1 - Math.cos(Math.PI * p)) / 2; p < 1 ? requestAnimationFrame(step) : res(); };
      requestAnimationFrame(step);
    });
  }, [dy, ms]);
}
async function titleCard(page, show, { h1 = 'HireTrack', p = '', small = '' } = {}) {
  await page.evaluate(([s, h, pp, sm]) => {
    let el = document.getElementById('demo-title');
    if (s) {
      if (!el) { el = document.createElement('div'); el.id = 'demo-title'; document.body.appendChild(el); }
      el.innerHTML = `<div class="logo">H</div><h1>${h}</h1><p>${pp}</p><small>${sm}</small>`;
      el.style.opacity = '1';
    } else if (el) { el.style.opacity = '0'; setTimeout(() => el.remove(), 650); }
  }, [show, h1, p, small]);
}
const column = (page, title) =>
  page.locator('section.column').filter({ has: page.getByRole('heading', { name: new RegExp(`^${title}`) }) });

// ── 녹화 ──
const ctx = await newContext();
const page = await ctx.newPage();
await page.goto(APP + '/', { waitUntil: 'load' });
await page.locator('.card').first().waitFor();
await sleep(1500);
const cast = await startCast(page);

await titleCard(page, true, { p: '채용 지원 관리 트래커', small: '공고 수집 · 칸반 보드 · 자기소개서 버전 관리 · 지원 통계' });
await sleep(3200);
await titleCard(page, false);
await sleep(700);

// 01 보드
await caption(page, '<b>01</b>지원 현황을 칸반 보드로 · 마감 D-day · 다가오는 면접');
await page.mouse.move(640, 150, { steps: 20 });
await sleep(900);
await moveTo(page, page.locator('.card', { hasText: '데이터랩스' }), { click: false });
await sleep(900);
await moveTo(page, page.locator('.card', { hasText: '오픈소프트' }), { click: false });
await sleep(1200);

// 02 공고 등록
await caption(page, '<b>02</b>공고 등록 · URL 을 넣으면 자동 추출, 직접 입력도 가능');
await moveTo(page, page.getByRole('button', { name: '+ 공고 등록' }));
const modal = page.locator('form.modal');
await type(page, modal.getByLabel(/회사명/), '하이어테크');
await type(page, modal.getByLabel(/직무명/), '백엔드 개발자');
await type(page, modal.getByLabel(/근무지/), '서울 강남구');
await moveTo(page, modal.getByLabel('마감일'));
await modal.getByLabel('마감일').fill(new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10));
await sleep(500);
await moveTo(page, modal.getByRole('button', { name: '등록', exact: true }));
await sleep(1300);

// 03 드래그로 상태 변경
await caption(page, '<b>03</b>카드를 끌어 상태 변경 · 모든 변경은 이력으로 남아요');
const card = column(page, '관심').locator('.card', { hasText: '하이어테크' });
const from = await point(page, card);
const toBox = await page.locator('.dropzone.status-APPLIED').boundingBox();
await page.mouse.move(from.x, from.y, { steps: 20 });
await sleep(300);
await page.mouse.down();
await page.mouse.move(from.x + 12, from.y, { steps: 4 });
await page.mouse.move(toBox.x + toBox.width / 2, toBox.y + 90, { steps: 40 });
await sleep(350);
await page.mouse.up();
await sleep(1800);

// 04 자기소개서
await caption(page, '<b>04</b>자기소개서 · 자동 임시저장, 버전 저장, 버전끼리 비교');
await moveTo(page, column(page, '관심').locator('.card', { hasText: '클라우드웍스' }));
await page.getByRole('tab', { name: '자기소개서' }).waitFor();
await sleep(700);
await moveTo(page, page.getByRole('tab', { name: '자기소개서' }));
await page.getByLabel('답변').waitFor();
await sleep(900);
await page.getByLabel('답변').click();
await page.keyboard.press('Control+End');
await page.getByLabel('답변').pressSequentially(' 장애 회고 문화를 팀에 정착시키겠습니다.', { delay: 45 });
await sleep(1500);
await moveTo(page, page.getByRole('button', { name: /버전 저장/ }));
await sleep(1600);

// 05 제출본 고정
await caption(page, '<b>05</b>지원하는 순간의 답변이 제출본으로 고정 · 면접 전에 그대로 확인');
await page.goto(`${APP}/postings/${state.ids['핀테크원']}?tab=essay`);
await page.getByText('지원 동기를 작성해 주세요').first().waitFor();
await sleep(1200);
await moveTo(page, page.getByText(/제출본 v2/).first(), { click: false });
await sleep(1800);

// 06 면접
await caption(page, '<b>06</b>면접 일정 · 캘린더 구독(1시간 전 알림) · 제출한 자소서 바로가기');
await page.goto(`${APP}/postings/${state.ids['모빌리티플러스']}`);
await page.getByText('면접 일정').first().waitFor();
await sleep(600);
await scrollPage(page, 330, 1200);
await sleep(600);
await moveTo(page, page.getByText('제출한 자기소개서 보기').first(), { click: false });
await sleep(1600);

// 07 대시보드
await caption(page, '<b>07</b>통계 · "지금 상태"가 아니라 "도달한 단계" 기준 퍼널 · 주간 추이');
await moveTo(page, page.getByRole('link', { name: '대시보드' }));
await page.locator('.recharts-surface').first().waitFor({ timeout: 15000 });
await sleep(1800);
await scrollPage(page, 330, 1500);
await sleep(900);
await moveTo(page, page.getByLabel('구분 기준'));
await page.getByLabel('구분 기준').selectOption('location');
await sleep(1800);

// 08 답변 라이브러리
await caption(page, '<b>08</b>답변 라이브러리 · 템플릿과 내가 쓴 과거 답변 검색');
await moveTo(page, page.getByRole('link', { name: '라이브러리' }));
await sleep(900);
await type(page, page.getByPlaceholder(/키워드로/), '장애', 160);
await sleep(1800);

// 09 크롬 확장 프로그램 (실제 팝업 화면 캡처)
await caption(page, '<b>09</b>크롬 확장 프로그램 · 보고 있는 공고 페이지에서 바로 등록');
await page.evaluate((png) => {
  const el = document.createElement('div'); el.id = 'demo-figure';
  el.innerHTML = `<img src="data:image/png;base64,${png}" alt="" /><div><h2>HireTrack 확장 프로그램</h2><ul>
    <li>activeTab 권한만 · 아이콘을 누른 탭만 읽기</li><li>렌더링된 HTML 을 서버 파서가 그대로 처리</li>
    <li>공고 추출·등록만 되는 전용 토큰</li><li>CORS 는 고정된 확장 ID 하나만 허용</li></ul></div>`;
  document.body.appendChild(el);
}, popupPng);
await sleep(4200);
await page.evaluate(() => document.getElementById('demo-figure')?.remove());

// 10 설정
await caption(page, '<b>10</b>마감 임박 메일 알림 · 구글·애플 캘린더 구독 URL');
await moveTo(page, page.getByRole('link', { name: '설정', exact: true }));
if (!page.url().endsWith('/settings')) await page.goto(APP + '/settings');
await page.getByText('캘린더 구독', { exact: true }).first().waitFor();
await sleep(900);
await scrollPage(page, 300, 1200);
await sleep(1500);
await cast.send('Page.stopScreencast');

// 11 모바일
const mctx = await newContext({ width: 390, height: 844 }, { isMobile: true, hasTouch: true, deviceScaleFactor: 1 });
const m = await mctx.newPage();
await m.goto(APP + '/', { waitUntil: 'load' });
await m.locator('.card').first().waitFor();
await sleep(1200);
const mcast = await startCast(m);
await caption(m, '<b>11</b>모바일에서도 그대로');
await sleep(1200);
await m.evaluate(() => document.querySelector('.board')?.scrollBy({ left: 300, behavior: 'smooth' }));
await sleep(1600);
await m.goto(APP + '/dashboard');
await m.locator('.recharts-surface').first().waitFor({ timeout: 15000 });
await sleep(800);
await scrollPage(m, 500, 1500);
await sleep(1200);
await mcast.send('Page.stopScreencast');

// 끝 카드
const endCast = await startCast(page);
await caption(page, '');
await titleCard(page, true, { p: '지원 현황을 한곳에서, 끝까지', small: 'github.com/cjsrudgh98-crypto/HireTrack' });
await sleep(3500);
await endCast.send('Page.stopScreencast');
await sleep(300);

// ── ffmpeg concat 목록 (프레임 사이 실제 시간 간격을 그대로 사용) ──
const lines = [];
for (let i = 0; i < frames.length; i++) {
  const dur = i + 1 < frames.length ? Math.min(Math.max(frames[i + 1].t - frames[i].t, 0.001), 4) : 1.5;
  lines.push(`file '${frames[i].file}'`, `duration ${dur.toFixed(3)}`);
}
lines.push(`file '${frames[frames.length - 1].file}'`);
writeFileSync(join(OUT, 'frames.txt'), lines.join('\n'));
const total = frames.length > 1 ? frames[frames.length - 1].t - frames[0].t : 0;
console.log(`frames: ${frames.length}, 길이 약 ${total.toFixed(1)}초`);

await browser.close();
