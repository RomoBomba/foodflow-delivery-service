package com.foodflow.catalog;

import com.foodflow.catalog.dish.Dish;
import com.foodflow.catalog.dish.DishRepository;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenRepository;
import com.foodflow.catalog.category.CategoryRepository;
import com.foodflow.catalog.ingredient.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read contract for other modules; repositories stay inside catalog. */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService {
    private final DishRepository dishes;
    private final KitchenRepository kitchens;
    private final CategoryRepository categories;
    private final IngredientRepository ingredients;

    public CatalogQueryService(DishRepository dishes, KitchenRepository kitchens,
                               CategoryRepository categories, IngredientRepository ingredients) {
        this.dishes = dishes;
        this.kitchens = kitchens;
        this.categories = categories;
        this.ingredients = ingredients;
    }

    public Optional<UUID> findKitchenIdByDishId(UUID id) { return dishes.findKitchenIdById(id); }
    public Optional<Dish> findDish(UUID id) { return dishes.findById(id); }
    public boolean dishExists(UUID id) { return dishes.existsById(id); }
    public Optional<Kitchen> findKitchen(UUID id) { return kitchens.findById(id); }
    public Optional<Kitchen> findOwnedKitchen(UUID ownerId) { return kitchens.findByOwnerId(ownerId); }
    public List<Kitchen> kitchens() { return kitchens.findAll(); }
    public List<Dish> unarchivedDishes() { return dishes.findAllByArchivedFalse(); }
    public List<Dish> menu(UUID kitchenId) { return dishes.findAllByKitchenIdOrderByCreatedAtDesc(kitchenId); }
    public long countCategories(Set<UUID> ids) { return categories.countByIdIn(ids); }
    public long countIngredients(Set<UUID> ids) { return ingredients.countByIdIn(ids); }
}
