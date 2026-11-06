package com.foodflow.recommendation;

import com.foodflow.catalog.CatalogQueryService;
import com.foodflow.common.ApiException;
import com.foodflow.identity.IdentityQueryService;
import com.foodflow.ordering.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class PreferenceService {
    private final PreferenceRepository repository;
    private final CatalogQueryService catalog;
    private final IdentityQueryService identityQueryService;
    private final CartService cartService;

    public PreferenceService(
            PreferenceRepository repository,
            CatalogQueryService catalog,
            IdentityQueryService identityQueryService,
            CartService cartService
    ) {
        this.repository = repository;
        this.catalog = catalog;
        this.identityQueryService = identityQueryService;
        this.cartService = cartService;
    }

    @Transactional(readOnly = true)
    public PreferenceResponse get(String email) {
        UUID userId = identityQueryService.getUserId(email);
        return repository.findById(userId)
                .map(PreferenceResponse::from)
                .orElseGet(() -> new PreferenceResponse(userId, Set.of(), Set.of(), Set.of(), null, null, null));
    }

    @Transactional
    public PreferenceResponse update(String email, PreferenceRequest request) {
        UUID userId = identityQueryService.getUserId(email);
        if (request.favoriteCategoryIds().stream().anyMatch(java.util.Objects::isNull)
                || request.favoriteIngredientIds().stream().anyMatch(java.util.Objects::isNull)
                || request.excludedIngredientIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PREFERENCE_ID", "Списки категорий и ингредиентов не должны содержать пустые идентификаторы");
        }
        if (request.kcalMin() != null && request.kcalMax() != null
                && request.kcalMin() > request.kcalMax()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_KCAL_RANGE", "Минимальная калорийность не должна превышать максимальную");
        }
        if (!java.util.Collections.disjoint(request.favoriteIngredientIds(), request.excludedIngredientIds())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PREFERENCE_INGREDIENT_CONFLICT", "Ингредиент нельзя одновременно любить и исключать");
        }
        if (!request.favoriteCategoryIds().isEmpty()
                && catalog.countCategories(request.favoriteCategoryIds()) != request.favoriteCategoryIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY", "Одна или несколько категорий не найдены");
        }
        Set<UUID> ingredientIds = new java.util.HashSet<>(request.favoriteIngredientIds());
        ingredientIds.addAll(request.excludedIngredientIds());
        if (!ingredientIds.isEmpty()
                && catalog.countIngredients(ingredientIds) != ingredientIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_INGREDIENT", "Один или несколько ингредиентов не найдены");
        }

        cartService.lockForPreferenceUpdate(userId);
        Preference preference = repository.findById(userId).orElseGet(() -> new Preference(userId));
        preference.update(
                request.favoriteCategoryIds(),
                request.favoriteIngredientIds(),
                request.excludedIngredientIds(),
                request.kcalMin(),
                request.kcalMax()
        );
        Preference saved = repository.save(preference);
        // FR-10: re-check existing cart contents immediately after stop ingredients change.
        cartService.removeIncompatibleItems(userId, request.excludedIngredientIds());
        return PreferenceResponse.from(saved);
    }
}
