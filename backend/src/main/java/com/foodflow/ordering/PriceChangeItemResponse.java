package com.foodflow.ordering;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceChangeItemResponse(
        UUID dishId,
        String dishName,
        int quantity,
        BigDecimal previousUnitPrice,
        BigDecimal currentUnitPrice,
        BigDecimal currentLineTotal
) { }
