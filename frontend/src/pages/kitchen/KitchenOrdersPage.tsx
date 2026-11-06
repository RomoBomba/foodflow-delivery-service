import { useState } from 'react';
import { errorText, isApiError } from '../../api/client';
import { kitchenApi } from '../../api/endpoints';
import type { Kitchen, Order, OrderStatus } from '../../api/types';
import { useToast } from '../../components/toast';
import { Alert, ErrorAlert, Loading, OrderStatusBadge, ReasonModal } from '../../components/ui';
import { countLabel, formatMoney, formatTime, plural, shortOrderNumber } from '../../lib/format';
import { useAsync, useDocumentTitle, usePolling, useSecondsSince } from '../../lib/hooks';
import { POLL_INTERVAL_MS, RefreshInfo } from '../OrdersPage';

/** Следующий шаг по таблице FR-16 и подпись кнопки. */
const NEXT: Partial<Record<OrderStatus, { to: OrderStatus; label: string }>> = {
  CREATED: { to: 'PREPARING', label: 'Принять' },
  PREPARING: { to: 'IN_DELIVERY', label: 'Передать в доставку' },
  IN_DELIVERY: { to: 'COMPLETED', label: 'Заказ получен' },
};

const COLUMNS: { title: string; statuses: OrderStatus[]; cls: string }[] = [
  { title: 'Новые', statuses: ['CREATED'], cls: 's-created' },
  { title: 'Готовится', statuses: ['PREPARING'], cls: 's-preparing' },
  { title: 'В доставке', statuses: ['IN_DELIVERY'], cls: 's-delivery' },
  { title: 'Завершены', statuses: ['COMPLETED', 'CANCELLED'], cls: 's-completed' },
];

export function KitchenOrdersPage() {
  useDocumentTitle('Заказы кухни');
  const toast = useToast();
  const orders = useAsync(() => kitchenApi.orders(), []);
  const kitchen = useAsync(() => kitchenApi.mine(), []);
  usePolling(() => void orders.reload(), POLL_INTERVAL_MS);
  const seconds = useSecondsSince(orders.loadedAt);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<Order | null>(null);
  const [showAllDone, setShowAllDone] = useState(false);

  const change = async (order: Order, status: OrderStatus, reason: string | null = null) => {
    setBusy(order.id);
    try {
      await kitchenApi.changeStatus(order.id, status, reason);
      toast(status === 'CANCELLED' ? 'Заказ отклонён, порции возвращены в остаток' : 'Статус обновлён', 'ok');
    } catch (error) {
      if (isApiError(error, 'INVALID_ORDER_TRANSITION')) {
        toast('Заказ уже изменился (например, клиент отменил его). Карточка обновлена.', 'warn');
      } else {
        toast(errorText(error), 'danger');
      }
    } finally {
      setBusy(null);
      void orders.reload();
    }
  };

  if (orders.loading && !orders.data) return <Loading label="Загружаем заказы…" />;
  const list = orders.data ?? [];
  const today = new Date().toDateString();
  const todayOrders = list.filter((o) => new Date(o.createdAt).toDateString() === today);
  const revenue = todayOrders.filter((o) => o.status === 'COMPLETED').reduce((s, o) => s + o.totalPrice, 0);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{kitchen.data?.name ?? 'Заказы кухни'}</h1>
          <div className="sub"><RefreshInfo seconds={seconds} failed={Boolean(orders.error) && Boolean(orders.data)} loading={orders.loading} onRefresh={() => void orders.reload()} /></div>
        </div>
        <div className="row wrap">
          <div className="panel" style={{ padding: '8px 14px' }}>
            <b style={{ fontSize: 18 }}>{todayOrders.length}</b>
            <div className="small muted">{plural(todayOrders.length, ['заказ', 'заказа', 'заказов'])} сегодня</div>
          </div>
          <div className="panel" style={{ padding: '8px 14px' }}>
            <b style={{ fontSize: 18 }}>{formatMoney(revenue)}</b>
            <div className="small muted">выполнено сегодня</div>
          </div>
          {kitchen.data && <AcceptingSwitch kitchen={kitchen.data} onChange={(k) => kitchen.setData(k)} />}
        </div>
      </div>

      {kitchen.data?.status === 'BLOCKED' && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="warn" title="Кухня заблокирована администратором.">
            {kitchen.data.moderationReason ? `Причина: ${kitchen.data.moderationReason}. ` : ''}
            Новые заказы не поступают. Уже созданные заказы можно довести до конца.
          </Alert>
        </div>
      )}
      {orders.error != null && (
        <div style={{ marginBottom: 16 }}>
          {orders.data ? (
            <Alert tone="warn" title="Нет связи с сервером.">Показаны последние данные; повторим через 15 секунд.</Alert>
          ) : (
            <ErrorAlert error={orders.error} onRetry={orders.reload} />
          )}
        </div>
      )}

      <div className="board">
        {COLUMNS.map((column) => {
          let items = list.filter((o) => column.statuses.includes(o.status));
          const done = column.statuses.includes('COMPLETED');
          if (done && !showAllDone) items = items.filter((o) => new Date(o.updatedAt).toDateString() === today);
          // Новые — старые сверху (очередь); остальные — свежие сверху.
          if (column.statuses[0] === 'CREATED') items = [...items].reverse();
          return (
            <section className="col" key={column.title} aria-label={column.title}>
              <h2>
                <span>{column.title}{done && !showAllDone ? ' сегодня' : ''}</span>
                <span className={`status ${column.cls}`}>{items.length}</span>
              </h2>
              {items.length === 0 && <p className="small muted" style={{ padding: '4px 6px 8px' }}>Пусто</p>}
              {items.map((order) => (
                <OrderCard
                  key={order.id}
                  order={order}
                  busy={busy === order.id}
                  onNext={(to) => void change(order, to)}
                  onReject={() => setRejecting(order)}
                />
              ))}
              {done && (
                <button type="button" className="btn ghost sm block" onClick={() => setShowAllDone((v) => !v)}>
                  {showAllDone ? 'Только сегодня' : 'Показать все'}
                </button>
              )}
            </section>
          );
        })}
      </div>

      {rejecting && (
        <ReasonModal
          title={`Отклонить заказ № ${shortOrderNumber(rejecting.orderNumber)}?`}
          description="Клиент увидит причину. Порции вернутся в остаток."
          confirmLabel="Отклонить заказ"
          onClose={() => setRejecting(null)}
          onConfirm={async (reason) => {
            const order = rejecting;
            setRejecting(null);
            await change(order, 'CANCELLED', reason);
          }}
        />
      )}
    </>
  );
}

