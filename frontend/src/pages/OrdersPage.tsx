import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { errorText, isApiError } from '../api/client';
import { ordersApi } from '../api/endpoints';
import type { Order } from '../api/types';
import { useToast } from '../components/toast';
import { Alert, Empty, ErrorAlert, Loading, Modal, OrderStatusBadge } from '../components/ui';
import { ORDER_FLOW, ORDER_STATUS_LABEL, countLabel, formatDateTime, formatMoney, formatTime, shortOrderNumber } from '../lib/format';
import { useAsync, useDocumentTitle, usePolling, useSecondsSince } from '../lib/hooks';
import { useDictionaries } from '../state/dictionaries';

export const POLL_INTERVAL_MS = 15_000;

export function OrdersPage() {
  useDocumentTitle('Мои заказы');
  const { orderId } = useParams();
  const location = useLocation();
  const created = Boolean((location.state as { created?: boolean } | null)?.created);
  const orders = useAsync(() => ordersApi.list(), []);
  usePolling(() => void orders.reload(), POLL_INTERVAL_MS);
  const seconds = useSecondsSince(orders.loadedAt);
  const { kitchenName } = useDictionaries();

  const list = orders.data ?? [];
  const selected = orderId ? list.find((o) => o.id === orderId) : list[0];

  if (orders.loading && !orders.data) return <Loading label="Загружаем заказы…" />;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Мои заказы</h1>
          <p className="sub">Новые сверху</p>
        </div>
        <RefreshInfo seconds={seconds} failed={Boolean(orders.error) && Boolean(orders.data)} onRefresh={() => void orders.reload()} loading={orders.loading} />
      </div>

      {created && selected?.id === orderId && selected?.status === 'CREATED' && (
        <div className="mt-8" style={{ marginBottom: 16 }}>
          <Alert tone="ok" title="Заказ оформлен.">Кухня получила заказ и скоро примет его в работу. Статус обновляется автоматически.</Alert>
        </div>
      )}
      {orders.error != null && !orders.data && <ErrorAlert error={orders.error} onRetry={orders.reload} />}
      {orders.error != null && orders.data && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="warn" title="Нет связи с сервером.">
            Показаны последние данные. Попробуем снова через 15 секунд — или нажмите «Обновить».
          </Alert>
        </div>
      )}

      {orders.data && list.length === 0 ? (
        <Empty title="Заказов пока нет">
          <p>Выберите блюда в каталоге — первый заказ появится здесь.</p>
          <p className="mt-16"><Link className="btn primary" to="/">В каталог</Link></p>
        </Empty>
      ) : (
        <div className={`with-side orders-layout${orderId ? ' has-detail' : ''}`}>
          <nav className="order-list order-list-col" aria-label="Список заказов">
            {list.map((order) => (
              <Link
                key={order.id}
                to={`/orders/${order.id}`}
                className="order-link"
                aria-current={selected?.id === order.id ? 'page' : undefined}
              >
                <div className="row between top">
                  <b>{formatDateTime(order.createdAt)}</b>
                  <OrderStatusBadge status={order.status} />
                </div>
                <div className="small muted mt-8">
                  {kitchenName(order.kitchenId) ?? 'Кухня'} · {countLabel(order.items.reduce((s, i) => s + i.quantity, 0), ['порция', 'порции', 'порций'])} · {formatMoney(order.totalPrice)}
                </div>
                {order.status === 'CANCELLED' && order.cancellationReason && (
                  <div className="tiny muted mt-8">Причина: {order.cancellationReason}</div>
                )}
              </Link>
            ))}
          </nav>
          {selected ? (
            <OrderDetail order={selected} kitchen={kitchenName(selected.kitchenId)} onChanged={() => void orders.reload()} />
          ) : (
            orderId && <MissingOrder orderId={orderId} />
          )}
        </div>
      )}
    </>
  );
}

export function RefreshInfo({ seconds, failed, onRefresh, loading }: { seconds: number | null; failed: boolean; onRefresh: () => void; loading: boolean }) {
  return (
    <div className="refresh" aria-live="polite">
      <span className={`dot${failed ? ' off' : ''}`} aria-hidden="true" />
      <span>
        {seconds === null ? 'Обновляем…' : `Обновлено ${seconds} с назад`} · автообновление каждые 15 с
      </span>
      <button type="button" className="btn sm" onClick={onRefresh} disabled={loading}>↻ Обновить</button>
    </div>
  );
}

function MissingOrder({ orderId }: { orderId: string }) {
  const order = useAsync(() => ordersApi.get(orderId), [orderId]);
  if (order.loading) return <Loading />;
  if (isApiError(order.error) && order.error.status === 404) {
    return <Empty title="Заказ не найден"><p>Такого заказа нет среди ваших.</p></Empty>;
  }
  return <ErrorAlert error={order.error} onRetry={order.reload} />;
}

