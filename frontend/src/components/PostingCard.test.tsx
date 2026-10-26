import { DndContext } from '@dnd-kit/core';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostingSummary } from '../types';
import { PostingCard } from './PostingCard';

const base: PostingSummary = {
  id: 1,
  company: '데이터랩스',
  title: '데이터 엔지니어',
  location: '서울 강남구',
  employmentType: 'FULL_TIME',
  deadline: '2026-10-28',
  status: 'INTERESTED',
  appliedAt: null,
  createdAt: '2026-10-20T00:00:00Z',
};

function renderCard(posting: PostingSummary) {
  render(
    <MemoryRouter>
      <DndContext>
        <PostingCard posting={posting} />
      </DndContext>
    </MemoryRouter>,
  );
  return screen.getByText(posting.company).closest('.card')!;
}

describe('칸반 카드', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 26, 9, 0)); // 2026-10-26 09:00 (로컬)
  });
  afterEach(() => vi.useRealTimers());

  it('지원 전이면 마감 D-day 를, 3일 이내면 급함 표시를 붙인다', () => {
    renderCard(base);

    expect(screen.getByText('D-2')).toHaveClass('badge-urgent');
  });

  it('지원 전에 마감이 지난 공고는 흐리게 표시한다', () => {
    const card = renderCard({ ...base, deadline: '2026-10-25' });

    expect(card).toHaveClass('card-expired');
    expect(screen.getByText('마감됨')).toHaveClass('badge-muted');
  });

  it('지원한 공고는 마감일 대신 지원일을 보여주고, 마감이 지나도 흐리게 하지 않는다', () => {
    const card = renderCard({ ...base, status: 'APPLIED', deadline: '2026-10-25', appliedAt: '2026-10-21T03:00:00Z' });

    expect(screen.getByText('지원 10/21')).toBeInTheDocument();
    expect(screen.queryByText('마감됨')).not.toBeInTheDocument();
    expect(card).not.toHaveClass('card-expired');
  });

  it('정규직이 아니면 고용형태를 함께 보여준다', () => {
    renderCard({ ...base, employmentType: 'FREELANCE' });

    expect(screen.getByText('프리랜서')).toBeInTheDocument();
  });
});
