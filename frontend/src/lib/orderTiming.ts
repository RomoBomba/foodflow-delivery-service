import type { Order, OrderHistoryEntry } from '../api/types';

export function currentStageStartedAt(order: Order): number | null {
  if (order.status === 'COMPLETED' || order.status === 'CANCELLED') return null;
  const times = order.history.filter((entry) => entry.toStatus === order.status)
    .map((entry) => Date.parse(entry.createdAt)).filter(Number.isFinite);
  if (times.length) return Math.max(...times);
  const created = Date.parse(order.createdAt);
  return order.status === 'CREATED' && Number.isFinite(created) ? created : null;
}

export function formatElapsed(seconds: number): string {
  const value = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(value / 3600);
  const minutes = Math.floor(value % 3600 / 60).toString().padStart(2, '0');
  const rest = (value % 60).toString().padStart(2, '0');
  return hours ? `${hours}:${minutes}:${rest}` : `${minutes}:${rest}`;
}

/** Duration of the stage ended by each transition, from server timestamps. */
export function transitionDurations(history: OrderHistoryEntry[]): Map<string, number> {
  const ordered = [...history].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
  const result = new Map<string, number>();
  for (let index = 1; index < ordered.length; index++) {
    const previous = ordered[index - 1]!;
    const current = ordered[index]!;
    if (current.fromStatus !== previous.toStatus) continue;
    const seconds = (Date.parse(current.createdAt) - Date.parse(previous.createdAt)) / 1000;
    if (Number.isFinite(seconds)) result.set(current.id, Math.max(0, seconds));
  }
  return result;
}
