package com.foodflow.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Deterministic MVP scoring; an ingredient count of zero means that criterion is inactive. */
public final class RecommendationScorer {
    private RecommendationScorer() { }

    /** Counts preserve exact fractions, including scores exactly halfway between two integers. */
    public static Integer calculate(Boolean categoryMatch, int ingredientMatches, int ingredientCount, Boolean kcalMatch) {
        if (ingredientCount < 0 || ingredientMatches < 0 || ingredientMatches > ingredientCount) {
            throw new IllegalArgumentException("Invalid ingredient match counts");
        }
        int totalWeight = 0;
        int wholeWeight = 0;
        if (categoryMatch != null) {
            wholeWeight += categoryMatch ? 50 : 0;
            totalWeight += 50;
        }
        if (kcalMatch != null) {
            wholeWeight += kcalMatch ? 20 : 0;
            totalWeight += 20;
        }
        if (ingredientCount > 0) totalWeight += 30;
        if (totalWeight == 0) return null;
        long denominator = Math.max(1, ingredientCount);
        long numerator = wholeWeight * denominator + 30L * ingredientMatches;
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalWeight * denominator), 0, RoundingMode.HALF_UP).intValueExact();
    }
}
