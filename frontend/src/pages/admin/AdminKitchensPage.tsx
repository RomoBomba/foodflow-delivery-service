import { useState } from 'react';
import { errorText } from '../../api/client';
import { adminApi } from '../../api/endpoints';
import type { Kitchen, KitchenStatus } from '../../api/types';
import { useToast } from '../../components/toast';
import { Empty, ErrorAlert, KitchenStatusBadge, Loading, ReasonModal } from '../../components/ui';
import { KITCHEN_STATUS_LABEL, formatDateTime } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/hooks';

const TABS: KitchenStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'BLOCKED'];
const TAB_LABEL: Record<KitchenStatus, string> = { PENDING: 'На проверке', APPROVED: 'Одобрены', REJECTED: 'Отклонены', BLOCKED: 'Заблокированы' };

export function AdminKitchensPage() {
  useDocumentTitle('Модерация кухонь');
  const [tab, setTab] = useState<KitchenStatus>('PENDING');
  const kitchens = useAsync(() => adminApi.kitchens(tab), [tab]);
  const [action, setAction] = useState<{ kind: 'reject' | 'block'; kitchen: Kitchen } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();

  const approve = async (kitchen: Kitchen) => {
    setBusy(kitchen.id);
    try {
      await adminApi.approve(kitchen.id);
      toast(`«${kitchen.name}» одобрена. Владелец получит кабинет кухни после повторного входа.`, 'ok');
    } catch (error) {
      toast(errorText(error), 'danger');
    } finally {
      setBusy(null);
      void kitchens.reload();
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Кухни</h1>
          <p className="sub">Одобрение заявок, отказ и блокировка — всегда с причиной.</p>
        </div>
      </div>
      <div className="tabs" role="tablist" aria-label="Статус кухни">
        {TABS.map((status) => (
          <button key={status} type="button" role="tab" aria-selected={tab === status} onClick={() => setTab(status)}>
            {TAB_LABEL[status]}
          </button>
        ))}
      </div>
      <ErrorAlert error={kitchens.error} onRetry={kitchens.reload} />
      {kitchens.loading && !kitchens.data && <Loading />}
      {kitchens.data && kitchens.data.length === 0 && <Empty title={`Нет кухонь со статусом «${KITCHEN_STATUS_LABEL[tab]}»`} />}
      <div className="grid-2" role="tabpanel">
        {kitchens.data?.map((kitchen) => (
          <article className="panel" key={kitchen.id} aria-labelledby={`k-${kitchen.id}`}>
            <div className="row between top wrap">
              <h2 id={`k-${kitchen.id}`} style={{ fontSize: 17 }}>{kitchen.name}</h2>
              <KitchenStatusBadge status={kitchen.status} />
            </div>
            <dl className="kv mt-12" style={{ gridTemplateColumns: '110px minmax(0, 1fr)' }}>
              <dt>Описание</dt><dd style={{ whiteSpace: 'pre-line' }}>{kitchen.description}</dd>
              <dt>Контакты</dt><dd>{kitchen.contacts}</dd>
              <dt>Район</dt><dd>{kitchen.deliveryArea}</dd>
              <dt>Подана</dt><dd>{formatDateTime(kitchen.createdAt)}</dd>
              {kitchen.moderationReason && (<><dt>Причина</dt><dd>{kitchen.moderationReason}</dd></>)}
              {kitchen.status === 'APPROVED' && (<><dt>Приём</dt><dd>{kitchen.acceptingOrders ? 'открыт' : 'закрыт'}</dd></>)}
            </dl>
            <div className="row wrap mt-16">
              {kitchen.status === 'PENDING' && (
                <>
                  <button type="button" className="btn primary sm" disabled={busy === kitchen.id} onClick={() => void approve(kitchen)}>Одобрить</button>
                  <button type="button" className="btn danger sm" onClick={() => setAction({ kind: 'reject', kitchen })}>Отклонить…</button>
                </>
              )}
              {kitchen.status === 'APPROVED' && (
                <>
                  <button type="button" className="btn danger sm" onClick={() => setAction({ kind: 'block', kitchen })}>Заблокировать…</button>
                  <span className="tiny muted">Созданные заказы кухня сможет завершить</span>
                </>
              )}
            </div>
          </article>
        ))}
      </div>

      {action && (
        <ReasonModal
          title={action.kind === 'reject' ? `Отклонить заявку «${action.kitchen.name}»?` : `Заблокировать «${action.kitchen.name}»?`}
          description={
            action.kind === 'reject'
              ? 'Заявитель увидит причину отказа.'
              : 'Блокировка запрещает новые заказы; созданные заказы кухня сможет завершить, история сохраняется.'
          }
          confirmLabel={action.kind === 'reject' ? 'Отклонить заявку' : 'Заблокировать'}
          onClose={() => setAction(null)}
          onConfirm={async (reason) => {
            if (action.kind === 'reject') await adminApi.reject(action.kitchen.id, reason);
            else await adminApi.block(action.kitchen.id, reason);
            toast(action.kind === 'reject' ? 'Заявка отклонена' : 'Кухня заблокирована', 'ok');
            setAction(null);
            void kitchens.reload();
          }}
        />
      )}
    </>
  );
}
