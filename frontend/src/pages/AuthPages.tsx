import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, errorText } from '../api/client';
import { Alert, Field, describe } from '../components/ui';
import { useDocumentTitle } from '../lib/hooks';
import { hasErrors, validateEmail, validatePassword, type FieldErrors } from '../lib/validation';
import { homePathFor, useAuth } from '../state/auth';
import type { User } from '../api/types';

function targetAfterLogin(user: User, from?: string): string {
  // Возвращаем на исходную страницу, только если она подходит роли.
  if (from && from !== '/login' && from !== '/register') {
    if (user.role === 'CLIENT' && !from.startsWith('/kitchen') && !from.startsWith('/admin')) return from;
    if (user.role === 'CLIENT' && from === '/kitchen/apply') return from;
    if (user.role === 'KITCHEN' && from.startsWith('/kitchen')) return from;
    if (user.role === 'ADMIN' && from.startsWith('/admin')) return from;
  }
  return homePathFor(user.role);
}

export function LoginPage() {
  useDocumentTitle('Вход');
  const { user, login, expired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user && !busy) return <Navigate to={targetAfterLogin(user, from)} replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const next: FieldErrors = {};
    const emailError = validateEmail(email);
    if (emailError) next.email = emailError;
    if (!password) next.password = 'Введите пароль';
    setErrors(next);
    if (hasErrors(next)) return;
    setBusy(true);
    setFailure(null);
    try {
      const me = await login(email, password);
      navigate(targetAfterLogin(me, from), { replace: true });
    } catch (error) {
      setFailure(error instanceof ApiError && error.status === 401 ? 'Неверный email или пароль' : errorText(error));
      setBusy(false);
    }
  };

  return (
    <div className="narrow" style={{ maxWidth: 440 }}>
      <form className="panel" onSubmit={submit} noValidate aria-labelledby="login-title">
        <h1 id="login-title" style={{ fontSize: 24, marginBottom: 4 }}>Вход</h1>
        <p className="muted small" style={{ marginBottom: 18 }}>Войдите, чтобы оформить заказ и сохранить корзину.</p>
        {expired && (
          <div style={{ marginBottom: 14 }}>
            <Alert tone="info">Сессия завершилась после 30 минут бездействия. Войдите снова — корзина сохранена.</Alert>
          </div>
        )}
        <Field id="login-email" label="Email" error={errors.email}>
          <input className="input" type="email" autoComplete="email" {...describe('login-email', errors.email)} value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field id="login-password" label="Пароль" error={errors.password}>
          <input className="input" type="password" autoComplete="current-password" {...describe('login-password', errors.password)} value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {failure && <div style={{ marginBottom: 14 }}><Alert tone="danger">{failure}</Alert></div>}
        <button type="submit" className="btn primary lg block" disabled={busy}>{busy ? 'Входим…' : 'Войти'}</button>
        <p className="small muted center mt-16">
          Нет аккаунта? <Link to="/register" state={{ from }}>Зарегистрироваться</Link>
        </p>
      </form>
    </div>
  );
}

export function RegisterPage() {
  useDocumentTitle('Регистрация');
  const { user, register, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user && !busy) return <Navigate to={homePathFor(user.role)} replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const next: FieldErrors = {};
    const emailError = validateEmail(email);
    if (emailError) next.email = emailError;
    const passwordError = validatePassword(password);
    if (passwordError) next.password = passwordError;
    if (!passwordError && repeat !== password) next.repeat = 'Пароли не совпадают';
    setErrors(next);
    if (hasErrors(next)) return;
    setBusy(true);
    setFailure(null);
    try {
      await register(email, password);
    } catch (error) {
      setBusy(false);
      if (error instanceof ApiError && error.status === 409) {
        setErrors({ email: 'Этот email уже зарегистрирован' });
      } else if (error instanceof ApiError && Object.keys(error.fields).length > 0) {
        setErrors(error.fields);
      } else {
        setFailure(errorText(error));
      }
      return;
    }
    // Регистрация не открывает сессию — входим теми же данными.
    try {
      const me = await login(email, password);
      navigate(targetAfterLogin(me, from), { replace: true });
    } catch {
      navigate('/login', { replace: true });
    }
  };

  return (
    <div className="narrow" style={{ maxWidth: 440 }}>
      <form className="panel" onSubmit={submit} noValidate aria-labelledby="reg-title">
        <h1 id="reg-title" style={{ fontSize: 24, marginBottom: 4 }}>Регистрация</h1>
        <p className="muted small" style={{ marginBottom: 18 }}>
          Аккаунт клиента. Открыть свою кухню можно после регистрации.
        </p>
        <Field id="reg-email" label="Email" error={errors.email}>
          <input className="input" type="email" autoComplete="email" maxLength={254} {...describe('reg-email', errors.email)} value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field id="reg-password" label="Пароль" error={errors.password} hint="8–128 символов">
          <input className="input" type="password" autoComplete="new-password" maxLength={128} {...describe('reg-password', errors.password, true)} value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field id="reg-repeat" label="Повторите пароль" error={errors.repeat}>
          <input className="input" type="password" autoComplete="new-password" maxLength={128} {...describe('reg-repeat', errors.repeat)} value={repeat} onChange={(e) => setRepeat(e.target.value)} />
        </Field>
        {failure && <div style={{ marginBottom: 14 }}><Alert tone="danger">{failure}</Alert></div>}
        <button type="submit" className="btn primary lg block" disabled={busy}>{busy ? 'Создаём…' : 'Создать аккаунт'}</button>
        <p className="small muted center mt-16">
          Уже есть аккаунт? <Link to="/login" state={{ from }}>Войти</Link>
        </p>
      </form>
    </div>
  );
}
