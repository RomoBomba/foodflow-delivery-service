import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { catalogApi, preferencesApi } from '../api/endpoints';
import type { CatalogDish, CatalogQuery, CatalogSort, RecommendationPage } from '../api/types';
import { BuyButton, useAddToCart } from '../components/AddToCart';
import { DishPhoto, Empty, ErrorAlert, Loading, Pagination, RatingLine } from '../components/ui';
import { countLabel, formatMoney } from '../lib/format';
import { useAsync, useDocumentTitle } from '../lib/hooks';
import { useAuth } from '../state/auth';
import { useDictionaries } from '../state/dictionaries';

const PAGE_SIZE = 20;

function readQuery(params: URLSearchParams, isClient: boolean): CatalogQuery {
  const number = (key: string) => {
    const value = params.get(key);
    if (value === null || value.trim() === '') return undefined;
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
  };
  const sortParam = params.get('sort');
  let sort: CatalogSort = isClient ? 'relevance' : 'priceAsc';
  if (sortParam === 'priceAsc' || sortParam === 'priceDesc') sort = sortParam;
  if (sortParam === 'relevance' && isClient) sort = 'relevance';
  return {
    kitchenId: params.get('kitchenId') ?? undefined,
    categoryId: params.get('categoryId') ?? undefined,
    minPrice: number('minPrice'),
    maxPrice: number('maxPrice'),
    availableOnly: params.get('availableOnly') === 'true' || undefined,
    page: Math.max(0, Math.floor(number('page') ?? 0)),
    size: PAGE_SIZE,
    sort,
  };
}

export function CatalogPage() {
  useDocumentTitle('Каталог');
  const { user, checking } = useAuth();
  const isClient = user?.role === 'CLIENT';
  const [params, setParams] = useSearchParams();
  const query = useMemo(() => readQuery(params, isClient), [params, isClient]);
  const { categories, kitchens, categoryName, ingredientName, rememberKitchen } = useDictionaries();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const { add, busyId, dialog } = useAddToCart();

  const catalog = useAsync(() => catalogApi.dishes(query), [JSON.stringify(query), checking]);

  // Балл соответствия есть только в /api/recommendations; каталог с sort=relevance
  // отдаёт порядок, но не сами баллы.
  const reco = useAsync<RecommendationPage | null>(
    () => (isClient ? preferencesApi.recommendations(0, 100) : Promise.resolve(null)),
    [isClient, user?.id],
  );
  const prefs = useAsync(() => (isClient ? preferencesApi.get() : Promise.resolve(null)), [isClient, user?.id]);

  useEffect(() => {
    catalog.data?.content.forEach((dish) => rememberKitchen(dish.kitchenId, dish.kitchenName));
  }, [catalog.data, rememberKitchen]);

  const scores = useMemo(() => {
    const map = new Map<string, number>();
    reco.data?.content.forEach((item) => {
      if (item.score !== null) map.set(item.dish.id, item.score);
    });
    return map;
  }, [reco.data]);

  const scoringEnabled = Boolean(reco.data?.scoringEnabled);

  const update = (changes: Record<string, string | undefined>, resetPage = true) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key);
      else next.set(key, value);
    }
    if (resetPage) next.delete('page');
    setParams(next);
  };

  const activeFilters = [query.kitchenId, query.categoryId, query.minPrice, query.maxPrice, query.availableOnly].filter(
    (v) => v !== undefined,
  ).length;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Домашняя еда рядом</h1>
          <p className="sub">Блюда одобренных кухонь. Доставка включена в цену, оплата при получении.</p>
        </div>
      </div>

      {isClient && prefs.data && reco.data && (
        <ProfileBanner
          scoringEnabled={scoringEnabled}
          summary={describeProfile(prefs.data, categoryName, ingredientName)}
        />
      )}
      {!user && !checking && (
        <div className="reco">
          <div>
            <div className="t">Персональный подбор</div>
            <div className="d">Войдите как клиент и укажите предпочтения — покажем, насколько блюдо вам подходит.</div>
          </div>
          <Link className="btn sm" to="/login">Войти</Link>
        </div>
      )}

      <div className="toolbar">
        <button
          type="button"
          className="btn filters-toggle"
          aria-expanded={filtersOpen}
          aria-controls="catalog-filters"
          onClick={() => setFiltersOpen((v) => !v)}
        >
          Фильтры{activeFilters > 0 ? ` · ${activeFilters}` : ''}
        </button>
        <span className="count" aria-live="polite">
          {catalog.data ? `Найдено ${countLabel(catalog.data.totalElements, ['блюдо', 'блюда', 'блюд'])}` : ''}
        </span>
        <label className="small muted" htmlFor="sort">Сортировка</label>
        <select
          id="sort"
          className="input"
          style={{ width: 'auto', minWidth: 200 }}
          value={query.sort}
          onChange={(event) => update({ sort: event.target.value })}
        >
          {isClient && <option value="relevance">По соответствию</option>}
          <option value="priceAsc">Сначала дешевле</option>
          <option value="priceDesc">Сначала дороже</option>
        </select>
      </div>

      <div className="with-side">
        <Filters
          key={params.toString()}
          open={filtersOpen}
          query={query}
          categories={categories}
          kitchens={kitchens}
          onApply={(changes) => {
            update(changes);
            setFiltersOpen(false);
          }}
        />

        <section aria-label="Блюда">
          <ErrorAlert error={catalog.error} onRetry={catalog.reload} />
          {catalog.loading && !catalog.data && <Loading />}
          {catalog.data && catalog.data.content.length === 0 && (
            <Empty title="Ничего не нашлось">
              <p>
                {query.page && query.page > 0
                  ? 'На этой странице блюд нет.'
                  : 'Попробуйте изменить фильтры или зайдите позже — кухни обновляют меню каждый день.'}
              </p>
            </Empty>
          )}
          {catalog.data && catalog.data.content.length > 0 && (
            <div className="cards">
              {catalog.data.content.map((dish) => (
                <DishCard
                  key={dish.id}
                  dish={dish}
                  score={isClient && scoringEnabled ? scores.get(dish.id) : undefined}
                  busy={busyId === dish.id}
                  onAdd={() => void add(dish)}
                />
              ))}
            </div>
          )}
          {catalog.data && (
            <>
              <Pagination
                page={catalog.data.page}
                totalPages={catalog.data.totalPages}
                onPage={(page) => {
                  update({ page: page > 0 ? String(page) : undefined }, false);
                  window.scrollTo({ top: 0 });
                }}
              />
              {catalog.data.totalPages > 1 && (
                <p className="tiny muted center mt-8">По {PAGE_SIZE} блюд на странице</p>
              )}
            </>
          )}
        </section>
      </div>
      {dialog}
    </>
  );
}

