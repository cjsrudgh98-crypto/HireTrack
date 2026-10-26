import { expect, test, type Page } from '@playwright/test';

/** 칸반 컬럼 (제목으로 찾음) */
const column = (page: Page, title: string) =>
  page.locator('section.column').filter({ has: page.getByRole('heading', { name: new RegExp(`^${title}`) }) });

async function signup(page: Page) {
  await page.goto('/login');
  await page.getByRole('tab', { name: '회원가입' }).click();
  await page.getByLabel('이메일').fill(`e2e-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@test.com`);
  await page.getByLabel('비밀번호').fill('password123');
  await page.getByRole('button', { name: '가입하고 시작하기' }).click();
  await expect(page).toHaveURL('/');
}

async function addPosting(page: Page, company: string, title = '백엔드 개발자') {
  await page.getByRole('button', { name: '+ 공고 등록' }).click();
  const modal = page.locator('form.modal');
  await modal.getByLabel(/회사명/).fill(company);
  await modal.getByLabel(/직무명/).fill(title);
  await modal.getByRole('button', { name: '등록', exact: true }).click();
  await expect(column(page, '관심').getByText(company)).toBeVisible();
}

test.beforeEach(async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept()); // "면접 진행으로 바꿀까요?" 같은 확인창은 수락
});

test('가입 → 공고 등록 → 상태 변경 → 대시보드에 반영', async ({ page }) => {
  await signup(page);
  await addPosting(page, '이투이테크');

  await column(page, '관심').getByText('이투이테크').click();
  await expect(page.getByRole('heading', { name: '백엔드 개발자' })).toBeVisible();
  await page.getByLabel('지원 상태').selectOption('APPLIED');
  await page.getByRole('tab', { name: '이력' }).click();
  await expect(page.getByText('관심 → 지원 완료')).toBeVisible();

  await page.getByRole('link', { name: '보드', exact: true }).click();
  await expect(column(page, '지원 완료').getByText('이투이테크')).toBeVisible();
  await expect(column(page, '지원 완료').getByText(/^지원 \d+\/\d+$/)).toBeVisible(); // 카드에 지원일

  await page.getByRole('link', { name: '대시보드' }).click();
  const total = page.locator('.stat-tile').filter({ hasText: '총 지원' });
  await expect(total.locator('.stat-value')).toHaveText('1건');
});

test('카드를 드래그해서 상태를 바꾸면 새로고침 후에도 유지된다', async ({ page }) => {
  await signup(page);
  await addPosting(page, '드래그소프트');

  const card = column(page, '관심').locator('.card', { hasText: '드래그소프트' });
  const target = page.locator('.dropzone.status-DOCUMENT_PASSED');
  const from = (await card.boundingBox())!;
  const to = (await target.boundingBox())!;

  // dnd-kit PointerSensor 는 5px 이상 움직여야 드래그로 본다 — 실제 사용자처럼 여러 단계로 이동
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(from.x + from.width / 2 + 10, from.y + from.height / 2, { steps: 3 });
  await page.mouse.move(to.x + to.width / 2, to.y + 30, { steps: 12 });
  await page.mouse.up();

  await expect(column(page, '서류 합격').getByText('드래그소프트')).toBeVisible();
  await page.reload();
  await expect(column(page, '서류 합격').getByText('드래그소프트')).toBeVisible();
});

test('자소서 버전 저장 후 지원하면 제출본으로 고정된다', async ({ page }) => {
  await signup(page);
  await addPosting(page, '자소서랩스');
  await column(page, '관심').getByText('자소서랩스').click();

  await page.getByRole('tab', { name: '자기소개서' }).click();
  await page.getByRole('button', { name: '+ 문항 추가' }).click();
  await page.getByLabel('질문').fill('지원 동기를 작성해 주세요.');
  await page.getByLabel('글자 수 제한').fill('100');
  await page.getByRole('button', { name: '추가', exact: true }).click();

  await page.getByLabel('답변').fill('사용자의 문제를 데이터로 해결하고 싶습니다.');
  await expect(page.locator('.char-counts')).toContainText('공백 포함 24 / 100');
  await page.getByRole('button', { name: /버전 저장 \(v1\)/ }).click();
  await expect(page.locator('.version-list')).toContainText('v1');

  await page.getByLabel('지원 상태').selectOption('APPLIED');
  await expect(page.getByText(/제출본 v1 — 지원할 때 낸 내용이라 수정할 수 없어요/)).toBeVisible();
  await expect(page.getByLabel('답변')).toHaveAttribute('readonly', '');
});

test('보드 밖 화면은 가로로 넘치지 않는다 (데스크톱·모바일)', async ({ page }) => {
  await signup(page);
  await addPosting(page, '가로넘침확인');
  for (const width of [1280, 390]) {
    await page.setViewportSize({ width, height: 800 });
    for (const path of ['/settings', '/dashboard', '/library']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${path} @ ${width}px`).toBeLessThanOrEqual(0);
    }
  }
});
