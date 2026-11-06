package com.foodflow.recommendation;

import com.foodflow.catalog.dish.CatalogDishResponse;
import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.dish.DishPageResponse;
import com.foodflow.catalog.CatalogQueryService;
import com.foodflow.catalog.ingredient.Ingredient;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenStatus;
import com.foodflow.common.ApiException;
import com.foodflow.feedback.ReviewQueryService;
import com.foodflow.feedback.ReviewSummaryResponse;
import com.foodflow.identity.IdentityQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final PreferenceRepository preferenceRepository;
    private final CatalogQueryService catalog;
    private final IdentityQueryService identityQueryService;
    private final ReviewQueryService reviewQueryService;

    public RecommendationService(
            PreferenceRepository preferenceRepository,
            CatalogQueryService catalog,
            IdentityQueryService identityQueryService,
            ReviewQueryService reviewQueryService
    ) {
        this.preferenceRepository = preferenceRepository;
        this.catalog = catalog;
        this.identityQueryService = identityQueryService;
        this.reviewQueryService = reviewQueryService;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(String email, int page, int size) {
        validatePage(page, size);
        Ranking ranking = rank(email, null, null, null, null, false);
        int from = pageStart(page, size, ranking.dishes().size());
        int to = Math.min(from + size, ranking.dishes().size());
        return new RecommendationResponse(
                ranking.scoringEnabled(), page, size,
                ranking.dishes().size(), pageCount(ranking.dishes().size(), size),
                ranking.scoringEnabled() ? null : "Заполните предпочтения, чтобы получить персональный рейтинг блюд.",
                ranking.dishes().subList(from, to)
        );
    }

    /** Applies the same personalised score as recommendations while preserving catalog filters and response shape. */
    @Transactional(readOnly = true)
    public DishPageResponse findCatalogSorted(
            String email,
            UUID kitchenId,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean availableOnly,
            int page,
            int size
    ) {
        validatePage(page, size);
        Ranking ranking = rank(email, kitchenId, categoryId, minPrice, maxPrice, availableOnly);
        List<RecommendationDishResponse> ordered = new ArrayList<>(ranking.dishes());
        if (!ranking.scoringEnabled()) {
            // With an empty preference profile the specification calls for the ordinary catalog, not an invented score.
            ordered.sort(Comparator
                    .comparing((RecommendationDishResponse item) -> item.dish().price())
                    .thenComparing(item -> item.dish().id()));
        }
        int from = pageStart(page, size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        List<CatalogDishResponse> content = ordered.subList(from, to).stream()
                .map(RecommendationDishResponse::dish)
                .toList();
        return new DishPageResponse(content, page, size, ordered.size(), pageCount(ordered.size(), size));
    }

    @Transactional(readOnly = true)
    public DishCompatibilityResponse checkCompatibility(String email, UUID dishId) {
        UUID userId = identityQueryService.getUserId(email);
        Dish dish = catalog.findDish(dishId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено"));
        Kitchen kitchen = catalog.findKitchen(dish.getKitchenId()).orElse(null);
        if (dish.isArchived() || kitchen == null || kitchen.getStatus() != KitchenStatus.APPROVED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DISH_NOT_FOUND", "Блюдо не найдено");
        }
        Preference preference = preferenceRepository.findById(userId).orElse(null);
        if (preference == null) return new DishCompatibilityResponse(dishId, true, null, null);
        for (Ingredient ingredient : dish.getIngredients()) {
            if (preference.getExcludedIngredientIds().contains(ingredient.getId())) {
                return new DishCompatibilityResponse(dishId, false,
                        "Блюдо содержит исключённый ингредиент: " + ingredient.getName(), ingredient.getId());
            }
        }
        return new DishCompatibilityResponse(dishId, true, null, null);
    }

    private Ranking rank(
            String email,
            UUID kitchenId,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean availableOnly
    ) {
        UUID userId = identityQueryService.getUserId(email);
        Preference preference = preferenceRepository.findById(userId).orElse(null);
        Set<UUID> excluded = preference == null ? Set.of() : preference.getExcludedIngredientIds();
        Map<UUID, Kitchen> kitchens = catalog.kitchens().stream()
                .collect(Collectors.toMap(Kitchen::getId, kitchen -> kitchen));

        List<Dish> eligible = catalog.unarchivedDishes().stream()
                .filter(dish -> {
                    Kitchen kitchen = kitchens.get(dish.getKitchenId());
                    return kitchen != null
                            && kitchen.getStatus() == KitchenStatus.APPROVED
                            && (kitchenId == null || kitchenId.equals(kitchen.getId()))
                            && (categoryId == null || categoryId.equals(dish.getCategory().getId()))
                            && (minPrice == null || dish.getPrice().compareTo(minPrice) >= 0)
                            && (maxPrice == null || dish.getPrice().compareTo(maxPrice) <= 0)
                            && (!availableOnly || (kitchen.isAcceptingOrders() && dish.getAvailablePortions() > 0))
                            && dish.getIngredients().stream().map(Ingredient::getId).noneMatch(excluded::contains);
                })
                .toList();

        boolean scoringEnabled = preference != null && hasActiveCriteria(preference);
        Map<UUID, ReviewSummaryResponse> reviewSummaries = reviewQueryService.summaries(
                eligible.stream().map(Dish::getId).toList());
        List<RecommendationDishResponse> scored = new ArrayList<>();
        for (Dish dish : eligible) {
            Score score = scoringEnabled ? calculate(dish, preference) : new Score(null, List.of());
            scored.add(new RecommendationDishResponse(
                    CatalogDishResponse.from(dish, kitchens.get(dish.getKitchenId()),
                            reviewSummaries.getOrDefault(dish.getId(), ReviewSummaryResponse.empty(dish.getId()))),
                    score.value(), score.explanation()
            ));
        }
        if (scoringEnabled) {
            scored.sort(Comparator
                    .comparing(RecommendationDishResponse::score, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(item -> item.dish().id()));
        } else {
            scored.sort(Comparator.comparing(item -> item.dish().id()));
        }
        return new Ranking(scoringEnabled, scored);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Допустимы page >= 0 и size от 1 до 100");
        }
    }

    private int pageStart(int page, int size, int total) {
        return (int) Math.min((long) page * size, total);
    }

    private int pageCount(int total, int size) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    private boolean hasActiveCriteria(Preference preference) {
        return !preference.getFavoriteCategoryIds().isEmpty()
                || !preference.getFavoriteIngredientIds().isEmpty()
                || preference.getKcalMin() != null
                || preference.getKcalMax() != null;
    }

    private Score calculate(Dish dish, Preference preference) {
        Boolean categoryMatch = null;
        int ingredientMatches = 0;
        int ingredientCount = 0;
        Boolean kcalMatch = null;
        List<String> explanation = new ArrayList<>();

        if (!preference.getFavoriteCategoryIds().isEmpty()) {
            categoryMatch = preference.getFavoriteCategoryIds().contains(dish.getCategory().getId());
            explanation.add("Любимая категория: " + (categoryMatch ? "совпадает" : "не совпадает"));
        }
        if (!preference.getFavoriteIngredientIds().isEmpty()) {
            Set<UUID> dishIngredients = dish.getIngredients().stream()
                    .map(Ingredient::getId).collect(Collectors.toSet());
            long matches = preference.getFavoriteIngredientIds().stream()
                    .filter(dishIngredients::contains).count();
            ingredientMatches = Math.toIntExact(matches);
            ingredientCount = preference.getFavoriteIngredientIds().size();
            explanation.add("Любимые ингредиенты: " + matches + " из " + preference.getFavoriteIngredientIds().size());
        }
        if (preference.getKcalMin() != null || preference.getKcalMax() != null) {
            kcalMatch = (preference.getKcalMin() == null || dish.getKcalPerPortion() >= preference.getKcalMin())
                    && (preference.getKcalMax() == null || dish.getKcalPerPortion() <= preference.getKcalMax());
            explanation.add("Калорийность: " + (kcalMatch ? "в заданном диапазоне" : "вне диапазона"));
        }
        return new Score(RecommendationScorer.calculate(categoryMatch, ingredientMatches, ingredientCount, kcalMatch), explanation);
    }

    private record Score(Integer value, List<String> explanation) { }
    private record Ranking(boolean scoringEnabled, List<RecommendationDishResponse> dishes) { }
}
