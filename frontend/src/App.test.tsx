import { render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

// 라우팅만 확인한다 — 각 화면은 자기 테스트가 있으므로 이름만 찍는 가짜로 바꾼다
vi.mock('./pages/BoardPage', () => ({ BoardPage: () => <h1>보드</h1> }));
vi.mock('./pages/PostingDetailPage', () => ({ PostingDetailPage: () => <h1>공고 상세</h1> }));
vi.mock('./pages/SettingsPage', () => ({ SettingsPage: () => <h1>설정</h1> }));
vi.mock('./pages/LibraryPage', () => ({ LibraryPage: () => <h1>라이브러리</h1> }));
vi.mock('./pages/DashboardPage', () => ({ DashboardPage: () => <h1>대시보드</h1> }));
vi.mock('./pages/LoginPage', () => ({ LoginPage: () => <h1>로그인</h1> }));

const auth = vi.hoisted(() => ({ user: null as { email: string } | null, loading: false }));
vi.mock('./auth/AuthContext', () => ({ useAuth: () => auth }));

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <Where />
    </MemoryRouter>,
  );
}

describe('화면 이동', () => {
  beforeEach(() => {
    auth.user = { email: 'demo@hiretrack.dev' };
    auth.loading = false;
  });

  it.each([
    ['/', '보드'],
    ['/postings/7', '공고 상세'],
    ['/settings', '설정'],
    ['/library', '라이브러리'],
  ])('로그인했으면 %s 에서 %s 화면을 연다', async (path, title) => {
    renderAt(path);

    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent(path);
  });

  it('대시보드는 차트 라이브러리를 따로 받아 온 뒤 연다', async () => {
    renderAt('/dashboard');

    expect(await screen.findByRole('heading', { name: '대시보드' })).toBeInTheDocument();
  });

  it('로그인하지 않았으면 어느 화면이든 로그인 화면으로 보낸다', async () => {
    auth.user = null;
    renderAt('/postings/7');

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/login');
  });

  it('저장된 로그인을 확인하는 동안에는 로그인 화면으로 보내지 않고 기다린다', () => {
    auth.user = null;
    auth.loading = true;
    renderAt('/settings');

    expect(screen.getByText('불러오는 중…')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/settings');
  });

  it('없는 주소는 보드로 보낸다', async () => {
    renderAt('/no-such-page');

    expect(await screen.findByRole('heading', { name: '보드' })).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent(/^\/$/);
  });

  it('로그인 화면은 로그인 여부와 상관없이 열린다', async () => {
    renderAt('/login');

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
  });
});
