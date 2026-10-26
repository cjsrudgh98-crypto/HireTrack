import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { essayApi, templateApi } from '../api';
import { ToastProvider } from '../components/Toast';
import type { AnswerSearchResult, AnswerTemplate } from '../types';
import { LibraryPage } from './LibraryPage';

vi.mock('../api', () => ({
  templateApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
  essayApi: { search: vi.fn() },
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { email: 'demo@hiretrack.dev' }, logout: vi.fn() }) }));

const TEMPLATE: AnswerTemplate = {
  id: 1,
  category: 'MOTIVATION',
  title: '지원 동기 · 대규모 트래픽',
  content: '대규모 트래픽을 안정적으로 처리하고 싶습니다.',
  createdAt: '2026-10-01T01:00:00Z',
  updatedAt: '2026-10-01T01:00:00Z',
};

const RESULT: AnswerSearchResult = {
  answerId: 9,
  questionId: 3,
  postingId: 7,
  company: '클라우드웍스',
  postingTitle: '백엔드 개발자',
  question: '지원 동기를 작성해 주세요.',
  category: 'MOTIVATION',
  version: 2,
  content: '장애 대응 자동화로 팀의 장애 부담을 줄이겠습니다.',
  charCount: 26,
  submitted: true,
  createdAt: '2026-10-21T01:00:00Z',
};

function renderLibrary() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MemoryRouter>
        <ToastProvider>
          <LibraryPage />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

const templatePanel = () => screen.getByRole('heading', { name: /^템플릿/ }).closest('section') as HTMLElement;

describe('답변 라이브러리', () => {
  beforeEach(() => {
    vi.mocked(templateApi.list).mockResolvedValue([TEMPLATE]);
    vi.mocked(essayApi.search).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('템플릿을 글자 수와 함께 보여주고, 유형 탭을 누르면 그 유형만 불러온다', async () => {
    const user = renderLibrary();

    expect(await screen.findByText('지원 동기 · 대규모 트래픽')).toBeInTheDocument();
    expect(within(templatePanel()).getByText(/^25자 ·/)).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: '협업경험' }));

    await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith('COLLABORATION'));
    expect(screen.getByRole('tab', { name: '협업경험' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText(/협업경험만/)).toBeInTheDocument();
  });

  it('새 템플릿은 지금 고른 유형으로 시작하고, 저장하면 목록을 다시 불러온다', async () => {
    vi.mocked(templateApi.create).mockResolvedValue({ ...TEMPLATE, id: 2, category: 'COLLABORATION', title: '협업 · 코드 리뷰' });
    const user = renderLibrary();
    await user.click(await screen.findByRole('tab', { name: '협업경험' }));

    await user.click(screen.getByRole('button', { name: '+ 새 템플릿' }));
    const form = within(templatePanel());
    expect(form.getByLabelText('유형')).toHaveDisplayValue('협업경험');
    await user.type(form.getByLabelText('제목'), '협업 · 코드 리뷰');
    await user.type(form.getByLabelText(/^내용/), 'PR 템플릿을 제안했습니다.');
    expect(form.getByText('내용 (15자)')).toBeInTheDocument();
    await user.click(form.getByRole('button', { name: '저장' }));

    await waitFor(() =>
      expect(templateApi.create).toHaveBeenCalledWith({ category: 'COLLABORATION', title: '협업 · 코드 리뷰', content: 'PR 템플릿을 제안했습니다.' }),
    );
    await waitFor(() => expect(form.queryByLabelText('제목')).not.toBeInTheDocument());
  });

  it('수정하면 기존 값이 채워진 폼으로 바뀌고, 저장하면 그 템플릿을 고친다', async () => {
    vi.mocked(templateApi.update).mockResolvedValue({ ...TEMPLATE, title: '지원 동기 · 안정성' });
    const user = renderLibrary();

    await user.click(await within(await screen.findByRole('article')).findByRole('button', { name: '수정' }));
    const title = screen.getByLabelText('제목');
    expect(title).toHaveValue('지원 동기 · 대규모 트래픽');
    await user.clear(title);
    await user.type(title, '지원 동기 · 안정성');
    await user.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(templateApi.update).toHaveBeenCalledWith(1, expect.objectContaining({ title: '지원 동기 · 안정성' })));
  });

  it('삭제는 확인한 뒤에만 한다', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    vi.mocked(templateApi.remove).mockResolvedValue(undefined);
    const user = renderLibrary();
    const card = await screen.findByRole('article');

    await user.click(within(card).getByRole('button', { name: '삭제' }));
    expect(templateApi.remove).not.toHaveBeenCalled();

    await user.click(within(card).getByRole('button', { name: '삭제' }));
    expect(confirm).toHaveBeenLastCalledWith("'지원 동기 · 대규모 트래픽' 템플릿을 삭제할까요?");
    await waitFor(() => expect(templateApi.remove).toHaveBeenCalledWith(1));
  });

  it('과거 답변을 검색하면 키워드를 강조하고, 그 공고의 자소서 탭으로 가는 링크를 보여준다', async () => {
    vi.mocked(essayApi.search).mockImplementation(async ({ keyword }) => (keyword ? [RESULT] : []));
    const user = renderLibrary();
    expect(await screen.findByText('찾은 답변이 없어요.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('과거 답변 검색'), '장애');

    const link = await screen.findByRole('link', { name: '클라우드웍스 · 백엔드 개발자' });
    expect(link).toHaveAttribute('href', '/postings/7?tab=essay');
    const marks = link.closest('li')!.querySelectorAll('mark');
    expect([...marks].map((m) => m.textContent)).toEqual(['장애', '장애']);
    expect(link.closest('li')).toHaveTextContent('v2 제출본 · 26자');
  });

  it('정규식 특수문자가 든 키워드도 그대로 찾아 강조한다', async () => {
    vi.mocked(essayApi.search).mockResolvedValue([{ ...RESULT, content: '(주)클라우드웍스 인턴 경험' }]);
    const user = renderLibrary();

    await user.type(screen.getByLabelText('과거 답변 검색'), '(주)');

    await waitFor(() => expect(essayApi.search).toHaveBeenLastCalledWith({ keyword: '(주)', category: undefined }));
    expect(await screen.findByText('(주)', { selector: 'mark' })).toBeInTheDocument();
  });

  it('템플릿이 없으면 만드는 방법을 안내한다', async () => {
    vi.mocked(templateApi.list).mockResolvedValue([]);
    renderLibrary();

    expect(await screen.findByText(/잘 쓴 답변을 템플릿으로 저장해 두면/)).toBeInTheDocument();
  });
});
