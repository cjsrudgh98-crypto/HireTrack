import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { essayApi, templateApi } from '../../api';
import { ApiError } from '../../api/client';
import type { EssayAnswer, EssayQuestion } from '../../types';
import { ToastProvider } from '../Toast';
import { EssayWorkspace } from './EssayWorkspace';

vi.mock('../../api', () => ({
  essayApi: {
    questions: vi.fn(),
    versions: vi.fn(),
    saveDraft: vi.fn(),
    saveVersion: vi.fn(),
    submit: vi.fn(),
    deleteQuestion: vi.fn(),
    addQuestion: vi.fn(),
    updateQuestion: vi.fn(),
    search: vi.fn(),
  },
  templateApi: { list: vi.fn(), create: vi.fn() },
}));

const answer = (version: number, content: string, extra: Partial<EssayAnswer> = {}): EssayAnswer => ({
  id: version,
  questionId: 10,
  version,
  content,
  charCount: [...content].length,
  submitted: false,
  sourceTemplateId: null,
  createdAt: '2026-10-26T01:00:00Z',
  ...extra,
});

const question = (extra: Partial<EssayQuestion> = {}): EssayQuestion => ({
  id: 10,
  question: '지원 동기를 작성해 주세요.',
  maxLength: 500,
  category: 'MOTIVATION',
  displayOrder: 1,
  latestAnswer: answer(1, '첫 버전'),
  submittedVersion: null,
  draft: null,
  locked: false,
  ...extra,
});

function renderWorkspace(q: EssayQuestion, versions: EssayAnswer[] = q.latestAnswer ? [q.latestAnswer] : []) {
  vi.mocked(essayApi.questions).mockResolvedValue([q]);
  vi.mocked(essayApi.versions).mockResolvedValue(versions);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <EssayWorkspace postingId={1} applied={false} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

const editor = () => screen.findByLabelText('답변');

describe('자기소개서 편집기', () => {
  beforeEach(() => {
    vi.mocked(essayApi.search).mockResolvedValue([]);
    vi.mocked(templateApi.list).mockResolvedValue([]);
    vi.mocked(essayApi.saveDraft).mockResolvedValue({ content: '', updatedAt: '2026-10-26T01:00:00Z' });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  describe('자동 임시저장', () => {
    beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));

    it('임시저장이 있으면 그 내용으로 시작하고, 입력이 1초 멈췄을 때 한 번만 보낸다', async () => {
      renderWorkspace(question({ draft: { content: '쓰던 내용', updatedAt: '2026-10-26T01:00:00Z' } }));
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      expect(await editor()).toHaveValue('쓰던 내용');
      await user.type(await editor(), ' 이어서');
      expect(screen.getByText('입력 중…')).toBeInTheDocument();
      expect(essayApi.saveDraft).not.toHaveBeenCalled(); // 글자마다 보내지 않는다

      await act(() => vi.advanceTimersByTimeAsync(1000));

      expect(essayApi.saveDraft).toHaveBeenCalledOnce();
      expect(essayApi.saveDraft).toHaveBeenCalledWith(10, '쓰던 내용 이어서');
      expect(await screen.findByText('임시저장됨')).toBeInTheDocument();
    });

    it('임시저장이 실패하면 실패했다고 보여준다', async () => {
      vi.mocked(essayApi.saveDraft).mockRejectedValue(new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.'));
      renderWorkspace(question());
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      await user.type(await editor(), '!');
      await act(() => vi.advanceTimersByTimeAsync(1000));

      expect(await screen.findByText('임시저장 실패')).toBeInTheDocument();
    });
  });

  describe('버전 저장', () => {
    it('보고 있던 최신 버전 번호를 함께 보내 새 버전으로 저장한다', async () => {
      vi.mocked(essayApi.saveVersion).mockResolvedValue(answer(2, '첫 버전 고침'));
      renderWorkspace(question());
      const user = userEvent.setup();

      await user.type(await editor(), ' 고침');
      await user.click(screen.getByRole('button', { name: '버전 저장 (v2)' }));

      await waitFor(() => expect(essayApi.saveVersion).toHaveBeenCalledWith(10, '첫 버전 고침', 1, null));
      expect(await screen.findByText('v2 으로 저장했어요.')).toBeInTheDocument();
    });

    it('최신 버전과 내용이 같으면 저장 버튼이 꺼져 있다', async () => {
      renderWorkspace(question());

      await editor();
      expect(screen.getByRole('button', { name: '버전 저장 (v2)' })).toBeDisabled();
    });

    it('다른 탭에서 먼저 저장해 409 가 오면 쓰던 내용을 지우지 않고 안내한다', async () => {
      vi.mocked(essayApi.saveVersion).mockRejectedValue(
        new ApiError(409, 'VERSION_CONFLICT', '다른 곳에서 먼저 새 버전이 저장됐습니다.'),
      );
      renderWorkspace(question());
      const user = userEvent.setup();

      await user.type(await editor(), ' 내가 쓴 부분');
      await user.click(screen.getByRole('button', { name: '버전 저장 (v2)' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('다른 탭에서 먼저 새 버전');
      expect(await editor()).toHaveValue('첫 버전 내가 쓴 부분');
      expect(essayApi.questions).toHaveBeenCalledTimes(2); // 최신 버전 번호를 다시 받아온다
    });
  });

  it('제출본으로 고정된 문항은 읽기 전용이고 저장·임시저장을 하지 않는다', async () => {
    const submitted = answer(2, '제출한 답변', { submitted: true });
    renderWorkspace(question({ locked: true, submittedVersion: 2, latestAnswer: submitted }), [submitted, answer(1, '초안')]);
    const user = userEvent.setup();

    expect(await editor()).toHaveAttribute('readonly');
    expect(screen.getByText(/제출본 v2 — 지원할 때 낸 내용이라 수정할 수 없어요/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /버전 저장/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();

    await user.type(await editor(), '바꾸기');
    expect(await editor()).toHaveValue('제출한 답변');
    expect(essayApi.saveDraft).not.toHaveBeenCalled();
  });

  it('글자 수 제한을 넘으면 몇 자 초과인지 보여준다', async () => {
    renderWorkspace(question({ maxLength: 5, latestAnswer: null }), []);
    const user = userEvent.setup();

    await user.type(await editor(), '여덟 글자 답변');

    expect(screen.getByText('⚠ 3자 초과')).toBeInTheDocument();
    expect(await editor()).toHaveClass('over');
  });

  it('템플릿을 가져오면 내용을 바꾸고, 저장할 때 출처 템플릿을 함께 보낸다', async () => {
    vi.mocked(templateApi.list).mockResolvedValue([
      { id: 7, category: 'MOTIVATION', title: '지원 동기 · 트래픽', content: '템플릿 문장', createdAt: '', updatedAt: '' },
    ]);
    vi.mocked(essayApi.saveVersion).mockResolvedValue(answer(2, '템플릿 문장', { sourceTemplateId: 7 }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWorkspace(question());
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: '가져오기' }));

    expect(window.confirm).toHaveBeenCalled(); // 쓰던 내용이 있으면 바꾸기 전에 묻는다
    expect(await editor()).toHaveValue('템플릿 문장');
    expect(screen.getByText('템플릿 기반')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '버전 저장 (v2)' }));
    await waitFor(() => expect(essayApi.saveVersion).toHaveBeenCalledWith(10, '템플릿 문장', 1, 7));
  });
});
