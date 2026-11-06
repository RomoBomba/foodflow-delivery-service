import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import type { UserRole } from '../api/types';
import { homePathFor, useAuth } from '../state/auth';
import { Empty, Loading } from './ui';

const ROLE_LABEL: Record<UserRole, string> = { CLIENT: 'клиента', KITCHEN: 'кухни', ADMIN: 'администратора' };

/** Клиентская проверка роли — только для навигации; права проверяет сервер (FR-02, NFR-03). */
export function RequireRole({ roles, children }: { roles: UserRole[]; children: ReactNode }) {
  const { user, checking } = useAuth();
  const location = useLocation();

  if (checking) return <Loading label="Проверяем сессию…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (!roles.includes(user.role)) {
    return (
      <Empty title="Раздел недоступен для этой роли">
        <p>
          Страница предназначена для {roles.map((r) => ROLE_LABEL[r]).join(' или ')}. Вы вошли как {user.role}.
        </p>
        {user.role !== 'CLIENT' && roles.includes('CLIENT') && (
          <p className="mt-8 small">Аккаунт кухни — отдельный кабинет: для заказов как клиент используйте другой аккаунт.</p>
        )}
        <p className="mt-16"><Link className="btn" to={homePathFor(user.role)}>На главную своего кабинета</Link></p>
      </Empty>
    );
  }
  return <>{children}</>;
}
