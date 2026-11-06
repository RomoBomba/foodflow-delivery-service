import { useCallback, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { errorText } from '../api/client';
import type { CatalogDish } from '../api/types';
import { useAuth } from '../state/auth';
import { useCart } from '../state/cart';
import { useDictionaries } from '../state/dictionaries';
import { countLabel } from '../lib/format';
import { useToast } from './toast';
import { Modal } from './ui';

interface Pending {
  dish: CatalogDish;
  quantity: number;
}

/**
 * Добавление в корзину. Корзина хранит блюда одной кухни: при 409
 * CART_KITCHEN_MISMATCH спрашиваем согласие и только потом повторяем
 * запрос с confirmReplaceKitchen=true (FR-11).
 */
export function useAddToCart(): { add: (dish: CatalogDish, quantity?: number) => Promise<boolean>; busyId: string | null; dialog: ReactNode } {
  const cart = useCart();
  const toast = useToast();
  const { kitchenName } = useDictionaries();
  const [pending, setPending] = useState<Pending | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const run = useCallback(
    async (dish: CatalogDish, quantity: number, confirm: boolean) => {
      setBusyId(dish.id);
      try {
        const result = await cart.add(dish.id, quantity, confirm);
        if (result.status === 'kitchen-mismatch') {
          setPending({ dish, quantity });
          return false;
        }
        toast(`«${dish.name}» в корзине`, 'ok');
        return true;
      } catch (error) {
        toast(errorText(error), 'danger');
        return false;
      } finally {
        setBusyId(null);
      }
    },
    [cart, toast],
  );

  const add = useCallback((dish: CatalogDish, quantity = 1) => run(dish, quantity, false), [run]);

  const currentKitchen = cart.cart.kitchenId ? kitchenName(cart.cart.kitchenId) : undefined;
  const dialog = pending && (
    <Modal
      title="Заменить корзину?"
      onClose={() => setPending(null)}
      actions={
        <>
          <button type="button" className="btn" onClick={() => setPending(null)}>Оставить как есть</button>
          <button
            type="button"
            className="btn danger"
            onClick={async () => {
              const { dish, quantity } = pending;
              setPending(null);
              await run(dish, quantity, true);
            }}
          >
            Очистить и добавить
          </button>
        </>
      }
    >
      <p className="small">
        В корзине блюда {currentKitchen ? <>кухни «{currentKitchen}»</> : 'другой кухни'}. Заказ может содержать блюда только одной кухни.
      </p>
      <div className="alert info mt-12">
        <span className="ic" aria-hidden="true">i</span>
        <div>
          Чтобы добавить «{pending.dish.name}» от «{pending.dish.kitchenName}», текущая корзина
          ({countLabel(cart.portions, ['порция', 'порции', 'порций'])}) будет очищена.
        </div>
      </div>
    </Modal>
  );

  return { add, busyId, dialog };
}

/** Кнопка покупки с учётом роли и доступности блюда. */
export function BuyButton({
  dish,
  busy,
  onAdd,
  size = 'sm',
  label = 'В корзину',
  blockedReason,
}: {
  dish: CatalogDish;
  busy: boolean;
  onAdd: () => void;
  size?: 'sm' | 'lg';
  label?: string;
  blockedReason?: string | null;
}) {
  const { user } = useAuth();
  const location = useLocation();
  const cls = `btn ${size}${size === 'lg' ? ' block' : ''}`;
  if (user && user.role !== 'CLIENT') return null;
  if (!dish.available) {
    return (
      <button type="button" className={cls} disabled>
        {dish.availablePortions === 0 ? 'Закончилось' : 'Недоступно'}
      </button>
    );
  }
  if (!user) {
    return (
      <Link className={`${cls} primary`} to="/login" state={{ from: location.pathname + location.search }}>
        Войти и заказать
      </Link>
    );
  }
  if (blockedReason) {
    return (
      <button type="button" className={cls} disabled title={blockedReason}>
        Нельзя заказать
      </button>
    );
  }
  return (
    <button type="button" className={`${cls} primary`} disabled={busy} onClick={onAdd} aria-label={`Добавить «${dish.name}» в корзину`}>
      {busy ? 'Добавляем…' : label}
    </button>
  );
}
