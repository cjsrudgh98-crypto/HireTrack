import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { essayApi, interviewApi, postingApi } from '../api';
import { ApiError } from '../api/client';
import { ToastProvider } from '../components/Toast';
import type { PostingDetail } from '../types';
import { PostingDetailPage } from './PostingDetailPage';

vi.mock('../api', () => ({
  postingApi: {
    get: vi.fn(), history: vi.fn(), changeStatus: vi.fn(), updateMemo: vi.fn(), remove: vi.fn(),
    list: vi.fn(), update: vi.fn(), create: vi.fn(), parse: vi.fn(),
  },
  interviewApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
  essayApi: { questions: vi.fn(), versions: vi.fn(), search: vi.fn(), saveDraft: vi.fn() },
  templateApi: { list: vi.fn() },
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@hiretrack.dev' }, logout: vi.fn() }) }));

const POSTING: PostingDetail = {
  id: 7,
  company: '클라우드웍스',
  title: '백엔드 개발자',
  location: '서울 성동구',
  employmentType: 'FULL_TIME',
  deadline: '2026-11-04',
  status: 'INTERESTED',
  appliedAt: null,
  url: 'https://example.com/jobs/7',
  requirements: 'Java/Spring 기반 API 개발 경험',
  memo: '커피챗 예정',
  createdAt: '2026-10-20T01:00:00Z',
  updatedAt: '2026-10-21T01:00:00Z',
};

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderDetail(path = '/postings/7') {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <ToastProvider>
          <Routes>
            <Route path="/" element={<p>보드 화면</p>} />
            <Route path="/postings/:id" element={<PostingDetailPage />} />
          </Routes>
          <Where />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('공고 상세', () => {
  beforeEach(() => {
    vi.mocked(postingApi.get).mockResolvedValue(POSTING);
    vi.mocked(interviewApi.list).mockResolvedValue([]);
    vi.mocked(postingApi.history).mockResolvedValue([
      { id: 1, fromStatus: null, toStatus: 'INTERESTED', changedAt: '2026-10-20T01:00:00Z', reverted: false },
      { id: 2, fromStatus: 'INTERESTED', toStatus: 'APPLIED', changedAt: '2026-10-21T01:00:00Z', reverted: true },
      { id: 3, fromStatus: 'APPLIED', toStatus: 'INTERESTED', changedAt: '2026-10-21T01:03:00Z', reverted: true },
    ]);
    vi.mocked(essayApi.questions).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('정보 탭에 근무지·고용형태·마감일·자격요건과 원본 공고 링크를 보여준다', async () => {
    renderDetail();

    expect(await screen.findByRole('heading', { name: '백엔드 개발자' })).toBeInTheDocument();
    expect(screen.getByText('서울 성동구')).toBeInTheDocument();
    expect(screen.getByText('정규직')).toBeInTheDocument();
    expect(screen.getByText('Java/Spring 기반 API 개발 경험')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /원본 공고 보기/ })).toHaveAttribute('href', 'https://example.com/jobs/7');
    expect(screen.getByLabelText('지원 상태')).toHaveValue('INTERESTED');
  });

  it('탭을 바꾸면 주소에 남기고, 이력 탭은 되돌린 변경을 따로 표시한다', async () => {
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('tab', { name: '이력' }));

    expect(screen.getByTestId('where')).toHaveTextContent('/postings/7?tab=history');
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('공고 등록 · 관심');
    expect(items[1]).toHaveClass('timeline-reverted');
    expect(items[1]).toHaveTextContent('관심 → 지원 완료 · 되돌림');
    expect(items[0]).not.toHaveClass('timeline-reverted');
  });

  it('주소에 tab=essay 가 있으면 자기소개서 탭으로 바로 연다 (면접 일정의 "제출한 자소서 보기" 링크)', async () => {
    renderDetail('/postings/7?tab=essay');

    expect(await screen.findByText('문항을 추가하면 여기서 답변을 쓰고 버전을 관리할 수 있어요.')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '자기소개서' })).toHaveAttribute('aria-selected', 'true');
  });

  it('상태를 고르면 서버에 바꾸라고 보낸다', async () => {
    vi.mocked(postingApi.changeStatus).mockResolvedValue({ ...POSTING, status: 'APPLIED', appliedAt: '2026-10-26T01:00:00Z' });
    const user = userEvent.setup();
    renderDetail();

    await user.selectOptions(await screen.findByLabelText('지원 상태'), 'APPLIED');

    await waitFor(() => expect(postingApi.changeStatus).toHaveBeenCalledWith(7, 'APPLIED'));
  });

  it('메모는 입력이 0.8초 멈추면 자동 저장한다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(postingApi.updateMemo).mockResolvedValue({ ...POSTING, memo: '커피챗 예정 · 질문 준비' });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderDetail();

    const memo = await screen.findByDisplayValue('커피챗 예정');
    await user.type(memo, ' · 질문 준비');
    expect(screen.getByText('입력 중…')).toBeInTheDocument();
    expect(postingApi.updateMemo).not.toHaveBeenCalled();

    await act(() => vi.advanceTimersByTimeAsync(800));

    expect(postingApi.updateMemo).toHaveBeenCalledOnce();
    expect(postingApi.updateMemo).toHaveBeenCalledWith(7, '커피챗 예정 · 질문 준비');
    expect(await screen.findByText('저장됨')).toBeInTheDocument();
  });

  it('삭제는 한 번 더 확인하고, 지운 뒤 보드로 돌아간다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(postingApi.remove).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: '삭제' }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("'클라우드웍스 · 백엔드 개발자' 공고를 삭제할까요?"));
    expect(await screen.findByText('보드 화면')).toBeInTheDocument();
    expect(postingApi.remove).toHaveBeenCalledWith(7);
  });

  it('삭제 확인에서 취소하면 지우지 않는다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: '삭제' }));

    expect(postingApi.remove).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: '백엔드 개발자' })).toBeInTheDocument();
  });

  it('수정을 누르면 지금 값이 채워진 수정 창을 연다', async () => {
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: '수정' }));

    const dialog = screen.getByRole('heading', { name: '공고 수정' }).closest('form') as HTMLElement;
    expect(within(dialog).getByDisplayValue('클라우드웍스')).toBeInTheDocument();
  });

  it('없는 공고면 이유와 함께 보드로 돌아가는 링크를 보여준다', async () => {
    vi.mocked(postingApi.get).mockRejectedValue(new ApiError(404, 'POSTING_NOT_FOUND', '공고를 찾을 수 없습니다.'));
    renderDetail('/postings/999');

    expect(await screen.findByText(/공고를 찾을 수 없습니다/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '보드로 돌아가기' })).toHaveAttribute('href', '/');
  });
});
