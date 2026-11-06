import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { resetCsrf } from './client';
import { kitchenApi, ordersApi, preferencesApi } from './endpoints';

const bodies: unknown[] = [];
beforeEach(() => {
  resetCsrf();
  bodies.length = 0;
  vi.stubGlobal('fetch', async (url: string, init: RequestInit = {}) => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' });
    bodies.push(JSON.parse(init.body as string));
    return Response.json({ id: 'order-1', version: 8 });
  });
});
afterEach(() => vi.unstubAllGlobals());

it('передаёт прочитанную версию при отмене клиентом', async () => {
  await ordersApi.cancel('order-1', 7);
  expect(bodies).toEqual([{ expectedVersion: 7 }]);
});

it('передаёт DELIVERING и версию при смене статуса кухней', async () => {
  await kitchenApi.changeStatus('order-1', 'DELIVERING', 7, null);
  expect(bodies).toEqual([{ status: 'DELIVERING', expectedVersion: 7, reason: null }]);
});

it('запрашивает баллы для текущих блюд, а не только первых 100 рекомендаций', async () => {
  const fetch = vi.fn().mockResolvedValue(Response.json({ content: [] }));
  vi.stubGlobal('fetch', fetch);
  await preferencesApi.recommendations(0, 20, ['dish-101', 'dish-102']);
  const url = new URL(fetch.mock.calls[0]![0] as string, 'http://localhost');
  expect(url.pathname).toBe('/api/recommendations');
  expect(url.searchParams.get('dishIds')).toBe('dish-101,dish-102');
  expect(url.searchParams.get('size')).toBe('20');
});
