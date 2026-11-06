import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../state/auth';
import { useCart } from '../state/cart';
import { countLabel, formatMoney } from '../lib/format';
import { useToast } from './toast';
import { errorText } from '../api/client';

interface NavItem {
  to: string;
  label: string;
  end?: boolean;
}

export function Layout() {
  const { user, logout } = useAuth();
  const { cart, enabled: cartEnabled, portions } = useCart();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const toast = useToast();

  useEffect(() => setOpen(false), [location.pathname]);

  let nav: NavItem[] = [{ to: '/', label: 'Каталог', end: true }];
  if (user?.role === 'CLIENT') {
    nav = [...nav, { to: '/orders', label: 'Мои заказы' }, { to: '/preferences', label: 'Предпочтения' }, { to: '/kitchen/apply', label: 'Открыть свою кухню' }];
  } else if (user?.role === 'KITCHEN') {
    nav = [
      { to: '/kitchen', label: 'Заказы', end: true },
      { to: '/kitchen/menu', label: 'Меню и остатки' },
      { to: '/kitchen/reviews', label: 'Отзывы' },
      { to: '/kitchen/profile', label: 'Профиль кухни' },
      { to: '/', label: 'Каталог', end: true },
    ];
  } else if (user?.role === 'ADMIN') {
    nav = [
      { to: '/admin', label: 'Кухни', end: true },
      { to: '/admin/reviews', label: 'Отзывы' },
      { to: '/', label: 'Каталог', end: true },
    ];
  }

  const onLogout = async () => {
    try {
      await logout();
      navigate('/');
    } catch (error) {
      toast(errorText(error), 'danger');
    }
  };

  return (
    <>
      <a className="skip-link" href="#main">Перейти к содержимому</a>
      <header className={`topbar${open ? ' open' : ''}`}>
        <Link to={user?.role === 'KITCHEN' ? '/kitchen' : user?.role === 'ADMIN' ? '/admin' : '/'} className="logo" aria-label="FoodFlow — на главную">
          <i aria-hidden="true">F</i>FoodFlow
        </Link>
        {user && user.role !== 'CLIENT' && (
          <span className="role">{user.role === 'KITCHEN' ? 'кухня' : 'админ'}</span>
        )}
        <nav className="nav" id="main-nav" aria-label="Основное меню">
          {nav.map((item) => (
            <NavLink key={item.to + item.label} to={item.to} end={item.end}>
              {item.label}
            </NavLink>
          ))}
          {user && (
            <button type="button" className="btn ghost sm mobile-logout" onClick={onLogout} style={{ justifyContent: 'flex-start' }}>
              Выйти ({user.email})
            </button>
          )}
        </nav>
        <div className="right">
          {cartEnabled && (
            <NavLink to="/cart" className={({ isActive }) => `cart-btn${isActive ? ' active' : ''}`} aria-label={`Корзина: ${countLabel(portions, ['порция', 'порции', 'порций'])}`}>
              Корзина{cart.items.length > 0 && <span className="desk-total"> · {formatMoney(cart.totalPrice)}</span>}
              {portions > 0 && <span className="badge">{portions}</span>}
            </NavLink>
          )}
          {user ? (
            <div className="user">
              <div className="avatar" aria-hidden="true">{user.email.charAt(0)}</div>
              <span className="email">{user.email}</span>
              <span className="role">{user.role}</span>
              <button type="button" className="btn sm" onClick={onLogout}>Выйти</button>
            </div>
          ) : (
            <>
              <Link className="btn sm" to="/login" state={{ from: location.pathname + location.search }}>Войти</Link>
              <Link className="btn primary sm" to="/register">Регистрация</Link>
            </>
          )}
          <button
            type="button"
            className="btn sm menu-toggle"
            aria-expanded={open}
            aria-controls="main-nav"
            onClick={() => setOpen((value) => !value)}
          >
            {open ? 'Закрыть' : 'Меню'}
          </button>
        </div>
      </header>
      <main className="page" id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        FoodFlow
      </footer>
    </>
  );
}
