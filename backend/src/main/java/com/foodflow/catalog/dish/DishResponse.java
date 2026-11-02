package com.foodflow.catalog.dish;

import com.foodflow.catalog.category.CategoryResponse;
import com.foodflow.catalog.ingredient.IngredientResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record DishResponse(
        UUID id,
        UUID kitchenId,
        String name,
        String description,
        CategoryResponse category,
        List<IngredientResponse> ingredients,
        BigDecimal price,
        int weightGrams,
        int kcalPerPortion,
        int availablePortions,
        String photoUrl,
        boolean archived,
        Instant createdAt
) {

    public static DishResponse from(Dish dish) {
        var ingredients = dish.getIngredients().stream()
                .map(ingredient -> new IngredientResponse(
                        ingredient.getId(),
                        ingredient.getName()
                ))
                .sorted(Comparator.comparing(IngredientResponse::name))
                .toList();

        return new DishResponse(
                dish.getId(),
                dish.getKitchenId(),
                dish.getName(),
                dish.getDescription(),
                new CategoryResponse(
                        dish.getCategory().getId(),
                        dish.getCategory().getName()
                ),
                ingredients,
                dish.getPrice(),
                dish.getWeightGrams(),
                dish.getKcalPerPortion(),
                dish.getAvailablePortions(),
                dish.getPhotoUrl(),
                dish.isArchived(),
                dish.getCreatedAt()
        );
    }
}