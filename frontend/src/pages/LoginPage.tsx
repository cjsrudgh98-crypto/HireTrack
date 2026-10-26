import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { user, login, signup } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await (mode === 'login' ? login(email, password) : signup(email, password));
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '요청에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const switchMode = (next: 'login' | 'signup') => {
    setMode(next);
    setError(null);
  };

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <div className="brand brand-lg">
          <span className="brand-mark">H</span>지원 트래커
        </div>
        <p className="auth-sub">채용 공고와 지원 현황을 한 보드에서 관리하세요.</p>

        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'tab active' : 'tab'} onClick={() => switchMode('login')}>
            로그인
          </button>
          <button type="button" role="tab" aria-selected={mode === 'signup'} className={mode === 'signup' ? 'tab active' : 'tab'} onClick={() => switchMode('signup')}>
            회원가입
          </button>
        </div>

        <label className="field">
          <span>이메일</span>
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </label>
        <label className="field">
          <span>비밀번호</span>
          <input
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={mode === 'signup' ? 8 : undefined}
            maxLength={64}
            required
          />
          {mode === 'signup' && <small className="hint">8자 이상 입력해 주세요.</small>}
        </label>

        {error && <p className="form-error">{error}</p>}

        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? '처리 중…' : mode === 'login' ? '로그인' : '가입하고 시작하기'}
        </button>
      </form>
    </main>
  );
}
