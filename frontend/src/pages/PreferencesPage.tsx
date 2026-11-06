import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { preferencesApi } from '../api/endpoints';
import type { NamedRef, Preferences, PreferencesRequest } from '../api/types';
import { useToast } from '../components/toast';
import { Alert, ErrorAlert, Loading } from '../components/ui';
import { useAsync, useDocumentTitle } from '../lib/hooks';
import { parseInteger } from '../lib/validation';
import { useCart } from '../state/cart';
import { useDictionaries } from '../state/dictionaries';

interface FormState {
  categories: Set<string>;
  favorites: Set<string>;
  excluded: Set<string>;
  kcalMin: string;
  kcalMax: string;
}

const LIMIT = 100;

function fromPrefs(prefs: Preferences): FormState {
  return {
    categories: new Set(prefs.favoriteCategoryIds),
    favorites: new Set(prefs.favoriteIngredientIds),
    excluded: new Set(prefs.excludedIngredientIds),
    kcalMin: prefs.kcalMin?.toString() ?? '',
    kcalMax: prefs.kcalMax?.toString() ?? '',
  };
}

export function PreferencesPage() {
  useDocumentTitle('Предпочтения');
  const prefs = useAsync(() => preferencesApi.get(), []);
  const { categories, ingredients, loaded } = useDictionaries();
  const cart = useCart();
  const toast = useToast();
  const [form, setForm] = useState<FormState | null>(null);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [removed, setRemoved] = useState<string[]>([]);
  const [showErrors, setShowErrors] = useState(false);

  useEffect(() => {
    if (prefs.data && !form) setForm(fromPrefs(prefs.data));
  }, [prefs.data, form]);

  const errors = useMemo(() => (form ? validate(form) : {}), [form]);

  if ((prefs.loading && !prefs.data) || !loaded) return <Loading />;
  if (!form) return <ErrorAlert error={prefs.error} onRetry={prefs.reload} />;

  const toggle = (key: 'categories' | 'favorites' | 'excluded', id: string) => {
    setForm((current) => {
      if (!current) return current;
      const next = new Set(current[key]);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { ...current, [key]: next };
    });
  };

  const save = async () => {
    setShowErrors(true);
    if (Object.keys(errors).length > 0) return;
    const request: PreferencesRequest = {
      favoriteCategoryIds: [...form.categories],
      favoriteIngredientIds: [...form.favorites],
      excludedIngredientIds: [...form.excluded],
      kcalMin: form.kcalMin.trim() === '' ? null : parseInteger(form.kcalMin),
      kcalMax: form.kcalMax.trim() === '' ? null : parseInteger(form.kcalMax),
    };
    setBusy(true);
    setSubmitError(null);
    setRemoved([]);
    const before = cart.cart.items;
    try {
      const saved = await preferencesApi.save(request);
      prefs.setData(saved);
      setForm(fromPrefs(saved));
      setShowErrors(false);
      // Сервер в той же транзакции убирает из корзины несовместимые блюда (раздел 7.2).
      const after = await cart.refresh().catch(() => null);
      if (after) {
        const left = new Set(after.items.map((i) => i.dishId));
        setRemoved(before.filter((i) => !left.has(i.dishId)).map((i) => i.dishName));
      }
      toast('Предпочтения сохранены', 'ok');
    } catch (error) {
      setSubmitError(error);
    } finally {
      setBusy(false);
    }
  };

  const reset = () => {
    setForm({ categories: new Set(), favorites: new Set(), excluded: new Set(), kcalMin: '', kcalMax: '' });
    setShowErrors(false);
  };

  const shownErrors = showErrors ? errors : {};
  const serverField = submitError instanceof ApiError ? submitError.code : null;
  const empty = form.categories.size === 0 && form.favorites.size === 0 && form.kcalMin.trim() === '' && form.kcalMax.trim() === '';

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Мои предпочтения</h1>
          <p className="sub">По ним считается балл соответствия и формируется подбор. Заполняйте только то, что важно.</p>
        </div>
      </div>

      <div className="with-aside aside-380">
        <div className="stack">
          <section className="panel" aria-labelledby="p-cat">
            <h2 id="p-cat">Любимые категории <Weight value={50} /></h2>
            <ChipPicker items={categories} selected={form.categories} onToggle={(id) => toggle('categories', id)} label="Любимые категории" />
            {shownErrors.categories && <p className="err-text">{shownErrors.categories}</p>}
          </section>

          <section className="panel" aria-labelledby="p-fav">
            <h2 id="p-fav">Любимые ингредиенты <Weight value={30} /></h2>
            <SearchableChips items={ingredients} selected={form.favorites} conflicts={form.excluded} onToggle={(id) => toggle('favorites', id)} label="Любимые ингредиенты" />
            <p className="tiny muted mt-8">Учитывается доля ваших любимых ингредиентов, которые есть в блюде.</p>
            {shownErrors.favorites && <p className="err-text">{shownErrors.favorites}</p>}
          </section>

          <section className="panel" aria-labelledby="p-exc">
            <h2 id="p-exc">Исключить ингредиенты</h2>
            <SearchableChips items={ingredients} selected={form.excluded} conflicts={form.favorites} bad onToggle={(id) => toggle('excluded', id)} label="Исключённые ингредиенты" />
            {(shownErrors.conflict || serverField === 'PREFERENCE_INGREDIENT_CONFLICT') && (
              <p className="err-text">{shownErrors.conflict ?? 'Ингредиент нельзя одновременно любить и исключать'}</p>
            )}
            <p className="tiny muted mt-8">
              Блюда с исключёнными ингредиентами не попадут в подбор, и их нельзя будет заказать. Фильтр опирается на состав, указанный кухней.
            </p>
          </section>

          <section className="panel" aria-labelledby="p-kcal">
            <h2 id="p-kcal">Калорийность порции <Weight value={20} /></h2>
            <div className="two">
              <div className="field">
                <label htmlFor="kcal-min">От, ккал</label>
                <input id="kcal-min" className="input" inputMode="numeric" value={form.kcalMin} aria-invalid={shownErrors.kcal ? true : undefined} onChange={(e) => setForm({ ...form, kcalMin: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor="kcal-max">До, ккал</label>
                <input id="kcal-max" className="input" inputMode="numeric" value={form.kcalMax} aria-invalid={shownErrors.kcal ? true : undefined} onChange={(e) => setForm({ ...form, kcalMax: e.target.value })} />
              </div>
            </div>
            {(shownErrors.kcal || serverField === 'INVALID_KCAL_RANGE') && (
              <p className="err-text">{shownErrors.kcal ?? 'Нижняя граница не может быть больше верхней'}</p>
            )}
            <p className="tiny muted">Необязательно. Можно указать только одну границу.</p>
          </section>

          <ErrorAlert error={submitError} />
          <div className="row wrap">
            <button type="button" className="btn primary lg" disabled={busy} onClick={save}>
              {busy ? 'Сохраняем…' : 'Сохранить предпочтения'}
            </button>
            <button type="button" className="btn lg ghost" onClick={reset}>Очистить всё</button>
          </div>
        </div>

        <aside className="stack">
          <section className="panel" aria-labelledby="how">
            <h2 id="how">Как считается балл</h2>
            <p className="small muted" style={{ marginBottom: 10 }}>
              Учитываются только заполненные критерии. Итог — от 0 до 100, округление 0,5 вверх.
            </p>
            <dl className="kv" style={{ gridTemplateColumns: '1fr auto' }}>
              <dt>Категория блюда в любимых</dt><dd className="strong">50</dd>
              <dt>Доля любимых ингредиентов</dt><dd className="strong">30</dd>
              <dt>Ккал в диапазоне</dt><dd className="strong">20</dd>
            </dl>
            <div className="mt-12">
              <Alert tone="info">
                Пример: категория подходит, 1 из 2 любимых ингредиентов, ккал в диапазоне → 50 + 15 + 20 = <b>85</b>.
              </Alert>
            </div>
          </section>
          {removed.length > 0 && (
            <Alert tone="warn" title="Корзина обновлена.">
              Удалено из-за исключённых ингредиентов: {removed.join(', ')}. <Link to="/cart">Открыть корзину</Link>
            </Alert>
          )}
          {empty && (
            <div className="panel soft">
              <p className="small">
                Пока не заполнен ни один критерий, подбор не работает: балл не показывается, каталог сортируется по цене.
              </p>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}

function validate(form: FormState): Record<string, string> {
  const errors: Record<string, string> = {};
  const conflict = [...form.favorites].filter((id) => form.excluded.has(id));
  if (conflict.length > 0) errors.conflict = 'Один ингредиент нельзя одновременно любить и исключать — уберите его из одного списка.';
  if (form.categories.size > LIMIT) errors.categories = `Не больше ${LIMIT} категорий`;
  if (form.favorites.size > LIMIT || form.excluded.size > LIMIT) errors.favorites = `Не больше ${LIMIT} ингредиентов в списке`;
  const min = form.kcalMin.trim() === '' ? null : parseInteger(form.kcalMin);
  const max = form.kcalMax.trim() === '' ? null : parseInteger(form.kcalMax);
  if ((form.kcalMin.trim() !== '' && min === null) || (form.kcalMax.trim() !== '' && max === null)) {
    errors.kcal = 'Введите целое неотрицательное число';
  } else if (min !== null && max !== null && min > max) {
    errors.kcal = 'Нижняя граница не может быть больше верхней';
  }
  return errors;
}

function Weight({ value }: { value: number }) {
  return (
    <span className="chip on" style={{ minHeight: 22, fontSize: 12, marginLeft: 8, verticalAlign: 'middle' }}>
      вес {value}
    </span>
  );
}

function ChipPicker({ items, selected, onToggle, label }: { items: NamedRef[]; selected: Set<string>; onToggle: (id: string) => void; label: string }) {
  return (
    <div className="chips" role="group" aria-label={label}>
      {items.map((item) => (
        <button key={item.id} type="button" className={`chip${selected.has(item.id) ? ' on' : ''}`} aria-pressed={selected.has(item.id)} onClick={() => onToggle(item.id)}>
          {item.name}
        </button>
      ))}
    </div>
  );
}

function SearchableChips({
  items,
  selected,
  conflicts,
  onToggle,
  bad,
  label,
}: {
  items: NamedRef[];
  selected: Set<string>;
  conflicts: Set<string>;
  onToggle: (id: string) => void;
  bad?: boolean;
  label: string;
}) {
  const [query, setQuery] = useState('');
  const q = query.trim().toLowerCase();
  const chosen = items.filter((i) => selected.has(i.id));
  const rest = items.filter((i) => !selected.has(i.id) && (q === '' || i.name.toLowerCase().includes(q)));
  const shown = q === '' ? rest.slice(0, 24) : rest.slice(0, 60);
  return (
    <>
      <input className="input" type="search" placeholder="Найти ингредиент…" aria-label={`${label}: поиск`} value={query} onChange={(e) => setQuery(e.target.value)} style={{ marginBottom: 10 }} />
      <div className="chips" role="group" aria-label={label}>
        {chosen.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-pressed
            className={`chip ${bad ? 'bad' : 'on'}`}
            style={conflicts.has(item.id) ? { outline: '2px solid var(--danger)' } : undefined}
            onClick={() => onToggle(item.id)}
          >
            {bad ? '⊘ ' : ''}{item.name} <span aria-hidden="true">✕</span>
          </button>
        ))}
        {shown.map((item) => (
          <button key={item.id} type="button" aria-pressed={false} className="chip" onClick={() => onToggle(item.id)}>
            {item.name}
          </button>
        ))}
        {q === '' && rest.length > shown.length && <span className="tiny muted" style={{ alignSelf: 'center' }}>и ещё {rest.length - shown.length} — воспользуйтесь поиском</span>}
        {q !== '' && rest.length === 0 && <span className="tiny muted">Ничего не найдено</span>}
      </div>
    </>
  );
}
