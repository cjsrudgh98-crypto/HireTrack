import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { interviewApi } from '../api';
import { INTERVIEW_TYPE_LABEL } from '../constants';
import { useChangeStatus } from '../hooks/usePostings';
import { formatSchedule, fromDatetimeLocal, toDatetimeLocal } from '../lib/format';
import type { ApplicationStatus, Interview, InterviewInput, InterviewType } from '../types';
import { useToast } from './Toast';

/** 면접 일정을 등록하면 "면접 진행"으로 옮길지 물어볼 상태 */
const ASK_MOVE_TO_INTERVIEW: ApplicationStatus[] = ['INTERESTED', 'APPLIED', 'DOCUMENT_PASSED'];

export function InterviewPanel({ postingId, postingStatus }: { postingId: number; postingStatus: ApplicationStatus }) {
  const { data: interviews } = useQuery({
    queryKey: ['interviews', postingId],
    queryFn: () => interviewApi.list(postingId),
  });
  const [editing, setEditing] = useState<Interview | 'new' | null>(null);
  const [now] = useState(() => Date.now()); // 지난 일정 흐리게 표시할 기준 시각 (화면을 연 시점)

  return (
    <section className="panel">
      <div className="panel-title">
        <h2>면접 일정</h2>
        {editing === null && (
          <button className="btn btn-ghost btn-sm" onClick={() => setEditing('new')}>
            + 일정 추가
          </button>
        )}
      </div>

      {editing === 'new' && (
        <InterviewForm postingId={postingId} postingStatus={postingStatus} onDone={() => setEditing(null)} />
      )}
      {interviews?.length === 0 && editing === null && (
        <p className="muted">면접·코딩테스트·과제 일정을 등록하면 캘린더 구독에도 1시간 전 알림과 함께 표시돼요.</p>
      )}

      <ul className="interview-list">
        {interviews?.map((i) =>
          editing !== 'new' && editing?.id === i.id ? (
            <li key={i.id}>
              <InterviewForm postingId={postingId} postingStatus={postingStatus} interview={i} onDone={() => setEditing(null)} />
            </li>
          ) : (
            <li key={i.id} className={`interview-item ${new Date(i.endsAt).getTime() < now ? 'interview-past' : ''}`}>
              <div className="interview-when">
                <strong>{formatSchedule(i.startsAt)}</strong>
                <span className="muted">{i.durationMinutes}분</span>
              </div>
              <div className="interview-body">
                <div>
                  <span className="tag">{INTERVIEW_TYPE_LABEL[i.type]}</span> {i.location && <span>{i.location}</span>}
                </div>
                {i.memo && <p className="interview-memo">{i.memo}</p>}
                {/* 면접 직전에 내가 낸 자기소개서를 바로 다시 볼 수 있게 */}
                <Link to={`/postings/${postingId}?tab=essay`} className="small">
                  제출한 자기소개서 보기 →
                </Link>
              </div>
              <button className="btn btn-ghost btn-sm" onClick={() => setEditing(i)}>
                수정
              </button>
            </li>
          ),
        )}
      </ul>
    </section>
  );
}

function InterviewForm({
  postingId,
  postingStatus,
  interview,
  onDone,
}: {
  postingId: number;
  postingStatus: ApplicationStatus;
  interview?: Interview;
  onDone: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const changeStatus = useChangeStatus((e) => toast(`상태 변경 실패: ${e.message}`));
  const [form, setForm] = useState({
    type: interview?.type ?? ('FIRST' as InterviewType),
    startsAt: interview ? toDatetimeLocal(interview.startsAt) : '',
    durationMinutes: interview?.durationMinutes ?? 60,
    location: interview?.location ?? '',
    memo: interview?.memo ?? '',
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['interviews'] });
  };

  const save = useMutation({
    mutationFn: () => {
      const input: InterviewInput = { ...form, startsAt: fromDatetimeLocal(form.startsAt) };
      return interview ? interviewApi.update(interview.id, input) : interviewApi.create(postingId, input);
    },
    onSuccess: () => {
      refresh();
      onDone();
      if (
        !interview &&
        ASK_MOVE_TO_INTERVIEW.includes(postingStatus) &&
        window.confirm("면접 일정을 등록했어요. 지원 상태를 '면접 진행'으로 바꿀까요?")
      ) {
        changeStatus.mutate({ id: postingId, status: 'INTERVIEW' });
      }
    },
  });

  const remove = useMutation({
    mutationFn: () => interviewApi.remove(interview!.id),
    onSuccess: () => {
      refresh();
      onDone();
    },
    onError: (e) => toast(e.message),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <form className="interview-form" onSubmit={submit}>
      <div className="question-form-row">
        <label className="field">
          <span>유형</span>
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as InterviewType })}>
            {Object.entries(INTERVIEW_TYPE_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>
            일시 <em>*</em>
          </span>
          <input
            type="datetime-local"
            value={form.startsAt}
            onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
            required
          />
        </label>
      </div>
      <div className="question-form-row">
        <label className="field">
          <span>소요 시간(분)</span>
          <input
            type="number"
            min={10}
            max={600}
            step={10}
            value={form.durationMinutes}
            onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })}
          />
        </label>
        <label className="field">
          <span>장소</span>
          <input
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            placeholder="예: 본사 3층 / Zoom"
            maxLength={200}
          />
        </label>
      </div>
      <label className="field">
        <span>메모</span>
        <textarea rows={2} value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} maxLength={2000} />
      </label>
      {save.error && <p className="form-error">{save.error.message}</p>}
      <div className="question-form-actions">
        {interview && (
          <button
            type="button"
            className="btn btn-danger btn-sm"
            onClick={() => window.confirm('이 면접 일정을 삭제할까요?') && remove.mutate()}
          >
            삭제
          </button>
        )}
        <span style={{ flex: 1 }} />
        <button type="button" className="btn btn-ghost btn-sm" onClick={onDone}>
          취소
        </button>
        <button type="submit" className="btn btn-primary btn-sm" disabled={save.isPending}>
          {interview ? '저장' : '등록'}
        </button>
      </div>
    </form>
  );
}
