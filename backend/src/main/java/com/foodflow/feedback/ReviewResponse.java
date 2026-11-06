package com.foodflow.feedback;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID orderId,
        UUID orderItemId,
        UUID dishId,
        int rating,
        boolean reordered,
        String text,
        boolean visible,
        String moderationReason,
        UUID moderatedBy,
        Instant moderatedAt,
        Instant createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getOrderId(), review.getOrderItemId(),
                review.getDishId(), review.getRating(), review.isReordered(), review.getReviewText(),
                review.isVisible(), review.getModerationReason(), review.getModeratedBy(),
                review.getModeratedAt(), review.getCreatedAt());
    }
}
