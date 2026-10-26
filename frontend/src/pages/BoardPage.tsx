import { useState } from 'react';
import { Header } from '../components/Header';
import { KanbanBoard } from '../components/KanbanBoard';
import { PostingFormModal } from '../components/PostingFormModal';
import { UpcomingInterviews } from '../components/UpcomingInterviews';
import { EMPLOYMENT_LABEL, REGIONS } from '../constants';
import { useDebounce } from '../hooks/useDebounce';
import { usePostings } from '../hooks/usePostings';
import type { EmploymentType, PostingFilters } from '../types';

export function BoardPage() {
  const [keyword, setKeyword] = useState('');
  const [employmentType, setEmploymentType] = useState<EmploymentType | ''>('');
  const [location, setLocation] = useState('');
  const [formOpen, setFormOpen] = useState(false);

  const filters: PostingFilters = { keyword: useDebounce(keyword), employmentType, location };
  const { data: postings, isPending, error } = usePostings(filters);
  const filtered = Boolean(filters.keyword || employmentType || location);

  return (
    <div className="page">
      <Header>
        <button className="btn btn-primary" onClick={() => setFormOpen(true)}>
          + 공고 등록
        </button>
        <input
          className="search"
          type="search"
          placeholder="회사명·직무 검색"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          aria-label="회사명·직무 검색"
        />
      </Header>

      <div className="toolbar">
        <select value={employmentType} onChange={(e) => setEmploymentType(e.target.value as EmploymentType | '')} aria-label="고용형태">
          <option value="">고용형태 전체</option>
          {Object.entries(EMPLOYMENT_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select value={location} onChange={(e) => setLocation(e.target.value)} aria-label="지역">
          <option value="">지역 전체</option>
          {REGIONS.map((region) => (
            <option key={region} value={region}>
              {region}
            </option>
          ))}
        </select>
        {filtered && (
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => {
              setKeyword('');
              setEmploymentType('');
              setLocation('');
            }}
          >
            필터 초기화
          </button>
        )}
        {postings && <span className="toolbar-count">총 {postings.length}건</span>}
      </div>

      <UpcomingInterviews />

      {error && <p className="form-error page-pad">목록을 불러오지 못했습니다: {error.message}</p>}
      {isPending ? (
        <div className="loading">불러오는 중…</div>
      ) : (
        <>
          {postings?.length === 0 && !filtered && (
            <div className="empty-hint">
              아직 등록한 공고가 없어요. <button className="link-btn" onClick={() => setFormOpen(true)}>첫 공고를 등록</button>해 보세요.
            </div>
          )}
          <KanbanBoard postings={postings ?? []} />
        </>
      )}

      {formOpen && <PostingFormModal onClose={() => setFormOpen(false)} />}
    </div>
  );
}
