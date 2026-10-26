import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi, calendarApi, essayApi, interviewApi, postingApi, settingsApi, statsApi, templateApi } from './index';
import { request } from './client';

vi.mock('./client', () => ({ request: vi.fn() }));

type Call = { name: string; run: () => unknown; method: string; path: string; body?: unknown };

const POSTING_INPUT = {
  company: 'A', title: 'B', url: '', location: '', employmentType: null, deadline: null, requirements: '', memo: '',
};
const QUESTION = { question: '지원 동기', maxLength: 500, category: 'MOTIVATION' as const };
const TEMPLATE = { category: 'MOTIVATION' as const, title: 't', content: 'c' };
const INTERVIEW = { type: 'FIRST' as const, startsAt: '2026-11-02T14:00:00+09:00', durationMinutes: 60, location: '', memo: '' };

/** 프론트가 부르는 모든 API: 함수 → 기대하는 메서드·경로·본문 */
const CALLS: Call[] = [
  { name: 'authApi.signup', run: () => authApi.signup('a@b.c', 'pw'), method: 'POST', path: '/api/auth/signup', body: { email: 'a@b.c', password: 'pw' } },
  { name: 'authApi.login', run: () => authApi.login('a@b.c', 'pw'), method: 'POST', path: '/api/auth/login', body: { email: 'a@b.c', password: 'pw' } },
  { name: 'authApi.me', run: () => authApi.me(), method: 'GET', path: '/api/users/me' },

  { name: 'postingApi.list(빈 필터)', run: () => postingApi.list({ keyword: '  ', employmentType: '', location: '' }), method: 'GET', path: '/api/postings' },
  {
    name: 'postingApi.list(필터)',
    run: () => postingApi.list({ keyword: ' 백엔드 ', employmentType: 'FREELANCE', location: '서울' }),
    method: 'GET',
    path: '/api/postings?keyword=%EB%B0%B1%EC%97%94%EB%93%9C&employmentType=FREELANCE&location=%EC%84%9C%EC%9A%B8',
  },
  { name: 'postingApi.get', run: () => postingApi.get(7), method: 'GET', path: '/api/postings/7' },
  { name: 'postingApi.create', run: () => postingApi.create(POSTING_INPUT), method: 'POST', path: '/api/postings', body: POSTING_INPUT },
  { name: 'postingApi.update', run: () => postingApi.update(7, POSTING_INPUT), method: 'PUT', path: '/api/postings/7', body: POSTING_INPUT },
  { name: 'postingApi.remove', run: () => postingApi.remove(7), method: 'DELETE', path: '/api/postings/7' },
  { name: 'postingApi.changeStatus', run: () => postingApi.changeStatus(7, 'APPLIED'), method: 'PATCH', path: '/api/postings/7/status', body: { status: 'APPLIED' } },
  { name: 'postingApi.updateMemo', run: () => postingApi.updateMemo(7, '메모'), method: 'PATCH', path: '/api/postings/7/memo', body: { memo: '메모' } },
  { name: 'postingApi.history', run: () => postingApi.history(7), method: 'GET', path: '/api/postings/7/history' },
  { name: 'postingApi.parse', run: () => postingApi.parse('https://x.com/1'), method: 'POST', path: '/api/postings/parse', body: { url: 'https://x.com/1' } },

  { name: 'essayApi.questions', run: () => essayApi.questions(7), method: 'GET', path: '/api/postings/7/questions' },
  { name: 'essayApi.addQuestion', run: () => essayApi.addQuestion(7, QUESTION), method: 'POST', path: '/api/postings/7/questions', body: QUESTION },
  { name: 'essayApi.updateQuestion', run: () => essayApi.updateQuestion(3, QUESTION), method: 'PUT', path: '/api/questions/3', body: QUESTION },
  { name: 'essayApi.deleteQuestion', run: () => essayApi.deleteQuestion(3), method: 'DELETE', path: '/api/questions/3' },
  { name: 'essayApi.versions', run: () => essayApi.versions(3), method: 'GET', path: '/api/questions/3/answers' },
  {
    name: 'essayApi.saveVersion',
    run: () => essayApi.saveVersion(3, '답변', null, 5),
    method: 'POST',
    path: '/api/questions/3/answers',
    body: { content: '답변', baseVersion: null, sourceTemplateId: 5 },
  },
  { name: 'essayApi.submit', run: () => essayApi.submit(3, 2), method: 'POST', path: '/api/questions/3/answers/2/submit' },
  { name: 'essayApi.saveDraft', run: () => essayApi.saveDraft(3, '초안'), method: 'PUT', path: '/api/questions/3/draft', body: { content: '초안' } },
  {
    name: 'essayApi.search',
    run: () => essayApi.search({ keyword: ' 협업 ', category: 'COLLABORATION', excludeQuestionId: 3 }),
    method: 'GET',
    path: '/api/answers/search?keyword=%ED%98%91%EC%97%85&category=COLLABORATION&excludeQuestionId=3',
  },
  { name: 'essayApi.search(빈 조건)', run: () => essayApi.search({ keyword: ' ' }), method: 'GET', path: '/api/answers/search?' },

  { name: 'templateApi.list', run: () => templateApi.list(), method: 'GET', path: '/api/templates' },
  { name: 'templateApi.list(유형)', run: () => templateApi.list('GROWTH'), method: 'GET', path: '/api/templates?category=GROWTH' },
  { name: 'templateApi.create', run: () => templateApi.create(TEMPLATE), method: 'POST', path: '/api/templates', body: TEMPLATE },
  { name: 'templateApi.update', run: () => templateApi.update(4, TEMPLATE), method: 'PUT', path: '/api/templates/4', body: TEMPLATE },
  { name: 'templateApi.remove', run: () => templateApi.remove(4), method: 'DELETE', path: '/api/templates/4' },

  { name: 'statsApi.summary', run: () => statsApi.summary(), method: 'GET', path: '/api/stats/summary' },
  { name: 'statsApi.funnel', run: () => statsApi.funnel(), method: 'GET', path: '/api/stats/funnel' },
  { name: 'statsApi.weekly', run: () => statsApi.weekly(), method: 'GET', path: '/api/stats/weekly' },
  { name: 'statsApi.weekly(시작일)', run: () => statsApi.weekly('2026-08-03'), method: 'GET', path: '/api/stats/weekly?from=2026-08-03' },
  { name: 'statsApi.responseTime', run: () => statsApi.responseTime(), method: 'GET', path: '/api/stats/response-time' },
  { name: 'statsApi.breakdown', run: () => statsApi.breakdown('template'), method: 'GET', path: '/api/stats/breakdown?by=template' },

  { name: 'interviewApi.list', run: () => interviewApi.list(7), method: 'GET', path: '/api/postings/7/interviews' },
  { name: 'interviewApi.create', run: () => interviewApi.create(7, INTERVIEW), method: 'POST', path: '/api/postings/7/interviews', body: INTERVIEW },
  { name: 'interviewApi.update', run: () => interviewApi.update(9, INTERVIEW), method: 'PUT', path: '/api/interviews/9', body: INTERVIEW },
  { name: 'interviewApi.remove', run: () => interviewApi.remove(9), method: 'DELETE', path: '/api/interviews/9' },
  { name: 'interviewApi.upcoming', run: () => interviewApi.upcoming(), method: 'GET', path: '/api/interviews/upcoming' },

  { name: 'calendarApi.subscription', run: () => calendarApi.subscription(), method: 'GET', path: '/api/users/me/calendar' },
  { name: 'calendarApi.regenerate', run: () => calendarApi.regenerate(), method: 'POST', path: '/api/users/me/calendar/regenerate' },

  { name: 'settingsApi.get', run: () => settingsApi.get(), method: 'GET', path: '/api/users/me/notification-settings' },
  {
    name: 'settingsApi.update',
    run: () => settingsApi.update({ emailEnabled: true, remindDays: [3, 1], sendHour: 9 }),
    method: 'PUT',
    path: '/api/users/me/notification-settings',
    body: { emailEnabled: true, remindDays: [3, 1], sendHour: 9 },
  },
];

