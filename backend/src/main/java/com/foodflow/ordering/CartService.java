package com.foodflow.ordering;

import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.dish.DishRepository;
import com.foodflow.catalog.ingredient.Ingredient;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenRepository;
import com.foodflow.catalog.kitchen.KitchenStatus;
import com.foodflow.common.ApiException;
import com.foodflow.identity.IdentityQueryService;
import com.foodflow.identity.UserRepository;
import com.foodflow.preferences.Preference;
import com.foodflow.preferences.PreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository itemRepository;
    private final DishRepository dishRepository;
    private final KitchenRepository kitchenRepository;
    private final PreferenceRepository preferenceRepository;
    private final IdentityQueryService identityQueryService;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository itemRepository,
            DishRepository dishRepository,
            KitchenRepository kitchenRepository,
            PreferenceRepository preferenceRepository,
            IdentityQueryService identityQueryService,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.dishRepository = dishRepository;
        this.kitchenRepository = kitchenRepository;
        this.preferenceRepository = preferenceRepository;
        this.identityQueryService = identityQueryService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse get(String email) {
        UUID userId = identityQueryService.getUserId(email);
        return cartRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new CartResponse(null, null, List.of(), BigDecimal.ZERO, false, null));
    }

    @Transactional
    public CartResponse add(String email, CartItemRequest request) {
        UUID userId = identityQueryService.getUserId(email);
        UUID kitchenId = dishRepository.findKitchenIdById(request.dishId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        Cart cart = getOrCreateLockedCart(userId);
        Kitchen kitchen = kitchenRepository.findByIdForUpdate(kitchenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        Dish dish = dishRepository.findByIdForUpdate(request.dishId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        ensurePurchasable(dish, kitchen, request.quantity());
        ensureNotExcluded(userId, dish);

        if (cart.getKitchenId() != null && !cart.getKitchenId().equals(kitchen.getId())) {
            if (!request.confirmReplaceKitchen()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CART_KITCHEN_MISMATCH",
                        "В корзине уже есть блюда другой кухни. Подтвердите очистку корзины.",
                        java.util.Map.of("currentKitchenId", cart.getKitchenId(), "requestedKitchenId", kitchen.getId())
                );
            }
            itemRepository.deleteByCartId(cart.getId());
        }

        cart.setKitchenId(kitchen.getId());
        CartItem existing = itemRepository.findByCartIdAndDishId(cart.getId(), dish.getId()).orElse(null);
        if (existing != null) {
            int updatedQuantity = existing.getQuantity() + request.quantity();
            if (updatedQuantity > 99) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CART_QUANTITY_LIMIT", "В корзине допускается не более 99 порций одного блюда");
            }
            ensurePurchasable(dish, kitchen, updatedQuantity);
            existing.setQuantity(updatedQuantity);
        } else {
            if (itemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId()).size() >= 30) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CART_ITEMS_LIMIT", "В корзине допускается не более 30 разных блюд");
            }
            itemRepository.save(new CartItem(cart.getId(), dish.getId(), request.quantity(), dish.getPrice()));
        }
        cart.touch();
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateQuantity(String email, UUID dishId, int quantity) {
        UUID userId = identityQueryService.getUserId(email);
        Cart cart = getLockedCart(userId);
        CartItem item = itemRepository.findByCartIdAndDishId(cart.getId(), dishId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Позиция корзины не найдена"));
        UUID kitchenId = dishRepository.findKitchenIdById(dishId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        Kitchen kitchen = kitchenRepository.findByIdForUpdate(kitchenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        Dish dish = dishRepository.findByIdForUpdate(dishId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        ensurePurchasable(dish, kitchen, quantity);
        ensureNotExcluded(userId, dish);
        item.setQuantity(quantity);
        cart.touch();
        return toResponse(cart);
    }

    @Transactional
    public CartResponse remove(String email, UUID dishId) {
        UUID userId = identityQueryService.getUserId(email);
        Cart cart = getLockedCart(userId);
        itemRepository.deleteByCartIdAndDishId(cart.getId(), dishId);
        if (itemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId()).isEmpty()) {
            cart.setKitchenId(null);
        }
        cart.touch();
        return toResponse(cart);
    }

    @Transactional
    public void clear(String email) {
        UUID userId = identityQueryService.getUserId(email);
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElse(null);
        if (cart == null) return;
        itemRepository.deleteByCartId(cart.getId());
        cart.setKitchenId(null);
        cart.touch();
    }

    @Transactional
    public void lockForPreferenceUpdate(UUID userId) {
        // Lock before reading/writing preferences, including for a previously absent cart.
        getOrCreateLockedCart(userId);
    }

    private Cart getOrCreateLockedCart(UUID userId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElse(null);
        if (cart == null) {
            userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Пользователь не найден"));
            cart = cartRepository.findByUserIdForUpdate(userId).orElse(null);
            if (cart == null) cart = cartRepository.saveAndFlush(new Cart(userId));
        }
        return cart;
    }

    @Transactional
    public void removeIncompatibleItems(UUID userId, java.util.Set<UUID> excludedIngredientIds) {
        if (excludedIngredientIds == null || excludedIngredientIds.isEmpty()) return;
        cartRepository.findByUserIdForUpdate(userId).ifPresent(cart -> {
            for (CartItem item : itemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId())) {
                dishRepository.findById(item.getDishId()).ifPresent(dish -> {
                    boolean incompatible = dish.getIngredients().stream()
                            .map(Ingredient::getId)
                            .anyMatch(excludedIngredientIds::contains);
                    if (incompatible) itemRepository.delete(item);
                });
            }
            if (itemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId()).isEmpty()) {
                cart.setKitchenId(null);
            }
            cart.touch();
        });
    }

    private Cart getLockedCart(UUID userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", "Корзина не найдена"));
    }

    private void ensurePurchasable(Dish dish, Kitchen kitchen, int quantity) {
        if (dish.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "DISH_ARCHIVED", "Архивное блюдо нельзя заказать");
        }
        if (kitchen.getStatus() != KitchenStatus.APPROVED || !kitchen.isAcceptingOrders()) {
            throw new ApiException(HttpStatus.CONFLICT, "KITCHEN_CLOSED", "Кухня сейчас не принимает заказы");
        }
        if (dish.getAvailablePortions() < quantity) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Недостаточно свободных порций", java.util.Map.of("availablePortions", dish.getAvailablePortions()));
        }
    }

    private void ensureNotExcluded(UUID userId, Dish dish) {
        Preference preference = preferenceRepository.findById(userId).orElse(null);
        if (preference == null || preference.getExcludedIngredientIds().isEmpty()) return;
        var conflict = dish.getIngredients().stream()
                .map(Ingredient::getId)
                .filter(preference.getExcludedIngredientIds()::contains)
                .findFirst();
        if (conflict.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXCLUDED_INGREDIENT", "Блюдо содержит исключённый ингредиент", java.util.Map.of("ingredientId", conflict.get()));
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean changed = false;
        for (CartItem item : itemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId())) {
            Dish dish = dishRepository.findById(item.getDishId()).orElse(null);
            if (dish == null) continue;
            Kitchen kitchen = kitchenRepository.findById(dish.getKitchenId()).orElse(null);
            boolean available = kitchen != null
                    && kitchen.getStatus() == KitchenStatus.APPROVED
                    && kitchen.isAcceptingOrders()
                    && !dish.isArchived()
                    && dish.getAvailablePortions() >= item.getQuantity();
            BigDecimal lineTotal = dish.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            boolean priceChanged = item.getPriceAtAdd().compareTo(dish.getPrice()) != 0;
            changed |= priceChanged;
            total = total.add(lineTotal);
            items.add(new CartItemResponse(
                    dish.getId(), dish.getName(), item.getQuantity(), item.getPriceAtAdd(),
                    dish.getPrice(), lineTotal, priceChanged, dish.getAvailablePortions(), available
            ));
        }
        return new CartResponse(cart.getId(), cart.getKitchenId(), items, total, changed, cart.getUpdatedAt());
    }
}
