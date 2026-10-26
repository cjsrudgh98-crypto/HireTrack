// 시연 영상 · 스크린샷용 예시 데이터 (모두 가상의 회사·공고)
//   준비: 백엔드(8080) · 프론트(5173) 실행 중
//   실행: node demo/seed-demo.mjs   → out/demo_state.json (계정 토큰 · 공고 ID)
// 같은 데모 계정으로 여러 번 돌려도 되도록, 기존 공고·템플릿을 지우고 다시 채운다.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const APP = process.env.APP_URL || 'http://localhost:5173';
const EMAIL = process.env.DEMO_EMAIL || 'demo@hiretrack.dev';
const PASSWORD = 'password123';

async function api(method, path, body, token) {
  const res = await fetch(`${APP}/api${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw Object.assign(new Error(`${method} ${path} → ${res.status} ${text}`), { status: res.status });
  return text ? JSON.parse(text) : null;
}

// 1) 계정: 가입, 이미 있으면 로그인 후 데이터 비우기
let auth;
try {
  auth = await api('POST', '/auth/signup', { email: EMAIL, password: PASSWORD });
} catch (e) {
  if (e.status !== 409) throw e;
  auth = await api('POST', '/auth/login', { email: EMAIL, password: PASSWORD });
}
const token = auth.accessToken;
for (const p of await api('GET', '/postings', null, token)) await api('DELETE', `/postings/${p.id}`, null, token);
for (const t of await api('GET', '/templates', null, token)) await api('DELETE', `/templates/${t.id}`, null, token);

const day = (n) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);
const at = (days, hour, minute = 0) => {
  const d = new Date(Date.now() + days * 86400000);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
};

// 2) 템플릿 (답변 라이브러리)
const tplMotivation = await api('POST', '/templates', {
  category: 'MOTIVATION', title: '지원 동기 · 대규모 트래픽',
  content: '사용자가 늘어도 흔들리지 않는 서비스를 만드는 일에 가장 큰 보람을 느낍니다. 개인 프로젝트에서 동시 요청으로 데이터가 꼬이는 문제를 직접 재현하고 행 잠금으로 해결하면서, 정합성을 지키는 백엔드 개발자가 되고 싶다는 목표가 분명해졌습니다.',
}, token);
await api('POST', '/templates', {
  category: 'COLLABORATION', title: '협업 · 코드 리뷰 문화',
  content: '팀 프로젝트에서 PR 템플릿과 리뷰 체크리스트를 제안해, 리뷰 대기 시간을 줄이고 같은 실수가 반복되지 않게 했습니다.',
}, token);
await api('POST', '/templates', {
  category: 'COMPETENCY', title: '직무 역량 · 테스트 자동화',
  content: 'H2 에서는 통과하던 쿼리가 PostgreSQL 에서 깨지는 문제를 Testcontainers 로 잡은 뒤, 모든 API 통합 테스트를 실제 DB 에서 돌리도록 바꿨습니다.',
}, token);

// 3) 공고 — [회사, 직무, 마감(일 후), 근무지, 고용형태, 상태 변경 순서]
const postings = [
  ['데이터랩스', '데이터 엔지니어', 2, '서울 강남구', 'FULL_TIME', []],
  ['클라우드웍스', '백엔드 개발자', 9, '서울 성동구', 'FULL_TIME', []],
  ['로지스틱스원', '서버 개발자 (신입)', 20, '경기 성남시', 'FULL_TIME', []],
  ['오픈소프트', '주니어 웹 개발자', -3, '서울 마포구', 'FREELANCE', []],
  ['핀테크원', '서버 개발자', 6, '서울 중구', 'FULL_TIME', ['APPLIED']],
  ['헬스케어랩', 'Java 백엔드 개발자', null, '서울 강서구', 'FULL_TIME', ['APPLIED']],
  ['커머스허브', 'Java 개발자', null, '서울 송파구', 'FULL_TIME', ['APPLIED', 'DOCUMENT_PASSED']],
  ['모빌리티플러스', '플랫폼 엔지니어', null, '경기 성남시', 'FULL_TIME', ['APPLIED', 'DOCUMENT_PASSED', 'INTERVIEW']],
  ['에듀테크랩', '백엔드 개발자', null, '서울 구로구', 'FULL_TIME', ['APPLIED', 'DOCUMENT_PASSED', 'INTERVIEW']],
  ['페이먼츠코리아', '결제 서버 개발자', null, '서울 영등포구', 'FULL_TIME', ['APPLIED', 'DOCUMENT_PASSED', 'INTERVIEW', 'ACCEPTED']],
  ['게임스튜디오', '게임 서버 개발자', null, '경기 성남시', 'FULL_TIME', ['APPLIED', 'REJECTED']],
  ['미디어그룹', '백엔드 개발자', null, '서울 마포구', 'OTHER', ['APPLIED', 'DOCUMENT_PASSED', 'REJECTED']],
  ['소셜앱', '서버 개발자', null, '서울 강남구', 'FULL_TIME', ['APPLIED', 'DOCUMENT_PASSED', 'INTERVIEW', 'REJECTED']],
];
const ids = {};
for (const [company, title, deadline, location, employmentType] of postings) {
  const p = await api('POST', '/postings', {
    company, title, location, employmentType,
    deadline: deadline === null ? null : day(deadline),
    url: `https://example.com/jobs/${encodeURIComponent(company)}`,
    requirements: 'Java · Spring Boot 기반 REST API 개발 경험\nRDBMS 설계와 쿼리 튜닝 경험\nGit 기반 협업 경험',
  }, token);
  ids[company] = p.id;
}

