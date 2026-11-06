import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { isApiError } from '../api/client';
import { cartApi } from '../api/endpoints';
import type { Cart, UUID } from '../api/types';
import { useAuth } from './auth';

export const EMPTY_CART: Cart = {
  id: null,
  kitchenId: null,
  items: [],
  totalPrice: 0,
  hasPriceChanges: false,
  updatedAt: null,
};

export type AddResult = { status: 'added'; cart: Cart } | { status: 'kitchen-mismatch' };

interface CartContextValue {
  cart: Cart;
  /** Корзина доступна только роли CLIENT. */
  enabled: boolean;
  portions: number;
  refresh: () => Promise<Cart>;
  /** POST прибавляет количество; при 409 CART_KITCHEN_MISMATCH нужен явный повтор с confirm. */
  add: (dishId: UUID, quantity: number, confirmReplaceKitchen?: boolean) => Promise<AddResult>;
  setQuantity: (dishId: UUID, quantity: number) => Promise<Cart>;
  remove: (dishId: UUID) => Promise<Cart>;
  clear: () => Promise<void>;
  replace: (cart: Cart) => void;
}

const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const enabled = user?.role === 'CLIENT';
  const [cart, setCart] = useState<Cart>(EMPTY_CART);

  const refresh = useCallback(async () => {
    const next = await cartApi.get();
    setCart(next);
    return next;
  }, []);

  useEffect(() => {
    if (!enabled) {
      setCart(EMPTY_CART);
      return;
    }
    refresh().catch(() => undefined);
  }, [enabled, user?.id, refresh]);

  const add = useCallback(
    async (dishId: UUID, quantity: number, confirmReplaceKitchen = false): Promise<AddResult> => {
      try {
        const next = await cartApi.add(dishId, quantity, confirmReplaceKitchen);
        setCart(next);
        return { status: 'added', cart: next };
      } catch (error) {
        if (isApiError(error, 'CART_KITCHEN_MISMATCH')) return { status: 'kitchen-mismatch' };
        // POST корзины не идемпотентен: после ошибки перечитываем корзину,
        // но не повторяем добавление автоматически (docs/API.md).
        refresh().catch(() => undefined);
        throw error;
      }
    },
    [refresh],
  );

  const setQuantity = useCallback(async (dishId: UUID, quantity: number) => {
    const next = await cartApi.setQuantity(dishId, quantity);
    setCart(next);
    return next;
  }, []);

  const remove = useCallback(async (dishId: UUID) => {
    const next = await cartApi.remove(dishId);
    setCart(next);
    return next;
  }, []);

  const clear = useCallback(async () => {
    await cartApi.clear();
    setCart(EMPTY_CART);
  }, []);

  const value = useMemo(
    () => ({
      cart,
      enabled,
      portions: cart.items.reduce((sum, item) => sum + item.quantity, 0),
      refresh,
      add,
      setQuantity,
      remove,
      clear,
      replace: setCart,
    }),
    [cart, enabled, refresh, add, setQuantity, remove, clear],
  );
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart вне CartProvider');
  return context;
}
