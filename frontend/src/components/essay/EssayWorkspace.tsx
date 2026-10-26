import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { essayApi, templateApi } from '../../api';
import { ApiError } from '../../api/client';
import { ESSAY_CATEGORIES, ESSAY_CATEGORY_LABEL } from '../../constants';
import { formatDateTime } from '../../lib/format';
import { countText } from '../../lib/textCount';
import type { EssayAnswer, EssayQuestion, QuestionInput } from '../../types';
import { useToast } from '../Toast';
import { DiffView } from './DiffView';

const essayKeys = {
  questions: (postingId: number) => ['essay', postingId, 'questions'] as const,
  versions: (postingId: number, questionId: number) => ['essay', postingId, 'versions', questionId] as const,
};

interface Props {
  postingId: number;
  /** 지원한 공고인지 — 나중에 추가한 문항을 직접 제출본으로 지정할 수 있다 */
  applied: boolean;
}

export function EssayWorkspace({ postingId, applied }: Props) {
  const { data: questions, isPending, error } = useQuery({
    queryKey: essayKeys.questions(postingId),
    queryFn: () => essayApi.questions(postingId),
  });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);

  const selected = questions?.find((q) => q.id === selectedId) ?? questions?.[0] ?? null;

  if (isPending) return <div className="loading">불러오는 중…</div>;
  if (error) return <p className="form-error">자기소개서를 불러오지 못했습니다: {error.message}</p>;

  return (
    <div className="essay-workspace">
      <aside className="essay-questions panel">
        <div className="panel-title">
          <h2>문항</h2>
          <button className="btn btn-ghost btn-sm" onClick={() => setAdding((v) => !v)}>
            {adding ? '닫기' : '+ 문항 추가'}
          </button>
        </div>
        {adding && (
          <QuestionForm
            postingId={postingId}
            onDone={(created) => {
              setAdding(false);
              if (created) setSelectedId(created.id);
            }}
          />
        )}
        {questions && questions.length === 0 && !adding && (
          <p className="muted">회사가 낸 자기소개서 문항을 추가해 보세요.</p>
        )}
        <ol className="question-list">
          {questions?.map((q, i) => (
            <li key={q.id}>
              <button
                className={`question-item ${selected?.id === q.id ? 'active' : ''}`}
                onClick={() => setSelectedId(q.id)}
              >
                <span className="question-no">{i + 1}</span>
                <span className="question-body">
                  <span className="question-text">{q.question}</span>
                  <span className="question-meta">
                    <span className="tag">{ESSAY_CATEGORY_LABEL[q.category]}</span>
                    {q.latestAnswer ? (
                      <span className={over(q) ? 'count-over' : ''}>
                        {q.latestAnswer.charCount}
                        {q.maxLength ? `/${q.maxLength}` : '자'}
                      </span>
                    ) : (
                      <span>미작성</span>
                    )}
                    {q.locked && <span title={`제출본 v${q.submittedVersion}`}>🔒 제출본</span>}
                    {q.draft && !q.locked && <span title="저장하지 않은 임시저장 있음">✎</span>}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ol>
      </aside>

      {selected ? (
        <AnswerEditor key={selected.id} postingId={postingId} question={selected} applied={applied} />
      ) : (
        <div className="essay-empty panel">문항을 추가하면 여기서 답변을 쓰고 버전을 관리할 수 있어요.</div>
      )}
    </div>
  );
}

function over(q: EssayQuestion) {
  return q.maxLength !== null && q.latestAnswer !== null && q.latestAnswer.charCount > q.maxLength;
}

// ---------- 문항 추가·수정 ----------

function QuestionForm({
  postingId,
  question,
  onDone,
}: {
  postingId: number;
  question?: EssayQuestion;
  onDone: (saved?: EssayQuestion) => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<QuestionInput>({
    question: question?.question ?? '',
    maxLength: question?.maxLength ?? null,
    category: question?.category ?? 'MOTIVATION',
  });
  const save = useMutation({
    mutationFn: () => (question ? essayApi.updateQuestion(question.id, form) : essayApi.addQuestion(postingId, form)),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: essayKeys.questions(postingId) });
      onDone(saved);
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <form className="question-form" onSubmit={submit}>
      <label className="field">
        <span>질문</span>
        <textarea
          rows={3}
          value={form.question}
          onChange={(e) => setForm({ ...form, question: e.target.value })}
          placeholder="예: 지원 동기와 입사 후 포부를 작성해 주세요."
          required
          maxLength={1000}
          autoFocus
        />
      </label>
      <div className="question-form-row">
        <label className="field">
          <span>글자 수 제한</span>
          <input
            type="number"
            min={1}
            max={20000}
            placeholder="없음"
            value={form.maxLength ?? ''}
            onChange={(e) => setForm({ ...form, maxLength: e.target.value ? Number(e.target.value) : null })}
          />
        </label>
        <label className="field">
          <span>유형</span>
          <select
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value as QuestionInput['category'] })}
          >
            {ESSAY_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {ESSAY_CATEGORY_LABEL[c]}
              </option>
            ))}
          </select>
        </label>
      </div>
      {save.error && <p className="form-error">{save.error.message}</p>}
      <div className="question-form-actions">
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => onDone()}>
          취소
        </button>
        <button type="submit" className="btn btn-primary btn-sm" disabled={save.isPending}>
          {question ? '수정' : '추가'}
        </button>
      </div>
    </form>
  );
}

