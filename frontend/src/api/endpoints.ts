import { api } from './client';
import type {
  Cart,
  CatalogDish,
  CatalogQuery,
  Compatibility,
  DishRequest,
  Kitchen,
  KitchenApplication,
  KitchenDish,
  KitchenStatus,
  NamedRef,
  Order,
  OrderCreateRequest,
  OrderStatus,
  Page,
  Preferences,
  PreferencesRequest,
  RecommendationPage,
  Review,
  ReviewItemRequest,
  User,
  UUID,
} from './types';

const id = (value: string) => encodeURIComponent(value);

export const authApi = {
  me: () => api<User>('/api/auth/me', { silent401: true }),
  register: (email: string, password: string) =>
    api<User>('/api/auth/register', { method: 'POST', json: { email, password } }),
  // Вход принимает form-urlencoded (Spring Security formLogin).
  login: (email: string, password: string) =>
    api<void>('/api/auth/login', { method: 'POST', form: { email, password }, silent401: true }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST', silent401: true }),
};

export const dictionaryApi = {
  categories: () => api<NamedRef[]>('/api/categories'),
  ingredients: () => api<NamedRef[]>('/api/ingredients'),
};

export const catalogApi = {
  dishes: (query: CatalogQuery) => api<Page<CatalogDish>>('/api/dishes', { query: { ...query } }),
  dish: (dishId: UUID) => api<CatalogDish>(`/api/dishes/${id(dishId)}`),
  reviews: (dishId: UUID) => api<Review[]>(`/api/dishes/${id(dishId)}/reviews`),
};

export const preferencesApi = {
  get: () => api<Preferences>('/api/preferences'),
  save: (request: PreferencesRequest) => api<Preferences>('/api/preferences', { method: 'PUT', json: request }),
  recommendations: (page = 0, size = 100) =>
    api<RecommendationPage>('/api/recommendations', { query: { page, size } }),
  compatibility: (dishId: UUID) =>
    api<Compatibility>(`/api/recommendations/dishes/${id(dishId)}/compatibility`),
};

export const cartApi = {
  get: () => api<Cart>('/api/cart'),
  add: (dishId: UUID, quantity: number, confirmReplaceKitchen = false) =>
    api<Cart>('/api/cart/items', { method: 'POST', json: { dishId, quantity, confirmReplaceKitchen } }),
  setQuantity: (dishId: UUID, quantity: number) =>
    api<Cart>(`/api/cart/items/${id(dishId)}`, { method: 'PATCH', json: { quantity } }),
  remove: (dishId: UUID) => api<Cart>(`/api/cart/items/${id(dishId)}`, { method: 'DELETE' }),
  clear: () => api<void>('/api/cart', { method: 'DELETE' }),
};

export const ordersApi = {
  create: (idempotencyKey: string, request: OrderCreateRequest) =>
    api<Order>('/api/orders', { method: 'POST', json: request, headers: { 'Idempotency-Key': idempotencyKey } }),
  list: () => api<Order[]>('/api/orders'),
  get: (orderId: UUID) => api<Order>(`/api/orders/${id(orderId)}`),
  cancel: (orderId: UUID) => api<Order>(`/api/orders/${id(orderId)}/cancel`, { method: 'POST' }),
  review: (orderId: UUID, items: ReviewItemRequest[]) =>
    api<Review[]>(`/api/orders/${id(orderId)}/reviews`, { method: 'POST', json: { items } }),
};

export const kitchenApi = {
  apply: (application: KitchenApplication) => api<Kitchen>('/api/kitchens', { method: 'POST', json: application }),
  mine: () => api<Kitchen>('/api/kitchens/me'),
  setAccepting: (acceptingOrders: boolean) =>
    api<Kitchen>('/api/kitchens/me/availability', { method: 'PATCH', json: { acceptingOrders } }),
  menu: () => api<KitchenDish[]>('/api/kitchens/me/dishes'),
  createDish: (request: DishRequest) => api<KitchenDish>('/api/kitchens/me/dishes', { method: 'POST', json: request }),
  updateDish: (dishId: UUID, request: DishRequest) =>
    api<KitchenDish>(`/api/kitchens/me/dishes/${id(dishId)}`, { method: 'PUT', json: request }),
  archiveDish: (dishId: UUID) => api<void>(`/api/kitchens/me/dishes/${id(dishId)}`, { method: 'DELETE' }),
  orders: () => api<Order[]>('/api/kitchens/me/orders'),
  changeStatus: (orderId: UUID, status: OrderStatus, reason: string | null) =>
    api<Order>(`/api/kitchens/me/orders/${id(orderId)}/status`, { method: 'PATCH', json: { status, reason } }),
  reviews: () => api<Review[]>('/api/kitchens/me/reviews'),
};

export const adminApi = {
  kitchens: (status: KitchenStatus) => api<Kitchen[]>('/api/admin/kitchens', { query: { status } }),
  approve: (kitchenId: UUID) => api<Kitchen>(`/api/admin/kitchens/${id(kitchenId)}/approve`, { method: 'POST' }),
  reject: (kitchenId: UUID, reason: string) =>
    api<Kitchen>(`/api/admin/kitchens/${id(kitchenId)}/reject`, { method: 'POST', json: { reason } }),
  block: (kitchenId: UUID, reason: string) =>
    api<Kitchen>(`/api/admin/kitchens/${id(kitchenId)}/block`, { method: 'POST', json: { reason } }),
  reviews: (visible?: boolean) => api<Review[]>('/api/admin/reviews', { query: { visible } }),
  hideReview: (reviewId: UUID, reason: string) =>
    api<Review>(`/api/admin/reviews/${id(reviewId)}/hide`, { method: 'POST', json: { reason } }),
};
