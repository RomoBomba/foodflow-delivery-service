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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository itemRepository;
    private final CatalogQueryService catalog;
    private final CatalogStockService stock;
    private final PreferenceQueryService preferenceQuery;
    private final IdentityQueryService identityQueryService;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository itemRepository,
            CatalogQueryService catalog,
            CatalogStockService stock,
            PreferenceQueryService preferenceQuery,
            IdentityQueryService identityQueryService
    ) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.catalog = catalog;
        this.stock = stock;
        this.preferenceQuery = preferenceQuery;
        this.identityQueryService = identityQueryService;
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
        UUID kitchenId = catalog.findKitchenIdByDishId(request.dishId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        Cart cart = getOrCreateLockedCart(userId);
        Kitchen kitchen = stock.lockKitchen(kitchenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        Dish dish = stock.lockDish(request.dishId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        stock.ensurePurchasable(dish, kitchen, request.quantity());

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
            stock.ensurePurchasable(dish, kitchen, updatedQuantity);
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
        UUID kitchenId = catalog.findKitchenIdByDishId(dishId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        Kitchen kitchen = stock.lockKitchen(kitchenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KITCHEN_NOT_FOUND", "Кухня не найдена"));
        Dish dish = stock.lockDish(dishId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        stock.ensurePurchasable(dish, kitchen, quantity);
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

    private Cart getOrCreateLockedCart(UUID userId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElse(null);
        if (cart == null) {
            identityQueryService.lockUser(userId);
            cart = cartRepository.findByUserIdForUpdate(userId).orElse(null);
            if (cart == null) cart = cartRepository.saveAndFlush(new Cart(userId));
        }
        return cart;
    }

    private Cart getLockedCart(UUID userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", "Корзина не найдена"));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean changed = false;
        var excluded = preferenceQuery.excludedIngredients(cart.getUserId());
        for (CartItem item : itemRepository.findAllByCartIdOrderByDishIdAsc(cart.getId())) {
            Dish dish = catalog.findDish(item.getDishId()).orElse(null);
            if (dish == null) continue;
            Kitchen kitchen = catalog.findKitchen(dish.getKitchenId()).orElse(null);
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
                    dish.getPrice(), lineTotal, priceChanged, dish.getAvailablePortions(), available,
                    dish.getPhotoUrl(), dish.getIngredients().stream().filter(ingredient -> excluded.contains(ingredient.getId()))
                            .map(ingredient -> ingredient.getName()).sorted().toList()
            ));
        }
        return new CartResponse(cart.getId(), cart.getKitchenId(), items, total, changed, cart.getUpdatedAt());
    }
}
