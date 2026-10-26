import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { interviewApi, postingApi } from '../api';
import { ApiError } from '../api/client';
import { ToastProvider } from '../components/Toast';
import type { Interview, PostingSummary } from '../types';
import { BoardPage } from './BoardPage';

vi.mock('../api', () => ({
  postingApi: { list: vi.fn(), changeStatus: vi.fn(), parse: vi.fn(), create: vi.fn(), update: vi.fn() },
  interviewApi: { upcoming: vi.fn() },
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@hiretrack.dev' }, logout: vi.fn() }) }));

const posting = (id: number, company: string, status: PostingSummary['status'], extra: Partial<PostingSummary> = {}): PostingSummary => ({
  id,
  company,
  title: '백엔드 개발자',
  location: '서울 강남구',
  employmentType: 'FULL_TIME',
  deadline: null,
  status,
  appliedAt: status === 'INTERESTED' ? null : '2026-10-20T01:00:00Z',
  createdAt: '2026-10-19T01:00:00Z',
  ...extra,
});

const POSTINGS = [
  posting(1, '데이터랩스', 'INTERESTED'),
  posting(2, '클라우드웍스', 'INTERESTED'),
  posting(3, '핀테크원', 'APPLIED'),
  posting(4, '페이먼츠코리아', 'ACCEPTED'),
  posting(5, '게임스튜디오', 'REJECTED'),
  posting(6, '소셜앱', 'REJECTED'),
];

const interview = (id: number, company: string, daysLater: number): Interview => {
  const d = new Date();
  d.setDate(d.getDate() + daysLater);
  d.setHours(14, 0, 0, 0);
  return {
    id, postingId: id, company, postingTitle: '백엔드 개발자', postingStatus: 'INTERVIEW', type: 'FIRST',
    startsAt: d.toISOString(), endsAt: d.toISOString(), durationMinutes: 60, location: null, memo: null,
  };
};

function renderBoard() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/']}>
        <ToastProvider>
          <Routes>
            <Route path="/" element={<BoardPage />} />
            <Route path="/postings/:id" element={<p>상세 화면</p>} />
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

const column = (title: string) =>
  screen.getAllByRole('heading', { level: 2 }).find((h) => h.textContent?.startsWith(title))!.closest('section') as HTMLElement;

describe('칸반 보드', () => {
  beforeEach(() => {
    vi.mocked(postingApi.list).mockResolvedValue(POSTINGS);
    vi.mocked(interviewApi.upcoming).mockResolvedValue([]);
  });

  afterEach(() => vi.clearAllMocks());

  it('상태별 컬럼에 카드를 나누고, 결과 컬럼은 최종 합격·불합격으로 다시 나눈다', async () => {
    renderBoard();

    expect(await screen.findByText('총 6건')).toBeInTheDocument();
    expect(within(column('관심')).getAllByText(/데이터랩스|클라우드웍스/)).toHaveLength(2);
    expect(within(column('지원 완료')).getByText('핀테크원')).toBeInTheDocument();
    const result = column('결과');
    expect(within(result).getByRole('heading')).toHaveTextContent('결과 3');
    expect(result.querySelector('.status-ACCEPTED')).toHaveTextContent('페이먼츠코리아');
    expect(result.querySelector('.status-REJECTED')).toHaveTextContent('불합격 2');
    expect(within(column('면접')).getByText('여기로 끌어다 놓기')).toBeInTheDocument();
  });

  it('검색어는 입력이 멈춘 뒤 한 번만 서버에 보낸다', async () => {
    const user = renderBoard();
    await screen.findByText('총 6건');

    await user.type(screen.getByLabelText('회사명·직무 검색'), '데이터');

    await waitFor(() => expect(postingApi.list).toHaveBeenLastCalledWith({ keyword: '데이터', employmentType: '', location: '' }));
    const keywords = vi.mocked(postingApi.list).mock.calls.map(([f]) => f.keyword);
    expect(keywords).not.toContain('데'); // 글자마다 요청하지 않는다
  });

  it('필터를 고르면 그 조건으로 다시 불러오고, 초기화하면 전체로 돌아간다', async () => {
    const user = renderBoard();
    await screen.findByText('총 6건');

    await user.selectOptions(screen.getByLabelText('고용형태'), '프리랜서');
    await waitFor(() => expect(postingApi.list).toHaveBeenLastCalledWith({ keyword: '', employmentType: 'FREELANCE', location: '' }));

    await user.click(screen.getByRole('button', { name: '필터 초기화' }));
    expect(screen.getByLabelText('고용형태')).toHaveValue('');
    expect(screen.queryByRole('button', { name: '필터 초기화' })).not.toBeInTheDocument();
  });

  it('공고가 하나도 없으면 첫 공고 등록을 안내하고, 누르면 등록 창을 연다', async () => {
    vi.mocked(postingApi.list).mockResolvedValue([]);
    const user = renderBoard();

    await user.click(await screen.findByRole('button', { name: '첫 공고를 등록' }));

    expect(screen.getByRole('heading', { name: '공고 등록' })).toBeInTheDocument();
  });

  it('필터 결과가 없을 때는 첫 공고 안내를 띄우지 않는다', async () => {
    const user = renderBoard();
    await screen.findByText('총 6건');
    vi.mocked(postingApi.list).mockResolvedValue([]);

    await user.selectOptions(screen.getByLabelText('지역'), '부산');

    expect(await screen.findByText('총 0건')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '첫 공고를 등록' })).not.toBeInTheDocument();
  });

  it('다가오는 면접은 최대 4개까지, 하루 안이면 급함 표시로 보여준다', async () => {
    vi.mocked(interviewApi.upcoming).mockResolvedValue([
      interview(11, '모빌리티플러스', 0), interview(12, '에듀테크랩', 3), interview(13, 'A사', 5),
      interview(14, 'B사', 6), interview(15, 'C사', 7),
    ]);
    renderBoard();

    const upcoming = await screen.findByLabelText('다가오는 면접');
    expect(within(upcoming).getAllByRole('link')).toHaveLength(4);
    expect(within(upcoming).getByText('오늘')).toHaveClass('badge-urgent');
    expect(within(upcoming).getByText('D-3')).not.toHaveClass('badge-urgent');
    expect(within(upcoming).queryByText('C사')).not.toBeInTheDocument();
  });

  it('카드를 누르면 공고 상세로 이동한다', async () => {
    const user = renderBoard();

    await user.click(await screen.findByText('핀테크원'));

    expect(await screen.findByText('상세 화면')).toBeInTheDocument();
  });

  it('목록을 불러오지 못하면 이유를 보여준다', async () => {
    vi.mocked(postingApi.list).mockRejectedValue(new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.'));
    renderBoard();

    expect(await screen.findByText('목록을 불러오지 못했습니다: 서버에 연결할 수 없습니다.')).toBeInTheDocument();
  });
});
