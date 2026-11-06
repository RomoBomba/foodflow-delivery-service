import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, NetworkError, errorText } from '../api/client';
import { ordersApi } from '../api/endpoints';
import type { CartItem, OrderCreateRequest, PriceChangedDetails } from '../api/types';
import { useToast } from '../components/toast';
import { Alert, DishPhoto, Empty, ErrorAlert, Field, Loading, Modal, Stepper, describe } from '../components/ui';
import { countLabel, formatMoney } from '../lib/format';
import { useDocumentTitle, useSessionDraft } from '../lib/hooks';
import { hasErrors, newIdempotencyKey, validateAddress, validateOptional, validatePhone, type FieldErrors } from '../lib/validation';
import { useCart } from '../state/cart';
import { useAuth } from '../state/auth';
import { useDictionaries } from '../state/dictionaries';

interface Draft {
  deliveryAddress: string;
  phone: string;
  comment: string;
}

/**
 * Попытка оформления: один Idempotency-Key живёт до успешного заказа. Пока исход
 * неизвестен (сеть), новый ключ не создаётся; после бизнес-отказа тот же ключ
 * можно использовать для исправленного запроса (FR-14, docs/API.md).
 */
interface Attempt {
  key: string;
  /** Тело последней отправки, если её исход неизвестен. */
  unknownBody: OrderCreateRequest | null;
}

const EMPTY_DRAFT: Draft = { deliveryAddress: '', phone: '', comment: '' };

function isBlocked(item: CartItem): boolean {
  return !item.available || item.quantity > item.availablePortions;
}

