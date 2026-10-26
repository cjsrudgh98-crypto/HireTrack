import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './api/client';

/** 4xx(권한·없는 공고·잘못된 입력)는 다시 보내도 결과가 같으므로 바로 실패, 네트워크·5xx 는 두 번까지 재시도 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  const clientError = error instanceof ApiError && error.status >= 400 && error.status < 500;
  return !clientError && failureCount < 2;
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: shouldRetry,
      },
    },
  });
}
