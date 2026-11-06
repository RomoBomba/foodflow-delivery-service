package com.foodflow.catalog.dish;

import com.foodflow.catalog.category.CategoryResponse;
import com.foodflow.catalog.ingredient.IngredientResponse;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenStatus;
import com.foodflow.feedback.ReviewSummaryResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CatalogDishResponse(
        UUID id,
        UUID kitchenId,
        String kitchenName,
        String name,
        String description,
        CategoryResponse category,
        List<IngredientResponse> ingredients,
        BigDecimal price,
        int weightGrams,
        int kcalPerPortion,
        int availablePortions,
        boolean available,
        String photoUrl,
        Double averageRating,
        long reviewCount,
        Integer repeatOrderPercent,
        long repeatOrderResponses
) {

    public static CatalogDishResponse from(Dish dish, Kitchen kitchen) {
        return from(dish, kitchen, ReviewSummaryResponse.empty(dish.getId()));
    }

    public static CatalogDishResponse from(
            Dish dish, Kitchen kitchen, ReviewSummaryResponse reviewSummary
    ) {
        DishResponse details = DishResponse.from(dish);
        boolean available = kitchen.getStatus() == KitchenStatus.APPROVED
                && kitchen.isAcceptingOrders()
                && dish.getAvailablePortions() > 0
                && !dish.isArchived();

        return new CatalogDishResponse(
                details.id(), details.kitchenId(), kitchen.getName(), details.name(),
                details.description(), details.category(), details.ingredients(),
                details.price(), details.weightGrams(), details.kcalPerPortion(),
                details.availablePortions(), available, details.photoUrl(),
                reviewSummary.averageRating(), reviewSummary.reviewCount(),
                reviewSummary.repeatOrderPercent(), reviewSummary.repeatOrderResponses()
        );
    }
}