package com.foodflow.preferences;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "preferences")
public class Preference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "kcal_min")
    private Integer kcalMin;

    @Column(name = "kcal_max")
    private Integer kcalMax;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_categories",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "user_id"))
    @Column(name = "category_id", nullable = false)
    private Set<UUID> favoriteCategoryIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_favorite_ingredients",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "user_id"))
    @Column(name = "ingredient_id", nullable = false)
    private Set<UUID> favoriteIngredientIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_excluded_ingredients",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "user_id"))
    @Column(name = "ingredient_id", nullable = false)
    private Set<UUID> excludedIngredientIds = new HashSet<>();

    protected Preference() {
    }

    public Preference(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public Integer getKcalMin() { return kcalMin; }
    public Integer getKcalMax() { return kcalMax; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<UUID> getFavoriteCategoryIds() { return favoriteCategoryIds; }
    public Set<UUID> getFavoriteIngredientIds() { return favoriteIngredientIds; }
    public Set<UUID> getExcludedIngredientIds() { return excludedIngredientIds; }

    public void update(
            Set<UUID> categories,
            Set<UUID> favoriteIngredients,
            Set<UUID> excludedIngredients,
            Integer min,
            Integer max
    ) {
        this.favoriteCategoryIds.clear();
        this.favoriteCategoryIds.addAll(categories);
        this.favoriteIngredientIds.clear();
        this.favoriteIngredientIds.addAll(favoriteIngredients);
        this.excludedIngredientIds.clear();
        this.excludedIngredientIds.addAll(excludedIngredients);
        this.kcalMin = min;
        this.kcalMax = max;
        this.updatedAt = Instant.now();
    }
}