export function CartPage() {
  useDocumentTitle('Корзина');
  const cart = useCart();
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const { kitchenName } = useDictionaries();
  const [draft, setDraft, clearDraft] = useSessionDraft<Draft>(`ff.checkout.draft.${user!.id}`, EMPTY_DRAFT);
  const [attempt, setAttempt, clearAttempt] = useSessionDraft<Attempt>(`ff.checkout.attempt.${user!.id}`, { key: '', unknownBody: null });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [priceChange, setPriceChange] = useState<{ details: PriceChangedDetails; body: OrderCreateRequest } | null>(null);
  const [networkFailed, setNetworkFailed] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [busyItem, setBusyItem] = useState<string | null>(null);
  const [reusedKey, setReusedKey] = useState(false);

  useEffect(() => {
    cart
      .refresh()
      .then(() => setLoadError(null))
      .catch(setLoadError)
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (attempt.unknownBody) setNetworkFailed(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const items = cart.cart.items;
  const blocked = items.filter(isBlocked);
  const currentKey = () => {
    if (attempt.key) return attempt.key;
    const key = newIdempotencyKey();
    setAttempt({ key, unknownBody: null });
    return key;
  };

  const [touched, setTouched] = useState<Partial<Record<keyof Draft, boolean>>>({});
  const validateField = (field: keyof Draft, value: string) => field === 'deliveryAddress'
    ? validateAddress(value) : field === 'phone' ? validatePhone(value) : validateOptional(value, 500);
  const showFieldError = (field: keyof Draft, value: string) => {
    const message = validateField(field, value);
    setErrors((previous) => {
      const next = { ...previous };
      if (message) next[field] = message; else delete next[field];
      return next;
    });
  };
  const blur = (field: keyof Draft) => {
    setTouched((previous) => ({ ...previous, [field]: true }));
    showFieldError(field, draft[field]);
  };
  const update = (field: keyof Draft, value: string) => {
    setDraft({ ...draft, [field]: value });
    if (touched[field] || errors[field]) showFieldError(field, value);
  };

  const validate = (): FieldErrors => {
    const result: FieldErrors = {};
    const address = validateAddress(draft.deliveryAddress);
    if (address) result.deliveryAddress = address;
    const phone = validatePhone(draft.phone);
    if (phone) result.phone = phone;
    const comment = validateOptional(draft.comment, 500);
    if (comment) result.comment = comment;
    return result;
  };

  const send = async (key: string, body: OrderCreateRequest) => {
    setSubmitting(true);
    setSubmitError(null);
    setReusedKey(false);
    // Persist before sending: a reload or lost response must retry the same request.
    setAttempt({ key, unknownBody: body });
    try {
      const order = await ordersApi.create(key, body);
      setAttempt({ key: '', unknownBody: null });
      setDraft(EMPTY_DRAFT);
      clearAttempt();
      clearDraft();
      await cart.refresh().catch(() => undefined);
      navigate(`/orders/${order.id}`, { state: { created: true } });
    } catch (error) {
      if (error instanceof NetworkError || (error instanceof ApiError && error.status >= 500)) {
        // Исход неизвестен: сохраняем ключ и тело, повтор будет с ними же.
        setAttempt({ key, unknownBody: body });
        setNetworkFailed(true);
        return;
      }
      setAttempt({ key, unknownBody: null });
      setNetworkFailed(false);
      handleBusinessError(error, body);
    } finally {
      setSubmitting(false);
    }
  };

  const handleBusinessError = (error: unknown, body: OrderCreateRequest) => {
    if (!(error instanceof ApiError)) return setSubmitError(error);
    switch (error.code) {
      case 'PRICE_CHANGED':
        setPriceChange({ details: error.body as unknown as PriceChangedDetails, body });
        void cart.refresh().catch(() => undefined);
        return;
      case 'IDEMPOTENCY_KEY_REUSED':
        // Предыдущая попытка с этим ключом уже создала заказ.
        setAttempt({ key: newIdempotencyKey(), unknownBody: null });
        setReusedKey(true);
        void cart.refresh().catch(() => undefined);
        return;
      case 'VALIDATION_ERROR':
      case 'INVALID_PHONE':
      case 'INVALID_ADDRESS': {
        const fields: FieldErrors = { ...error.fields };
        if (error.code === 'INVALID_PHONE') fields.phone = error.message;
        if (error.code === 'INVALID_ADDRESS') fields.deliveryAddress = error.message;
        setErrors(fields);
        if (!hasErrors(fields)) setSubmitError(error);
        return;
      }
      default:
        if (['CART_CHANGED', 'CART_EMPTY', 'INSUFFICIENT_STOCK', 'DISH_UNAVAILABLE', 'KITCHEN_CLOSED', 'DISH_ARCHIVED'].includes(error.code)) {
          void cart.refresh().catch(() => undefined);
        }
        setSubmitError(error);
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const fieldErrors = validate();
    setErrors(fieldErrors);
    if (submitting || attempt.unknownBody || hasErrors(fieldErrors) || blocked.length > 0 || items.length === 0) return;
    const body: OrderCreateRequest = {
      deliveryAddress: draft.deliveryAddress.trim(),
      phone: draft.phone.trim(),
      comment: draft.comment.trim() || null,
      items: items.map((item) => ({ dishId: item.dishId, quantity: item.quantity })),
      acceptCurrentPrices: false,
      confirmedPriceFingerprint: null,
    };
    void send(currentKey(), body);
  };

  const retryUnknown = () => {
    if (!attempt.unknownBody) return setNetworkFailed(false);
    void send(attempt.key, attempt.unknownBody);
  };

  const confirmPrices = () => {
    if (!priceChange) return;
    const body: OrderCreateRequest = {
      ...priceChange.body,
      acceptCurrentPrices: true,
      confirmedPriceFingerprint: priceChange.details.updatedPriceFingerprint,
    };
    setPriceChange(null);
    void send(currentKey(), body);
  };

  const changeQuantity = async (item: CartItem, quantity: number) => {
    setBusyItem(item.dishId);
    try {
      await cart.setQuantity(item.dishId, quantity);
    } catch (error) {
      toast(errorText(error), 'danger');
      void cart.refresh().catch(() => undefined);
    } finally {
      setBusyItem(null);
    }
  };

  const removeItem = async (item: CartItem) => {
    setBusyItem(item.dishId);
    try {
      await cart.remove(item.dishId);
      toast(`«${item.dishName}» удалено из корзины`, 'info');
    } catch (error) {
      toast(errorText(error), 'danger');
    } finally {
      setBusyItem(null);
    }
  };

  if (loading && items.length === 0) return <Loading label="Загружаем корзину…" />;

  const kitchen = cart.cart.kitchenId ? kitchenName(cart.cart.kitchenId) : undefined;

  const recovery = attempt.unknownBody && !submitting ? (
    <Alert tone="warn" title="Результат оформления неизвестен.">
      <p>Заказ мог сохраниться, даже если ответ не пришёл. Проверьте результат перед новым оформлением.</p>
      <button type="button" className="btn primary mt-12" onClick={retryUnknown}>Проверить результат оформления</button>
      <Link className="btn mt-12" to="/orders">Мои заказы</Link>
    </Alert>
  ) : null;

  if (items.length === 0) {
    return (
      <>
        <ErrorAlert error={loadError} />
        {recovery}
        {reusedKey && <ReusedKeyAlert />}
        <Empty title="Корзина пуста">
          <p>Выберите блюда в каталоге — в одном заказе блюда одной кухни.</p>
          <p className="mt-16"><Link className="btn primary" to="/">В каталог</Link></p>
        </Empty>
      </>
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Корзина</h1>
          <p className="sub">{kitchen ? `${kitchen} · ` : ''}в одном заказе блюда только одной кухни</p>
        </div>
        <button type="button" className="btn ghost" onClick={() => setConfirmClear(true)}>Очистить корзину</button>
      </div>

      {recovery}
      <div className="with-aside">
        <div className="stack">
          {reusedKey && <ReusedKeyAlert />}
          {cart.cart.hasPriceChanges && (
            <Alert tone="warn" title="Цена изменилась.">
              С момента добавления кухня изменила цену. Итог пересчитан по актуальным ценам; при оформлении потребуется подтверждение.
            </Alert>
          )}
          {blocked.length > 0 && (
            <Alert tone="danger" title="Часть блюд недоступна.">
              Уменьшите количество или удалите отмеченные позиции, чтобы оформить заказ.
            </Alert>
          )}
          <section className="panel" aria-label="Позиции корзины">
            {items.map((item) => (
              <div className="cart-item" key={item.dishId}>
                <DishPhoto url={item.photoUrl} name={item.dishName} />
                <div style={{ minWidth: 0 }}>
                  <Link className="strong" to={`/dishes/${item.dishId}`} style={{ color: 'var(--text)' }}>{item.dishName}</Link>
                  {item.excludedIngredients.length > 0 && <p className="tiny preference-warning">В составе есть исключённые вами ингредиенты: {item.excludedIngredients.join(', ')}. Это предупреждение — блюдо можно заказать, например для другого человека.</p>}
                  <div className="small">
                    {item.priceChanged ? (
                      <>
                        <span className="old-price">{formatMoney(item.priceAtAdd)}</span>{' '}
                        <span className="changed">{formatMoney(item.currentPrice)} за порцию — цена изменилась</span>
                      </>
                    ) : (
                      <span className="muted">{formatMoney(item.currentPrice)} за порцию · осталось {item.availablePortions}</span>
                    )}
                  </div>
                  {isBlocked(item) && (
                    <div className="small" style={{ color: 'var(--danger)', fontWeight: 600 }}>
                      {!item.available
                        ? 'Блюдо сейчас недоступно — удалите позицию'
                        : `Свободно только ${item.availablePortions} — уменьшите количество`}
                    </div>
                  )}
                </div>
                <div className="qty">
                  <Stepper
                    value={item.quantity}
                    max={99}
                    disabled={busyItem === item.dishId}
                    label={`Количество «${item.dishName}»`}
                    onChange={(quantity) => void changeQuantity(item, quantity)}
                  />
                </div>
                <div className="sum num">{formatMoney(item.lineTotal)}</div>
                <button
                  type="button"
                  className="icon-btn"
                  aria-label={`Удалить «${item.dishName}»`}
                  disabled={busyItem === item.dishId}
                  onClick={() => void removeItem(item)}
                >
                  ✕
                </button>
              </div>
            ))}
            <p className="tiny muted mt-8">До 30 разных блюд, по 1–99 порций каждого.</p>
          </section>
        </div>

        <form className="panel" onSubmit={submit} noValidate aria-labelledby="checkout-title">
          <h2 id="checkout-title">Оформление</h2>
          <Field id="address" label="Адрес доставки *" error={errors.deliveryAddress} hint="10–500 символов">
            <textarea
              className="input"
              style={{ minHeight: 64 }}
              autoComplete="street-address"
              maxLength={500}
              {...describe('address', errors.deliveryAddress, true)}
              value={draft.deliveryAddress}
              onChange={(e) => update('deliveryAddress', e.target.value)}
              onBlur={() => blur('deliveryAddress')}
            />
          </Field>
          <Field id="phone" label="Телефон *" error={errors.phone} hint="от 10 до 15 цифр, например +7 (999) 123-45-67">
            <input
              className="input"
              type="tel"
              autoComplete="tel"
              maxLength={25}
              {...describe('phone', errors.phone, true)}
              value={draft.phone}
              onChange={(e) => update('phone', e.target.value)}
              onBlur={() => blur('phone')}
            />
          </Field>
          <Field id="comment" label="Комментарий для кухни" error={errors.comment} hint={`необязательно · ${draft.comment.length} / 500`}>
            <textarea
              className="input"
              maxLength={500}
              placeholder="Например: домофон не работает, позвоните"
              {...describe('comment', errors.comment, true)}
              value={draft.comment}
              onChange={(e) => update('comment', e.target.value)}
              onBlur={() => blur('comment')}
            />
          </Field>
          <div className="pay">
            <span aria-hidden="true" style={{ width: 18, height: 18, borderRadius: '50%', border: '5px solid var(--accent)', flex: 'none' }} />
            <div>
              <b>Оплата при получении</b>
              <div className="small muted">онлайн-оплаты в учебной версии нет</div>
            </div>
          </div>
          <hr className="divider" />
          <div className="total-row">
            <span className="muted">Блюда ({countLabel(cart.portions, ['порция', 'порции', 'порций'])})</span>
            <span className="num">{formatMoney(cart.cart.totalPrice)}</span>
          </div>
          <div className="total-row"><span className="muted">Доставка</span><span>включена</span></div>
          <div className="total-row big"><span>Итого</span><span className="num">{formatMoney(cart.cart.totalPrice)}</span></div>
          <p className="tiny muted" style={{ margin: '4px 0 14px' }}>Итоговую сумму рассчитывает и фиксирует сервер.</p>
          <ErrorAlert error={submitError} />
          <button type="submit" className="btn primary lg block mt-12" disabled={submitting || blocked.length > 0 || attempt.unknownBody !== null}>
            {submitting ? 'Оформляем…' : 'Оформить заказ'}
          </button>
          {blocked.length > 0 && <p className="tiny mt-8" style={{ color: 'var(--danger)' }}>Уберите недоступные позиции</p>}
        </form>
      </div>

      {priceChange && (
        <Modal
          title="Цены изменились"
          onClose={() => setPriceChange(null)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setPriceChange(null)}>Вернуться в корзину</button>
              <button type="button" className="btn primary" onClick={confirmPrices} disabled={submitting}>
                Подтвердить {formatMoney(priceChange.details.updatedTotal)}
              </button>
            </>
          }
        >
          <p className="small muted" style={{ marginBottom: 12 }}>
            Пока вы оформляли заказ, кухня обновила цены. Проверьте новый расчёт — без подтверждения заказ не создаётся.
          </p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Блюдо</th><th>Было</th><th>Стало</th><th className="right-text">Сумма</th></tr>
              </thead>
              <tbody>
                {priceChange.details.items.map((item) => (
                  <tr key={item.dishId}>
                    <td>{item.dishName} × {item.quantity}</td>
                    <td className="old-price">{formatMoney(item.previousUnitPrice)}</td>
                    <td className="changed">{formatMoney(item.currentUnitPrice)}</td>
                    <td className="right-text strong num">{formatMoney(item.currentLineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="total-row big"><span>Новый итог</span><span className="num">{formatMoney(priceChange.details.updatedTotal)}</span></div>
        </Modal>
      )}

      {networkFailed && (
        <Modal
          title="Результат оформления неизвестен"
          onClose={() => setNetworkFailed(false)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setNetworkFailed(false)}>Закрыть</button>
              <button type="button" className="btn primary" onClick={retryUnknown} disabled={submitting}>
                {submitting ? 'Отправляем…' : 'Повторить отправку'}
              </button>
            </>
          }
        >
          <p className="small">
            Ответ сервера не получен или содержит ошибку, и мы не знаем, успел ли он принять заказ. Повтор безопасен: второй заказ не создастся.
          </p>
          <Alert tone="warn">Введённые данные сохранены.</Alert>
        </Modal>
      )}

      {confirmClear && (
        <Modal
          title="Очистить корзину?"
          onClose={() => setConfirmClear(false)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setConfirmClear(false)}>Отмена</button>
              <button
                type="button"
                className="btn danger"
                onClick={async () => {
                  setConfirmClear(false);
                  try {
                    await cart.clear();
                  } catch (error) {
                    toast(errorText(error), 'danger');
                  }
                }}
              >
                Очистить
              </button>
            </>
          }
        >
          <p className="small">Из корзины будут удалены все блюда ({countLabel(cart.portions, ['порция', 'порции', 'порций'])}).</p>
        </Modal>
      )}
    </>
  );
}

function ReusedKeyAlert() {
  return (
    <Alert tone="info" title="Заказ уже был создан.">
      Предыдущая попытка оформления успела сохраниться. Проверьте его в разделе <Link to="/orders">Мои заказы</Link>.
    </Alert>
  );
}
