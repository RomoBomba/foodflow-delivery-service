package com.foodflow.preferences;

import com.foodflow.catalog.dish.CatalogDishResponse;

import java.util.List;

public record RecommendationDishResponse(
        CatalogDishResponse dish,
        Integer score,
        List<String> explanation
) { }