function ProfileBanner({ scoringEnabled, summary }: { scoringEnabled: boolean; summary: string }) {
  if (!scoringEnabled) {
    return (
      <div className="reco">
        <div>
          <div className="t">Заполните предпочтения</div>
          <div className="d">Пока профиль пуст, балл соответствия не считается и каталог отсортирован по цене.</div>
        </div>
        <Link className="btn sm" to="/preferences">Указать предпочтения</Link>
      </div>
    );
  }
  return (
    <div className="reco">
      <div>
        <div className="t">Подобрано по вашим предпочтениям</div>
        <div className="d">{summary}. Блюда с исключёнными ингредиентами в подборе не показываются.</div>
      </div>
      <Link className="btn sm" to="/preferences">Изменить предпочтения</Link>
    </div>
  );
}

function describeProfile(
  prefs: { favoriteCategoryIds: string[]; favoriteIngredientIds: string[]; excludedIngredientIds: string[]; kcalMin: number | null; kcalMax: number | null },
  categoryName: (id: string) => string | undefined,
  ingredientName: (id: string) => string | undefined,
): string {
  const parts: string[] = [];
  const names = (ids: string[], resolve: (id: string) => string | undefined) =>
    ids.map(resolve).filter(Boolean).join(', ');
  if (prefs.favoriteCategoryIds.length) parts.push(`любимые категории: ${names(prefs.favoriteCategoryIds, categoryName)}`);
  if (prefs.favoriteIngredientIds.length) parts.push(`любимые ингредиенты: ${names(prefs.favoriteIngredientIds, ingredientName)}`);
  if (prefs.kcalMin !== null || prefs.kcalMax !== null) {
    parts.push(`${prefs.kcalMin ?? 0}–${prefs.kcalMax ?? '∞'} ккал`);
  }
  if (prefs.excludedIngredientIds.length) parts.push(`исключено: ${names(prefs.excludedIngredientIds, ingredientName)}`);
  const text = parts.join(' · ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function Filters({
  open,
  query,
  categories,
  kitchens,
  onApply,
}: {
  open: boolean;
  query: CatalogQuery;
  categories: { id: string; name: string }[];
  kitchens: { id: string; name: string }[];
  onApply: (changes: Record<string, string | undefined>) => void;
}) {
  const [kitchenId, setKitchenId] = useState(query.kitchenId ?? '');
  const [categoryId, setCategoryId] = useState(query.categoryId ?? '');
  const [minPrice, setMinPrice] = useState(query.minPrice?.toString() ?? '');
  const [maxPrice, setMaxPrice] = useState(query.maxPrice?.toString() ?? '');
  const [availableOnly, setAvailableOnly] = useState(Boolean(query.availableOnly));
  const [error, setError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const min = minPrice.trim() === '' ? undefined : Number(minPrice.replace(',', '.'));
    const max = maxPrice.trim() === '' ? undefined : Number(maxPrice.replace(',', '.'));
    if ((min !== undefined && (!Number.isFinite(min) || min < 0)) || (max !== undefined && (!Number.isFinite(max) || max < 0))) {
      return setError('Цена не может быть отрицательной');
    }
    if (min !== undefined && max !== undefined && min > max) return setError('Минимальная цена больше максимальной');
    setError(null);
    onApply({
      kitchenId: kitchenId || undefined,
      categoryId: categoryId || undefined,
      minPrice: min?.toString(),
      maxPrice: max?.toString(),
      availableOnly: availableOnly ? 'true' : undefined,
    });
  };

  return (
    <form className="panel filters" id="catalog-filters" data-open={open} onSubmit={submit} aria-label="Фильтры каталога">
      <h3><label htmlFor="f-kitchen">Кухня</label></h3>
      <select id="f-kitchen" className="input" value={kitchenId} onChange={(e) => setKitchenId(e.target.value)}>
        <option value="">Все кухни</option>
        {kitchens.map((k) => (
          <option key={k.id} value={k.id}>{k.name}</option>
        ))}
      </select>

      <h3><label htmlFor="f-category">Категория</label></h3>
      <select id="f-category" className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
        <option value="">Все категории</option>
        {categories.map((c) => (
          <option key={c.id} value={c.id}>{c.name}</option>
        ))}
      </select>

      <h3>Цена, ₽</h3>
      <div className="price-range">
        <input className="input" inputMode="decimal" aria-label="Цена от" placeholder="от" value={minPrice} onChange={(e) => setMinPrice(e.target.value)} aria-invalid={error ? true : undefined} />
        <input className="input" inputMode="decimal" aria-label="Цена до" placeholder="до" value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)} aria-invalid={error ? true : undefined} />
      </div>
      {error && <p className="tiny mt-8" style={{ color: 'var(--danger)', fontWeight: 600 }} role="alert">{error}</p>}

      <h3>Наличие</h3>
      <label className="check">
        <input type="checkbox" checked={availableOnly} onChange={(e) => setAvailableOnly(e.target.checked)} />
        Только в наличии
      </label>
      <hr className="divider" />
      <div className="row between">
        <button
          type="button"
          className="btn ghost sm"
          onClick={() => onApply({ kitchenId: undefined, categoryId: undefined, minPrice: undefined, maxPrice: undefined, availableOnly: undefined })}
        >
          Сбросить
        </button>
        <button type="submit" className="btn primary sm">Показать</button>
      </div>
    </form>
  );
}

function DishCard({ dish, score, busy, onAdd }: { dish: CatalogDish; score?: number; busy: boolean; onAdd: () => void }) {
  return (
    <article className={`card${dish.available ? '' : ' off'}`}>
      <DishPhoto
        url={dish.photoUrl}
        name={dish.name}
        label={
          score !== undefined ? (
            <span className="score" title="Соответствие вашему профилю, 0–100">
              {score}<span className="word"> · соответствие</span>
            </span>
          ) : undefined
        }
      />
      <div className="body">
        <h3><Link to={`/dishes/${dish.id}`}>{dish.name}</Link></h3>
        <div className="kitchen">{dish.kitchenName}</div>
        <div className="facts">
          {dish.weightGrams} г · {dish.kcalPerPortion} ккал ·{' '}
          {dish.available ? (
            <>осталось {dish.availablePortions}</>
          ) : (
            <span className="out">{dish.availablePortions === 0 ? 'нет в наличии' : 'кухня не принимает заказы'}</span>
          )}
        </div>
        <RatingLine {...dish} />
        <div className="foot">
          <span className="price">{formatMoney(dish.price)}</span>
          <BuyButton dish={dish} busy={busy} onAdd={onAdd} />
        </div>
      </div>
    </article>
  );
}