describe('API 요청 정의', () => {
  beforeEach(() => vi.mocked(request).mockReset().mockResolvedValue(undefined));

  it.each(CALLS)('$name → $method $path', ({ run, method, path, body }) => {
    run();

    expect(request).toHaveBeenCalledOnce();
    const args = vi.mocked(request).mock.calls[0];
    expect(args[0]).toBe(method);
    expect(args[1]).toBe(path);
    if (body === undefined) expect(args).toHaveLength(2);
    else expect(args[2]).toEqual(body);
  });

  it('모든 API 함수가 위 표에 있다 (새 함수를 만들면 여기에도 추가)', () => {
    const defined = Object.entries({ authApi, postingApi, essayApi, templateApi, statsApi, interviewApi, calendarApi, settingsApi })
      .flatMap(([group, api]) => Object.keys(api).map((fn) => `${group}.${fn}`));
    const tested = new Set(CALLS.map((c) => c.name.split('(')[0]));

    expect(defined.filter((name) => !tested.has(name))).toEqual([]);
  });
});

// ── 백엔드 계약: 프론트가 부르는 경로가 실제 Spring 컨트롤러에 있는지 ──
// 테스트는 frontend/ 에서 실행된다(npm test). 저장소 루트의 백엔드 소스를 읽는다
const JAVA_ROOT = join(process.cwd(), '..', 'src', 'main', 'java');

function controllerFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) return controllerFiles(full);
    return name.endsWith('Controller.java') ? [full] : [];
  });
}

/** @RequestMapping(클래스) + @GetMapping 등(메서드) → "GET /api/postings/{id}" 목록 */
function backendEndpoints(): { method: string; path: string; pattern: RegExp }[] {
  return controllerFiles(JAVA_ROOT).flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const prefix = /@RequestMapping\("([^"]+)"\)\s*public class/.exec(source)?.[1] ?? '';
    return [...source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?/g)].map(([, verb, sub]) => {
      const path = prefix + (sub ?? '');
      const pattern = new RegExp(`^${path.replace(/[.]/g, '\\.').replace(/\{[^}]+\}/g, '[^/]+')}$`);
      return { method: verb.toUpperCase(), path, pattern };
    });
  });
}

describe.skipIf(!existsSync(JAVA_ROOT))('백엔드와의 계약', () => {
  const endpoints = existsSync(JAVA_ROOT) ? backendEndpoints() : [];

  it.each(CALLS)('$name 의 경로가 백엔드 컨트롤러에 있다', ({ method, path }) => {
    const bare = path.split('?')[0];

    expect(endpoints.some((e) => e.method === method && e.pattern.test(bare)), `${method} ${bare}`).toBe(true);
  });

  it('웹앱이 쓰지 않는 백엔드 API 는 확장 프로그램·캘린더 앱용으로 정해 둔 것뿐이다', () => {
    const used = CALLS.map((c) => ({ method: c.method, path: c.path.split('?')[0] }));
    const unused = endpoints
      .filter((e) => !used.some((u) => u.method === e.method && e.pattern.test(u.path)))
      .map((e) => `${e.method} ${e.path}`)
      .sort();

    expect(unused).toEqual([
      'GET /api/calendar/{token}.ics', // 캘린더 앱이 구독
      'GET /api/questions/{questionId}/answers/{version}', // 특정 버전 단건 조회 (웹앱은 목록으로 받음)
      'GET /api/templates/{id}', // 단건 조회 (웹앱은 목록으로 받음)
      'POST /api/auth/extension/login', // 확장 프로그램
      'POST /api/postings/parse-html', // 확장 프로그램
    ]);
  });
});
