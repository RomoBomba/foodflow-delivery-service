import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getCsrf, isApiError, onUnauthorized, resetCsrf } from '../api/client';
import { authApi } from '../api/endpoints';
import type { User, UserRole } from '../api/types';

interface AuthContextValue {
  user: User | null;
  /** Первичная проверка сессии ещё идёт. */
  checking: boolean;
  /** Сессия истекла во время работы — показываем пояснение на странице входа. */
  expired: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  refresh: () => Promise<User | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(true);
  const [expired, setExpired] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const me = await authApi.me();
      setUser(me);
      return me;
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        setUser(null);
        return null;
      }
      throw error;
    }
  }, []);

  useEffect(() => {
    refresh()
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
  }, [refresh]);

  useEffect(
    () =>
      onUnauthorized(() => {
        setUser((current) => {
          if (current) setExpired(true);
          return null;
        });
      }),
    [],
  );

  const login = useCallback(
    async (email: string, password: string) => {
      // Токен до входа и после входа разный: старый после входа отвергается (docs/API.md).
      await getCsrf(true);
      await authApi.login(email.trim(), password);
      resetCsrf();
      const me = await refresh();
      if (!me) throw new Error('Не удалось открыть сессию. Повторите вход.');
      setExpired(false);
      return me;
    },
    [refresh],
  );

  const register = useCallback(async (email: string, password: string) => {
    await getCsrf();
    return authApi.register(email.trim(), password);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      resetCsrf();
      setUser(null);
      setExpired(false);
    }
  }, []);

  const value = useMemo(
    () => ({ user, checking, expired, login, register, logout, refresh }),
    [user, checking, expired, login, register, logout, refresh],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth вне AuthProvider');
  return context;
}

export function homePathFor(role: UserRole | undefined): string {
  if (role === 'KITCHEN') return '/kitchen';
  if (role === 'ADMIN') return '/admin';
  return '/';
}
