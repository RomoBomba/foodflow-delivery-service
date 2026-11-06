import { useMemo } from 'react';
import { kitchenApi } from '../../api/endpoints';
import { Empty, ErrorAlert, Loading } from '../../components/ui';
import { countLabel, formatDate, formatRating } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/hooks';

/** Кухня только читает видимые отзывы на свои блюда (FR-21). */
export function KitchenReviewsPage() {
  useDocumentTitle('Отзывы о кухне');
  const reviews = useAsync(() => kitchenApi.reviews(), []);
  const menu = useAsync(() => kitchenApi.menu(), []);
  const names = useMemo(() => new Map((menu.data ?? []).map((d) => [d.id, d.name])), [menu.data]);

  const byDish = useMemo(() => {
    const groups = new Map<string, { count: number; sum: number; yes: number }>();
    (reviews.data ?? []).forEach((r) => {
      const g = groups.get(r.dishId) ?? { count: 0, sum: 0, yes: 0 };
      g.count += 1;
      g.sum += r.rating;
      g.yes += r.reordered ? 1 : 0;
      groups.set(r.dishId, g);
    });
    return [...groups.entries()].sort((a, b) => b[1].count - a[1].count);
  }, [reviews.data]);

  if (reviews.loading && !reviews.data) return <Loading />;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Отзывы покупателей</h1>
          <p className="sub">Только чтение. Скрытые модератором отзывы здесь не показываются.</p>
        </div>
      </div>
      <ErrorAlert error={reviews.error} onRetry={reviews.reload} />
      {reviews.data && reviews.data.length === 0 ? (
        <Empty title="Отзывов пока нет"><p>Покупатели могут оценить блюда после выполненного заказа.</p></Empty>
      ) : (
        <div className="with-aside aside-360">
          <section className="panel" style={{ paddingTop: 4, paddingBottom: 4 }} aria-label="Отзывы">
            {(reviews.data ?? []).map((review) => (
              <article className="rev" key={review.id}>
                <div className="row between wrap">
                  <span>
                    <span className="stars" aria-label={`Оценка ${review.rating} из 5`}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
                    {' · '}<b>{names.get(review.dishId) ?? 'Блюдо'}</b>
                  </span>
                  <span className="tiny muted">{formatDate(review.createdAt)}</span>
                </div>
                {review.text && <p className="mt-8" style={{ whiteSpace: 'pre-line' }}>{review.text}</p>}
                <p className="tiny muted mt-8">Заказали бы снова: {review.reordered ? 'да' : 'нет'}</p>
              </article>
            ))}
          </section>
          <aside className="panel" aria-labelledby="by-dish">
            <h2 id="by-dish">По блюдам</h2>
            <table>
              <thead><tr><th>Блюдо</th><th>Оценка</th><th>Снова</th></tr></thead>
              <tbody>
                {byDish.map(([dishId, g]) => (
                  <tr key={dishId}>
                    <td>{names.get(dishId) ?? 'Блюдо'}<div className="tiny muted">{countLabel(g.count, ['отзыв', 'отзыва', 'отзывов'])}</div></td>
                    <td className="num">★ {formatRating(Math.round((g.sum / g.count) * 10) / 10)}</td>
                    <td className="num">{Math.round((g.yes / g.count) * 100)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </aside>
        </div>
      )}
    </>
  );
}
