package com.foodflow.catalog.ingredient;

import java.util.UUID;

public record IngredientResponse(
        UUID id,
        String name
) {
}