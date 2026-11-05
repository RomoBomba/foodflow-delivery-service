package com.foodflow.ordering;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID dishId,
        String dishName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int weightGrams,
        int kcalPerPortion
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getDishId(), item.getDishName(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal(),
                item.getWeightGrams(), item.getKcalPerPortion());
    }
}