function OrderCard({ order, busy, onNext, onReject }: { order: Order; busy: boolean; onNext: (to: OrderStatus) => void; onReject: () => void }) {
  const next = NEXT[order.status];
  const portions = order.items.reduce((s, i) => s + i.quantity, 0);
  const finished = order.status === 'COMPLETED' || order.status === 'CANCELLED';
  return (
    <article className={`oc${order.status === 'CREATED' ? ' new' : ''}`} aria-label={`Заказ ${shortOrderNumber(order.orderNumber)}`}>
      <div className="row between top">
        <b>{formatTime(order.createdAt)} · № {shortOrderNumber(order.orderNumber)}</b>
        {finished ? <OrderStatusBadge status={order.status} /> : <span className="strong num">{formatMoney(order.totalPrice)}</span>}
      </div>
      {finished ? (
        <div className="addr mt-8">
          {countLabel(portions, ['порция', 'порции', 'порций'])} · {formatMoney(order.totalPrice)}
          {order.cancellationReason && <div>Причина: «{order.cancellationReason}»</div>}
          {order.status === 'CANCELLED' && !order.cancellationReason && <div>Клиент отменил до принятия</div>}
        </div>
      ) : (
        <>
          <div className="items">
            {order.items.map((item) => (
              <div key={item.id}><span>{item.dishName}</span><span>× {item.quantity}</span></div>
            ))}
          </div>
          <div className="addr">{order.deliveryAddress} · {order.phone}</div>
          {order.comment && <div className="addr">«{order.comment}»</div>}
          <div className="acts">
            {next && (
              <button type="button" className="btn primary sm" disabled={busy} onClick={() => onNext(next.to)}>
                {busy ? '…' : next.label}
              </button>
            )}
            {order.status === 'CREATED' && (
              <button type="button" className="btn danger sm" disabled={busy} onClick={onReject}>Отклонить</button>
            )}
          </div>
        </>
      )}
    </article>
  );
}

function AcceptingSwitch({ kitchen, onChange }: { kitchen: Kitchen; onChange: (kitchen: Kitchen) => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const disabled = kitchen.status !== 'APPROVED' || busy;
  const toggle = async () => {
    setBusy(true);
    try {
      const updated = await kitchenApi.setAccepting(!kitchen.acceptingOrders);
      onChange(updated);
      toast(updated.acceptingOrders ? 'Приём заказов открыт' : 'Приём заказов закрыт — блюда нельзя купить', updated.acceptingOrders ? 'ok' : 'info');
    } catch (error) {
      toast(errorText(error), 'danger');
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="switch-row">
      <button type="button" role="switch" className="switch" aria-checked={kitchen.acceptingOrders} aria-label="Принимаю заказы" disabled={disabled} onClick={toggle} />
      <div>
        <b id="accepting-label">Принимаю заказы</b>
        <div className="tiny muted">{kitchen.status === 'APPROVED' ? 'выключите, если кухня закрыта' : 'недоступно для текущего статуса'}</div>
      </div>
    </div>
  );
}
