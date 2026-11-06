// HTTP-клиент FoodFlow: относительные URL (/api), cookie сессии и CSRF-токен
// в заголовке для всех изменяющих запросов (docs/API.md, «Сессия и CSRF»).

export interface ApiErrorBody {
  code?: string;
  message?: string;
  fields?: Record<string, string>;
  [key: string]: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;
  readonly body: ApiErrorBody;
  readonly requestId: string | null;

  constructor(status: number, body: ApiErrorBody, requestId: string | null) {
    super(body.message ?? defaultMessage(status));
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? `HTTP_${status}`;
    this.fields = body.fields ?? {};
    this.body = body;
    this.requestId = requestId;
  }
}

/** Сеть недоступна или ответ потерян: исход запроса неизвестен. */
export class NetworkError extends Error {
  constructor() {
    super('Нет связи с сервером. Проверьте подключение и повторите попытку.');
    this.name = 'NetworkError';
  }
}

export function defaultMessage(status: number): string {
  if (status === 401) return 'Нужно войти в аккаунт.';
  if (status === 403) return 'Недостаточно прав для этого действия.';
  if (status === 404) return 'Не найдено.';
  if (status === 409) return 'Действие конфликтует с текущим состоянием. Обновите данные.';
  if (status >= 500) return 'Сервер временно не смог обработать запрос. Повторите попытку.';
  return 'Запрос отклонён. Проверьте данные.';
}

type Listener = () => void;
const unauthorizedListeners = new Set<Listener>();

/** Сессия истекла или пользователь вышел в другой вкладке. */
export function onUnauthorized(listener: Listener): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

interface CsrfToken {
  token: string;
  headerName: string;
}

let csrf: CsrfToken | null = null;
let csrfPending: Promise<CsrfToken> | null = null;

async function loadCsrf(): Promise<CsrfToken> {
  let response: Response;
  try {
    response = await fetch('/api/auth/csrf', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
  } catch {
    throw new NetworkError();
  }
  if (!response.ok) throw new ApiError(response.status, {}, response.headers.get('X-Request-ID'));
  const body = (await response.json()) as CsrfToken;
  csrf = body;
  return body;
}

/** CSRF-токен привязан к сессии; после входа и выхода его нужно получить заново. */
export function getCsrf(force = false): Promise<CsrfToken> {
  if (csrf && !force) return Promise.resolve(csrf);
  if (!csrfPending) {
    csrfPending = loadCsrf().finally(() => {
      csrfPending = null;
    });
  }
  return csrfPending;
}

export function resetCsrf(): void {
  csrf = null;
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Query;
  json?: unknown;
  form?: Record<string, string>;
  headers?: Record<string, string>;
  /** Не сообщать глобально о 401 (например, при проверке текущей сессии). */
  silent401?: boolean;
  signal?: AbortSignal;
}

export function buildUrl(path: string, query?: Query): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

async function send(path: string, options: RequestOptions, csrfToken: CsrfToken | null): Promise<Response> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  let body: BodyInit | undefined;
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  } else if (options.form) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
    body = new URLSearchParams(options.form).toString();
  }
  if (csrfToken) headers[csrfToken.headerName] = csrfToken.token;
  try {
    return await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body,
      credentials: 'same-origin',
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new NetworkError();
  }
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const mutating = method !== 'GET';
  let response = await send(path, options, mutating ? await getCsrf() : null);

  // 403 без тела после смены сессии чаще всего означает устаревший CSRF-токен:
  // получаем новый и повторяем запрос один раз. Повтор безопасен — сервер отклонил
  // исходный запрос до выполнения бизнес-логики.
  if (mutating && response.status === 403) {
    const probe = await response.clone().text();
    if (!probe) {
      response = await send(path, options, await getCsrf(true));
    }
  }

  const body = await readBody(response);
  if (!response.ok) {
    if (response.status === 401 && !options.silent401) {
      resetCsrf();
      unauthorizedListeners.forEach((listener) => listener());
    }
    const errorBody: ApiErrorBody = body && typeof body === 'object' ? (body as ApiErrorBody) : {};
    throw new ApiError(response.status, errorBody, response.headers.get('X-Request-ID'));
  }
  return body as T;
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

/** Текст ошибки для показа пользователю; для 5xx добавляет идентификатор запроса (NFR-09). */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status >= 500 && error.requestId) {
      return `${error.message} Код запроса: ${error.requestId}.`;
    }
    return error.message;
  }
  if (error instanceof NetworkError) return error.message;
  return 'Что-то пошло не так. Обновите страницу.';
}
