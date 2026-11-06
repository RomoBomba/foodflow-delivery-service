import { kitchenApi } from '../../api/endpoints';
import { Alert, ErrorAlert, KitchenStatusBadge, Loading } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/hooks';

export function KitchenProfilePage() {
  useDocumentTitle('Профиль кухни');
  const kitchen = useAsync(() => kitchenApi.mine(), []);
  if (kitchen.loading && !kitchen.data) return <Loading />;
  if (!kitchen.data) return <ErrorAlert error={kitchen.error} onRetry={kitchen.reload} />;
  const k = kitchen.data;
  return (
    <div className="narrow" style={{ maxWidth: 720 }}>
      <div className="page-head">
        <div>
          <h1>{k.name}</h1>
          <p className="sub">Реквизиты кухни, указанные в заявке</p>
        </div>
        <KitchenStatusBadge status={k.status} />
      </div>
      <section className="panel">
        <dl className="kv">
          <dt>Описание</dt><dd style={{ whiteSpace: 'pre-line' }}>{k.description}</dd>
          <dt>Контакты</dt><dd>{k.contacts}</dd>
          <dt>Район доставки</dt><dd>{k.deliveryArea}</dd>
          <dt>Приём заказов</dt><dd>{k.acceptingOrders ? 'открыт' : 'закрыт'}</dd>
          <dt>Заявка подана</dt><dd>{formatDateTime(k.createdAt)}</dd>
          {k.moderatedAt && (<><dt>Решение</dt><dd>{formatDateTime(k.moderatedAt)}</dd></>)}
        </dl>
        {k.status === 'BLOCKED' && (
          <div className="mt-16">
            <Alert tone="danger" title="Кухня заблокирована.">
              {k.moderationReason ? `Причина: ${k.moderationReason}. ` : ''}Новые заказы не поступают; созданные можно завершить.
            </Alert>
          </div>
        )}
        <p className="tiny muted mt-16">Изменить реквизиты можно через администратора — отдельного редактирования в учебной версии нет.</p>
      </section>
    </div>
  );
}
