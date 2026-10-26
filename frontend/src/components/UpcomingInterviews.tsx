import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { interviewApi } from '../api';
import { INTERVIEW_TYPE_LABEL } from '../constants';
import { daysUntil, formatSchedule } from '../lib/format';

/** 보드 상단: 다가오는 면접 (최대 4개) */
export function UpcomingInterviews() {
  const { data } = useQuery({ queryKey: ['interviews', 'upcoming'], queryFn: interviewApi.upcoming });
  if (!data?.length) return null;

  return (
    <div className="upcoming" aria-label="다가오는 면접">
      <span className="upcoming-title">다가오는 면접</span>
      {data.slice(0, 4).map((i) => {
        const days = daysUntil(i.startsAt.slice(0, 10));
        return (
          <Link key={i.id} to={`/postings/${i.postingId}`} className="upcoming-item">
            <span className={`badge ${days <= 1 ? 'badge-urgent' : ''}`}>{days <= 0 ? '오늘' : `D-${days}`}</span>
            <strong>{i.company}</strong>
            <span>{INTERVIEW_TYPE_LABEL[i.type]}</span>
            <span className="muted">{formatSchedule(i.startsAt)}</span>
          </Link>
        );
      })}
    </div>
  );
}