// 4) 지원 전에 자소서를 써 둔 공고: 핀테크원 — 지원하는 순간 제출본으로 고정된다
const q = await api('POST', `/postings/${ids['핀테크원']}/questions`, { question: '지원 동기를 작성해 주세요.', maxLength: 500, category: 'MOTIVATION' }, token);
await api('POST', `/questions/${q.id}/answers`, { content: tplMotivation.content, baseVersion: null, sourceTemplateId: tplMotivation.id }, token);
await api('POST', `/questions/${q.id}/answers`, {
  content: tplMotivation.content + ' 핀테크원의 결제 정산 시스템에서 이 경험을 살리고 싶습니다.', baseVersion: 1,
}, token);

for (const [company, , , , , statuses] of postings) {
  for (const status of statuses) await api('PATCH', `/postings/${ids[company]}/status`, { status }, token);
}

// 5) 작성 중인 자소서: 클라우드웍스 — 버전 2개 + 임시저장, 문항 2개
const cw = ids['클라우드웍스'];
const q1 = await api('POST', `/postings/${cw}/questions`, { question: '지원 동기와 입사 후 포부를 작성해 주세요.', maxLength: 500, category: 'MOTIVATION' }, token);
await api('POST', `/questions/${q1.id}/answers`, { content: '클라우드 인프라 위에서 대규모 트래픽을 다루는 경험을 쌓고 싶어 지원했습니다.', baseVersion: null }, token);
await api('POST', `/questions/${q1.id}/answers`, { content: '클라우드 인프라 위에서 초당 수천 건의 요청을 안정적으로 처리하는 백엔드를 만들고 싶어 지원했습니다. 입사 후에는 장애 대응 자동화에 기여하겠습니다.', baseVersion: 1 }, token);
await api('PUT', `/questions/${q1.id}/draft`, { content: '클라우드 인프라 위에서 초당 수천 건의 요청을 안정적으로 처리하는 백엔드를 만들고 싶어 지원했습니다. 입사 후에는 모니터링과 장애 대응 자동화로 팀의 운영 부담을 줄이겠습니다.' }, token);
await api('POST', `/postings/${cw}/questions`, { question: '협업 중 갈등을 해결한 경험을 작성해 주세요.', maxLength: 700, category: 'COLLABORATION' }, token);
// 실수로 바꿨다 바로 되돌린 상태 변경 — 이력에는 취소선으로 남고 통계에서는 빠진다
await api('PATCH', `/postings/${cw}/status`, { status: 'APPLIED' }, token);
await api('PATCH', `/postings/${cw}/status`, { status: 'INTERESTED' }, token);
await api('PATCH', `/postings/${cw}/memo`, { memo: '현직자 커피챗 예정. 기술 블로그의 장애 회고 글 3개 읽어 두기.' }, token);

