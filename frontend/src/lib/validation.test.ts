import { describe, expect, it } from 'vitest';
import {
  isValidIdempotencyKey,
  newIdempotencyKey,
  parseInteger,
  parsePrice,
  validateAddress,
  validateContacts,
  validateEmail,
  validatePassword,
  validatePhone,
} from './validation';

describe('validatePhone (FR-12: 10–15 цифр, символы +() -, длина ≤ 25)', () => {
  it.each(['+7 (999) 123-45-67', '89991234567', '+7-999-123-45-67', '123456789012345', '+7 (999) 123-45-67-------'])('принимает %s', (phone) => {
    expect(validatePhone(phone)).toBeNull();
  });
  it.each([
    ['', 'Введите телефон'],
    ['0000000000', 'Номер не может состоять из одной повторяющейся цифры'],
    ['+7 (777) 777-77-77', 'Номер не может состоять из одной повторяющейся цифры'],
    ['+7 999 12', 'Введите от 10 до 15 цифр'],
    ['1234567890123456', 'Введите от 10 до 15 цифр'],
    ['+7 999 123 45 67 ext', 'Допустимы цифры, пробел и символы + ( ) -'],
    ['+7 (999) 123-45-67--------', 'Не длиннее 25 символов'],
  ])('отклоняет «%s»', (phone, message) => {
    expect(validatePhone(phone)).toBe(message);
  });
});

describe('validateAddress (10–500 символов после обрезки пробелов)', () => {
  it('считает длину без краевых пробелов', () => {
    expect(validateAddress('   123456789   ')).not.toBeNull();
    expect(validateAddress('  1234567890  ')).toBeNull();
  });
  it('ограничивает 500 символами', () => {
    expect(validateAddress('a'.repeat(500))).toBeNull();
    expect(validateAddress('a'.repeat(501))).not.toBeNull();
  });
});

describe('учётные данные (FR-01)', () => {
  it('пароль 8–128 символов', () => {
    expect(validatePassword('1234567')).not.toBeNull();
    expect(validatePassword('12345678')).toBeNull();
    expect(validatePassword('x'.repeat(128))).toBeNull();
    expect(validatePassword('x'.repeat(129))).not.toBeNull();
  });
  it('email обрезается от пробелов', () => {
    expect(validateEmail('  Anna@Demo.test ')).toBeNull();
    expect(validateEmail('anna@')).not.toBeNull();
  });
});

describe('числа меню (FR-06, FR-07)', () => {
  it('цена — положительная, до двух знаков после запятой', () => {
    expect(parsePrice('250.50')).toBe(250.5);
    expect(parsePrice('250,5')).toBe(250.5);
    expect(parsePrice('0')).toBeNull();
    expect(parsePrice('-1')).toBeNull();
    expect(parsePrice('1.999')).toBeNull();
    expect(parsePrice('12345678901')).toBeNull();
  });
  it('остаток — целое неотрицательное, дробные значения отклоняются', () => {
    expect(parseInteger('0')).toBe(0);
    expect(parseInteger('12')).toBe(12);
    expect(parseInteger('1.5')).toBeNull();
    expect(parseInteger('-3')).toBeNull();
    expect(parseInteger('')).toBeNull();
  });
});

describe('Idempotency-Key (FR-14)', () => {
  it('генерирует ключ, который принимает backend', () => {
    const key = newIdempotencyKey();
    expect(isValidIdempotencyKey(key)).toBe(true);
    expect(newIdempotencyKey()).not.toBe(key);
  });
});

describe('контакты кухни', () => {
  it.each(['0окшк', '0000000000', 'wrong@', 'a@b.test; 0000000000'])('отклоняет %s', (value) => {
    expect(validateContacts(value)).not.toBeNull();
  });
  it.each(['+7 (999) 123-45-67', 'kitchen@example.test', '79991234567; kitchen@example.test'])('принимает %s', (value) => {
    expect(validateContacts(value)).toBeNull();
  });
});
