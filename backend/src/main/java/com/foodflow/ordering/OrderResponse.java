package com.foodflow.ordering;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID customerId,
        UUID kitchenId,
        String deliveryAddress,
        String phone,
        String paymentMethod,
        String comment,
        OrderStatus status,
        BigDecimal totalPrice,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items,
        List<OrderHistoryResponse> history
) { }