// ---------- 답변 편집 + 버전 패널 ----------

type DraftState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';
const DRAFT_LABEL: Record<DraftState, string> = {
  idle: '',
  dirty: '입력 중…',
  saving: '임시저장 중…',
  saved: '임시저장됨',
  error: '임시저장 실패',
};
const EDITING = -1; // 비교 선택지에서 "지금 편집 중인 내용"

function AnswerEditor({ postingId, question, applied }: { postingId: number; question: EssayQuestion; applied: boolean }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const latest = question.latestAnswer;

  // 임시저장이 있으면 이어서, 없으면 최신 버전에서 시작
  const [content, setContent] = useState(() => question.draft?.content ?? latest?.content ?? '');
  const [sourceTemplateId, setSourceTemplateId] = useState<number | null>(null);
  const [draftState, setDraftState] = useState<DraftState>(question.draft ? 'saved' : 'idle');
  const [conflict, setConflict] = useState(false);
  const [editing, setEditing] = useState(false);
  const lastDraft = useRef(content);

  const { data: versions } = useQuery({
    queryKey: essayKeys.versions(postingId, question.id),
    queryFn: () => essayApi.versions(question.id),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['essay', postingId] });

  // ---- 자동 임시저장 (입력이 1초 멈추면) ----
  useEffect(() => {
    if (question.locked || content === lastDraft.current) return;
    setDraftState('dirty');
    const timer = setTimeout(() => {
      setDraftState('saving');
      essayApi
        .saveDraft(question.id, content)
        .then(() => {
          lastDraft.current = content;
          setDraftState('saved');
        })
        .catch(() => setDraftState('error'));
    }, 1000);
    return () => clearTimeout(timer);
  }, [content, question.id, question.locked]);

  // ---- 버전 저장 ----
  const saveVersion = useMutation({
    mutationFn: () => essayApi.saveVersion(question.id, content, latest?.version ?? null, sourceTemplateId),
    onSuccess: (saved) => {
      lastDraft.current = content; // 서버가 임시저장을 비웠으므로 같은 내용은 다시 임시저장하지 않는다
      setDraftState('idle');
      setSourceTemplateId(null);
      setConflict(false);
      toast(`v${saved.version} 으로 저장했어요.`);
      refresh();
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') {
        setConflict(true);
        refresh(); // 최신 버전 번호를 받아와서 다음 저장의 baseVersion 으로 쓴다
      } else {
        toast(error.message);
      }
    },
  });

  const submit = useMutation({
    mutationFn: (version: number) => essayApi.submit(question.id, version),
    onSuccess: (a) => {
      toast(`v${a.version} 을 제출본으로 고정했어요.`);
      refresh();
    },
    onError: (error) => toast(error.message),
  });

  const removeQuestion = useMutation({
    mutationFn: () => essayApi.deleteQuestion(question.id),
    onSuccess: refresh,
    onError: (error) => toast(error.message),
  });

  const counts = countText(content);
  const limitOver = question.maxLength !== null && counts.withSpaces > question.maxLength;
  const unchanged = latest !== null && content === latest.content;

  return (
    <>
      <section className="essay-editor panel">
        {editing ? (
          <QuestionForm postingId={postingId} question={question} onDone={() => setEditing(false)} />
        ) : (
          <div className="essay-question-head">
            <div>
              <span className="tag">{ESSAY_CATEGORY_LABEL[question.category]}</span>
              <h2 className="essay-question">{question.question}</h2>
            </div>
            {!question.locked && (
              <div className="essay-question-actions">
                <button className="btn btn-ghost btn-sm" onClick={() => setEditing(true)}>
                  수정
                </button>
                <button
                  className="btn btn-danger btn-sm"
                  onClick={() => window.confirm('이 문항과 모든 답변 버전을 삭제할까요?') && removeQuestion.mutate()}
                >
                  삭제
                </button>
              </div>
            )}
          </div>
        )}

        {question.locked && (
          <div className="banner banner-lock">
            🔒 제출본 v{question.submittedVersion} — 지원할 때 낸 내용이라 수정할 수 없어요. 면접 준비할 때 그대로 확인하세요.
          </div>
        )}
        {conflict && (
          <div className="banner banner-warn" role="alert">
            다른 탭에서 먼저 새 버전(v{latest?.version})이 저장됐어요. 지금 쓰던 내용은 그대로 두었어요 — 오른쪽에서 비교해 본 뒤 다시
            [버전 저장]을 누르면 v{(latest?.version ?? 0) + 1} 로 저장됩니다.
          </div>
        )}

        <textarea
          className={`essay-textarea ${limitOver ? 'over' : ''}`}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          readOnly={question.locked}
          placeholder="답변을 작성하세요. 입력하는 동안 자동으로 임시저장되고, [버전 저장]을 누르면 새 버전으로 기록돼요."
          aria-label="답변"
        />

        <div className="essay-footer">
          <div className="char-counts" aria-live="polite">
            <span className={limitOver ? 'count-over' : ''}>
              공백 포함 <strong>{counts.withSpaces.toLocaleString()}</strong>
              {question.maxLength && ` / ${question.maxLength.toLocaleString()}`}
            </span>
            <span>
              공백 제외 <strong>{counts.withoutSpaces.toLocaleString()}</strong>
            </span>
            <span title="한글 2바이트, 영문·숫자·공백 1바이트, 줄바꿈 2바이트">
              <strong>{counts.bytes.toLocaleString()}</strong> byte
            </span>
            {limitOver && <span className="count-over">⚠ {counts.withSpaces - question.maxLength!}자 초과</span>}
          </div>
          {!question.locked && (
            <div className="essay-actions">
              <span className={`save-state save-${draftState}`}>{DRAFT_LABEL[draftState]}</span>
              {(sourceTemplateId ?? latest?.sourceTemplateId) && <span className="tag tag-template">템플릿 기반</span>}
              <SaveToLibrary category={question.category} content={content} />
              <button
                className="btn btn-primary btn-sm"
                onClick={() => saveVersion.mutate()}
                disabled={!content.trim() || unchanged || saveVersion.isPending}
                title={unchanged ? '최신 버전과 내용이 같아요' : undefined}
              >
                {saveVersion.isPending ? '저장 중…' : `버전 저장 (v${(latest?.version ?? 0) + 1})`}
              </button>
            </div>
          )}
        </div>
      </section>

      <aside className="essay-side">
        <VersionPanel
          versions={versions ?? []}
          editingContent={content}
          locked={question.locked}
          canSubmit={applied && !question.locked}
          onSubmit={(v) =>
            window.confirm(`v${v} 을 제출본으로 고정할까요? 고정 후에는 이 문항을 수정할 수 없어요.`) && submit.mutate(v)
          }
        />
        {!question.locked && (
          <Recommendations
            question={question}
            onImport={(text, templateId) => {
              if (content.trim() && !window.confirm('지금 쓰던 내용을 가져온 내용으로 바꿀까요? (임시저장·버전은 남아 있어요)')) return;
              setContent(text);
              setSourceTemplateId(templateId);
            }}
          />
        )}
      </aside>
    </>
  );
}

