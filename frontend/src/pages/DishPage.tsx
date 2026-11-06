import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { isApiError } from '../api/client';
import { catalogApi, preferencesApi } from '../api/endpoints';
import type { Compatibility, Preferences, RecommendationItem } from '../api/types';
import { BuyButton, useAddToCart } from '../components/AddToCart';
import { Alert, DishPhoto, Empty, ErrorAlert, Loading, Stepper } from '../components/ui';
import { countLabel, formatDate, formatMoney, formatRating, plural } from '../lib/format';
import { useAsync, useDocumentTitle } from '../lib/hooks';
import { useAuth } from '../state/auth';
import { useDictionaries } from '../state/dictionaries';

interface Personal {
  prefs: Preferences;
  compatibility: Compatibility;
  recommendation: RecommendationItem | null;
  scoringEnabled: boolean;
}

export function DishPage() {
  const { dishId = '' } = useParams();
  const { user } = useAuth();
  const isClient = user?.role === 'CLIENT';
  const { rememberKitchen } = useDictionaries();
  const dish = useAsync(() => catalogApi.dish(dishId), [dishId]);
  const reviews = useAsync(() => catalogApi.reviews(dishId), [dishId]);
  const personal = useAsync<Personal | null>(async () => {
    if (!isClient) return null;
    const [prefs, compatibility, reco] = await Promise.all([
      preferencesApi.get(),
      preferencesApi.compatibility(dishId),
      preferencesApi.recommendations(0, 100),
    ]);
    return {
      prefs,
      compatibility,
      scoringEnabled: reco.scoringEnabled,
      recommendation: reco.content.find((item) => item.dish.id === dishId) ?? null,
    };
  }, [dishId, isClient, user?.id]);
  const [quantity, setQuantity] = useState(1);
  const { add, busyId, dialog } = useAddToCart();
  useDocumentTitle(dish.data?.name ?? 'Блюдо');

  useEffect(() => {
    if (dish.data) rememberKitchen(dish.data.kitchenId, dish.data.kitchenName);
  }, [dish.data, rememberKitchen]);

  if (dish.loading && !dish.data) return <Loading />;
  if (dish.error && isApiError(dish.error) && dish.error.status === 404) {
    return (
      <Empty title="Блюдо недоступно">
        <p>Блюдо снято с продажи, отправлено в архив или кухня ещё не прошла проверку.</p>
        <p className="mt-16"><Link className="btn" to="/">В каталог</Link></p>
      </Empty>
    );
  }
  if (!dish.data) return <ErrorAlert error={dish.error} onRetry={dish.reload} />;

  const d = dish.data;
  const p = personal.data;
  const favorite = new Set(p?.prefs.favoriteIngredientIds ?? []);
  const excluded = new Set(p?.prefs.excludedIngredientIds ?? []);
  const incompatible = p && !p.compatibility.compatible ? p.compatibility.reason ?? 'Блюдо содержит исключённый ингредиент' : null;
  const maxQuantity = Math.max(1, Math.min(99, d.availablePortions));

  return (
    <>
      <nav className="crumbs" aria-label="Навигация">
        <Link to="/">Каталог</Link> / <Link to={`/?categoryId=${d.category.id}`}>{d.category.name}</Link> / {d.name}
      </nav>
      <div className="dish-layout">
        <DishPhoto url={d.photoUrl} name={d.name} className="main-photo" />

        <div>
          <div className="row wrap" style={{ gap: 8, marginBottom: 8 }}>
            <span className="chip">{d.category.name}</span>
            <Link className="chip" to={`/?kitchenId=${d.kitchenId}`}>{d.kitchenName}</Link>
          </div>
          <h1 style={{ fontSize: 32 }}>{d.name}</h1>
          <p className="muted mt-8" style={{ whiteSpace: 'pre-line' }}>{d.description}</p>
          <div className="facts-grid">
            <div className="fact"><div className="v">{d.weightGrams} г</div><div className="k">масса порции</div></div>
            <div className="fact"><div className="v">{d.kcalPerPortion}</div><div className="k">ккал на порцию</div></div>
            <div className="fact">
              <div className="v">{d.available ? d.availablePortions : 0}</div>
              <div className="k">{d.available ? 'порций доступно' : d.availablePortions === 0 ? 'нет в наличии' : 'кухня закрыта'}</div>
            </div>
            <div className="fact">
              <div className="v">{d.averageRating !== null && d.reviewCount > 0 ? `★ ${formatRating(d.averageRating)}` : '—'}</div>
              <div className="k">{d.reviewCount > 0 ? countLabel(d.reviewCount, ['отзыв', 'отзыва', 'отзывов']) : 'Нет оценок'}</div>
            </div>
          </div>

          <h2 style={{ fontSize: 15, marginBottom: 8 }}>Состав</h2>
          <ul className="chips" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {d.ingredients.map((ingredient) => {
              const isExcluded = excluded.has(ingredient.id);
              const isFavorite = favorite.has(ingredient.id);
              return (
                <li key={ingredient.id} className={`chip${isExcluded ? ' bad' : isFavorite ? ' on' : ''}`}>
                  {isExcluded ? '⊘ ' : isFavorite ? '♥ ' : ''}
                  {ingredient.name}
                  {isExcluded && <span className="sr-only"> (исключён в ваших предпочтениях)</span>}
                  {isFavorite && <span className="sr-only"> (любимый)</span>}
                </li>
              );
            })}
          </ul>
          <p className="tiny muted mt-8">
            Состав указан кухней. Фильтр отражает заявленный состав, а не проверку следовых аллергенов.
          </p>
        </div>

        {/* Покупка идёт сразу после описания: на узком экране она выше отзывов. */}
        <aside className="stack dish-aside" aria-label="Покупка и подбор">
          <div className="panel">
            <div className="price" style={{ fontSize: 30 }}>{formatMoney(d.price)}</div>
            <p className="small muted" style={{ margin: '4px 0 14px' }}>Доставка включена · оплата при получении</p>
            {isClient && d.available && !incompatible && (
              <div className="row" style={{ marginBottom: 14 }}>
                <Stepper value={quantity} max={maxQuantity} onChange={setQuantity} label="Количество порций" />
                <span className="small muted">до {maxQuantity} порций</span>
              </div>
            )}
            <BuyButton
              dish={d}
              size="lg"
              label="Добавить в корзину"
              busy={busyId === d.id}
              blockedReason={incompatible}
              onAdd={() => void add(d, quantity).then((ok) => ok && setQuantity(1))}
            />
            {isClient && (
              <p className="tiny muted mt-8">Добавление не резервирует порции: остаток проверяется при оформлении.</p>
            )}
          </div>

          {incompatible && (
            <Alert tone="danger" title="Блюдо вам не подходит.">
              {incompatible}. Оно скрыто из вашего подбора, добавить его в корзину нельзя.{' '}
              <Link to="/preferences">Изменить предпочтения</Link>
            </Alert>
          )}

          {isClient && p && !incompatible && <WhyPanel personal={p} />}
          {personal.error != null && <ErrorAlert error={personal.error} onRetry={personal.reload} />}
        </aside>

        <section className="panel dish-reviews" aria-labelledby="reviews-title">
            <h2 id="reviews-title">Отзывы покупателей</h2>
            {d.reviewCount === 0 ? (
              <p className="muted">Нет оценок. Отзывы оставляют покупатели после выполненного заказа.</p>
            ) : (
              <div className="agg">
                <div><div className="v">{formatRating(d.averageRating ?? 0)}</div><div className="small muted">средняя оценка</div></div>
                <div>
                  <div className="v">{d.repeatOrderPercent ?? 0}%</div>
                  <div className="small muted">заказали бы снова · {countLabel(d.repeatOrderResponses, ['ответ', 'ответа', 'ответов'])}</div>
                </div>
                <div><div className="v">{d.reviewCount}</div><div className="small muted">{plural(d.reviewCount, ['видимый отзыв', 'видимых отзыва', 'видимых отзывов'])}</div></div>
              </div>
            )}
            <ErrorAlert error={reviews.error} onRetry={reviews.reload} />
            {reviews.data?.slice(0, 20).map((review) => (
              <article className="rev" key={review.id}>
                <div className="row between">
                  <span className="stars" aria-label={`Оценка ${review.rating} из 5`}>
                    {'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}
                  </span>
                  <span className="tiny muted">{formatDate(review.createdAt)}</span>
                </div>
                {review.text && <p className="mt-8" style={{ whiteSpace: 'pre-line' }}>{review.text}</p>}
                <p className="tiny muted mt-8">Заказал бы снова: {review.reordered ? 'да' : 'нет'}</p>
              </article>
            ))}
        </section>
      </div>
      {dialog}
    </>
  );
}

function WhyPanel({ personal }: { personal: Personal }) {
  if (!personal.scoringEnabled) {
    return (
      <div className="panel soft">
        <h2 style={{ fontSize: 17, marginBottom: 6 }}>Насколько подходит вам</h2>
        <p className="small">Заполните предпочтения, чтобы увидеть балл соответствия.</p>
        <Link className="btn sm mt-12" to="/preferences">Заполнить</Link>
      </div>
    );
  }
  const item = personal.recommendation;
  if (!item || item.score === null) return null;
  return (
    <section className="panel why" aria-labelledby="why-title">
      <h2 id="why-title" style={{ fontSize: 17, marginBottom: 6 }}>Почему подходит вам</h2>
      <div className="big-score"><b>{item.score}</b><span className="muted">из 100 · соответствие профилю</span></div>
      <ul>
        {item.explanation.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <p className="tiny muted mt-12">
        Веса критериев: категория 50, любимые ингредиенты 30, калорийность 20; учитываются только заполненные.
        Балл — результат фиксированной формулы, а не вероятность, что блюдо понравится. Оценки покупателей на него не влияют.
      </p>
    </section>
  );
}