// 6) 면접 일정
await api('POST', `/postings/${ids['모빌리티플러스']}/interviews`, { type: 'FIRST', startsAt: at(2, 14), durationMinutes: 60, location: '판교 본사 3층 회의실', memo: '시스템 설계 질문 대비 · 포트폴리오 출력' }, token);
await api('POST', `/postings/${ids['에듀테크랩']}/interviews`, { type: 'CODING_TEST', startsAt: at(4, 10), durationMinutes: 120, location: '온라인 (프로그래머스)', memo: null }, token);
await api('POST', `/postings/${ids['에듀테크랩']}/interviews`, { type: 'SECOND', startsAt: at(9, 15, 30), durationMinutes: 60, location: '구로 본사', memo: null }, token);

// 7) 알림 설정 · 캘린더 구독 URL 발급
await api('PUT', '/users/me/notification-settings', { emailEnabled: true, remindDays: [3, 1], sendHour: 9 }, token);
await api('GET', '/users/me/calendar', null, token);

// 8) (선택) 날짜를 과거로 펼치기 — API 로 넣으면 모든 기록이 "오늘"이라 주간 추이·응답 기간 차트가 의미가 없다.
//    데모 전용 PostgreSQL 컨테이너(DEMO_DB_CONTAINER)로 띄운 서버일 때만, 등록일·상태 변경 시각만 바꾼다.
//    [등록, 지원, 다음 단계, ...] 각각 "며칠 전"
const TIMELINE = {
  '페이먼츠코리아': [68, 66, 63, 52, 40], '소셜앱': [66, 64, 59, 48, 41], '미디어그룹': [56, 54, 47, 44],
  '게임스튜디오': [47, 45, 23], '모빌리티플러스': [40, 38, 30, 18], '에듀테크랩': [39, 37, 35, 12],
  '커머스허브': [26, 24, 13], '헬스케어랩': [18, 16], '핀테크원': [17, 15],
  '오픈소프트': [20], '클라우드웍스': [12], '데이터랩스': [5], '로지스틱스원': [3],
};
if (process.env.DEMO_DB_CONTAINER) {
  const { execFileSync } = await import('node:child_process');
  const ts = (daysAgo, i) => `now() - interval '${daysAgo} days' + interval '${(i * 97) % 9 + 9} hours'`;
  const sql = ['begin;'];
  for (const [company, days] of Object.entries(TIMELINE)) {
    const id = ids[company];
    const app = `(select id from application where posting_id = ${id})`;
    // 클라우드웍스는 오늘 메모·자소서를 고친 공고라 수정일은 그대로 둔다
    const updated = company === '클라우드웍스' ? 'updated_at' : ts(days.at(-1), id);
    sql.push(`update job_posting set created_at = ${ts(days[0], id)} - interval '9 hours', updated_at = ${updated} where id = ${id};`);
    days.forEach((d, k) => sql.push(
      `update status_history set changed_at = ${ts(d, id + k)} - interval '9 hours' where id = (select id from status_history where application_id = ${app} order by id offset ${k} limit 1);`));
    if (days.length > 1) sql.push(`update application set applied_at = (select changed_at from status_history where application_id = ${app} order by id offset 1 limit 1) where id = ${app};`);
  }
  // 핀테크원 자소서는 지원 전에 쓴 것
  sql.push(`update essay_answers set created_at = now() - interval '16 days' + (version * interval '5 hours') where question_id in (select id from essay_questions where posting_id = ${ids['핀테크원']});`);
  sql.push(`update answer_templates set created_at = now() - interval '30 days', updated_at = now() - interval '30 days' where user_id = (select id from users where email = '${EMAIL}');`);
  sql.push('commit;');
  execFileSync('docker', ['exec', '-i', process.env.DEMO_DB_CONTAINER, 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'hiretrack', '-d', 'hiretrack'],
    { input: sql.join('\n'), stdio: ['pipe', 'inherit', 'inherit'] });
  console.log('날짜를 과거 10주로 펼쳤습니다');
}

mkdirSync(join(here, 'out'), { recursive: true });
writeFileSync(join(here, 'out', 'demo_state.json'), JSON.stringify({ email: EMAIL, token, ids }, null, 2));
console.log(`seeded ${EMAIL}: 공고 ${postings.length}개`);
