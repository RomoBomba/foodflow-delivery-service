import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, NetworkError, api, buildUrl, errorText, onUnauthorized, resetCsrf } from './client';

type Call = { url: string; init: RequestInit };

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

let calls: Call[];
let queue: (() => Response | Promise<Response>)[];

beforeEach(() => {
  calls = [];
  queue = [];
  resetCsrf();
  vi.stubGlobal('fetch', async (url: string, init: RequestInit = {}) => {
    calls.push({ url, init });
    const next = queue.shift();
    if (!next) throw new Error(`Неожиданный запрос ${url}`);
    return next();
  });
});

afterEach(() => vi.unstubAllGlobals());

const headerOf = (call: Call, name: string) => (call.init.headers as Record<string, string> | undefined)?.[name];

describe('api client', () => {
  it('строит query-строку без пустых параметров', () => {
    expect(buildUrl('/api/dishes', { page: 0, sort: 'priceAsc', kitchenId: undefined, minPrice: '' })).toBe(
      '/api/dishes?page=0&sort=priceAsc',
    );
  });

  it('добавляет CSRF-токен в изменяющие запросы и не добавляет в GET', async () => {
    queue.push(() => json(200, { id: 'c1', items: [] }));
    queue.push(() => json(200, { token: 't-1', headerName: 'X-CSRF-TOKEN' }));
    queue.push(() => json(201, { id: 'c1', items: [] }));
    await api('/api/cart');
    await api('/api/cart/items', { method: 'POST', json: { dishId: 'd', quantity: 1 } });
    expect(headerOf(calls[0]!, 'X-CSRF-TOKEN')).toBeUndefined();
    expect(calls[1]!.url).toBe('/api/auth/csrf');
    expect(headerOf(calls[2]!, 'X-CSRF-TOKEN')).toBe('t-1');
    expect(calls[2]!.init.credentials).toBe('same-origin');
  });

  it('при 403 без тела обновляет CSRF-токен и повторяет запрос один раз', async () => {
    queue.push(() => json(200, { token: 'old', headerName: 'X-CSRF-TOKEN' }));
    queue.push(() => new Response(null, { status: 403 }));
    queue.push(() => json(200, { token: 'new', headerName: 'X-CSRF-TOKEN' }));
    queue.push(() => new Response(null, { status: 204 }));
    await api('/api/auth/logout', { method: 'POST' });
    expect(calls.map((c) => c.url)).toEqual(['/api/auth/csrf', '/api/auth/logout', '/api/auth/csrf', '/api/auth/logout']);
    expect(headerOf(calls[3]!, 'X-CSRF-TOKEN')).toBe('new');
  });

  it('разбирает бизнес-ошибку с кодом, полями расчёта и X-Request-ID', async () => {
    queue.push(() => json(200, { token: 't', headerName: 'X-CSRF-TOKEN' }));
    queue.push(() =>
      json(
        409,
        { code: 'PRICE_CHANGED', message: 'Цена изменилась', updatedTotal: 930, updatedPriceFingerprint: 'f'.repeat(64), items: [] },
        { 'X-Request-ID': 'req-42' },
      ),
    );
    const error = (await api('/api/orders', { method: 'POST', json: {}, headers: { 'Idempotency-Key': 'key-12345' } }).catch(
      (e: unknown) => e,
    )) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('PRICE_CHANGED');
    expect(error.body.updatedTotal).toBe(930);
    expect(error.requestId).toBe('req-42');
    expect(headerOf(calls[1]!, 'Idempotency-Key')).toBe('key-12345');
  });

  it('сообщает о 401 подписчикам, кроме тихих запросов', async () => {
    const listener = vi.fn();
    const off = onUnauthorized(listener);
    queue.push(() => new Response(null, { status: 401 }));
    queue.push(() => new Response(null, { status: 401 }));
    await expect(api('/api/auth/me', { silent401: true })).rejects.toBeInstanceOf(ApiError);
    expect(listener).not.toHaveBeenCalled();
    await expect(api('/api/orders')).rejects.toMatchObject({ status: 401, code: 'HTTP_401' });
    expect(listener).toHaveBeenCalledTimes(1);
    off();
  });

  it('превращает сбой сети в NetworkError (исход неизвестен)', async () => {
    queue.push(() => Promise.reject(new TypeError('Failed to fetch')));
    await expect(api('/api/orders')).rejects.toBeInstanceOf(NetworkError);
  });

  it('добавляет код запроса к тексту ошибок 5xx', () => {
    expect(errorText(new ApiError(500, {}, 'abc'))).toContain('abc');
    expect(errorText(new ApiError(409, { message: 'Корзина пуста' }, 'abc'))).toBe('Корзина пуста');
  });

  it('отправляет вход как form-urlencoded', async () => {
    queue.push(() => json(200, { token: 't', headerName: 'X-CSRF-TOKEN' }));
    queue.push(() => new Response(null, { status: 204 }));
    await api('/api/auth/login', { method: 'POST', form: { email: 'a@b.test', password: 'p@ss word' } });
    expect(headerOf(calls[1]!, 'Content-Type')).toContain('application/x-www-form-urlencoded');
    expect(calls[1]!.init.body).toBe('email=a%40b.test&password=p%40ss+word');
  });
});
