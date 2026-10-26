import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '../api';
import { setUnauthorizedHandler, tokenStorage } from '../api/client';
import type { User } from '../types';

interface AuthState {
  user: User | null;
  /** 저장된 토큰으로 /me 를 확인하는 중인지 */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => tokenStorage.get() !== null);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (!tokenStorage.get()) return;
    authApi
      .me()
      .then(setUser)
      // 401 이면 위 핸들러가 로그아웃시킨다. 서버가 잠깐 안 뜬 것(네트워크·5xx)으로 토큰까지 지우지는 않는다
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [logout]);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      logout,
      login: async (email, password) => {
        const res = await authApi.login(email, password);
        tokenStorage.set(res.accessToken);
        setUser(res.user);
      },
      signup: async (email, password) => {
        const res = await authApi.signup(email, password);
        tokenStorage.set(res.accessToken);
        setUser(res.user);
      },
    }),
    [user, loading, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
