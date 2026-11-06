import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, isApiError } from '../api/client';
import { kitchenApi } from '../api/endpoints';
import type { Kitchen, KitchenApplication } from '../api/types';
import { Alert, ErrorAlert, Field, KitchenStatusBadge, Loading, describe } from '../components/ui';
import { formatDateTime } from '../lib/format';
import { useAsync, useDocumentTitle } from '../lib/hooks';
import { hasErrors, validateRequired, type FieldErrors } from '../lib/validation';
import { useAuth } from '../state/auth';

const LIMITS: Record<keyof KitchenApplication, number> = { name: 120, description: 1000, contacts: 500, deliveryArea: 200 };
const LABELS: Record<keyof KitchenApplication, string> = {
  name: 'Название кухни',
  description: 'Описание',
  contacts: 'Контакты',
  deliveryArea: 'Район доставки',
};

export function KitchenApplyPage() {
  useDocumentTitle('Открыть свою кухню');
  const mine = useAsync<Kitchen | null>(async () => {
    try {
      return await kitchenApi.mine();
    } catch (error) {
      if (isApiError(error) && error.status === 404) return null;
      throw error;
    }
  }, []);

  if (mine.loading && mine.data === undefined) return <Loading />;
  if (mine.data === undefined) return <ErrorAlert error={mine.error} onRetry={mine.reload} />;

  return (
    <div className="narrow" style={{ maxWidth: 680 }}>
      <div className="page-head">
        <div>
          <h1>Открыть свою кухню</h1>
          <p className="sub">Заявку проверит администратор. До одобрения кухня не видна в каталоге.</p>
        </div>
      </div>
      {mine.data ? <ApplicationStatus kitchen={mine.data} /> : <ApplicationForm onCreated={(k) => mine.setData(k)} />}
    </div>
  );
}

function ApplicationStatus({ kitchen }: { kitchen: Kitchen }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <section className="panel" aria-labelledby="app-title">
      <div className="row between top wrap">
        <h2 id="app-title" style={{ fontSize: 20 }}>{kitchen.name}</h2>
        <KitchenStatusBadge status={kitchen.status} />
      </div>
      <dl className="kv mt-16">
        <dt>Описание</dt><dd>{kitchen.description}</dd>
        <dt>Контакты</dt><dd>{kitchen.contacts}</dd>
        <dt>Район</dt><dd>{kitchen.deliveryArea}</dd>
        <dt>Подана</dt><dd>{formatDateTime(kitchen.createdAt)}</dd>
      </dl>
      <div className="mt-16">
        {kitchen.status === 'PENDING' && <Alert tone="info" title="На проверке.">Ждём решения администратора. Обновите страницу позже.</Alert>}
        {kitchen.status === 'APPROVED' && user?.role === 'CLIENT' && (
          <Alert
            tone="ok"
            title="Заявка одобрена."
            action={
              <button
                type="button"
                className="btn sm primary"
                onClick={async () => {
                  await logout();
                  navigate('/login', { state: { from: '/kitchen' } });
                }}
              >
                Войти снова
              </button>
            }
          >
            Аккаунт получил роль кухни. Войдите снова, чтобы открыть кабинет кухни.
          </Alert>
        )}
        {kitchen.status === 'APPROVED' && user?.role === 'KITCHEN' && (
          <Alert tone="ok" title="Кухня работает.">
            <Link to="/kitchen">Перейти в кабинет кухни</Link>
          </Alert>
        )}
        {kitchen.status === 'REJECTED' && (
          <Alert tone="danger" title="Заявка отклонена.">
            {kitchen.moderationReason ? `Причина: ${kitchen.moderationReason}.` : ''} Повторная заявка с этого аккаунта невозможна.
          </Alert>
        )}
        {kitchen.status === 'BLOCKED' && (
          <Alert tone="danger" title="Кухня заблокирована.">
            {kitchen.moderationReason ? `Причина: ${kitchen.moderationReason}.` : ''} Приём новых заказов закрыт.
          </Alert>
        )}
      </div>
    </section>
  );
}

function ApplicationForm({ onCreated }: { onCreated: (kitchen: Kitchen) => void }) {
  const [form, setForm] = useState<KitchenApplication>({ name: '', description: '', contacts: '', deliveryArea: '' });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [failure, setFailure] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const next: FieldErrors = {};
    (Object.keys(LIMITS) as (keyof KitchenApplication)[]).forEach((key) => {
      const error = validateRequired(form[key], LIMITS[key], LABELS[key]);
      if (error) next[key] = error;
    });
    setErrors(next);
    if (hasErrors(next)) return;
    setBusy(true);
    setFailure(null);
    try {
      const kitchen = await kitchenApi.apply({
        name: form.name.trim(),
        description: form.description.trim(),
        contacts: form.contacts.trim(),
        deliveryArea: form.deliveryArea.trim(),
      });
      onCreated(kitchen);
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fields).length) setErrors(error.fields);
      else setFailure(error);
    } finally {
      setBusy(false);
    }
  };

  const input = (key: keyof KitchenApplication, area = false) => {
    const props = {
      className: 'input',
      maxLength: LIMITS[key],
      value: form[key],
      ...describe(`k-${key}`, errors[key], true),
      onChange: (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value }),
    };
    return area ? <textarea {...props} /> : <input {...props} />;
  };

  return (
    <form className="panel" onSubmit={submit} noValidate>
      <Field id="k-name" label="Название кухни *" error={errors.name} hint="до 120 символов">{input('name')}</Field>
      <Field id="k-description" label="Описание *" error={errors.description} hint="до 1000 символов">{input('description', true)}</Field>
      <Field id="k-contacts" label="Контакты *" error={errors.contacts} hint="телефон, email — до 500 символов">{input('contacts')}</Field>
      <Field id="k-deliveryArea" label="Район доставки *" error={errors.deliveryArea} hint="до 200 символов">{input('deliveryArea')}</Field>
      <ErrorAlert error={failure} />
      <button type="submit" className="btn primary lg block mt-12" disabled={busy}>{busy ? 'Отправляем…' : 'Отправить заявку'}</button>
      <p className="tiny muted mt-12">Один аккаунт может подать одну заявку. После одобрения аккаунт станет кабинетом кухни.</p>
    </form>
  );
}
