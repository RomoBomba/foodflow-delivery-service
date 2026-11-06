package com.foodflow.recommendation;

import java.util.UUID;

public record DishCompatibilityResponse(
        UUID dishId,
        boolean compatible,
        String reason,
        UUID excludedIngredientId
) { }
