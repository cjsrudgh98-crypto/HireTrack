import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request, setUnauthorizedHandler, tokenStorage } from './client';

const json = (status: number, body: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

describe('API 클라이언트', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const onUnauthorized = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    setUnauthorizedHandler(onUnauthorized);
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    onUnauthorized.mockReset();
  });

  it('저장된 토큰을 Bearer 헤더로, 본문은 JSON 으로 보낸다', async () => {
    tokenStorage.set('abc');
    fetchMock.mockResolvedValue(json(200, { ok: true }));

    await request('POST', '/api/postings', { company: 'A' });

    const [, init] = fetchMock.mock.calls[0];
    expect(init?.headers).toEqual({ Authorization: 'Bearer abc', 'Content-Type': 'application/json' });
    expect(init?.body).toBe('{"company":"A"}');
  });

  it('204 응답은 본문을 읽지 않고 undefined 를 돌려준다', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(request('DELETE', '/api/postings/1')).resolves.toBeUndefined();
  });

  it('에러 응답은 서버의 code·message 를 담은 ApiError 로 바꾼다', async () => {
    fetchMock.mockResolvedValue(json(429, { code: 'LOGIN_LOCKED', message: '로그인에 여러 번 실패해 잠시 잠겼어요.' }));

    const error = await request('POST', '/api/auth/login', {}).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 429, code: 'LOGIN_LOCKED', message: '로그인에 여러 번 실패해 잠시 잠겼어요.' });
  });

  it('JSON 이 아닌 에러 응답도 기본 메시지로 처리한다', async () => {
    fetchMock.mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }));

    await expect(request('GET', '/api/postings')).rejects.toMatchObject({ status: 502, code: 'UNKNOWN' });
  });

  it('로그인한 상태에서 401 이 오면(토큰 만료) 로그아웃 처리를 부른다', async () => {
    tokenStorage.set('expired');
    fetchMock.mockResolvedValue(json(401, { code: 'UNAUTHORIZED', message: '로그인이 필요합니다.' }));

    await expect(request('GET', '/api/postings')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('로그인 전 401(비밀번호 틀림)은 로그아웃 처리를 부르지 않는다', async () => {
    fetchMock.mockResolvedValue(json(401, { code: 'INVALID_CREDENTIALS', message: '이메일 또는 비밀번호가 올바르지 않습니다.' }));

    await expect(request('POST', '/api/auth/login', {})).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('서버에 닿지 못하면 NETWORK_ERROR 로 알려준다', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(request('GET', '/api/postings')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});
