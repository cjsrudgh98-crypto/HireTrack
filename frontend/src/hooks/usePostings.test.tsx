import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { postingApi } from '../api';
import type { PostingFilters, PostingSummary } from '../types';
import { postingKeys, useChangeStatus } from './usePostings';

vi.mock('../api', () => ({
  postingApi: { changeStatus: vi.fn() },
}));

const FILTERS: PostingFilters = { keyword: '', employmentType: '', location: '' };
const CARD: PostingSummary = {
  id: 1,
  company: '○○테크',
  title: '백엔드',
  location: null,
  employmentType: 'FULL_TIME',
  deadline: null,
  status: 'INTERESTED',
  appliedAt: null,
  createdAt: '2026-10-20T10:00:00+09:00',
};

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  queryClient.setQueryData(postingKeys.list(FILTERS), [CARD]);
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const onError = vi.fn();
  const { result } = renderHook(() => useChangeStatus(onError), { wrapper });
  const cached = () => queryClient.getQueryData<PostingSummary[]>(postingKeys.list(FILTERS))![0];
  return { result, onError, cached };
}

describe('칸반 상태 변경 — 낙관적 업데이트', () => {
  // 각 테스트가 mock 동작을 직접 지정한다 (Vitest 5 에서 rejected promise 를 돌려준 mock 을 mockReset 하면
  // 테스트가 끝난 뒤 원래 에러가 다시 보고되는 문제가 있어 beforeEach 초기화는 쓰지 않음)

  it('드래그하면 API 응답 전에 카드가 먼저 옮겨지고, 첫 지원이면 지원일도 바로 보인다', async () => {
    let resolve!: () => void;
    vi.mocked(postingApi.changeStatus).mockReturnValue(new Promise((r) => (resolve = () => r({} as never))));
    const { result, cached } = setup();

    act(() => result.current.mutate({ id: 1, status: 'APPLIED' }));

    await waitFor(() => expect(cached().status).toBe('APPLIED'));
    expect(cached().appliedAt).not.toBeNull();
    resolve();
  });

  it('API 가 실패하면 원래 위치로 되돌리고 오류를 알린다', async () => {
    vi.mocked(postingApi.changeStatus).mockRejectedValue(new Error('서버 오류'));
    const { result, onError, cached } = setup();

    act(() => result.current.mutate({ id: 1, status: 'DOCUMENT_PASSED' }));

    await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onError.mock.calls[0][0]).toHaveProperty('message', '서버 오류');
    expect(cached().status).toBe('INTERESTED');
    expect(cached().appliedAt).toBeNull();
  });
});
