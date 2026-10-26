import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { essayApi, templateApi } from '../api';
import { Header } from '../components/Header';
import { useToast } from '../components/Toast';
import { ESSAY_CATEGORIES, ESSAY_CATEGORY_LABEL } from '../constants';
import { useDebounce } from '../hooks/useDebounce';
import { formatDateTime } from '../lib/format';
import { countText } from '../lib/textCount';
import type { AnswerTemplate, EssayCategory, TemplateInput } from '../types';

/** 키워드 부분을 <mark> 로 강조 */
function Highlight({ text, keyword }: { text: string; keyword: string }) {
  const k = keyword.trim();
  if (!k) return <>{text}</>;
  const parts = text.split(new RegExp(`(${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi'));
  return <>{parts.map((p, i) => (p.toLowerCase() === k.toLowerCase() ? <mark key={i}>{p}</mark> : p))}</>;
}

export function LibraryPage() {
  const [category, setCategory] = useState<EssayCategory | ''>('');
  const [editing, setEditing] = useState<AnswerTemplate | 'new' | null>(null);
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounce(keyword);

  const templates = useQuery({
    queryKey: ['templates', category || 'all'],
    queryFn: () => templateApi.list(category || undefined),
  });
  const answers = useQuery({
    queryKey: ['answers', 'search', 'library', debouncedKeyword, category],
    queryFn: () => essayApi.search({ keyword: debouncedKeyword, category: category || undefined }),
  });

  return (
    <div className="page">
      <Header />
      <div className="detail library">
        <h1 className="settings-title">답변 라이브러리</h1>

        <div className="chip-group category-filter" role="tablist" aria-label="문항 유형">
          {(['', ...ESSAY_CATEGORIES] as const).map((c) => (
            <button
              key={c || 'all'}
              role="tab"
              aria-selected={category === c}
              className={`chip ${category === c ? 'chip-on' : ''}`}
              onClick={() => setCategory(c)}
            >
              {c ? ESSAY_CATEGORY_LABEL[c] : '전체'}
            </button>
          ))}
        </div>

        <div className="library-layout">
          <section className="panel">
            <div className="panel-title">
              <h2>템플릿 {templates.data && <span className="muted">{templates.data.length}</span>}</h2>
              <button className="btn btn-primary btn-sm" onClick={() => setEditing('new')}>
                + 새 템플릿
              </button>
            </div>
            {editing === 'new' && (
              <TemplateForm initialCategory={category || 'MOTIVATION'} onDone={() => setEditing(null)} />
            )}
            {templates.data?.length === 0 && editing !== 'new' && (
              <p className="muted">잘 쓴 답변을 템플릿으로 저장해 두면 새 문항을 쓸 때 추천돼요. 자소서 편집 화면의 [라이브러리에 저장]으로도 만들 수 있어요.</p>
            )}
            <div className="template-list">
              {templates.data?.map((t) =>
                editing !== 'new' && editing?.id === t.id ? (
                  <TemplateForm key={t.id} template={t} initialCategory={t.category} onDone={() => setEditing(null)} />
                ) : (
                  <TemplateCard key={t.id} template={t} onEdit={() => setEditing(t)} />
                ),
              )}
            </div>
          </section>

          <section className="panel">
            <div className="panel-title">
              <h2>과거 답변 검색</h2>
            </div>
            <input
              type="search"
              placeholder="키워드로 내가 쓴 답변 찾기 (예: 협업, 트래픽)"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              aria-label="과거 답변 검색"
            />
            <p className="hint">문항마다 가장 최근 버전에서 찾아요{category && ` · ${ESSAY_CATEGORY_LABEL[category]}만`}.</p>
            {answers.data?.length === 0 && <p className="muted">찾은 답변이 없어요.</p>}
            <ul className="answer-results">
              {answers.data?.map((a) => (
                <li key={a.answerId} className="answer-result">
                  <div className="recommend-head">
                    <Link to={`/postings/${a.postingId}?tab=essay`} className="answer-company">
                      {a.company} · {a.postingTitle}
                    </Link>
                    <span className="tag">{ESSAY_CATEGORY_LABEL[a.category]}</span>
                    <span className="muted">
                      v{a.version}
                      {a.submitted ? ' 제출본' : ''} · {a.charCount}자
                    </span>
                  </div>
                  <p className="recommend-question">{a.question}</p>
                  <p className="answer-snippet">
                    <Highlight text={a.content} keyword={debouncedKeyword} />
                  </p>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}

function TemplateCard({ template, onEdit }: { template: AnswerTemplate; onEdit: () => void }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const remove = useMutation({
    mutationFn: () => templateApi.remove(template.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['templates'] }),
    onError: (e) => toast(e.message),
  });
  return (
    <article className="template-card">
      <div className="recommend-head">
        <span className="tag">{ESSAY_CATEGORY_LABEL[template.category]}</span>
        <strong>{template.title}</strong>
      </div>
      <p className="answer-snippet">{template.content}</p>
      <div className="template-foot">
        <span className="muted">
          {countText(template.content).withSpaces}자 · {formatDateTime(template.updatedAt)}
        </span>
        <span>
          <button className="btn btn-ghost btn-sm" onClick={onEdit}>
            수정
          </button>{' '}
          <button
            className="btn btn-danger btn-sm"
            onClick={() => window.confirm(`'${template.title}' 템플릿을 삭제할까요?`) && remove.mutate()}
          >
            삭제
          </button>
        </span>
      </div>
    </article>
  );
}

function TemplateForm({
  template,
  initialCategory,
  onDone,
}: {
  template?: AnswerTemplate;
  initialCategory: EssayCategory;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<TemplateInput>({
    category: template?.category ?? initialCategory,
    title: template?.title ?? '',
    content: template?.content ?? '',
  });
  const save = useMutation({
    mutationFn: () => (template ? templateApi.update(template.id, form) : templateApi.create(form)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      onDone();
    },
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <form className="template-form" onSubmit={submit}>
      <div className="question-form-row">
        <label className="field">
          <span>제목</span>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required maxLength={200} autoFocus />
        </label>
        <label className="field">
          <span>유형</span>
          <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value as EssayCategory })}>
            {ESSAY_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {ESSAY_CATEGORY_LABEL[c]}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="field">
        <span>내용 ({countText(form.content).withSpaces}자)</span>
        <textarea rows={6} value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} required maxLength={20000} />
      </label>
      {save.error && <p className="form-error">{save.error.message}</p>}
      <div className="question-form-actions">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onDone}>
          취소
        </button>
        <button type="submit" className="btn btn-primary btn-sm" disabled={save.isPending}>
          저장
        </button>
      </div>
    </form>
  );
}
