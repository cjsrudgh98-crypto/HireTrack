import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { interviewApi, postingApi } from '../api';
import type { ApplicationStatus, Interview } from '../types';
import { InterviewPanel } from './InterviewPanel';
import { ToastProvider } from './Toast';

vi.mock('../api', () => ({
  interviewApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
  postingApi: { changeStatus: vi.fn() },
}));

const CREATED: Interview = {
  id: 3,
  postingId: 1,
  company: '모빌리티플러스',
  postingTitle: '플랫폼 엔지니어',
  postingStatus: 'APPLIED',
  type: 'FIRST',
  startsAt: '2026-11-02T14:00:00+09:00',
  endsAt: '2026-11-02T15:00:00+09:00',
  durationMinutes: 60,
  location: '판교 본사',
  memo: null,
};

function renderPanel(status: ApplicationStatus) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ToastProvider>
          <InterviewPanel postingId={1} postingStatus={status} />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

async function registerInterview(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: '+ 일정 추가' }));
  fireEvent.change(screen.getByLabelText(/일시/), { target: { value: '2026-11-02T14:00' } });
  await user.type(screen.getByLabelText('장소'), '판교 본사');
  await user.click(screen.getByRole('button', { name: '등록' }));
}

describe('면접 일정', () => {
  beforeEach(() => {
    vi.mocked(interviewApi.list).mockResolvedValue([]);
    vi.mocked(interviewApi.create).mockResolvedValue(CREATED);
  });

  afterEach(() => vi.restoreAllMocks());

  it('입력한 시각을 내 시간대 오프셋을 붙여 보낸다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = renderPanel('APPLIED');

    await registerInterview(user);

    await waitFor(() => expect(interviewApi.create).toHaveBeenCalledOnce());
    const [postingId, input] = vi.mocked(interviewApi.create).mock.calls[0];
    expect(postingId).toBe(1);
    expect(input).toMatchObject({ type: 'FIRST', durationMinutes: 60, location: '판교 본사' });
    expect(input.startsAt).toMatch(/^2026-11-02T14:00:00[+-]\d{2}:\d{2}$/);
  });

  it('지원 완료 단계에서 일정을 등록하면 "면접 진행"으로 옮길지 묻고, 동의하면 상태를 바꾼다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(postingApi.changeStatus).mockResolvedValue({} as never);
    const user = renderPanel('APPLIED');

    await registerInterview(user);

    await waitFor(() => expect(postingApi.changeStatus).toHaveBeenCalledWith(1, 'INTERVIEW'));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("'면접 진행'으로 바꿀까요?"));
  });

  it('이미 면접 단계면 묻지 않는다', async () => {
    const confirm = vi.spyOn(window, 'confirm');
    const user = renderPanel('INTERVIEW');

    await registerInterview(user);

    await waitFor(() => expect(interviewApi.create).toHaveBeenCalledOnce());
    expect(confirm).not.toHaveBeenCalled();
    expect(postingApi.changeStatus).not.toHaveBeenCalled();
  });
});
