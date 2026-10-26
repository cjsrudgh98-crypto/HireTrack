import { useState, type ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const NAV = [
  { to: '/', label: '보드', end: true },
  { to: '/dashboard', label: '대시보드', end: false },
  { to: '/library', label: '라이브러리', end: false },
  { to: '/settings', label: '설정', end: false },
];

export function Header({ children }: { children?: ReactNode }) {
  const { user, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="header">
      <Link to="/" className="brand">
        <span className="brand-mark">H</span>
        <span className="brand-text">지원 트래커</span>
      </Link>
      <nav className="nav" aria-label="주 메뉴">
        {NAV.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="header-actions">{children}</div>
      <div className="user-menu">
        <button
          className="avatar"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label="내 정보"
          aria-expanded={menuOpen}
        >
          {user?.email.charAt(0).toUpperCase()}
        </button>
        {menuOpen && (
          <div className="user-dropdown" onMouseLeave={() => setMenuOpen(false)}>
            <div className="user-email">{user?.email}</div>
            <button className="btn btn-ghost btn-block" onClick={logout}>
              로그아웃
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
