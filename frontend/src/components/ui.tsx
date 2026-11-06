import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import type { KitchenStatus, OrderStatus } from '../api/types';
import { errorText } from '../api/client';
import {
  KITCHEN_STATUS_CLASS,
  KITCHEN_STATUS_LABEL,
  ORDER_STATUS_CLASS,
  ORDER_STATUS_LABEL,
  countLabel,
  formatRating,
} from '../lib/format';

type Tone = 'warn' | 'danger' | 'info' | 'ok';
const ICON: Record<Tone, string> = { warn: '!', danger: '!', info: 'i', ok: '✓' };

export function Alert({ tone, title, children, action }: { tone: Tone; title?: ReactNode; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className={`alert ${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <span className="ic" aria-hidden="true">{ICON[tone]}</span>
      <div className="grow">
        {title && <b>{title} </b>}
        {children}
      </div>
      {action}
    </div>
  );
}

export function ErrorAlert({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null;
  return (
    <Alert
      tone="danger"
      action={onRetry && <button type="button" className="btn sm" onClick={onRetry}>Повторить</button>}
    >
      {errorText(error)}
    </Alert>
  );
}

export function Loading({ label = 'Загружаем…' }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" /> <span>{label}</span>
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`status ${ORDER_STATUS_CLASS[status]}`}>{ORDER_STATUS_LABEL[status]}</span>;
}

export function KitchenStatusBadge({ status }: { status: KitchenStatus }) {
  return <span className={`status ${KITCHEN_STATUS_CLASS[status]}`}>{KITCHEN_STATUS_LABEL[status]}</span>;
}

/** Фото блюда; при отсутствии или ошибке загрузки — стандартное изображение (FR-09). */
export function DishPhoto({ url, name, className = '', label }: { url: string | null; name: string; className?: string; label?: ReactNode }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [url]);
  const tone = hashTone(name);
  return (
    <div className={`photo ${className}`} style={{ background: tone }}>
      {url && !failed ? (
        <img src={url} alt={name} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} />
      ) : (
        <span className="placeholder" aria-hidden="true" />
      )}
      {label}
    </div>
  );
}

const TONES = [
  'linear-gradient(135deg,#d98e4a,#b5592b)',
  'linear-gradient(135deg,#8fb365,#557d3b)',
  'linear-gradient(135deg,#e0b85a,#c08a2b)',
  'linear-gradient(135deg,#c96f5a,#93412f)',
  'linear-gradient(135deg,#7fa9b8,#4d7887)',
  'linear-gradient(135deg,#b9a27c,#84704f)',
  'linear-gradient(135deg,#d6a3a0,#a96a66)',
];

function hashTone(text: string): string {
  let hash = 0;
  for (const char of text) hash = (hash * 31 + char.charCodeAt(0)) | 0;
  return TONES[Math.abs(hash) % TONES.length]!;
}

export function RatingLine({
  averageRating,
  reviewCount,
  repeatOrderPercent,
  repeatOrderResponses,
}: {
  averageRating: number | null;
  reviewCount: number;
  repeatOrderPercent: number | null;
  repeatOrderResponses: number;
}) {
  if (averageRating === null || reviewCount === 0) {
    return <div className="rating">Нет оценок</div>;
  }
  return (
    <div className="rating">
      <span className="star" aria-label={`Средняя оценка ${formatRating(averageRating)} из 5`}>★ {formatRating(averageRating)}</span>
      <span>{countLabel(reviewCount, ['отзыв', 'отзыва', 'отзывов'])}</span>
      {repeatOrderPercent !== null && (
        <span>
          {repeatOrderPercent}% заказали бы снова ({repeatOrderResponses})
        </span>
      )}
    </div>
  );
}

export function Stepper({
  value,
  min = 1,
  max = 99,
  onChange,
  disabled,
  label,
}: {
  value: number;
  min?: number;
  max?: number;
  onChange: (value: number) => void;
  disabled?: boolean;
  label: string;
}) {
  return (
    <div className="stepper" role="group" aria-label={label}>
      <button type="button" aria-label="Меньше" disabled={disabled || value <= min} onClick={() => onChange(value - 1)}>−</button>
      <output aria-live="polite">{value}</output>
      <button type="button" aria-label="Больше" disabled={disabled || value >= max} onClick={() => onChange(value + 1)}>+</button>
    </div>
  );
}

export function Field({
  label,
  error,
  hint,
  children,
  id,
}: {
  label: ReactNode;
  error?: string | null;
  hint?: ReactNode;
  id: string;
  children: ReactNode;
}) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <span className="err" id={`${id}-err`}>{error}</span>
      ) : (
        hint && <span className="hint" id={`${id}-hint`}>{hint}</span>
      )}
    </div>
  );
}

/** Свойства aria для поля с ошибкой/подсказкой. */
export function describe(id: string, error?: string | null, hint?: boolean) {
  return {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `${id}-err` : hint ? `${id}-hint` : undefined,
  } as const;
}

export function Modal({
  title,
  onClose,
  children,
  actions,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
}) {
  const titleId = useId();
  const ref = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const node = ref.current;
    const first = node?.querySelector<HTMLElement>('textarea, input, select, button.primary, button');
    first?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCloseRef.current();
      if (event.key === 'Tab' && node) {
        const focusable = [...node.querySelectorAll<HTMLElement>('button, [href], input, select, textarea')].filter(
          (el) => !el.hasAttribute('disabled'),
        );
        if (focusable.length === 0) return;
        const firstEl = focusable[0]!;
        const lastEl = focusable[focusable.length - 1]!;
        if (event.shiftKey && document.activeElement === firstEl) {
          event.preventDefault();
          lastEl.focus();
        } else if (!event.shiftKey && document.activeElement === lastEl) {
          event.preventDefault();
          firstEl.focus();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      previous?.focus?.();
    };
  }, []);

  return (
    <div className="backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={ref}>
        <h2 id={titleId}>{title}</h2>
        {children}
        {actions && <div className="actions">{actions}</div>}
      </div>
    </div>
  );
}

/** Окно с обязательной причиной (отказ кухни, блокировка, скрытие отзыва). */
export function ReasonModal({
  title,
  description,
  confirmLabel,
  onConfirm,
  onClose,
  max = 500,
}: {
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  onConfirm: (reason: string) => Promise<void>;
  onClose: () => void;
  max?: number;
}) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [failure, setFailure] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const fieldId = useId();

  const submit = async () => {
    const text = reason.trim();
    if (!text) return setError('Укажите причину');
    if (text.length > max) return setError(`Не больше ${max} символов`);
    setBusy(true);
    setFailure(null);
    try {
      await onConfirm(text);
    } catch (e) {
      setFailure(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={title}
      onClose={onClose}
      actions={
        <>
          <button type="button" className="btn" onClick={onClose}>Отмена</button>
          <button type="button" className="btn danger-solid" disabled={busy} onClick={submit}>
            {busy ? 'Сохраняем…' : confirmLabel}
          </button>
        </>
      }
    >
      {description && <p className="small muted" style={{ marginBottom: 12 }}>{description}</p>}
      <Field id={fieldId} label="Причина *" error={error} hint={`обязательно · до ${max} символов`}>
        <textarea
          className="input"
          {...describe(fieldId, error, true)}
          value={reason}
          maxLength={max}
          onChange={(event) => {
            setReason(event.target.value);
            setError(null);
          }}
        />
      </Field>
      <ErrorAlert error={failure} />
    </Modal>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) return null;
  const pages: number[] = [];
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  for (let i = start; i < Math.min(totalPages, start + 5); i++) pages.push(i);
  return (
    <nav className="pager" aria-label="Страницы">
      <button type="button" aria-label="Предыдущая страница" disabled={page === 0} onClick={() => onPage(page - 1)}>‹</button>
      {pages.map((p) => (
        <button key={p} type="button" aria-current={p === page ? 'page' : undefined} onClick={() => onPage(p)}>
          {p + 1}
        </button>
      ))}
      <button type="button" aria-label="Следующая страница" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>›</button>
    </nav>
  );
}
