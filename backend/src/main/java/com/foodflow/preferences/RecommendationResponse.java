package com.foodflow.preferences;

import java.util.List;

public record RecommendationResponse(
        boolean scoringEnabled,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String message,
        List<RecommendationDishResponse> content
) { }
