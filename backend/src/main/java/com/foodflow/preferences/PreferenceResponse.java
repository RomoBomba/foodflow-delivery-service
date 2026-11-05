package com.foodflow.preferences;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PreferenceResponse(
        UUID userId,
        Set<UUID> favoriteCategoryIds,
        Set<UUID> favoriteIngredientIds,
        Set<UUID> excludedIngredientIds,
        Integer kcalMin,
        Integer kcalMax,
        Instant updatedAt
) {
    public static PreferenceResponse from(Preference preference) {
        return new PreferenceResponse(
                preference.getUserId(),
                Set.copyOf(preference.getFavoriteCategoryIds()),
                Set.copyOf(preference.getFavoriteIngredientIds()),
                Set.copyOf(preference.getExcludedIngredientIds()),
                preference.getKcalMin(),
                preference.getKcalMax(),
                preference.getUpdatedAt()
        );
    }
}
