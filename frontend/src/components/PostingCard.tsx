import { useDraggable } from '@dnd-kit/core';
import { useNavigate } from 'react-router-dom';
import { EMPLOYMENT_LABEL } from '../constants';
import { daysUntil, formatDday, formatShortDate } from '../lib/format';
import type { PostingSummary } from '../types';

export function PostingCardBody({ posting }: { posting: PostingSummary }) {
  // 아직 지원 전이면 마감 D-day, 지원했으면 지원일을 보여준다.
  const dday = posting.status === 'INTERESTED' && posting.deadline ? formatDday(posting.deadline) : null;

  return (
    <>
      <div className="card-company">{posting.company}</div>
      <div className="card-title">{posting.title}</div>
      <div className="card-meta">
        {dday && (
          <span className={`badge ${dday.urgent ? 'badge-urgent' : dday.past ? 'badge-muted' : ''}`}>
            {dday.text}
          </span>
        )}
        {posting.appliedAt && <span className="meta-text">지원 {formatShortDate(posting.appliedAt)}</span>}
        {posting.employmentType && posting.employmentType !== 'FULL_TIME' && (
          <span className="meta-text">{EMPLOYMENT_LABEL[posting.employmentType]}</span>
        )}
        {posting.location && <span className="meta-text">{posting.location}</span>}
      </div>
    </>
  );
}

export function PostingCard({ posting }: { posting: PostingSummary }) {
  const navigate = useNavigate();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: posting.id, data: posting });
  // 지원하지 않은 채 마감이 지난 공고는 흐리게
  const expired = posting.status === 'INTERESTED' && posting.deadline !== null && daysUntil(posting.deadline) < 0;

  return (
    <div
      ref={setNodeRef}
      className={`card ${isDragging ? 'card-dragging' : ''} ${expired ? 'card-expired' : ''}`}
      onClick={() => navigate(`/postings/${posting.id}`)}
      {...listeners}
      {...attributes}
    >
      <PostingCardBody posting={posting} />
    </div>
  );
}
