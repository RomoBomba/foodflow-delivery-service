import type { Order } from '../api/types';
import { useSecondsSince } from '../lib/hooks';
import { currentStageStartedAt, formatElapsed } from '../lib/orderTiming';

export function OrderStageTimer({ order }: { order: Order }) {
  const seconds = useSecondsSince(currentStageStartedAt(order));
  if (seconds === null) return null;
  const label = order.status === 'CREATED' ? 'Ждёт принятия' : order.status === 'PREPARING' ? 'Готовится' : 'В доставке';
  return <span className="stage-timer" title="Время с начала текущего этапа"><span>{label}</span><b className="stage-time">{formatElapsed(seconds)}</b></span>;
}
