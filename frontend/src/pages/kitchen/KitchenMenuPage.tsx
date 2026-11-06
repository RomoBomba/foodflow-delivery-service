import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ApiError, errorText } from '../../api/client';
import { kitchenApi } from '../../api/endpoints';
import type { DishRequest, KitchenDish } from '../../api/types';
import { useToast } from '../../components/toast';
import { Alert, DishPhoto, Empty, ErrorAlert, Field, Loading, Modal, describe } from '../../components/ui';
import { countLabel, formatMoney } from '../../lib/format';
import { useAsync, useDocumentTitle } from '../../lib/hooks';
import { hasErrors, isHttpUrl, parseInteger, parsePrice, validateRequired, type FieldErrors } from '../../lib/validation';
import { useDictionaries } from '../../state/dictionaries';

interface DishForm {
  name: string;
  description: string;
  categoryId: string;
  ingredientIds: string[];
  price: string;
  weightGrams: string;
  kcalPerPortion: string;
  availablePortions: string;
  photoUrl: string;
}

const EMPTY: DishForm = {
  name: '',
  description: '',
  categoryId: '',
  ingredientIds: [],
  price: '',
  weightGrams: '',
  kcalPerPortion: '',
  availablePortions: '0',
  photoUrl: '',
};

function toForm(dish: KitchenDish): DishForm {
  return {
    name: dish.name,
    description: dish.description,
    categoryId: dish.category.id,
    ingredientIds: dish.ingredients.map((i) => i.id),
    price: dish.price.toFixed(2),
    weightGrams: String(dish.weightGrams),
    kcalPerPortion: String(dish.kcalPerPortion),
    availablePortions: String(dish.availablePortions),
    photoUrl: dish.photoUrl ?? '',
  };
}

