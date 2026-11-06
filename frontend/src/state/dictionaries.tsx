import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { catalogApi, dictionaryApi } from '../api/endpoints';
import type { NamedRef, UUID } from '../api/types';

interface DictionariesValue {
  categories: NamedRef[];
  ingredients: NamedRef[];
  /** Кухни, у которых есть опубликованные блюда (отдельного публичного списка кухонь в API нет). */
  kitchens: NamedRef[];
  loaded: boolean;
  categoryName: (id: UUID) => string | undefined;
  ingredientName: (id: UUID) => string | undefined;
  kitchenName: (id: UUID) => string | undefined;
  rememberKitchen: (id: UUID, name: string) => void;
}

const DictionariesContext = createContext<DictionariesValue | null>(null);

const byName = (a: NamedRef, b: NamedRef) => a.name.localeCompare(b.name, 'ru');

export function DictionariesProvider({ children }: { children: ReactNode }) {
  const [categories, setCategories] = useState<NamedRef[]>([]);
  const [ingredients, setIngredients] = useState<NamedRef[]>([]);
  const [kitchenMap, setKitchenMap] = useState<Map<UUID, string>>(new Map());
  const [loaded, setLoaded] = useState(false);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    Promise.allSettled([
      dictionaryApi.categories().then((list) => setCategories([...list].sort(byName))),
      dictionaryApi.ingredients().then((list) => setIngredients([...list].sort(byName))),
      catalogApi.dishes({ size: 100, page: 0, sort: 'priceAsc' }).then((page) => {
        setKitchenMap((previous) => {
          const next = new Map(previous);
          page.content.forEach((dish) => next.set(dish.kitchenId, dish.kitchenName));
          return next;
        });
      }),
    ]).finally(() => setLoaded(true));
  }, []);

  const rememberKitchen = useCallback((id: UUID, name: string) => {
    setKitchenMap((previous) => {
      if (previous.get(id) === name) return previous;
      const next = new Map(previous);
      next.set(id, name);
      return next;
    });
  }, []);

  const value = useMemo<DictionariesValue>(() => {
    const categoryIndex = new Map(categories.map((c) => [c.id, c.name]));
    const ingredientIndex = new Map(ingredients.map((i) => [i.id, i.name]));
    return {
      categories,
      ingredients,
      kitchens: [...kitchenMap.entries()].map(([id, name]) => ({ id, name })).sort(byName),
      loaded,
      categoryName: (id) => categoryIndex.get(id),
      ingredientName: (id) => ingredientIndex.get(id),
      kitchenName: (id) => kitchenMap.get(id),
      rememberKitchen,
    };
  }, [categories, ingredients, kitchenMap, loaded, rememberKitchen]);

  return <DictionariesContext.Provider value={value}>{children}</DictionariesContext.Provider>;
}

export function useDictionaries(): DictionariesValue {
  const context = useContext(DictionariesContext);
  if (!context) throw new Error('useDictionaries вне DictionariesProvider');
  return context;
}
