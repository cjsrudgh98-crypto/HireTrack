import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { postingApi } from '../api';
import type { ParseResult } from '../types';
import { PostingFormModal } from './PostingFormModal';
import { ToastProvider } from './Toast';

vi.mock('../api', () => ({
  postingApi: { parse: vi.fn(), create: vi.fn(), update: vi.fn() },
}));

const PARTIAL: ParseResult = {
  success: true,
  source: 'SARAMIN',
  data: {
    company: '○○테크',
    title: '백엔드 개발자',
    location: '서울 강남구',
    employmentType: 'FULL_TIME',
    deadline: '2026-11-05',
    requirements: null,
  },
  missingFields: ['requirements'],
  message: null,
};

function renderModal() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <PostingFormModal onClose={() => {}} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

const field = (label: RegExp) => screen.getByLabelText(label, { selector: 'input, textarea, select' });

describe('공고 등록 폼 — URL 자동 추출', () => {
  beforeEach(() => vi.mocked(postingApi.parse).mockReset());

  it('추출 결과로 칸을 채우고(저장은 하지 않음) 채운 칸·못 찾은 칸을 표시한다', async () => {
    vi.mocked(postingApi.parse).mockResolvedValue(PARTIAL);
    const user = renderModal();

    await user.type(field(/공고 URL/), 'https://www.saramin.co.kr/x');
    await user.click(screen.getByRole('button', { name: '추출' }));

    expect(await screen.findByDisplayValue('○○테크')).toBeInTheDocument();
    expect(field(/근무지/)).toHaveValue('서울 강남구');
    expect(field(/마감일/)).toHaveValue('2026-11-05');
    expect(field(/회사명/).closest('label')).toHaveClass('field-auto');
    expect(field(/자격요건/).closest('label')).toHaveTextContent('직접 입력 필요');
    expect(screen.getByText(/사람인에서 5개 항목을 채웠어요/)).toBeInTheDocument();
    expect(postingApi.create).not.toHaveBeenCalled();
  });

  it('자동으로 채운 칸을 사용자가 고치면 자동 입력 표시가 사라진다', async () => {
    vi.mocked(postingApi.parse).mockResolvedValue(PARTIAL);
    const user = renderModal();
    await user.type(field(/공고 URL/), 'https://www.saramin.co.kr/x');
    await user.click(screen.getByRole('button', { name: '추출' }));
    await screen.findByDisplayValue('○○테크');

    await user.type(field(/회사명/), '(주)');

    expect(field(/회사명/).closest('label')).not.toHaveClass('field-auto');
    expect(field(/근무지/).closest('label')).toHaveClass('field-auto');
  });

  it('추출에 실패하면 안내를 보여주고 직접 입력할 수 있게 둔다', async () => {
    vi.mocked(postingApi.parse).mockResolvedValue({
      ...PARTIAL,
      success: false,
      data: { ...PARTIAL.data, company: null, title: null },
      message: '자동 추출에 실패했어요. 직접 입력해 주세요.',
    });
    const user = renderModal();

    await user.type(field(/공고 URL/), 'https://spa.example.com/');
    await user.click(screen.getByRole('button', { name: '추출' }));

    expect(await screen.findByText('자동 추출에 실패했어요. 직접 입력해 주세요.')).toHaveClass('extract-error');
    expect(field(/회사명/)).toHaveValue('');
    expect(field(/회사명/)).toBeEnabled();
  });
});
