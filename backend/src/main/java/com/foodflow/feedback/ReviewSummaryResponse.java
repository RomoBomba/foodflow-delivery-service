package com.foodflow.feedback;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ReviewSummaryResponse(
        UUID dishId,
        Double averageRating,
        long reviewCount,
        Integer repeatOrderPercent,
        long repeatOrderResponses
) {
    public static ReviewSummaryResponse empty(UUID dishId) {
        return new ReviewSummaryResponse(dishId, null, 0, null, 0);
    }

    public static ReviewSummaryResponse from(UUID dishId, ReviewRepository.ReviewAggregate aggregate) {
        if (aggregate == null || aggregate.getReviewCount() == null || aggregate.getReviewCount() == 0) {
            return empty(dishId);
        }
        Double average = aggregate.getAverageRating() == null ? null
                : BigDecimal.valueOf(aggregate.getAverageRating()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        long count = aggregate.getReviewCount();
        int repeat = BigDecimal.valueOf(aggregate.getRepeatCount() == null ? 0 : aggregate.getRepeatCount())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValue();
        long responses = aggregate.getRepeatOrderResponses() == null ? count : aggregate.getRepeatOrderResponses();
        return new ReviewSummaryResponse(dishId, average, count, repeat, responses);
    }
}
