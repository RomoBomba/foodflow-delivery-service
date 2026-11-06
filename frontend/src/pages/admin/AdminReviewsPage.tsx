import { useEffect, useState } from 'react';
import { adminApi, catalogApi } from '../../api/endpoints';
import type { Review } from '../../api/types';
import { useToast } from '../../components/toast';
import { Empty, ErrorAlert, Loading, ReasonModal } from '../../components/ui';
import { formatDate, formatDateTime } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/hooks';

type Filter = 'all' | 'visible' | 'hidden';
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'visible', label: 'Видимые' },
  { value: 'hidden', label: 'Скрытые' },
  { value: 'all', label: 'Все' },
];

/** Название блюда по dishId: в ReviewResponse его нет, берём из публичной карточки. */
function useDishNames(ids: string[]) {
  const [names, setNames] = useState<Record<string, string>>({});
  const key = [...new Set(ids)].sort().join(',');
  useEffect(() => {
    const missing = key.split(',').filter((id) => id && !(id in names));
    if (missing.length === 0) return;
    let cancelled = false;
    Promise.all(
      missing.map((id) =>
        catalogApi
          .dish(id)
          .then((dish) => [id, `${dish.name} · ${dish.kitchenName}`] as const)
          .catch(() => [id, 'Блюдо снято с продажи'] as const),
      ),
    ).then((pairs) => {
      if (!cancelled) setNames((current) => ({ ...current, ...Object.fromEntries(pairs) }));
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return names;
}

export function AdminReviewsPage() {
  useDocumentTitle('Модерация отзывов');
  const [filter, setFilter] = useState<Filter>('visible');
  const reviews = useAsync(() => adminApi.reviews(filter === 'all' ? undefined : filter === 'visible'), [filter]);
  const names = useDishNames((reviews.data ?? []).map((r) => r.dishId));
  const [hiding, setHiding] = useState<Review | null>(null);
  const toast = useToast();

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Отзывы</h1>
          <p className="sub">Скрытие — с причиной. Оценки покупателей не редактируются; скрытый отзыв не входит в агрегаты.</p>
        </div>
      </div>
      <div className="tabs" role="tablist" aria-label="Фильтр отзывов">
        {FILTERS.map((f) => (
          <button key={f.value} type="button" role="tab" aria-selected={filter === f.value} onClick={() => setFilter(f.value)}>{f.label}</button>
        ))}
      </div>
      <ErrorAlert error={reviews.error} onRetry={reviews.reload} />
      {reviews.loading && !reviews.data && <Loading />}
      {reviews.data && reviews.data.length === 0 && <Empty title="Отзывов нет" />}
      {reviews.data && reviews.data.length > 0 && (
        <section className="panel" style={{ paddingTop: 4, paddingBottom: 4 }} role="tabpanel">
          {reviews.data.map((review) => (
            <article className="rev" key={review.id} style={review.visible ? undefined : { background: 'var(--surface-2)', margin: '0 -20px', padding: '14px 20px' }}>
              <div className="row between wrap top">
                <span>
                  <span className="stars" aria-label={`Оценка ${review.rating} из 5`}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
                  {' · '}<b>{names[review.dishId] ?? '…'}</b>
                </span>
                <span className="row" style={{ gap: 8 }}>
                  {!review.visible && <span className="status s-cancelled">Скрыт</span>}
                  <span className="tiny muted">{formatDate(review.createdAt)}</span>
                </span>
              </div>
              {review.text ? (
                <p className="mt-8" style={{ whiteSpace: 'pre-line' }}>{review.text}</p>
              ) : (
                <p className="mt-8 muted small">Без текста</p>
              )}
              <div className="row between wrap mt-8">
                <span className="tiny muted">
                  Заказали бы снова: {review.reordered ? 'да' : 'нет'}
                  {!review.visible && review.moderatedAt && <> · скрыт {formatDateTime(review.moderatedAt)} · причина: «{review.moderationReason}»</>}
                </span>
                {review.visible && (
                  <button type="button" className="btn danger sm" onClick={() => setHiding(review)}>Скрыть…</button>
                )}
              </div>
            </article>
          ))}
        </section>
      )}

      {hiding && (
        <ReasonModal
          title="Скрыть отзыв?"
          description="Отзыв останется в базе с записью модерации, но пропадёт из карточки блюда и агрегатов."
          confirmLabel="Скрыть отзыв"
          onClose={() => setHiding(null)}
          onConfirm={async (reason) => {
            await adminApi.hideReview(hiding.id, reason);
            toast('Отзыв скрыт', 'ok');
            setHiding(null);
            void reviews.reload();
          }}
        />
      )}
    </>
  );
}
