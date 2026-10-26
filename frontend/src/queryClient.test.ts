import { describe, expect, it } from 'vitest';
import { ApiError } from './api/client';
import { createQueryClient, shouldRetry } from './queryClient';

describe('조회 재시도 정책', () => {
  it('4xx 는 다시 보내도 같으므로 재시도하지 않는다', () => {
    expect(shouldRetry(0, new ApiError(404, 'POSTING_NOT_FOUND', '공고를 찾을 수 없습니다.'))).toBe(false);
    expect(shouldRetry(0, new ApiError(403, 'FORBIDDEN', '접근 권한이 없습니다.'))).toBe(false);
    expect(shouldRetry(0, new ApiError(429, 'TOO_MANY_REQUESTS', '요청이 너무 많습니다.'))).toBe(false);
  });

  it('네트워크 오류·5xx 는 두 번까지 재시도한다', () => {
    const network = new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.');
    expect(shouldRetry(0, network)).toBe(true);
    expect(shouldRetry(1, new ApiError(503, 'UNKNOWN', '요청을 처리하지 못했습니다.'))).toBe(true);
    expect(shouldRetry(2, network)).toBe(false);
  });

  it('앱 전체 기본값으로 쓰인다', () => {
    const options = createQueryClient().getDefaultOptions().queries;

    expect(options?.retry).toBe(shouldRetry);
    expect(options?.refetchOnWindowFocus).toBe(false);
    expect(options?.staleTime).toBe(30_000);
  });
});
