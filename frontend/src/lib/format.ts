import type { KitchenStatus, OrderStatus } from '../api/types';

const money = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/** Денежные значения считает сервер; здесь только вывод (FR-12). */
export function formatMoney(value: number): string {
  return money.format(value);
}

const dateTime = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit',
});
const timeOnly = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });
const dateOnly = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

export function formatTime(iso: string): string {
  return timeOnly.format(new Date(iso));
}

export function formatDate(iso: string): string {
  return dateOnly.format(new Date(iso));
}

/** Средняя оценка до одного знака: 4 → «4,0» (FR-20). */
export function formatRating(value: number): string {
  return value.toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/** Выбор формы слова: plural(5, ['отзыв', 'отзыва', 'отзывов']). */
export function plural(count: number, forms: [string, string, string]): string {
  const n = Math.abs(count) % 100;
  const n1 = n % 10;
  if (n > 10 && n < 20) return forms[2];
  if (n1 > 1 && n1 < 5) return forms[1];
  if (n1 === 1) return forms[0];
  return forms[2];
}

export function countLabel(count: number, forms: [string, string, string]): string {
  return `${count} ${plural(count, forms)}`;
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  CREATED: 'Создан',
  PREPARING: 'Готовится',
  IN_DELIVERY: 'В доставке',
  COMPLETED: 'Выполнен',
  CANCELLED: 'Отменён',
};

export const ORDER_STATUS_CLASS: Record<OrderStatus, string> = {
  CREATED: 's-created',
  PREPARING: 's-preparing',
  IN_DELIVERY: 's-delivery',
  COMPLETED: 's-completed',
  CANCELLED: 's-cancelled',
};

export const ORDER_FLOW: OrderStatus[] = ['CREATED', 'PREPARING', 'IN_DELIVERY', 'COMPLETED'];

export const KITCHEN_STATUS_LABEL: Record<KitchenStatus, string> = {
  PENDING: 'На проверке',
  APPROVED: 'Одобрена',
  REJECTED: 'Отклонена',
  BLOCKED: 'Заблокирована',
};

export const KITCHEN_STATUS_CLASS: Record<KitchenStatus, string> = {
  PENDING: 's-pending',
  APPROVED: 's-completed',
  REJECTED: 's-cancelled',
  BLOCKED: 's-blocked',
};

/** Короткий номер для интерфейса: orderNumber сейчас совпадает с UUID (docs/API.md). */
export function shortOrderNumber(orderNumber: string): string {
  const compact = orderNumber.replace(/-/g, '');
  return compact.length > 6 ? `…${compact.slice(-6)}` : orderNumber;
}
