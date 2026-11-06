package com.foodflow.ordering;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID kitchenId,
        List<CartItemResponse> items,
        BigDecimal totalPrice,
        boolean hasPriceChanges,
        Instant updatedAt
) { }
