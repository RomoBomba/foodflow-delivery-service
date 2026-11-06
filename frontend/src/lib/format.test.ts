import { describe, expect, it } from 'vitest';
import { formatMoney, formatRating, plural, shortOrderNumber } from './format';

describe('format', () => {
  it('склоняет существительные', () => {
    expect(plural(1, ['отзыв', 'отзыва', 'отзывов'])).toBe('отзыв');
    expect(plural(3, ['отзыв', 'отзыва', 'отзывов'])).toBe('отзыва');
    expect(plural(5, ['отзыв', 'отзыва', 'отзывов'])).toBe('отзывов');
    expect(plural(11, ['отзыв', 'отзыва', 'отзывов'])).toBe('отзывов');
    expect(plural(21, ['отзыв', 'отзыва', 'отзывов'])).toBe('отзыв');
    expect(plural(112, ['отзыв', 'отзыва', 'отзывов'])).toBe('отзывов');
  });

  it('выводит среднюю оценку с одним знаком (FR-20: 5, 4, 3 → 4,0)', () => {
    expect(formatRating(4)).toBe('4,0');
    expect(formatRating(4.666)).toBe('4,7');
  });

  it('форматирует рубли без лишних копеек', () => {
    const plain = (value: number) => formatMoney(value).replace(/\s/g, ' ');
    expect(plain(320)).toBe('320 ₽');
    expect(plain(250.5)).toBe('250,5 ₽');
    expect(plain(1170)).toBe('1 170 ₽');
  });

  it('сокращает UUID заказа', () => {
    expect(shortOrderNumber('7f3c9a12-4e6b-4d1a-9c55-2b8e0f61a3d4')).toBe('…61a3d4');
  });
});
