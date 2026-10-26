import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { postingApi } from '../api';
import { EMPLOYMENT_LABEL, PARSE_SOURCE_LABEL } from '../constants';
import { useSavePosting } from '../hooks/usePostings';
import type { EmploymentType, ParsedField, PostingDetail, PostingInput } from '../types';

const EMPTY: PostingInput = {
  company: '',
  title: '',
  url: '',
  location: '',
  employmentType: 'FULL_TIME',
  deadline: null,
  requirements: '',
  memo: '',
};

function toInput(posting: PostingDetail): PostingInput {
  return {
    company: posting.company,
    title: posting.title,
    url: posting.url ?? '',
    location: posting.location ?? '',
    employmentType: posting.employmentType,
    deadline: posting.deadline,
    requirements: posting.requirements ?? '',
    memo: posting.memo ?? '',
  };
}

type Notice = { kind: 'info' | 'error'; text: string } | null;

interface Props {
  /** 넘기면 수정 모드 */
  posting?: PostingDetail;
  onClose: () => void;
  onSaved?: (saved: PostingDetail) => void;
}

export function PostingFormModal({ posting, onClose, onSaved }: Props) {
  const [form, setForm] = useState<PostingInput>(() => (posting ? toInput(posting) : EMPTY));
  const save = useSavePosting();

  // URL 자동 추출 상태: 채워진 칸은 배경색으로, 못 찾은 칸은 "직접 입력 필요"로 표시
  const [extracting, setExtracting] = useState(false);
  const [autoFilled, setAutoFilled] = useState<Set<ParsedField>>(new Set());
  const [missing, setMissing] = useState<Set<ParsedField>>(new Set());
  const [notice, setNotice] = useState<Notice>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const set = <K extends keyof PostingInput>(key: K, value: PostingInput[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    // 사용자가 직접 고친 칸은 더 이상 "자동 입력" 표시를 하지 않는다
    if (autoFilled.has(key as ParsedField) || missing.has(key as ParsedField)) {
      setAutoFilled((prev) => without(prev, key as ParsedField));
      setMissing((prev) => without(prev, key as ParsedField));
    }
  };

  const extract = async () => {
    if (!form.url.trim() || extracting) return;
    setExtracting(true);
    setNotice(null);
    try {
      const result = await postingApi.parse(form.url.trim());
      if (!result.success) {
        setAutoFilled(new Set());
        setMissing(new Set());
        setNotice({ kind: 'error', text: result.message ?? '자동 추출에 실패했어요. 직접 입력해 주세요.' });
        return;
      }
      // 추출된 값만 덮어쓴다 — 못 찾은 칸은 사용자가 이미 입력한 값을 그대로 둔다
      const patch: Partial<PostingInput> = {};
      for (const [key, value] of Object.entries(result.data) as [ParsedField, string | null][]) {
        if (value === null) continue;
        if (key === 'employmentType') patch.employmentType = value as EmploymentType;
        else patch[key] = value;
      }
      const filled = new Set(Object.keys(patch) as ParsedField[]);
      setForm((prev) => ({ ...prev, ...patch }));
      setAutoFilled(filled);
      setMissing(new Set(result.missingFields));
      setNotice({
        kind: 'info',
        text: `${PARSE_SOURCE_LABEL[result.source]}에서 ${filled.size}개 항목을 채웠어요. 저장 전에 내용을 확인해 주세요.`,
      });
    } catch (error) {
      setNotice({ kind: 'error', text: error instanceof Error ? error.message : '자동 추출에 실패했어요.' });
    } finally {
      setExtracting(false);
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(
      { id: posting?.id, input: form },
      {
        onSuccess: (saved) => {
          onSaved?.(saved);
          onClose();
        },
      },
    );
  };

  /** 자동 추출 결과에 따라 칸 스타일과 안내를 붙인다 */
  const field = (key: ParsedField, label: ReactNode, control: ReactNode, wide = false) => (
    <label className={`field ${wide ? 'field-wide' : ''} ${autoFilled.has(key) ? 'field-auto' : ''}`}>
      <span className="field-label">
        {label}
        {missing.has(key) && <span className="field-missing">⚠ 직접 입력 필요</span>}
      </span>
      {control}
    </label>
  );

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="modal" onSubmit={handleSubmit} aria-labelledby="posting-form-title">
        <div className="modal-header">
          <h2 id="posting-form-title">{posting ? '공고 수정' : '공고 등록'}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>

        <div className="extract-bar">
          <label className="field">
            <span>공고 URL</span>
            <div className="extract-row">
              <input
                type="url"
                placeholder="https:// 공고 주소를 붙여넣고 [추출]을 누르세요"
                value={form.url}
                onChange={(e) => set('url', e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    extract();
                  }
                }}
                maxLength={500}
                autoFocus={!posting}
              />
              <button type="button" className="btn btn-ghost" onClick={extract} disabled={!form.url.trim() || extracting}>
                {extracting ? <span className="spinner" aria-label="추출 중" /> : '추출'}
              </button>
            </div>
          </label>
          {notice && <p className={`extract-notice extract-${notice.kind}`} role="status">{notice.text}</p>}
        </div>

        <div className="modal-body form-grid">
          {field(
            'company',
            <>회사명 <em>*</em></>,
            <input value={form.company} onChange={(e) => set('company', e.target.value)} required maxLength={100} />,
          )}
          {field(
            'title',
            <>직무명 <em>*</em></>,
            <input value={form.title} onChange={(e) => set('title', e.target.value)} required maxLength={200} />,
          )}
          {field(
            'location',
            '근무지',
            <input placeholder="예: 서울 강남구" value={form.location} onChange={(e) => set('location', e.target.value)} maxLength={100} />,
          )}
          {field(
            'employmentType',
            '고용형태',
            <select
              value={form.employmentType ?? ''}
              onChange={(e) => set('employmentType', (e.target.value || null) as EmploymentType | null)}
            >
              {Object.entries(EMPLOYMENT_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>,
          )}
          {field(
            'deadline',
            '마감일',
            <input type="date" value={form.deadline ?? ''} onChange={(e) => set('deadline', e.target.value || null)} />,
          )}
          <div className="field-spacer" />
          {field(
            'requirements',
            '자격요건',
            <textarea rows={4} value={form.requirements} onChange={(e) => set('requirements', e.target.value)} maxLength={5000} />,
            true,
          )}
          <label className="field field-wide">
            <span>메모</span>
            <textarea rows={3} value={form.memo} onChange={(e) => set('memo', e.target.value)} maxLength={10000} />
          </label>
        </div>

        {save.error && <p className="form-error">{save.error.message}</p>}

        <div className="modal-footer">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            취소
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? '저장 중…' : posting ? '저장' : '등록'}
          </button>
        </div>
      </form>
    </div>
  );
}

function without<T>(set: Set<T>, value: T): Set<T> {
  const next = new Set(set);
  next.delete(value);
  return next;
}
