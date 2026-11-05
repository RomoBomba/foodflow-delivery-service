package com.foodflow.preferences;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Deterministic MVP scoring rules; absent preference criteria do not contribute to the denominator. */
public final class RecommendationScorer {
    private RecommendationScorer() { }

    /** @return rounded score from 0..100, or null when no criterion is active. */
    public static Integer calculate(Boolean categoryMatch, Double favoriteIngredientShare, Boolean kcalMatch) {
        double weighted = 0;
        int totalWeight = 0;
        if (categoryMatch != null) {
            weighted += 50 * (categoryMatch ? 1 : 0);
            totalWeight += 50;
        }
        if (favoriteIngredientShare != null) {
            double share = Math.max(0, Math.min(1, favoriteIngredientShare));
            weighted += 30 * share;
            totalWeight += 30;
        }
        if (kcalMatch != null) {
            weighted += 20 * (kcalMatch ? 1 : 0);
            totalWeight += 20;
        }
        if (totalWeight == 0) return null;
        return BigDecimal.valueOf(100 * weighted / totalWeight)
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
