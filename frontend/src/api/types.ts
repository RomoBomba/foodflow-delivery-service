// Типы повторяют DTO backend (пакет com.foodflow). UUID и даты приходят строками,
// BigDecimal — числом в рублях (docs/API.md).

export type UUID = string;
export type Instant = string;

export type UserRole = 'CLIENT' | 'KITCHEN' | 'ADMIN';

export interface User {
  id: UUID;
  email: string;
  role: UserRole;
}

export interface NamedRef {
  id: UUID;
  name: string;
}

export interface CatalogDish {
  id: UUID;
  kitchenId: UUID;
  kitchenName: string;
  name: string;
  description: string;
  category: NamedRef;
  ingredients: NamedRef[];
  price: number;
  weightGrams: number;
  kcalPerPortion: number;
  availablePortions: number;
  available: boolean;
  photoUrl: string | null;
  averageRating: number | null;
  reviewCount: number;
  repeatOrderPercent: number | null;
  repeatOrderResponses: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type CatalogSort = 'priceAsc' | 'priceDesc' | 'relevance';

export interface CatalogQuery {
  kitchenId?: UUID;
  categoryId?: UUID;
  minPrice?: number;
  maxPrice?: number;
  availableOnly?: boolean;
  page?: number;
  size?: number;
  sort?: CatalogSort;
}

export interface RecommendationItem {
  dish: CatalogDish;
  score: number | null;
  explanation: string[];
}

export interface RecommendationPage extends Page<RecommendationItem> {
  scoringEnabled: boolean;
  message: string | null;
}

export interface Compatibility {
  dishId: UUID;
  compatible: boolean;
  reason: string | null;
  excludedIngredientId: UUID | null;
}

export interface Preferences {
  userId: UUID;
  favoriteCategoryIds: UUID[];
  favoriteIngredientIds: UUID[];
  excludedIngredientIds: UUID[];
  kcalMin: number | null;
  kcalMax: number | null;
  updatedAt: Instant | null;
}

export interface PreferencesRequest {
  favoriteCategoryIds: UUID[];
  favoriteIngredientIds: UUID[];
  excludedIngredientIds: UUID[];
  kcalMin: number | null;
  kcalMax: number | null;
}

export interface CartItem {
  dishId: UUID;
  dishName: string;
  quantity: number;
  priceAtAdd: number;
  currentPrice: number;
  lineTotal: number;
  priceChanged: boolean;
  availablePortions: number;
  available: boolean;
}

export interface Cart {
  id: UUID | null;
  kitchenId: UUID | null;
  items: CartItem[];
  totalPrice: number;
  hasPriceChanges: boolean;
  updatedAt: Instant | null;
}

export type OrderStatus = 'CREATED' | 'PREPARING' | 'IN_DELIVERY' | 'COMPLETED' | 'CANCELLED';

export interface OrderItem {
  id: UUID;
  dishId: UUID;
  dishName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  weightGrams: number;
  kcalPerPortion: number;
}

export interface OrderHistoryEntry {
  id: UUID;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  actorId: UUID;
  reason: string | null;
  createdAt: Instant;
}

export interface Order {
  id: UUID;
  orderNumber: string;
  customerId: UUID;
  kitchenId: UUID;
  deliveryAddress: string;
  phone: string;
  paymentMethod: string;
  comment: string | null;
  status: OrderStatus;
  totalPrice: number;
  cancellationReason: string | null;
  createdAt: Instant;
  updatedAt: Instant;
  items: OrderItem[];
  history: OrderHistoryEntry[];
}

export interface OrderCreateRequest {
  deliveryAddress: string;
  phone: string;
  comment: string | null;
  items: { dishId: UUID; quantity: number }[];
  acceptCurrentPrices: boolean;
  confirmedPriceFingerprint: string | null;
}

export interface PriceChangeItem {
  dishId: UUID;
  dishName: string;
  quantity: number;
  previousUnitPrice: number;
  currentUnitPrice: number;
  currentLineTotal: number;
}

export interface PriceChangedDetails {
  updatedTotal: number;
  updatedPriceFingerprint: string;
  items: PriceChangeItem[];
}

export interface Review {
  id: UUID;
  orderId: UUID;
  orderItemId: UUID;
  dishId: UUID;
  rating: number;
  reordered: boolean;
  text: string | null;
  visible: boolean;
  moderationReason: string | null;
  moderatedBy: UUID | null;
  moderatedAt: Instant | null;
  createdAt: Instant;
}

export interface ReviewItemRequest {
  orderItemId: UUID;
  rating: number;
  reordered: boolean;
  text: string | null;
}

export type KitchenStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'BLOCKED';

export interface Kitchen {
  id: UUID;
  name: string;
  description: string;
  contacts: string;
  deliveryArea: string;
  status: KitchenStatus;
  acceptingOrders: boolean;
  createdAt: Instant;
  moderationReason: string | null;
  moderatedAt: Instant | null;
}

export interface KitchenApplication {
  name: string;
  description: string;
  contacts: string;
  deliveryArea: string;
}

export interface KitchenDish {
  id: UUID;
  kitchenId: UUID;
  name: string;
  description: string;
  category: NamedRef;
  ingredients: NamedRef[];
  price: number;
  weightGrams: number;
  kcalPerPortion: number;
  availablePortions: number;
  photoUrl: string | null;
  archived: boolean;
  createdAt: Instant;
}

export interface DishRequest {
  name: string;
  description: string;
  categoryId: UUID;
  ingredientIds: UUID[];
  price: number;
  weightGrams: number;
  kcalPerPortion: number;
  availablePortions: number;
  photoUrl: string | null;
}
