import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api';
import { ApiError, tokenStorage } from '../api/client';
import { LoginPage } from '../pages/LoginPage';
import { AuthProvider, useAuth } from './AuthContext';

vi.mock('../api', () => ({
  authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
}));

const USER = { id: 1, email: 'demo@hiretrack.dev', createdAt: '2026-10-26T00:00:00Z' };

function Who() {
  const { user, loading } = useAuth();
  return <p>{loading ? '확인 중' : (user?.email ?? '로그아웃 상태')}</p>;
}

function renderWithAuth(ui: React.ReactNode) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('로그인 상태 복원', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
  });

  it('저장된 토큰이 있으면 내 정보를 받아 로그인 상태로 시작한다', async () => {
    tokenStorage.set('valid');
    vi.mocked(authApi.me).mockResolvedValue(USER);

    renderWithAuth(<Who />);

    expect(await screen.findByText('demo@hiretrack.dev')).toBeInTheDocument();
  });

  it('서버가 잠깐 응답하지 않아도 토큰을 지우지 않는다 (다시 열면 로그인 유지)', async () => {
    tokenStorage.set('valid');
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.'));

    renderWithAuth(<Who />);

    expect(await screen.findByText('로그아웃 상태')).toBeInTheDocument();
    expect(tokenStorage.get()).toBe('valid');
  });

  it('토큰이 없으면 서버에 묻지 않는다', async () => {
    renderWithAuth(<Who />);

    expect(await screen.findByText('로그아웃 상태')).toBeInTheDocument();
    expect(authApi.me).not.toHaveBeenCalled();
  });
});

describe('로그인 화면', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.login).mockReset();
  });

  it('로그인에 성공하면 토큰을 저장한다', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'new-token', tokenType: 'Bearer', user: USER });
    renderWithAuth(<LoginPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('이메일'), 'demo@hiretrack.dev');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(tokenStorage.get()).toBe('new-token'));
    expect(authApi.login).toHaveBeenCalledWith('demo@hiretrack.dev', 'password123');
  });

  it('여러 번 틀려 잠기면 서버의 안내 문구를 그대로 보여준다', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError(429, 'LOGIN_LOCKED', '로그인에 여러 번 실패해 잠시 잠겼어요. 15분 뒤에 다시 시도해 주세요.'),
    );
    renderWithAuth(<LoginPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('이메일'), 'demo@hiretrack.dev');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText(/15분 뒤에 다시 시도해 주세요/)).toHaveClass('form-error');
    expect(tokenStorage.get()).toBeNull();
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled();
  });

  it('회원가입 탭에서는 비밀번호 8자 안내를 보여주고 가입 API 를 부른다', async () => {
    vi.mocked(authApi.signup).mockResolvedValue({ accessToken: 't', tokenType: 'Bearer', user: USER });
    renderWithAuth(<LoginPage />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('tab', { name: '회원가입' }));
    expect(screen.getByLabelText('비밀번호')).toHaveAccessibleDescription('8자 이상 입력해 주세요.');
    await user.type(screen.getByLabelText('이메일'), 'new@hiretrack.dev');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '가입하고 시작하기' }));

    await waitFor(() => expect(authApi.signup).toHaveBeenCalledWith('new@hiretrack.dev', 'password123'));
  });
});
