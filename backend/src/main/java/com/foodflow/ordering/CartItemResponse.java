package com.foodflow.ordering;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

public record CartItemResponse(
        UUID dishId,
        String dishName,
        int quantity,
        BigDecimal priceAtAdd,
        BigDecimal currentPrice,
        BigDecimal lineTotal,
        boolean priceChanged,
        int availablePortions,
        boolean available,
        String photoUrl,
        List<String> excludedIngredients
) { }
