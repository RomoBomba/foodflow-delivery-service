package com.foodflow.catalog;

import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.dish.DishRepository;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenRepository;
import com.foodflow.catalog.kitchen.KitchenStatus;
import com.foodflow.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Participates in the caller's cart/order transaction; never commits stock independently. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CatalogStockService {
    private final DishRepository dishes;
    private final KitchenRepository kitchens;

    public CatalogStockService(DishRepository dishes, KitchenRepository kitchens) {
        this.dishes = dishes;
        this.kitchens = kitchens;
    }

    public Optional<Kitchen> lockKitchen(UUID id) { return kitchens.findByIdForUpdate(id); }
    public Optional<Dish> lockDish(UUID id) { return dishes.findByIdForUpdate(id); }

    public void ensurePurchasable(Dish dish, Kitchen kitchen, int quantity) {
        if (dish.isArchived()) throw new ApiException(HttpStatus.CONFLICT, "DISH_ARCHIVED", "Архивное блюдо нельзя заказать");
        if (kitchen.getStatus() != KitchenStatus.APPROVED || !kitchen.isAcceptingOrders()) {
            throw new ApiException(HttpStatus.CONFLICT, "KITCHEN_CLOSED", "Кухня сейчас не принимает заказы");
        }
        if (!dish.getKitchenId().equals(kitchen.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DISH_UNAVAILABLE", "Блюдо не принадлежит кухне заказа");
        }
        if (dish.getAvailablePortions() < quantity) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Недостаточно свободных порций",
                    Map.of("dishId", dish.getId(), "availablePortions", dish.getAvailablePortions()));
        }

    }

    public void reserve(UUID dishId, int quantity) {
        lockDish(dishId).orElseThrow().reservePortions(quantity);
    }

    public void release(UUID dishId, int quantity) {
        lockDish(dishId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "DISH_MISSING", "Блюдо резерва не найдено"))
                .releasePortions(quantity);
    }
}