function SaveToLibrary({ category, content }: { category: EssayQuestion['category']; content: string }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const save = useMutation({
    mutationFn: () => templateApi.create({ category, title, content }),
    onSuccess: () => {
      toast('라이브러리에 저장했어요.');
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      setOpen(false);
      setTitle('');
    },
    onError: (error) => toast(error.message),
  });

  if (!open) {
    return (
      <button className="btn btn-ghost btn-sm" onClick={() => setOpen(true)} disabled={!content.trim()}>
        라이브러리에 저장
      </button>
    );
  }
  return (
    <form
      className="inline-form"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="템플릿 제목"
        required
        maxLength={200}
        autoFocus
        aria-label="템플릿 제목"
      />
      <button type="submit" className="btn btn-primary btn-sm" disabled={save.isPending}>
        저장
      </button>
      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setOpen(false)}>
        취소
      </button>
    </form>
  );
}

function VersionPanel({
  versions,
  editingContent,
  locked,
  canSubmit,
  onSubmit,
}: {
  versions: EssayAnswer[];
  editingContent: string;
  locked: boolean;
  canSubmit: boolean;
  onSubmit: (version: number) => void;
}) {
  // 기본 비교: 편집 중 내용이 최신 버전과 다르면 최신 버전 ↔ 편집 중(저장 전 무엇을 고쳤는지),
  // 같으면 직전 버전 ↔ 편집 중(마지막 저장에서 무엇이 바뀌었는지). 제출본 고정 상태면 직전 버전 ↔ 제출본
  const [left, setLeft] = useState<number | null>(null);
  const [right, setRight] = useState<number>(EDITING);
  const editedSinceLatest = !locked && versions.length > 0 && editingContent !== versions[0].content;
  const leftVersion = left ?? (editedSinceLatest ? versions[0].version : (versions[1]?.version ?? versions[0]?.version ?? null));
  const rightVersion = locked && right === EDITING ? (versions[0]?.version ?? EDITING) : right;

  const textOf = (v: number | null) =>
    v === EDITING ? editingContent : (versions.find((a) => a.version === v)?.content ?? '');
  const options = useMemo(
    () => [
      ...(locked ? [] : [{ value: EDITING, label: '편집 중' }]),
      ...versions.map((a) => ({ value: a.version, label: `v${a.version}${a.submitted ? ' (제출본)' : ''}` })),
    ],
    [versions, locked],
  );

  return (
    <section className="panel version-panel">
      <div className="panel-title">
        <h2>버전</h2>
        <span className="muted">{versions.length}개</span>
      </div>
      {versions.length === 0 ? (
        <p className="muted">[버전 저장]을 누르면 여기에 쌓여요. 저장한 버전은 지워지지 않아요.</p>
      ) : (
        <>
          <ul className="version-list">
            {versions.map((a) => (
              <li key={a.id} className={a.submitted ? 'submitted' : ''}>
                <button className="version-pick" onClick={() => setLeft(a.version)} title="이 버전을 비교 기준으로">
                  <strong>v{a.version}</strong>
                  {a.submitted && <span className="tag tag-submitted">제출본</span>}
                  <span className="muted">{formatDateTime(a.createdAt)}</span>
                  <span className="muted">{a.charCount}자</span>
                </button>
                {canSubmit && (
                  <button className="link-btn small" onClick={() => onSubmit(a.version)}>
                    제출본 지정
                  </button>
                )}
              </li>
            ))}
          </ul>

          <div className="compare">
            <div className="compare-selects">
              <select value={leftVersion ?? ''} onChange={(e) => setLeft(Number(e.target.value))} aria-label="비교 기준 버전">
                {options.filter((o) => o.value !== EDITING).map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
              <span aria-hidden>↔</span>
              <select value={rightVersion} onChange={(e) => setRight(Number(e.target.value))} aria-label="비교 대상">
                {options.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <DiffView before={textOf(leftVersion)} after={textOf(rightVersion)} />
          </div>
        </>
      )}
    </section>
  );
}

function Recommendations({
  question,
  onImport,
}: {
  question: EssayQuestion;
  onImport: (content: string, templateId: number | null) => void;
}) {
  const { data: templates } = useQuery({
    queryKey: ['templates', question.category],
    queryFn: () => templateApi.list(question.category),
  });
  const { data: pastAnswers } = useQuery({
    queryKey: ['answers', 'search', question.category, question.id],
    queryFn: () => essayApi.search({ category: question.category, excludeQuestionId: question.id }),
  });

  const empty = templates?.length === 0 && pastAnswers?.length === 0;

  return (
    <section className="panel recommend-panel">
      <div className="panel-title">
        <h2>추천 · {ESSAY_CATEGORY_LABEL[question.category]}</h2>
      </div>
      {empty && <p className="muted">같은 유형의 템플릿이나 과거 답변이 아직 없어요.</p>}
      {templates?.map((t) => (
        <article key={`t${t.id}`} className="recommend-item">
          <div className="recommend-head">
            <span className="tag tag-template">템플릿</span>
            <strong>{t.title}</strong>
          </div>
          <p className="recommend-snippet" title={t.content}>{t.content}</p>
          <button className="btn btn-ghost btn-sm" onClick={() => onImport(t.content, t.id)}>
            가져오기
          </button>
        </article>
      ))}
      {pastAnswers?.slice(0, 5).map((a) => (
        <article key={`a${a.answerId}`} className="recommend-item">
          <div className="recommend-head">
            <span className="tag">{a.company}</span>
            <span className="muted">v{a.version}{a.submitted ? ' 제출본' : ''}</span>
          </div>
          <p className="recommend-question">{a.question}</p>
          <p className="recommend-snippet" title={a.content}>{a.content}</p>
          <button className="btn btn-ghost btn-sm" onClick={() => onImport(a.content, null)}>
            가져오기
          </button>
        </article>
      ))}
    </section>
  );
}
