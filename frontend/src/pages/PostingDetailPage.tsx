import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { postingApi } from '../api';
import { EssayWorkspace } from '../components/essay/EssayWorkspace';
import { Header } from '../components/Header';
import { InterviewPanel } from '../components/InterviewPanel';
import { PostingFormModal } from '../components/PostingFormModal';
import { useToast } from '../components/Toast';
import { EMPLOYMENT_LABEL, STATUS_LABEL, STATUS_ORDER } from '../constants';
import {
  postingKeys,
  useChangeStatus,
  useDeletePosting,
  useHistory,
  usePosting,
  useUpdateMemo,
} from '../hooks/usePostings';
import { formatDateTime, formatDday, formatShortDate } from '../lib/format';
import type { ApplicationStatus, PostingDetail } from '../types';

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';

const SAVE_LABEL: Record<SaveState, string> = {
  idle: '',
  dirty: '입력 중…',
  saving: '저장 중…',
  saved: '저장됨',
  error: '저장 실패 — 다시 입력하면 재시도합니다',
};

/** 입력이 멈추고 0.8초 뒤 자동 저장. 페이지를 떠날 때 남은 변경도 저장한다. */
function MemoEditor({ posting }: { posting: PostingDetail }) {
  const queryClient = useQueryClient();
  const { mutate: saveMemo } = useUpdateMemo(posting.id); // mutate 는 렌더 간에 같은 함수(안정적)
  const [memo, setMemo] = useState(posting.memo ?? '');
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const latest = useRef(memo); // 언마운트 시점의 최신 값 — 렌더 중이 아니라 입력 핸들러에서 갱신
  const saved = useRef(memo);

  const handleChange = (value: string) => {
    latest.current = value;
    setMemo(value);
  };

  useEffect(() => {
    if (memo === saved.current) return;
    setSaveState('dirty');
    const timer = setTimeout(() => {
      setSaveState('saving');
      saveMemo(memo, {
        onSuccess: () => {
          saved.current = memo;
          if (latest.current === memo) setSaveState('saved');
        },
        onError: () => setSaveState('error'),
      });
    }, 800);
    return () => clearTimeout(timer);
  }, [memo, saveMemo]);

  useEffect(
    () => () => {
      if (latest.current !== saved.current) {
        postingApi
          .updateMemo(posting.id, latest.current)
          .then((res) => queryClient.setQueryData(postingKeys.detail(posting.id), res))
          .catch(() => {});
      }
    },
    [posting.id, queryClient],
  );

  return (
    <section className="panel">
      <div className="panel-title">
        <h2>메모</h2>
        <span className={`save-state save-${saveState}`}>{SAVE_LABEL[saveState]}</span>
      </div>
      <textarea
        className="memo"
        rows={8}
        placeholder="면접 일정, 연락 담당자, 준비할 것 등을 자유롭게 적어 두세요."
        value={memo}
        onChange={(e) => handleChange(e.target.value)}
        maxLength={10000}
      />
    </section>
  );
}

type Tab = 'info' | 'essay' | 'history';
const TABS: { key: Tab; label: string }[] = [
  { key: 'info', label: '정보' },
  { key: 'essay', label: '자기소개서' },
  { key: 'history', label: '이력' },
];

function HistoryTimeline({ postingId }: { postingId: number }) {
  const { data: history, isPending } = useHistory(postingId);
  if (isPending) return <div className="loading">불러오는 중…</div>;

  return (
    <ol className="timeline">
      {history?.map((h) => (
        <li key={h.id} className={`timeline-item status-${h.toStatus} ${h.reverted ? 'timeline-reverted' : ''}`}>
          <span className="timeline-date" title={formatDateTime(h.changedAt)}>
            {formatShortDate(h.changedAt)}
          </span>
          <span className="timeline-text">
            {h.fromStatus ? (
              <>
                {STATUS_LABEL[h.fromStatus]} → <strong>{STATUS_LABEL[h.toStatus]}</strong>
              </>
            ) : (
              <>
                공고 등록 · <strong>{STATUS_LABEL[h.toStatus]}</strong>
              </>
            )}
            {h.reverted && (
              <span className="muted" title="10분 안에 바로 되돌린 변경이라 통계에서 제외돼요">
                {' '}· 되돌림
              </span>
            )}
          </span>
        </li>
      ))}
    </ol>
  );
}

