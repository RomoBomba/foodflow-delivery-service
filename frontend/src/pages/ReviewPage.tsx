import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError, isApiError } from '../api/client';
import { catalogApi, ordersApi } from '../api/endpoints';
import type { Order, Review, ReviewItemRequest } from '../api/types';
import { useToast } from '../components/toast';
import { Alert, DishPhoto, Empty, ErrorAlert, Loading, OrderStatusBadge } from '../components/ui';
import { formatDate, formatDateTime, formatMoney } from '../lib/format';
import { useAsync, useDocumentTitle } from '../lib/hooks';
import { useDictionaries } from '../state/dictionaries';

interface ItemDraft {
  rating: number;
  reordered: boolean | null;
  text: string;
}

interface Loaded {
  order: Order;
  /** Уже опубликованные отзывы по позициям этого заказа (отдельного эндпоинта нет — берём публичные отзывы блюд). */
  existing: Map<string, Review>;
}

export function ReviewPage() {
  useDocumentTitle('Оценка заказа');
  const { orderId = '' } = useParams();
  const toast = useToast();
  const { kitchenName } = useDictionaries();
  const data = useAsync<Loaded>(async () => {
    const order = await ordersApi.get(orderId);
    const itemIds = new Set(order.items.map((i) => i.id));
    const dishIds = [...new Set(order.items.map((i) => i.dishId))];
    const lists = await Promise.all(dishIds.map((dishId) => catalogApi.reviews(dishId).catch(() => [] as Review[])));
    const existing = new Map<string, Review>();
    lists.flat().forEach((review) => {
      if (itemIds.has(review.orderItemId)) existing.set(review.orderItemId, review);
    });
    return { order, existing };
  }, [orderId]);
  const [drafts, setDrafts] = useState<Record<string, ItemDraft>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  if (data.loading && !data.data) return <Loading />;
  if (isApiError(data.error) && data.error.status === 404) {
    return <Empty title="Заказ не найден"><p><Link to="/orders">Вернуться к заказам</Link></p></Empty>;
  }
  if (!data.data) return <ErrorAlert error={data.error} onRetry={data.reload} />;

  const { order, existing } = data.data;
  const open = order.items.filter((item) => !existing.has(item.id));
  const draftOf = (id: string): ItemDraft => drafts[id] ?? { rating: 0, reordered: null, text: '' };
  const setDraft = (id: string, change: Partial<ItemDraft>) => {
    setDrafts((all) => ({ ...all, [id]: { ...draftOf(id), ...change } }));
    setErrors(({ [id]: _drop, ...rest }) => rest);
  };

  const submit = async () => {
    const nextErrors: Record<string, string> = {};
    const items: ReviewItemRequest[] = [];
    for (const item of open) {
      const draft = drafts[item.id];
      const touched = draft && (draft.rating > 0 || draft.reordered !== null || draft.text.trim() !== '');
      if (!touched) continue;
      if (draft.rating < 1) nextErrors[item.id] = 'Поставьте оценку от 1 до 5';
      else if (draft.reordered === null) nextErrors[item.id] = 'Ответьте, заказали бы снова';
      else if (draft.text.length > 1000) nextErrors[item.id] = 'Текст не длиннее 1000 символов';
      else items.push({ orderItemId: item.id, rating: draft.rating, reordered: draft.reordered, text: draft.text.trim() || null });
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    if (items.length === 0) {
      setSubmitError(new ApiError(400, { message: 'Оцените хотя бы одно блюдо.' }, null));
      return;
    }
    setBusy(true);
    setSubmitError(null);
    try {
      await ordersApi.review(order.id, items);
      toast('Спасибо! Отзывы опубликованы', 'ok');
      setSent(true);
      setDrafts({});
      await data.reload();
    } catch (error) {
      setSubmitError(error);
      if (isApiError(error, 'REVIEW_ALREADY_EXISTS')) await data.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="narrow">
      <nav className="crumbs" aria-label="Навигация">
        <Link to="/orders">Мои заказы</Link> / <Link to={`/orders/${order.id}`}>Заказ от {formatDateTime(order.createdAt)}</Link>
      </nav>
      <div className="page-head">
        <div>
          <h1>Оцените блюда</h1>
          <p className="sub row wrap" style={{ gap: 8 }}>
            {kitchenName(order.kitchenId) ?? 'Кухня'} · <OrderStatusBadge status={order.status} />
          </p>
        </div>
      </div>

      {order.status !== 'COMPLETED' ? (
        <Alert tone="info" title="Пока рано.">Оценить блюда можно после того, как заказ будет выполнен.</Alert>
      ) : (
        <section className="panel">
          {order.items.map((item) => {
            const done = existing.get(item.id);
            const draft = draftOf(item.id);
            const error = errors[item.id];
            const nameId = `item-${item.id}`;
            return (
              <div className="review-item" key={item.id} role="group" aria-labelledby={nameId}>
                <DishPhoto url={null} name={item.dishName} />
                <div>
                  <div className="row between wrap">
                    <h2 id={nameId} style={{ fontSize: 17 }}>{item.dishName}</h2>
                    <span className="small muted">{item.quantity} × {formatMoney(item.unitPrice)}</span>
                  </div>
                  {done ? (
                    <div className="done-box mt-8">
                      <span className="stars">{'★'.repeat(done.rating)}{'☆'.repeat(5 - done.rating)}</span> · Заказали бы снова: {done.reordered ? 'да' : 'нет'}
                      {done.text && <> · «{done.text}»</>}
                      <div className="tiny muted mt-8">Отзыв оставлен {formatDate(done.createdAt)} · повторно оценить позицию нельзя</div>
                    </div>
                  ) : (
                    <>
                      <div className="row wrap top mt-12" style={{ gap: 28 }}>
                        <div>
                          <div className="small strong" id={`${nameId}-rate`}>Оценка *</div>
                          <div className="rate" role="radiogroup" aria-labelledby={`${nameId}-rate`}>
                            {[1, 2, 3, 4, 5].map((value) => (
                              <button
                                key={value}
                                type="button"
                                role="radio"
                                aria-checked={draft.rating === value}
                                aria-label={`${value} из 5`}
                                className={value <= draft.rating ? 'on' : ''}
                                onClick={() => setDraft(item.id, { rating: value })}
                              >
                                ★
                              </button>
                            ))}
                          </div>
                        </div>
                        <div>
                          <div className="small strong" style={{ marginBottom: 6 }} id={`${nameId}-again`}>Заказали бы снова? *</div>
                          <div className="seg" role="group" aria-labelledby={`${nameId}-again`}>
                            <button type="button" aria-pressed={draft.reordered === true} onClick={() => setDraft(item.id, { reordered: true })}>Да</button>
                            <button type="button" aria-pressed={draft.reordered === false} onClick={() => setDraft(item.id, { reordered: false })}>Нет</button>
                          </div>
                        </div>
                      </div>
                      <div className="field mt-12" style={{ marginBottom: 0 }}>
                        <label htmlFor={`${nameId}-text`}>Комментарий</label>
                        <textarea
                          id={`${nameId}-text`}
                          className="input"
                          maxLength={1000}
                          placeholder="Что понравилось или нет?"
                          value={draft.text}
                          onChange={(e) => setDraft(item.id, { text: e.target.value })}
                        />
                        <span className="hint">необязательно · {draft.text.length} / 1000</span>
                        {error && <span className="err" role="alert">{error}</span>}
                      </div>
                    </>
                  )}
                </div>
              </div>
            );
          })}

          {open.length > 0 ? (
            <div className="row between wrap mt-16">
              <p className="small muted">Отзыв появится в карточке блюда и войдёт в среднюю оценку и долю «заказали бы снова».</p>
              <div className="row">
                <Link className="btn" to={`/orders/${order.id}`}>Позже</Link>
                <button type="button" className="btn primary" disabled={busy} onClick={submit}>
                  {busy ? 'Отправляем…' : 'Отправить отзывы'}
                </button>
              </div>
            </div>
          ) : (
            <div className="mt-16">
              <Alert tone="ok" title={sent ? 'Отзывы отправлены.' : 'Все блюда оценены.'}>Спасибо! Оценки учтены в карточках блюд.</Alert>
            </div>
          )}
          <div className="mt-12"><ErrorAlert error={submitError} /></div>
        </section>
      )}
    </div>
  );
}
