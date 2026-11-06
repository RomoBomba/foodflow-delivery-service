package com.foodflow.preferences;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScorerTest {
    @ParameterizedTest
    @CsvSource(value = {
            "true, 1, 2, true, 85",
            "true, 0, 0, null, 100",
            "null, 0, 0, null, null",
            "false, 0, 1, false, 0",
            "null, 1, 8, null, 13",
            "true, 0, 0, false, 71",
            "null, 1, 1, true, 100",
            "null, 0, 0, true, 100"
    }, nullValues = "null")
    void scoresOnlyActiveCriteria(Boolean category, int matches, int count, Boolean kcal, Integer expected) {
        assertThat(RecommendationScorer.calculate(category, matches, count, kcal)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void allIngredientRatiosUpToProfileLimitMatchExactHalfUpRounding() {
        for (Boolean category : new Boolean[]{null, false, true}) {
            for (Boolean kcal : new Boolean[]{null, false, true}) {
                for (int total = 1; total <= 100; total++) {
                    for (int matches = 0; matches <= total; matches++) {
                        int weight = 30 + (category == null ? 0 : 50) + (kcal == null ? 0 : 20);
                        long numerator = 100L * (30L * matches + (Boolean.TRUE.equals(category) ? 50L * total : 0)
                                + (Boolean.TRUE.equals(kcal) ? 20L * total : 0));
                        long denominator = (long) weight * total;
                        int expected = (int) ((2 * numerator + denominator) / (2 * denominator));
                        assertThat(RecommendationScorer.calculate(category, matches, total, kcal))
                                .as("category=%s kcal=%s ingredients=%d/%d", category, kcal, matches, total)
                                .isEqualTo(expected);
                    }
                }
            }
        }
    }
}
