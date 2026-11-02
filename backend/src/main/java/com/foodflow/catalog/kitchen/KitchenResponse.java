package com.foodflow.catalog.kitchen;

import java.time.Instant;
import java.util.UUID;

public record KitchenResponse(
        UUID id,
        String name,
        String description,
        String contacts,
        String deliveryArea,
        KitchenStatus status,
        boolean acceptingOrders,
        Instant createdAt,
        String moderationReason,
        Instant moderatedAt
) {

    public static KitchenResponse from(Kitchen kitchen) {
        return new KitchenResponse(
                kitchen.getId(),
                kitchen.getName(),
                kitchen.getDescription(),
                kitchen.getContacts(),
                kitchen.getDeliveryArea(),
                kitchen.getStatus(),
                kitchen.isAcceptingOrders(),
                kitchen.getCreatedAt(),
                kitchen.getModerationReason(),
                kitchen.getModeratedAt()
        );
    }
}