package com.foodflow.catalog.dish;

import com.foodflow.catalog.category.Category;
import com.foodflow.catalog.ingredient.Ingredient;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "dishes")
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kitchen_id", nullable = false)
    private UUID kitchenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToMany
    @JoinTable(
            name = "dish_ingredients",
            joinColumns = @JoinColumn(name = "dish_id"),
            inverseJoinColumns = @JoinColumn(name = "ingredient_id")
    )
    private Set<Ingredient> ingredients = new HashSet<>();

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;

    @Column(name = "kcal_per_portion", nullable = false)
    private int kcalPerPortion;

    @Column(name = "available_portions", nullable = false)
    private int availablePortions;

    @Column(name = "photo_url", length = 2048)
    private String photoUrl;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Dish() {
    }

    public Dish(
            UUID kitchenId,
            Category category,
            Set<Ingredient> ingredients,
            DishCreateRequest request
    ) {
        this.kitchenId = kitchenId;
        this.category = category;
        this.ingredients = new HashSet<>(ingredients);
        this.name = request.name().strip();
        this.description = request.description().strip();
        this.price = request.price();
        this.weightGrams = request.weightGrams();
        this.kcalPerPortion = request.kcalPerPortion();
        this.availablePortions = request.availablePortions();
        this.photoUrl = request.photoUrl();
        this.archived = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getKitchenId() { return kitchenId; }

    public Category getCategory() { return category; }

    public Set<Ingredient> getIngredients() { return ingredients; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public BigDecimal getPrice() { return price; }

    public int getWeightGrams() { return weightGrams; }

    public int getKcalPerPortion() { return kcalPerPortion; }

    public int getAvailablePortions() { return availablePortions; }

    public String getPhotoUrl() { return photoUrl; }

    public boolean isArchived() { return archived; }

    /** Reduce free portions when an order is reserved. Must be called under a row lock. */
    public void reservePortions(int quantity) {
        if (quantity < 1 || availablePortions < quantity) {
            throw new IllegalStateException("Insufficient free portions");
        }
        availablePortions -= quantity;
    }

    /** Return portions only when an order is cancelled before preparation. */
    public void releasePortions(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        availablePortions = Math.addExact(availablePortions, quantity);
    }

    public Instant getCreatedAt() { return createdAt; }

    void updateDetails(
            Category category,
            Set<Ingredient> ingredients,
            DishCreateRequest request
    ) {
        this.category = category;

        this.ingredients.clear();
        this.ingredients.addAll(ingredients);

        this.name = request.name().strip();
        this.description = request.description().strip();
        this.price = request.price();
        this.weightGrams = request.weightGrams();
        this.kcalPerPortion = request.kcalPerPortion();
        this.availablePortions = request.availablePortions();
        this.photoUrl = request.photoUrl();
    }

    void archive() {
        this.archived = true;
    }
}