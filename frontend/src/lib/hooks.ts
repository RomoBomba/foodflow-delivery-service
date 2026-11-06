import { useCallback, useEffect, useRef, useState } from 'react';

export interface AsyncState<T> {
  data: T | undefined;
  error: unknown;
  loading: boolean;
  /** Время последней успешной загрузки. */
  loadedAt: number | null;
  reload: () => Promise<void>;
  setData: (value: T | ((previous: T | undefined) => T)) => void;
}

/**
 * Загружает данные и хранит последний успешный результат: при ошибке повторной
 * загрузки прежние данные не стираются (FR-17).
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setDataState] = useState<T | undefined>(undefined);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [loadedAt, setLoadedAt] = useState<number | null>(null);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const generation = useRef(0);

  const reload = useCallback(async () => {
    const current = ++generation.current;
    setLoading(true);
    try {
      const value = await loaderRef.current();
      if (current !== generation.current) return;
      setDataState(value);
      setError(null);
      setLoadedAt(Date.now());
    } catch (e) {
      if (current !== generation.current) return;
      setError(e);
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    setDataState(undefined);
    setLoadedAt(null);
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const setData = useCallback((value: T | ((previous: T | undefined) => T)) => {
    setDataState((previous) => (typeof value === 'function' ? (value as (p: T | undefined) => T)(previous) : value));
  }, []);

  return { data, error, loading, loadedAt, reload, setData };
}

/** Опрос раз в intervalMs, только пока вкладка активна (FR-17, NFR-10). */
export function usePolling(callback: () => void, intervalMs: number, enabled = true): void {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    if (!enabled) return undefined;
    const tick = () => {
      if (document.visibilityState === 'visible') callbackRef.current();
    };
    const timer = window.setInterval(tick, intervalMs);
    const onVisible = () => {
      if (document.visibilityState === 'visible') callbackRef.current();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [intervalMs, enabled]);
}

/** Секунды с момента timestamp, обновляются каждую секунду. */
export function useSecondsSince(timestamp: number | null): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return timestamp === null ? null : Math.max(0, Math.round((now - timestamp) / 1000));
}

/** Черновик формы в sessionStorage: переживает перезагрузку вкладки (NFR-10). */
export function useSessionDraft<T>(key: string, initial: T): [T, (value: T) => void, () => void] {
  const [value, setValue] = useState<T>(() => {
    try {
      const raw = sessionStorage.getItem(key);
      return raw ? { ...initial, ...(JSON.parse(raw) as T) } : initial;
    } catch {
      return initial;
    }
  });
  const update = useCallback(
    (next: T) => {
      setValue(next);
      try {
        sessionStorage.setItem(key, JSON.stringify(next));
      } catch {
        /* хранилище недоступно — черновик живёт только в памяти */
      }
    },
    [key],
  );
  const clear = useCallback(() => {
    try {
      sessionStorage.removeItem(key);
    } catch {
      /* ignore */
    }
  }, [key]);
  return [value, update, clear];
}

export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title ? `${title} — FoodFlow` : 'FoodFlow — домашняя еда рядом';
  }, [title]);
}
