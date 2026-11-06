package com.foodflow.preferences;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record PreferenceRequest(
        @Size(max = 100) Set<UUID> favoriteCategoryIds,
        @Size(max = 100) Set<UUID> favoriteIngredientIds,
        @Size(max = 100) Set<UUID> excludedIngredientIds,
        @Min(0) Integer kcalMin,
        @Min(0) Integer kcalMax
) {
    public PreferenceRequest {
        favoriteCategoryIds = immutableSet(favoriteCategoryIds);
        favoriteIngredientIds = immutableSet(favoriteIngredientIds);
        excludedIngredientIds = immutableSet(excludedIngredientIds);
    }

    private static Set<UUID> immutableSet(Set<UUID> values) {
        // Preserve nulls here so the service can return a structured 400 instead of an NPE.
        return Collections.unmodifiableSet(values == null ? Set.of() : new HashSet<>(values));
    }
}
