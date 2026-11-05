package com.foodflow.ordering;

import java.time.Instant;
import java.util.UUID;

public record OrderHistoryResponse(
        UUID id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        UUID actorId,
        String reason,
        Instant createdAt
) {
    public static OrderHistoryResponse from(OrderStatusHistory history) {
        return new OrderHistoryResponse(history.getId(), history.getFromStatus(),
                history.getToStatus(), history.getActorId(), history.getReason(), history.getCreatedAt());
    }
}