export function PostingDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const toast = useToast();
  const { data: posting, error, isPending } = usePosting(id);
  const changeStatus = useChangeStatus((err) => toast(`상태 변경 실패: ${err.message}`));
  const deletePosting = useDeletePosting();
  const [editing, setEditing] = useState(false);
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') ?? 'info') as Tab;
  const setTab = (next: Tab) => setParams(next === 'info' ? {} : { tab: next }, { replace: true });
  const [editVersion, setEditVersion] = useState(0);

  const handleDelete = () => {
    if (!posting || !window.confirm(`'${posting.company} · ${posting.title}' 공고를 삭제할까요? 메모와 이력도 함께 삭제됩니다.`)) return;
    deletePosting.mutate(posting.id, {
      onSuccess: () => navigate('/', { replace: true }),
      onError: (err) => toast(`삭제 실패: ${err.message}`),
    });
  };

  if (isPending) {
    return (
      <div className="page">
        <Header />
        <div className="loading">불러오는 중…</div>
      </div>
    );
  }

  if (error || !posting) {
    return (
      <div className="page">
        <Header />
        <div className="empty-hint">
          {error?.message ?? '공고를 찾을 수 없습니다.'} <Link to="/">보드로 돌아가기</Link>
        </div>
      </div>
    );
  }

  const dday = posting.deadline ? formatDday(posting.deadline) : null;

  return (
    <div className="page">
      <Header />
      <div className={`detail ${tab === 'essay' ? 'detail-wide' : ''}`}>
        <Link to="/" className="back-link">
          ← 보드로
        </Link>

        <div className="detail-head detail-hero">
          <div>
            <div className="detail-company">{posting.company}</div>
            <h1 className="detail-title">{posting.title}</h1>
          </div>
          <select
            className={`status-select status-${posting.status}`}
            value={posting.status}
            onChange={(e) => changeStatus.mutate({ id: posting.id, status: e.target.value as ApplicationStatus })}
            aria-label="지원 상태"
          >
            {STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </div>

        <div className="tabs-line" role="tablist" aria-label="공고 상세">
          {TABS.map((t) => (
            <button
              key={t.key}
              role="tab"
              aria-selected={tab === t.key}
              className={`tab-line ${tab === t.key ? 'active' : ''}`}
              onClick={() => setTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'essay' ? (
          <EssayWorkspace postingId={posting.id} applied={posting.appliedAt !== null} />
        ) : (
        <div className="detail-layout">
          <div className="detail-main">
            {tab === 'history' ? (
              <section className="panel">
                <div className="panel-title">
                  <h2>상태 변경 이력</h2>
                </div>
                <HistoryTimeline postingId={posting.id} />
              </section>
            ) : (
            <>
            <section className="panel">
              <dl className="info-grid info-grid-top">
                <dt>근무지</dt>
                <dd>{posting.location ?? '-'}</dd>
                <dt>고용형태</dt>
                <dd>{posting.employmentType ? EMPLOYMENT_LABEL[posting.employmentType] : '-'}</dd>
                <dt>마감일</dt>
                <dd>
                  {posting.deadline ?? '-'}
                  {dday && (
                    <span className={`badge ${dday.urgent ? 'badge-urgent' : dday.past ? 'badge-muted' : ''}`}>{dday.text}</span>
                  )}
                </dd>
                <dt>지원일</dt>
                <dd>{posting.appliedAt ? formatDateTime(posting.appliedAt) : '-'}</dd>
              </dl>

              {posting.requirements && (
                <div className="requirements">
                  <h3>자격요건</h3>
                  <p>{posting.requirements}</p>
                </div>
              )}
            </section>

            <InterviewPanel postingId={posting.id} postingStatus={posting.status} />

            <MemoEditor key={`${posting.id}-${editVersion}`} posting={posting} />
            </>
            )}
          </div>

          <aside className="detail-side">
            {posting.url && (
              <a className="btn btn-primary btn-block" href={posting.url} target="_blank" rel="noopener noreferrer">
                원본 공고 보기 ↗
              </a>
            )}
            <button className="btn btn-ghost btn-block" onClick={() => setEditing(true)}>
              수정
            </button>
            <button className="btn btn-danger btn-block" onClick={handleDelete} disabled={deletePosting.isPending}>
              삭제
            </button>
            <p className="side-meta">
              등록 {formatDateTime(posting.createdAt)}
              <br />
              수정 {formatDateTime(posting.updatedAt)}
            </p>
          </aside>
        </div>
        )}
      </div>

      {editing && (
        <PostingFormModal
          posting={posting}
          onClose={() => setEditing(false)}
          onSaved={() => setEditVersion((v) => v + 1)}
        />
      )}
    </div>
  );
}
