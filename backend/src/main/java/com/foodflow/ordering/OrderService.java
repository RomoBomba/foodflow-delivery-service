package com.foodflow.ordering;

import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.CatalogQueryService;
import com.foodflow.catalog.CatalogStockService;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenStatus;
import com.foodflow.common.ApiException;
import com.foodflow.identity.IdentityQueryService;
import com.foodflow.recommendation.PreferenceQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final ReservationService reservations;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogQueryService catalog;
    private final CatalogStockService stock;
    private final PreferenceQueryService preferenceQuery;
    private final IdentityQueryService identityQueryService;

    public OrderService(
            ReservationService reservations,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository historyRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            CatalogQueryService catalog,
            CatalogStockService stock,
            PreferenceQueryService preferenceQuery,
            IdentityQueryService identityQueryService
    ) {
        this.reservations = reservations;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.catalog = catalog;
        this.stock = stock;
        this.preferenceQuery = preferenceQuery;
        this.identityQueryService = identityQueryService;
    }

    @Transactional
    public OrderCreationResult create(String email, String idempotencyKey, OrderCreateRequest request) {
        UUID customerId = identityQueryService.getUserId(email);
        validateIdempotencyKey(idempotencyKey);
        validatePhone(request.phone());
        validateAddress(request.deliveryAddress());
        String fingerprint = fingerprint(request);

        // Serialize this customer's checkout before checking the key, including a refilled cart.
        Cart cart = cartRepository.findByUserIdForUpdate(customerId).orElse(null);
        CustomerOrder existing = orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).orElse(null);
        if (existing != null) return new OrderCreationResult(idempotentResult(existing, fingerprint, request), true);
        if (cart == null) throw new ApiException(HttpStatus.CONFLICT, "CART_EMPTY", "Корзина пуста");
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_EMPTY", "Корзина пуста");
        }
        if (cart.getKitchenId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_CART", "У корзины не определена кухня");
        }
        ensureRequestMatchesCart(request.items(), cartItems);

        // Serialize checkouts for the same kitchen. Dish locks below are always acquired by UUID order.
        Kitchen kitchen = stock.lockKitchen(cart.getKitchenId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        if (kitchen.getStatus() != KitchenStatus.APPROVED || !kitchen.isAcceptingOrders()) {
            throw new ApiException(HttpStatus.CONFLICT, "KITCHEN_CLOSED", "Кухня не принимает новые заказы");
        }

        var excluded = preferenceQuery.excludedIngredients(customerId);
        List<Dish> lockedDishes = new ArrayList<>();
        Map<UUID, CartItem> cartItemByDish = cartItems.stream()
                .collect(Collectors.toMap(CartItem::getDishId, item -> item));
        for (CartItem cartItem : cartItems) {
            Dish dish = stock.lockDish(cartItem.getDishId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "DISH_UNAVAILABLE", "Одно из блюд больше недоступно"));
            stock.ensurePurchasable(dish, kitchen, cartItem.getQuantity(), excluded);
            lockedDishes.add(dish);
        }

        List<PriceChangeItemResponse> priceChanges = new ArrayList<>();
        BigDecimal currentTotal = BigDecimal.ZERO;
        for (Dish dish : lockedDishes) {
            CartItem item = cartItemByDish.get(dish.getId());
            BigDecimal lineTotal = dish.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            currentTotal = currentTotal.add(lineTotal);
            if (item.getPriceAtAdd().compareTo(dish.getPrice()) != 0) {
                priceChanges.add(new PriceChangeItemResponse(
                        dish.getId(), dish.getName(), item.getQuantity(), item.getPriceAtAdd(),
                        dish.getPrice(), lineTotal
                ));
            }
        }
        String currentPriceFingerprint = priceFingerprint(lockedDishes, cartItemByDish);
        if (!priceChanges.isEmpty()) {
            boolean confirmedExactPrices = request.acceptCurrentPrices()
                    && currentPriceFingerprint.equals(request.confirmedPriceFingerprint());
            if (!confirmedExactPrices) {
                throw new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED",
                        "Цена одного или нескольких блюд изменилась. Проверьте расчёт и подтвердите именно эту версию цен.",
                        Map.of("updatedTotal", currentTotal,
                                "updatedPriceFingerprint", currentPriceFingerprint,
                                "items", priceChanges));
            }
        }

        CustomerOrder order = orderRepository.saveAndFlush(new CustomerOrder(
                customerId, kitchen.getId(), idempotencyKey, fingerprint,
                request.deliveryAddress().strip(), normalizePhone(request.phone()),
                request.comment() == null ? null : request.comment().strip(), currentTotal
        ));
        for (Dish dish : lockedDishes) {
            CartItem item = cartItemByDish.get(dish.getId());
            stock.reserve(dish.getId(), item.getQuantity());
            OrderItem savedItem = orderItemRepository.save(new OrderItem(
                    order.getId(), dish.getId(), dish.getName(), dish.getPrice(), item.getQuantity(),
                    dish.getWeightGrams(), dish.getKcalPerPortion()
            ));
            reservations.create(savedItem);
        }
        historyRepository.save(new OrderStatusHistory(order.getId(), null, OrderStatus.CREATED, customerId, null));
        cartItemRepository.deleteByCartId(cart.getId());
        cart.setKitchenId(null);
        cart.touch();
        return new OrderCreationResult(toResponse(order), false);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String email) {
        UUID userId = identityQueryService.getUserId(email);
        return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(String email, UUID orderId) {
        UUID userId = identityQueryService.getUserId(email);
        CustomerOrder order = orderRepository.findByIdAndCustomerId(orderId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден"));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getKitchenOrders(String email) {
        UUID ownerId = identityQueryService.getUserId(email);
        Kitchen kitchen = catalog.findOwnedKitchen(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        return orderRepository.findAllByKitchenIdOrderByCreatedAtDesc(kitchen.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse cancelByCustomer(String email, UUID orderId, long expectedVersion) {
        UUID customerId = identityQueryService.getUserId(email);
        CustomerOrder order = orderRepository.findByIdAndCustomerForUpdate(orderId, customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден"));
        ensureVersion(order, expectedVersion);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "Отменить можно только заказ в статусе CREATED");
        }
        OrderStatus oldStatus = order.getStatus();
        order.changeStatus(OrderStatus.CANCELLED, "Отменено клиентом");
        orderRepository.flush(); // @Version guards the update before releasing the reservation.
        reservations.release(order.getId());
        historyRepository.save(new OrderStatusHistory(order.getId(), oldStatus, OrderStatus.CANCELLED, customerId, "Отменено клиентом"));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse changeStatusByKitchen(String email, UUID orderId, OrderStatusRequest request) {
        UUID ownerId = identityQueryService.getUserId(email);
        Kitchen kitchen = catalog.findOwnedKitchen(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        CustomerOrder order = orderRepository.findByIdAndKitchenForUpdate(orderId, kitchen.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден"));

        ensureVersion(order, request.expectedVersion());
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();
        String reason = to == OrderStatus.CANCELLED && request.reason() != null
                ? request.reason().strip() : null;
        if (!OrderTransitions.isAllowed(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_TRANSITION", "Недопустимый переход статуса заказа");
        }
        if (to == OrderStatus.CANCELLED) {
            if (reason == null || reason.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CANCELLATION_REASON_REQUIRED", "Укажите причину отклонения заказа");
            }
        }
        order.changeStatus(to, reason);
        orderRepository.flush();
        if (to == OrderStatus.CANCELLED) reservations.release(order.getId());
        if (to == OrderStatus.COMPLETED) reservations.consume(order.getId());
        historyRepository.save(new OrderStatusHistory(order.getId(), from, to, ownerId, reason));
        return toResponse(order);
    }

    private void ensureVersion(CustomerOrder order, long expectedVersion) {
        if (order.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT",
                    "Заказ изменился. Обновите карточку заказа.", Map.of("currentVersion", order.getVersion()));
        }
    }

    private void ensureRequestMatchesCart(List<OrderLineRequest> requested, List<CartItem> cartItems) {
        if (requested == null || requested.isEmpty() || requested.size() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ITEMS", "Заказ должен содержать от 1 до 30 позиций");
        }
        Map<UUID, Integer> requestMap = new LinkedHashMap<>();
        for (OrderLineRequest line : requested) {
            if (requestMap.putIfAbsent(line.dishId(), line.quantity()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_ORDER_ITEM", "Одно блюдо указано несколько раз");
            }
        }
        Map<UUID, Integer> cartMap = cartItems.stream().collect(Collectors.toMap(CartItem::getDishId, CartItem::getQuantity));
        if (!cartMap.equals(requestMap)) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_CHANGED", "Содержимое корзины изменилось. Обновите корзину перед оформлением.");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key должен содержать 8–128 символов A-Z, a-z, цифр, '.', '_', ':' или '-'");
        }
    }

    private void validatePhone(String phone) {
        long digits = phone == null ? 0 : phone.chars().filter(c -> c >= '0' && c <= '9').count();
        if (phone == null || digits < 10 || digits > 15 || !phone.matches("[0-9+() -]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Телефон должен содержать от 10 до 15 цифр");
        }
        if (normalizePhone(phone).chars().distinct().count() == 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Номер не может состоять из одной повторяющейся цифры");
        }
    }

    private void validateAddress(String address) {
        String normalized = address == null ? "" : address.strip();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ADDRESS", "Адрес доставки должен содержать от 10 до 500 символов");
        }
    }

    private String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    private String fingerprint(OrderCreateRequest request) {
        String lines = request.items().stream()
                .sorted(Comparator.comparing(line -> line.dishId().toString()))
                .map(line -> line.dishId() + ":" + line.quantity())
                .collect(Collectors.joining("|"));
        String raw = encodeField(request.deliveryAddress().strip())
                + encodeField(normalizePhone(request.phone()))
                + encodeField(request.comment() == null ? "" : request.comment().strip())
                + encodeField(Boolean.toString(request.acceptCurrentPrices()))
                + encodeField(request.confirmedPriceFingerprint() == null ? "" : request.confirmedPriceFingerprint())
                + encodeField(lines);
        return sha256(raw);
    }

    private String encodeField(String value) {
        return value.length() + ":" + value;
    }

    private String priceFingerprint(List<Dish> dishes, Map<UUID, CartItem> cartItems) {
        String raw = dishes.stream()
                .sorted(Comparator.comparing(dish -> dish.getId().toString()))
                .map(dish -> dish.getId() + ":" + dish.getPrice().toPlainString() + ":"
                        + cartItems.get(dish.getId()).getQuantity())
                .collect(Collectors.joining("\n"));
        return sha256(raw);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private OrderResponse idempotentResult(CustomerOrder existing, String fingerprint, OrderCreateRequest request) {
        if (!existing.getRequestFingerprint().equals(fingerprint)
                && !matchesLegacyRequest(existing, request)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Этот ключ уже использован с другим содержимым заказа");
        }
        return toResponse(existing);
    }

    private boolean matchesLegacyRequest(CustomerOrder order, OrderCreateRequest request) {
        // Preserve retries for orders created before length-prefixed fingerprints.
        // The old hash alone is ambiguous; compare persisted details and line snapshots too.
        String comment = request.comment() == null ? "" : request.comment().strip();
        if (!order.getDeliveryAddress().equals(request.deliveryAddress().strip())
                || !order.getPhone().equals(normalizePhone(request.phone()))
                || !(order.getComment() == null ? "" : order.getComment()).equals(comment)) {
            return false;
        }
        String lines = request.items().stream()
                .sorted(Comparator.comparing(line -> line.dishId().toString()))
                .map(line -> line.dishId() + ":" + line.quantity())
                .collect(Collectors.joining("|"));
        String savedLines = orderItemRepository.findAllByOrderIdOrderByDishIdAsc(order.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getDishId().toString()))
                .map(item -> item.getDishId() + ":" + item.getQuantity())
                .collect(Collectors.joining("|"));
        if (!lines.equals(savedLines)) return false;
        String legacy = request.deliveryAddress().strip() + "\n" + normalizePhone(request.phone()) + "\n"
                + comment + "\n" + request.acceptCurrentPrices() + "\n"
                + (request.confirmedPriceFingerprint() == null ? "" : request.confirmedPriceFingerprint())
                + "\n" + lines;
        return order.getRequestFingerprint().equals(sha256(legacy));
    }

    private OrderResponse toResponse(CustomerOrder order) {
        List<OrderItemResponse> items = orderItemRepository.findAllByOrderIdOrderByDishIdAsc(order.getId())
                .stream().map(OrderItemResponse::from).toList();
        List<OrderHistoryResponse> history = historyRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId())
                .stream().map(OrderHistoryResponse::from).toList();
        return new OrderResponse(
                order.getId(), order.getId().toString(), order.getCustomerId(), order.getKitchenId(),
                order.getDeliveryAddress(), order.getPhone(), order.getPaymentMethod(), order.getComment(), order.getStatus(), order.getVersion(),
                order.getTotalPrice(), order.getCancellationReason(), order.getCreatedAt(), order.getUpdatedAt(),
                items, history
        );
    }
}
