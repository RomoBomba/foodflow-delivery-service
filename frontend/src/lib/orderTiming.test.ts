import { describe, expect, it } from 'vitest';
import type { Order, OrderHistoryEntry } from '../api/types';
import { currentStageStartedAt, formatElapsed, transitionDurations } from './orderTiming';

const history: OrderHistoryEntry[] = [
  { id: 'created', fromStatus: null, toStatus: 'CREATED', actorId: 'client', reason: null, createdAt: '2026-11-06T10:00:00Z' },
  { id: 'preparing', fromStatus: 'CREATED', toStatus: 'PREPARING', actorId: 'kitchen', reason: null, createdAt: '2026-11-06T10:02:00Z' },
  { id: 'delivery', fromStatus: 'PREPARING', toStatus: 'DELIVERING', actorId: 'kitchen', reason: null, createdAt: '2026-11-06T10:12:30Z' },
];
const order = (status: Order['status']) => ({ status, history, createdAt: history[0]!.createdAt }) as Order;

describe('таймер этапов заказа', () => {
  it('сбрасывает отсчёт при переходе и восстанавливает его из серверной истории', () => {
    expect(currentStageStartedAt(order('CREATED'))).toBe(Date.parse('2026-11-06T10:00:00Z'));
    expect(currentStageStartedAt(order('PREPARING'))).toBe(Date.parse('2026-11-06T10:02:00Z'));
    expect(currentStageStartedAt(order('DELIVERING'))).toBe(Date.parse('2026-11-06T10:12:30Z'));
  });
  it.each(['COMPLETED', 'CANCELLED'] as const)('не запускает таймер для %s', (status) => {
    expect(currentStageStartedAt(order(status))).toBeNull();
  });
  it('фиксирует длительность предыдущих этапов независимо от порядка истории', () => {
    const durations = transitionDurations([...history].reverse());
    expect(durations.get('preparing')).toBe(120);
    expect(durations.get('delivery')).toBe(630);
    expect(durations.has('created')).toBe(false);
  });
  it('показывает часы без сброса минут и не показывает отрицательное время', () => {
    expect(formatElapsed(3661)).toBe('1:01:01');
    expect(formatElapsed(630)).toBe('10:30');
    expect(formatElapsed(-1)).toBe('00:00');
  });
});
