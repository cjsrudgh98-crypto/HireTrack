import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { statsApi } from '../api';
import { ApiError } from '../api/client';
import type { BreakdownRow, ResponseTime, StatsSummary } from '../types';
import { DashboardPage } from './DashboardPage';

vi.mock('../api', () => ({
  statsApi: { summary: vi.fn(), funnel: vi.fn(), weekly: vi.fn(), responseTime: vi.fn(), breakdown: vi.fn() },
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@hiretrack.dev' }, logout: vi.fn() }) }));

const ratio = (numerator: number, denominator: number) => ({
  numerator,
  denominator,
  rate: denominator === 0 ? null : numerator / denominator,
});

const SUMMARY: StatsSummary = {
  totalPostings: 13,
  totalApplied: 9,
  documentPass: { decided: ratio(6, 7), overall: ratio(6, 9), pending: 2 },
  interview: { decided: ratio(0, 0), overall: ratio(0, 6), pending: 4 },
  finalAccept: { decided: ratio(1, 2), overall: ratio(1, 4), pending: 2 },
  acceptedCount: 1,
};

const RESPONSE: ResponseTime = {
  count: 7,
  averageDays: 8.3,
  medianDays: 7,
  waiting: 2,
  buckets: [
    { label: '3일 이내', count: 2 },
    { label: '4~7일', count: 2 },
  ],
};

const row = (key: string, label: string, extra: Partial<BreakdownRow> = {}): BreakdownRow => ({
  key,
  label,
  applied: 8,
  passed: 5,
  failed: 1,
  pending: 2,
  decided: ratio(5, 6),
  overall: ratio(5, 8),
  ...extra,
});

function renderDashboard() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

// 같은 단계 이름("최종 합격")이 퍼널에도 있어서 요약 타일의 라벨만 찾는다
const tile = (label: string) =>
  screen.getAllByText(label).find((el) => el.classList.contains('stat-label'))!.closest('.stat-tile') as HTMLElement;
const card = (title: string) => screen.getByRole('heading', { name: title }).closest('.chart-card') as HTMLElement;

describe('지원 통계 화면', () => {
  beforeAll(() => {
    // 차트 컨테이너가 크기 변화를 감시할 때 쓰는 브라우저 API (jsdom 에는 없다)
    globalThis.ResizeObserver ??= class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  beforeEach(() => {
    vi.mocked(statsApi.summary).mockResolvedValue(SUMMARY);
    vi.mocked(statsApi.funnel).mockResolvedValue([
      { stage: 'APPLIED', label: '지원', count: 9, conversion: null },
      { stage: 'DOCUMENT_PASSED', label: '서류 합격', count: 6, conversion: ratio(6, 9) },
      { stage: 'ACCEPTED', label: '최종 합격', count: 0, conversion: ratio(0, 4) },
    ]);
    vi.mocked(statsApi.weekly).mockResolvedValue([{ weekStart: '2026-10-19', count: 2 }]);
    vi.mocked(statsApi.responseTime).mockResolvedValue(RESPONSE);
    vi.mocked(statsApi.breakdown).mockImplementation(async (by) => ({
      by,
      rows: by === 'location' ? [row('seoul', '서울'), row('busan', '부산', { decided: ratio(0, 0), pending: 2 })] : [row('FULL_TIME', '정규직')],
    }));
  });

  it('통과율은 결과가 나온 건 기준 %와 건수, 전체 기준, 대기 건수를 함께 보여준다', async () => {
    renderDashboard();

    expect(await screen.findByText('관심 포함 전체 13건')).toBeInTheDocument();
    expect(within(tile('총 지원')).getByText('9건')).toBeInTheDocument();
    const documentPass = tile('서류 통과율');
    expect(within(documentPass).getByText('86%')).toBeInTheDocument();
    expect(documentPass).toHaveTextContent('결과 나온 7건 중 6건');
    expect(documentPass).toHaveTextContent('전체 기준 67% (6/9) · 대기 2건');
    expect(tile('최종 합격')).toHaveTextContent('면접 후 합격률 50% (1/2) · 대기 2건');
  });

  it('결과가 하나도 없는 단계는 0% 가 아니라 — 로 보여준다', async () => {
    renderDashboard();

    await screen.findByText('관심 포함 전체 13건');
    expect(within(tile('면접 전환율')).getByText('—')).toBeInTheDocument();
    expect(tile('면접 전환율')).toHaveTextContent('결과 나온 0건 중 0건');
  });

  it('퍼널은 0건 단계도 라벨과 함께 보여주고, 직전 단계 대비 비율을 붙인다', async () => {
    renderDashboard();

    const funnel = await waitFor(() => card('지원 퍼널'));
    expect(await within(funnel).findByText('6건 · 67% (6/9)')).toBeInTheDocument();
    expect(within(funnel).getByText('0건 · 0% (0/4)')).toBeInTheDocument();
    expect(within(funnel).getAllByText('최종 합격').length).toBeGreaterThan(0);
  });

  it('응답 기간은 평균·중앙값·대기 건수를 요약하고, 결과가 없으면 안내 문구를 보여준다', async () => {
    renderDashboard();
    expect(await screen.findByText(/응답 대기 2건/)).toBeInTheDocument();
    expect(card('응답까지 걸린 기간')).toHaveTextContent('평균 8.3일 · 중앙값 7일 (7건)');
  });

  it('응답한 지원이 없으면 그렇게 알려준다', async () => {
    vi.mocked(statsApi.responseTime).mockResolvedValue({ count: 0, averageDays: null, medianDays: null, waiting: 3, buckets: [] });
    renderDashboard();

    expect(await screen.findByText('아직 결과가 나온 지원이 없어요.')).toBeInTheDocument();
  });

  it('조건을 바꾸면 그 기준으로 다시 불러오고, 결과가 없는 조건은 대기 건수만 보여준다', async () => {
    const user = renderDashboard();
    expect(await screen.findByText('83% (5/6) · 대기 2')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('구분 기준'), 'location');

    expect(statsApi.breakdown).toHaveBeenLastCalledWith('location');
    const breakdown = card('조건별 서류 통과율');
    expect(await within(breakdown).findByText('— · 대기 2건')).toBeInTheDocument();
    expect(within(breakdown).getAllByText('서울').length).toBeGreaterThan(0);
  });

  it('지원한 공고가 없으면 조건별 통과율 대신 안내를 보여준다', async () => {
    vi.mocked(statsApi.breakdown).mockResolvedValue({ by: 'employmentType', rows: [] });
    renderDashboard();

    expect(await screen.findByText('지원한 공고가 생기면 보여드릴게요.')).toBeInTheDocument();
  });

  it('통계를 불러오지 못하면 이유를 보여준다', async () => {
    vi.mocked(statsApi.summary).mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', '서버 오류가 발생했습니다.'));
    renderDashboard();

    expect(await screen.findByText('통계를 불러오지 못했습니다: 서버 오류가 발생했습니다.')).toBeInTheDocument();
  });
});