function OrderDetail({ order, kitchen, onChanged }: { order: Order; kitchen?: string; onChanged: () => void }) {
  const toast = useToast();
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const stepIndex = ORDER_FLOW.indexOf(order.status);
  const historyTime = (status: string) => order.history.find((h) => h.toStatus === status)?.createdAt;

  const cancel = async () => {
    setBusy(true);
    try {
      await ordersApi.cancel(order.id, order.version);
      toast('Заказ отменён, порции возвращены кухне', 'ok');
    } catch (error) {
      toast(
        isApiError(error, 'ORDER_VERSION_CONFLICT') ? 'Заказ изменился. Проверьте обновлённую карточку.' :
        isApiError(error, 'ORDER_NOT_CANCELLABLE') ? 'Кухня уже приняла заказ — отменить его нельзя.' : errorText(error),
        'danger',
      );
    } finally {
      setBusy(false);
      setConfirm(false);
      onChanged();
    }
  };

  return (
    <div className="stack">
      <Link className="btn ghost sm back-link" to="/orders" style={{ justifySelf: 'start' }}>← Все заказы</Link>
      <section className="panel" aria-labelledby="order-title">
        <div className="row between top wrap">
          <div>
            <h2 id="order-title" style={{ marginBottom: 4, fontSize: 18 }}>Заказ от {formatDateTime(order.createdAt)}</h2>
            <div className="small muted">№ {shortOrderNumber(order.orderNumber)} · {kitchen ?? 'Кухня'}</div>
          </div>
          <OrderStatusBadge status={order.status} />
        </div>
        {order.status === 'CANCELLED' ? (
          <div className="mt-16">
            <Alert tone="info" title="Заказ отменён.">
              {order.cancellationReason ? `Причина: ${order.cancellationReason.replace(/[.!]$/, '')}.` : 'Отменён до принятия кухней.'} Порции возвращены кухне.
            </Alert>
          </div>
        ) : (
          <ol className="track mt-16" aria-label="Этапы заказа">
            {ORDER_FLOW.map((status, index) => {
              const at = historyTime(status);
              const cls = index < stepIndex ? 'done' : index === stepIndex ? (status === 'COMPLETED' ? 'done' : 'now') : '';
              return (
                <li key={status} className={cls} aria-current={index === stepIndex ? 'step' : undefined}>
                  {ORDER_STATUS_LABEL[status]}
                  {at && <><br /><span className="tiny muted">{formatTime(at)}</span></>}
                </li>
              );
            })}
          </ol>
        )}
      </section>

      <div className="grid-2">
        <section className="panel" aria-labelledby="items-title">
          <h2 id="items-title">Состав заказа</h2>
          <table className="responsive-table">
            <thead>
              <tr><th>Блюдо</th><th>Цена</th><th>Кол-во</th><th className="right-text">Сумма</th></tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.id}>
                  <td style={{ gridColumn: '1 / -1' }}>
                    {item.dishName}
                    <div className="tiny muted">{item.weightGrams} г · {item.kcalPerPortion} ккал</div>
                  </td>
                  <td data-label="Цена" className="num">{formatMoney(item.unitPrice)}</td>
                  <td data-label="Кол-во">{item.quantity}</td>
                  <td data-label="Сумма" className="right-text strong num">{formatMoney(item.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <hr className="divider" />
          <div className="total-row"><span className="muted">Доставка</span><span>включена</span></div>
          <div className="total-row big"><span>Итого</span><span className="num">{formatMoney(order.totalPrice)}</span></div>
        </section>

        <section className="panel" aria-labelledby="delivery-title">
          <h2 id="delivery-title">Доставка</h2>
          <dl className="kv">
            <dt>Адрес</dt><dd>{order.deliveryAddress}</dd>
            <dt>Телефон</dt><dd>{order.phone}</dd>
            {order.comment && (<><dt>Комментарий</dt><dd>{order.comment}</dd></>)}
            <dt>Оплата</dt><dd>При получении</dd>
          </dl>
          <hr className="divider" />
          {order.status === 'CREATED' && (
            <>
              <p className="small muted">Отменить заказ можно, пока кухня его не приняла.</p>
              <button type="button" className="btn danger mt-12" onClick={() => setConfirm(true)}>Отменить заказ</button>
            </>
          )}
          {order.status === 'COMPLETED' && (
            <Link className="btn primary" to={`/orders/${order.id}/review`}>Оценить блюда</Link>
          )}
          {(order.status === 'PREPARING' || order.status === 'DELIVERING') && (
            <p className="small muted">Кухня уже готовит заказ — отменить его нельзя.</p>
          )}
        </section>
      </div>

      <section className="panel" aria-labelledby="history-title">
        <h2 id="history-title">История статусов</h2>
        <table className="responsive-table">
          <thead>
            <tr><th>Время</th><th>Переход</th><th>Кто</th><th>Причина</th></tr>
          </thead>
          <tbody>
            {[...order.history].reverse().map((entry) => (
              <tr key={entry.id}>
                <td>{formatTime(entry.createdAt)}</td>
                <td>{entry.fromStatus ? ORDER_STATUS_LABEL[entry.fromStatus] : '—'} → {ORDER_STATUS_LABEL[entry.toStatus]}</td>
                <td data-label="Кто">{entry.actorId === order.customerId ? 'Вы' : 'Кухня'}</td>
                <td data-label="Причина" className={entry.reason ? '' : 'muted'}>{entry.reason ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {confirm && (
        <Modal
          title="Отменить заказ?"
          onClose={() => setConfirm(false)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setConfirm(false)}>Не отменять</button>
              <button type="button" className="btn danger-solid" disabled={busy} onClick={cancel}>
                {busy ? 'Отменяем…' : 'Отменить заказ'}
              </button>
            </>
          }
        >
          <p className="small">Заказ на {formatMoney(order.totalPrice)} будет отменён, порции вернутся кухне.</p>
        </Modal>
      )}
    </div>
  );
}
