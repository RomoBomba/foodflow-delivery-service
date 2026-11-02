package com.foodflow.catalog.dish;

import java.util.List;

public record DishPageResponse(
        List<CatalogDishResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}