export function KitchenMenuPage() {
  useDocumentTitle('Меню и остатки');
  const menu = useAsync(() => kitchenApi.menu(), []);
  const kitchen = useAsync(() => kitchenApi.mine(), []);
  const [editing, setEditing] = useState<KitchenDish | 'new' | null>(null);
  const toast = useToast();

  const dishes = useMemo(() => {
    const list = [...(menu.data ?? [])];
    return list.sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name, 'ru'));
  }, [menu.data]);
  const active = dishes.filter((d) => !d.archived);

  const onSaved = (dish: KitchenDish, created: boolean) => {
    menu.setData((list = []) => (created ? [...list, dish] : list.map((d) => (d.id === dish.id ? dish : d))));
    setEditing(dish);
    toast(created ? 'Блюдо опубликовано' : 'Изменения сохранены', 'ok');
  };

  if (menu.loading && !menu.data) return <Loading label="Загружаем меню…" />;

  const approved = kitchen.data?.status === 'APPROVED';

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Меню и остатки</h1>
          <p className="sub">{countLabel(active.length, ['блюдо', 'блюда', 'блюд'])} в меню · {dishes.length - active.length} в архиве</p>
        </div>
        <button type="button" className="btn primary" disabled={!approved} onClick={() => setEditing('new')}>+ Новое блюдо</button>
      </div>
      {kitchen.data && !approved && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="warn">Меню можно менять только у одобренной кухни. Текущий статус: {kitchen.data.status}.</Alert>
        </div>
      )}
      <ErrorAlert error={menu.error} onRetry={menu.reload} />

      <div className={`with-aside ${editing ? 'aside-520' : 'single'}`}>
        <section className="panel" style={{ padding: '12px 10px' }} aria-label="Блюда кухни">
          {dishes.length === 0 ? (
            <Empty title="Меню пустое"><p>Добавьте первое блюдо — оно сразу появится в каталоге.</p></Empty>
          ) : (
            <div className="table-wrap">
              <table className="menu-table responsive-table">
                <thead>
                  <tr><th aria-label="Фото" /><th>Блюдо</th><th>Цена</th><th>Свободно порций</th><th aria-label="Действия" /></tr>
                </thead>
                <tbody>
                  {dishes.map((dish) => (
                    <tr key={dish.id} className={`${dish.archived ? 'archived' : ''}${editing !== 'new' && editing?.id === dish.id ? ' selected' : ''}`}>
                      <td><DishPhoto url={dish.photoUrl} name={dish.name} /></td>
                      <td>
                        <b>{dish.name}</b>
                        <div className="tiny muted">
                          {dish.archived ? 'в архиве · остаётся в истории заказов' : `${dish.category.name} · ${dish.weightGrams} г · ${dish.kcalPerPortion} ккал`}
                        </div>
                      </td>
                      <td data-label="Цена" className="num">{formatMoney(dish.price)}</td>
                      <td data-label="Свободно">
                        {dish.archived ? '—' : dish.availablePortions === 0 ? <span className="out">0 · нет в продаже</span> : dish.availablePortions}
                      </td>
                      <td>
                        {dish.archived ? (
                          <span className="status s-cancelled">архив</span>
                        ) : (
                          <button type="button" className="btn sm" onClick={() => setEditing(dish)} disabled={!approved}>Изменить</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {editing && (
          <DishEditor
            key={editing === 'new' ? 'new' : editing.id}
            dish={editing === 'new' ? null : editing}
            onClose={() => setEditing(null)}
            onSaved={onSaved}
            onArchived={(id) => {
              menu.setData((list = []) => list.map((d) => (d.id === id ? { ...d, archived: true } : d)));
              setEditing(null);
              toast('Блюдо отправлено в архив', 'info');
            }}
          />
        )}
      </div>
    </>
  );
}

function DishEditor({
  dish,
  onClose,
  onSaved,
  onArchived,
}: {
  dish: KitchenDish | null;
  onClose: () => void;
  onSaved: (dish: KitchenDish, created: boolean) => void;
  onArchived: (id: string) => void;
}) {
  const { categories, ingredients } = useDictionaries();
  const [form, setForm] = useState<DishForm>(dish ? toForm(dish) : EMPTY);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [failure, setFailure] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState('');
  const [confirmArchive, setConfirmArchive] = useState(false);

  useEffect(() => {
    setForm(dish ? toForm(dish) : EMPTY);
    setErrors({});
  }, [dish]);

  const set = (key: keyof DishForm, value: string | string[]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors(({ [key]: _drop, ...rest }) => rest);
  };

  const validate = (): { errors: FieldErrors; request: DishRequest | null } => {
    const e: FieldErrors = {};
    const name = validateRequired(form.name, 120, 'Название');
    if (name) e.name = name;
    const description = validateRequired(form.description, 1000, 'Описание');
    if (description) e.description = description;
    if (!form.categoryId) e.categoryId = 'Выберите категорию';
    if (form.ingredientIds.length < 1) e.ingredientIds = 'Укажите хотя бы один ингредиент';
    if (form.ingredientIds.length > 50) e.ingredientIds = 'Не больше 50 ингредиентов';
    const price = parsePrice(form.price);
    if (price === null) e.price = 'Положительная цена, до 2 знаков после запятой';
    const weight = parseInteger(form.weightGrams);
    if (weight === null || weight <= 0) e.weightGrams = 'Положительное целое число';
    const kcal = parseInteger(form.kcalPerPortion);
    if (kcal === null) e.kcalPerPortion = 'Целое число ≥ 0';
    const portions = parseInteger(form.availablePortions);
    if (portions === null) e.availablePortions = 'Целое число ≥ 0';
    const photo = form.photoUrl.trim();
    if (photo && !isHttpUrl(photo)) e.photoUrl = 'Ссылка должна начинаться с http:// или https://';
    if (hasErrors(e)) return { errors: e, request: null };
    return {
      errors: e,
      request: {
        name: form.name.trim(),
        description: form.description.trim(),
        categoryId: form.categoryId,
        ingredientIds: form.ingredientIds,
        price: price!,
        weightGrams: weight!,
        kcalPerPortion: kcal!,
        availablePortions: portions!,
        photoUrl: photo || null,
      },
    };
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const { errors: nextErrors, request } = validate();
    setErrors(nextErrors);
    if (!request) return;
    setBusy(true);
    setFailure(null);
    try {
      const saved = dish ? await kitchenApi.updateDish(dish.id, request) : await kitchenApi.createDish(request);
      onSaved(saved, !dish);
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fields).length > 0) setErrors(error.fields);
      else setFailure(error);
    } finally {
      setBusy(false);
    }
  };

  const archive = async () => {
    if (!dish) return;
    setBusy(true);
    try {
      await kitchenApi.archiveDish(dish.id);
      onArchived(dish.id);
    } catch (error) {
      setFailure(new Error(errorText(error)));
    } finally {
      setBusy(false);
      setConfirmArchive(false);
    }
  };

  const chosen = new Set(form.ingredientIds);
  const q = query.trim().toLowerCase();
  const options = ingredients.filter((i) => !chosen.has(i.id) && (q === '' || i.name.toLowerCase().includes(q))).slice(0, 30);
  const priceChanged = dish && parsePrice(form.price) !== null && parsePrice(form.price) !== dish.price;

  return (
    <form className="panel" onSubmit={submit} noValidate aria-labelledby="editor-title">
      <div className="row between">
        <h2 id="editor-title" style={{ margin: 0, fontSize: 18 }}>{dish ? dish.name : 'Новое блюдо'}</h2>
        <button type="button" className="icon-btn" aria-label="Закрыть редактор" onClick={onClose}>✕</button>
      </div>
      <hr className="divider" />
      <Field id="d-name" label="Название *" error={errors.name} hint="до 120 символов">
        <input className="input" maxLength={120} {...describe('d-name', errors.name, true)} value={form.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field id="d-description" label="Описание *" error={errors.description} hint="до 1000 символов">
        <textarea className="input" maxLength={1000} {...describe('d-description', errors.description, true)} value={form.description} onChange={(e) => set('description', e.target.value)} />
      </Field>
      <div className="two">
        <Field id="d-category" label="Категория *" error={errors.categoryId}>
          <select className="input" {...describe('d-category', errors.categoryId)} value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)}>
            <option value="">Выберите…</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field id="d-price" label="Цена, ₽ *" error={errors.price}>
          <input className="input" inputMode="decimal" {...describe('d-price', errors.price)} value={form.price} onChange={(e) => set('price', e.target.value)} />
        </Field>
        <Field id="d-weight" label="Масса порции, г *" error={errors.weightGrams}>
          <input className="input" inputMode="numeric" {...describe('d-weight', errors.weightGrams)} value={form.weightGrams} onChange={(e) => set('weightGrams', e.target.value)} />
        </Field>
        <Field id="d-kcal" label="Ккал на порцию *" error={errors.kcalPerPortion}>
          <input className="input" inputMode="numeric" {...describe('d-kcal', errors.kcalPerPortion)} value={form.kcalPerPortion} onChange={(e) => set('kcalPerPortion', e.target.value)} />
        </Field>
        <Field id="d-portions" label="Свободно порций *" error={errors.availablePortions} hint="целое ≥ 0; 0 — нет в продаже">
          <input className="input" inputMode="numeric" {...describe('d-portions', errors.availablePortions, true)} value={form.availablePortions} onChange={(e) => set('availablePortions', e.target.value)} />
        </Field>
        <Field id="d-photo" label="Фото (URL)" error={errors.photoUrl} hint="необязательно · http/https">
          <input className="input" type="url" maxLength={2048} placeholder="https://…" {...describe('d-photo', errors.photoUrl, true)} value={form.photoUrl} onChange={(e) => set('photoUrl', e.target.value)} />
        </Field>
      </div>

      <div className="field">
        <span className="label" id="d-ingr-label">Состав * <span className="tiny muted">({form.ingredientIds.length} / 50)</span></span>
        <div className="chips" role="group" aria-labelledby="d-ingr-label">
          {form.ingredientIds.map((id) => (
            <button key={id} type="button" className="chip on" aria-label={`Убрать ${ingredients.find((i) => i.id === id)?.name ?? ''}`} onClick={() => set('ingredientIds', form.ingredientIds.filter((x) => x !== id))}>
              {ingredients.find((i) => i.id === id)?.name ?? id} <span aria-hidden="true">✕</span>
            </button>
          ))}
        </div>
        <input className="input mt-8" type="search" placeholder="Добавить ингредиент из справочника…" aria-label="Поиск ингредиента" value={query} onChange={(e) => setQuery(e.target.value)} />
        {(q !== '' || form.ingredientIds.length === 0) && (
          <div className="chips mt-8">
            {options.map((i) => (
              <button key={i.id} type="button" className="chip" onClick={() => { set('ingredientIds', [...form.ingredientIds, i.id]); setQuery(''); }}>
                + {i.name}
              </button>
            ))}
            {options.length === 0 && <span className="tiny muted">Ничего не найдено</span>}
          </div>
        )}
        {errors.ingredientIds ? <span className="err">{errors.ingredientIds}</span> : <span className="hint">Состав и калорийность нужны для подбора и исключений.</span>}
      </div>

      {priceChanged && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="info">Новая цена действует для новых заказов. Оформленные заказы сохраняют прежнюю цену; клиенты с блюдом в корзине подтвердят новую.</Alert>
        </div>
      )}
      <ErrorAlert error={failure} />
      <div className="row wrap mt-12">
        <button type="submit" className="btn primary grow" disabled={busy}>{busy ? 'Сохраняем…' : dish ? 'Сохранить' : 'Опубликовать'}</button>
        <button type="button" className="btn" onClick={onClose}>Отмена</button>
        {dish && <button type="button" className="btn danger" onClick={() => setConfirmArchive(true)}>В архив</button>}
      </div>

      {confirmArchive && dish && (
        <Modal
          title={`Отправить «${dish.name}» в архив?`}
          onClose={() => setConfirmArchive(false)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setConfirmArchive(false)}>Отмена</button>
              <button type="button" className="btn danger-solid" disabled={busy} onClick={archive}>В архив</button>
            </>
          }
        >
          <p className="small">Блюдо исчезнет из каталога, его нельзя будет редактировать. В истории заказов оно сохранится. Удаления нет — только архив.</p>
        </Modal>
      )}
    </form>
  );
}
