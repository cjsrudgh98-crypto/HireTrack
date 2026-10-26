import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { calendarApi, settingsApi } from '../api';
import { ApiError } from '../api/client';
import { ToastProvider } from '../components/Toast';
import type { NotificationSettings } from '../types';
import { SettingsPage } from './SettingsPage';

vi.mock('../api', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
  calendarApi: { subscription: vi.fn(), regenerate: vi.fn() },
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@hiretrack.dev' }, logout: vi.fn() }) }));

const SETTINGS: NotificationSettings = { emailEnabled: true, remindDays: [3, 1], sendHour: 9, email: 'demo@hiretrack.dev' };
const OLD_URL = 'http://localhost:5173/api/calendar/old-token.ics';
const NEW_URL = 'http://localhost:5173/api/calendar/new-token.ics';

function renderSettings() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MemoryRouter>
        <ToastProvider>
          <SettingsPage />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

const chip = (day: number) => screen.getByRole('checkbox', { name: `D-${day}` });
const saveButton = () => screen.getByRole('button', { name: '저장' });

describe('설정 · 마감 알림', () => {
  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(SETTINGS);
    vi.mocked(settingsApi.update).mockImplementation(async (input) => ({ ...input, email: SETTINGS.email }));
    vi.mocked(calendarApi.subscription).mockResolvedValue({ url: OLD_URL, issuedAt: '2026-10-26T01:00:00Z' });
  });

  afterEach(() => vi.restoreAllMocks());

  it('서버 설정을 보여주고, 바꾼 것이 없으면 저장 버튼이 꺼져 있다', async () => {
    renderSettings();

    expect(await screen.findByText('demo@hiretrack.dev')).toBeInTheDocument();
    expect(chip(7)).not.toBeChecked();
    expect(chip(3)).toBeChecked();
    expect(chip(1)).toBeChecked();
    expect(screen.getByRole('combobox')).toHaveDisplayValue('오전 9시');
    expect(saveButton()).toBeDisabled();
  });

  it('시점과 시각을 바꿔 저장하면 시점을 큰 순서로 정리해 보내고 저장 안내를 띄운다', async () => {
    const user = renderSettings();
    await screen.findByText('demo@hiretrack.dev');

    await user.click(chip(7));
    await user.selectOptions(screen.getByRole('combobox'), '오후 6시');
    await user.click(saveButton());

    await waitFor(() => expect(settingsApi.update).toHaveBeenCalledWith({ emailEnabled: true, remindDays: [7, 3, 1], sendHour: 18 }));
    expect(await screen.findByText('알림 설정을 저장했어요.')).toBeInTheDocument();
    expect(saveButton()).toBeDisabled(); // 저장한 값이 새 기준이 된다
  });

  it('알림을 켠 채 시점을 모두 빼면 저장할 수 없다고 알려준다', async () => {
    const user = renderSettings();
    await screen.findByText('demo@hiretrack.dev');

    await user.click(chip(3));
    await user.click(chip(1));

    expect(screen.getByText('알림 시점을 하나 이상 골라 주세요.')).toBeInTheDocument();
    expect(saveButton()).toBeDisabled();
  });

  it('알림을 끄면 시점·시각은 고를 수 없게 되고, 끈 상태로 저장할 수 있다', async () => {
    const user = renderSettings();
    await screen.findByText('demo@hiretrack.dev');

    await user.click(screen.getByLabelText('이메일 알림 켜기'));

    expect(chip(3)).toBeDisabled();
    expect(screen.getByRole('combobox')).toBeDisabled();
    await user.click(saveButton());
    await waitFor(() => expect(settingsApi.update).toHaveBeenCalledWith({ emailEnabled: false, remindDays: [3, 1], sendHour: 9 }));
  });

  it('저장에 실패하면 이유를 알려주고 고친 값은 그대로 둔다', async () => {
    vi.mocked(settingsApi.update).mockRejectedValue(new ApiError(400, 'INVALID_INPUT', '입력값이 올바르지 않습니다.'));
    const user = renderSettings();
    await screen.findByText('demo@hiretrack.dev');

    await user.click(chip(7));
    await user.click(saveButton());

    expect(await screen.findByText('저장 실패: 입력값이 올바르지 않습니다.')).toBeInTheDocument();
    expect(chip(7)).toBeChecked();
    expect(saveButton()).toBeEnabled();
  });
});

describe('설정 · 캘린더 구독', () => {
  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(SETTINGS);
    vi.mocked(calendarApi.subscription).mockResolvedValue({ url: OLD_URL, issuedAt: '2026-10-26T01:00:00Z' });
    vi.mocked(calendarApi.regenerate).mockResolvedValue({ url: NEW_URL, issuedAt: '2026-10-26T02:00:00Z' });
  });

  afterEach(() => vi.restoreAllMocks());

  it('구독 URL 을 복사한다', async () => {
    const user = renderSettings();
    // userEvent 가 테스트용 클립보드를 붙인 뒤에 감시한다
    const writeText = vi.spyOn(navigator.clipboard, 'writeText');

    expect(await screen.findByLabelText('캘린더 구독 URL')).toHaveValue(OLD_URL);
    await user.click(screen.getByRole('button', { name: 'URL 복사' }));

    expect(writeText).toHaveBeenCalledWith(OLD_URL);
    expect(await screen.findByText('구독 URL 을 복사했어요.')).toBeInTheDocument();
  });

  it('클립보드를 쓸 수 없으면 직접 복사하라고 안내한다', async () => {
    const user = renderSettings();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'));

    await user.click(await screen.findByRole('button', { name: 'URL 복사' }));

    expect(await screen.findByText('복사하지 못했어요. 주소를 직접 선택해 복사해 주세요.')).toBeInTheDocument();
  });

  it('재발급은 한 번 더 확인한 뒤 새 URL 로 바꾼다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = renderSettings();

    await user.click(await screen.findByRole('button', { name: 'URL 재발급' }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('더 이상 갱신되지 않아요'));
    expect(await screen.findByDisplayValue(NEW_URL)).toBeInTheDocument();
    expect(screen.getByText(/새 구독 URL 을 발급했어요/)).toBeInTheDocument();
  });

  it('재발급 확인에서 취소하면 아무것도 바꾸지 않는다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = renderSettings();

    await user.click(await screen.findByRole('button', { name: 'URL 재발급' }));

    expect(calendarApi.regenerate).not.toHaveBeenCalled();
    expect(screen.getByLabelText('캘린더 구독 URL')).toHaveValue(OLD_URL);
  });
});
