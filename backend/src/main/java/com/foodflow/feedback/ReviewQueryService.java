package com.foodflow.feedback;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewQueryService {
    private final ReviewRepository repository;

    public ReviewQueryService(ReviewRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(UUID dishId) {
        return ReviewSummaryResponse.from(dishId, repository.summarize(dishId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, ReviewSummaryResponse> summaries(List<UUID> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) return Map.of();
        return repository.summarizeForDishes(dishIds).stream()
                .collect(Collectors.toMap(
                        ReviewRepository.DishReviewAggregate::getDishId,
                        aggregate -> ReviewSummaryResponse.from(aggregate.getDishId(), aggregate)
                ));
    }
}
