// Клиентские проверки повторяют серверные (FR-01, FR-04, FR-06, FR-12, FR-19);
// сервер всё равно проверяет всё заново (NFR-04).

export type FieldErrors = Record<string, string>;

export function hasErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(value: string): string | null {
  const email = value.trim();
  if (!email) return 'Введите email';
  if (email.length > 254 || !EMAIL.test(email)) return 'Введите корректный email';
  return null;
}

export function validatePassword(value: string): string | null {
  if (value.length < 8) return 'Минимум 8 символов';
  if (value.length > 128) return 'Не больше 128 символов';
  return null;
}

export function validateAddress(value: string): string | null {
  const length = value.trim().length;
  if (length < 10) return 'Адрес должен содержать не меньше 10 символов';
  if (length > 500) return 'Адрес должен содержать не больше 500 символов';
  return null;
}

/** Телефон: 10–15 цифр, допустимы `+() -`, общая длина ≤ 25 (docs/API.md). */
export function validatePhone(value: string): string | null {
  const phone = value.trim();
  if (!phone) return 'Введите телефон';
  if (phone.length > 25) return 'Не длиннее 25 символов';
  if (!/^[0-9+() -]+$/.test(phone)) return 'Допустимы цифры, пробел и символы + ( ) -';
  const digits = phone.replace(/\D/g, '').length;
  if (digits < 10 || digits > 15) return 'Введите от 10 до 15 цифр';
  if (/^(\d)\1+$/.test(phone.replace(/\D/g, ''))) return 'Номер не может состоять из одной повторяющейся цифры';
  return null;
}

export function validateRequired(value: string, max: number, label = 'Поле'): string | null {
  const length = value.trim().length;
  if (length === 0) return `${label}: заполните поле`;
  if (length > max) return `${label}: не больше ${max} символов`;
  return null;
}

export function validateOptional(value: string, max: number): string | null {
  return value.trim().length > max ? `Не больше ${max} символов` : null;
}

/** Целое число из поля ввода или null, если поле пустое/некорректное. */
export function parseInteger(value: string): number | null {
  const text = value.trim();
  if (!/^\d+$/.test(text)) return null;
  const number = Number(text);
  return Number.isSafeInteger(number) ? number : null;
}

/** Цена: положительная, до 10 цифр перед точкой и 2 после (FR-06). */
export function parsePrice(value: string): number | null {
  const text = value.trim().replace(',', '.');
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(text)) return null;
  const number = Number(text);
  return number > 0 ? number : null;
}

export function isHttpUrl(value: string): boolean {
  return /^https?:\/\/\S+$/.test(value.trim()) && value.trim().length <= 2048;
}

/** Ключ попытки оформления: 8–128 символов [A-Za-z0-9._:-]; UUID подходит. */
export function newIdempotencyKey(): string {
  const c: Crypto = globalThis.crypto;
  // randomUUID доступен только в защищённом контексте (HTTPS или localhost).
  if (typeof c.randomUUID === 'function') return c.randomUUID();
  const bytes = new Uint8Array(16);
  c.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function isValidIdempotencyKey(key: string): boolean {
  return /^[A-Za-z0-9._:-]{8,128}$/.test(key);
}

/** Один или несколько телефонов/email; свободный текст не считается контактом. */
export function validateContacts(value: string): string | null {
  if (!value.trim() || value.length > 500) return 'Укажите телефон или email, не больше 500 символов';
  for (const part of value.split(/[;,\n]/)) {
    const contact = part.trim();
    const error = contact.includes('@') ? validateEmail(contact) : validatePhone(contact);
    if (error) return 'Укажите корректный телефон или email; несколько контактов разделяйте точкой с запятой';
  }
  return null;
}
