import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { BoardPage } from './pages/BoardPage';
import { LibraryPage } from './pages/LibraryPage';
import { LoginPage } from './pages/LoginPage';
import { PostingDetailPage } from './pages/PostingDetailPage';
import { SettingsPage } from './pages/SettingsPage';

// 차트 라이브러리(Recharts)는 대시보드에서만 쓰므로 따로 나눠 받는다
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="loading">불러오는 중…</div>;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RequireAuth><BoardPage /></RequireAuth>} />
      <Route path="/postings/:id" element={<RequireAuth><PostingDetailPage /></RequireAuth>} />
      <Route path="/settings" element={<RequireAuth><SettingsPage /></RequireAuth>} />
      <Route path="/dashboard" element={<RequireAuth><Suspense fallback={<div className="loading">불러오는 중…</div>}><DashboardPage /></Suspense></RequireAuth>} />
      <Route path="/library" element={<RequireAuth><LibraryPage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
