import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { postingApi } from '../api';
import type { ApplicationStatus, PostingDetail, PostingFilters, PostingInput, PostingSummary } from '../types';

export const postingKeys = {
  lists: ['postings'] as const,
  list: (filters: PostingFilters) => ['postings', filters] as const,
  detail: (id: number) => ['posting', id] as const,
  history: (id: number) => ['posting', id, 'history'] as const,
};

export function usePostings(filters: PostingFilters) {
  return useQuery({
    queryKey: postingKeys.list(filters),
    queryFn: () => postingApi.list(filters),
    placeholderData: keepPreviousData,
  });
}

export function usePosting(id: number) {
  return useQuery({ queryKey: postingKeys.detail(id), queryFn: () => postingApi.get(id) });
}

export function useHistory(id: number) {
  return useQuery({ queryKey: postingKeys.history(id), queryFn: () => postingApi.history(id) });
}

function applyStatus<T extends PostingSummary>(posting: T, status: ApplicationStatus): T {
  const appliedAt =
    status === 'APPLIED' && !posting.appliedAt ? new Date().toISOString() : posting.appliedAt;
  return { ...posting, status, appliedAt };
}

/**
 * 상태 변경 — 낙관적 업데이트.
 * 화면(목록·상세 캐시)을 먼저 바꾸고, API 가 실패하면 이전 스냅샷으로 되돌린다.
 */
export function useChangeStatus(onError?: (error: Error) => void) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: ApplicationStatus }) =>
      postingApi.changeStatus(id, status),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey: postingKeys.lists });
      await queryClient.cancelQueries({ queryKey: postingKeys.detail(id), exact: true });

      const previousLists = queryClient.getQueriesData<PostingSummary[]>({ queryKey: postingKeys.lists });
      const previousDetail = queryClient.getQueryData<PostingDetail>(postingKeys.detail(id));

      queryClient.setQueriesData<PostingSummary[]>({ queryKey: postingKeys.lists }, (old) =>
        old?.map((p) => (p.id === id ? applyStatus(p, status) : p)),
      );
      if (previousDetail) {
        queryClient.setQueryData(postingKeys.detail(id), applyStatus(previousDetail, status));
      }
      return { previousLists, previousDetail };
    },
    onError: (error, { id }, context) => {
      context?.previousLists.forEach(([key, data]) => queryClient.setQueryData(key, data));
      if (context?.previousDetail) queryClient.setQueryData(postingKeys.detail(id), context.previousDetail);
      onError?.(error);
    },
    onSettled: (_data, _error, { id }) => {
      queryClient.invalidateQueries({ queryKey: postingKeys.lists });
      queryClient.invalidateQueries({ queryKey: ['posting', id] });
      // 첫 지원이면 서버에서 자소서 제출본이 고정되고, 통계도 바뀐다
      queryClient.invalidateQueries({ queryKey: ['essay', id] });
      queryClient.invalidateQueries({ queryKey: ['stats'] });
    },
  });
}

export function useSavePosting() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: PostingInput }) =>
      id ? postingApi.update(id, input) : postingApi.create(input),
    onSuccess: (saved) => {
      queryClient.setQueryData(postingKeys.detail(saved.id), saved);
      queryClient.invalidateQueries({ queryKey: postingKeys.lists });
    },
  });
}

export function useDeletePosting() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => postingApi.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: ['posting', id] });
      queryClient.invalidateQueries({ queryKey: postingKeys.lists });
    },
  });
}

export function useUpdateMemo(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memo: string) => postingApi.updateMemo(id, memo),
    onSuccess: (saved) => queryClient.setQueryData(postingKeys.detail(id), saved),
  });
}
