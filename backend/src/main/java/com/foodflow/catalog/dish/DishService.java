package com.foodflow.catalog.dish;

import com.foodflow.catalog.category.Category;
import com.foodflow.catalog.category.CategoryRepository;
import com.foodflow.catalog.ingredient.Ingredient;
import com.foodflow.catalog.ingredient.IngredientRepository;
import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenRepository;
import com.foodflow.catalog.kitchen.KitchenStatus;
import com.foodflow.identity.IdentityQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DishService {

    private final DishRepository dishRepository;
    private final KitchenRepository kitchenRepository;
    private final CategoryRepository categoryRepository;
    private final IngredientRepository ingredientRepository;
    private final IdentityQueryService identityQueryService;

    public DishService(
            DishRepository dishRepository,
            KitchenRepository kitchenRepository,
            CategoryRepository categoryRepository,
            IngredientRepository ingredientRepository,
            IdentityQueryService identityQueryService
    ) {
        this.dishRepository = dishRepository;
        this.kitchenRepository = kitchenRepository;
        this.categoryRepository = categoryRepository;
        this.ingredientRepository = ingredientRepository;
        this.identityQueryService = identityQueryService;
    }

    @Transactional
    public DishResponse create(
            String email,
            DishCreateRequest request
    ) {
        Kitchen kitchen = getApprovedKitchenForWrite(email);
        Category category = getCategory(request.categoryId());
        Set<Ingredient> ingredients = getIngredients(
                request.ingredientIds()
        );

        Dish dish = new Dish(
                kitchen.getId(),
                category,
                ingredients,
                request
        );

        return DishResponse.from(dishRepository.saveAndFlush(dish));
    }

    @Transactional
    public DishResponse update(
            String email,
            UUID dishId,
            DishCreateRequest request
    ) {
        Kitchen kitchen = getApprovedKitchenForWrite(email);
        Dish dish = getOwnedDishForWrite(dishId, kitchen.getId());

        if (dish.isArchived()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Архивное блюдо нельзя редактировать"
            );
        }

        dish.updateDetails(
                getCategory(request.categoryId()),
                getIngredients(request.ingredientIds()),
                request
        );

        return DishResponse.from(dish);
    }

    @Transactional
    public void archive(String email, UUID dishId) {
        Kitchen kitchen = getApprovedKitchenForWrite(email);
        Dish dish = getOwnedDishForWrite(dishId, kitchen.getId());

        dish.archive();
    }

    @Transactional(readOnly = true)
    public List<DishResponse> getMyMenu(String email) {
        UUID ownerId = identityQueryService.getUserId(email);

        Kitchen kitchen = kitchenRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня пользователя не найдена"
                ));

        return dishRepository
                .findAllByKitchenIdOrderByCreatedAtDesc(kitchen.getId())
                .stream()
                .map(DishResponse::from)
                .toList();
    }

    private Kitchen getApprovedKitchenForWrite(String email) {
        UUID ownerId = identityQueryService.getUserId(email);

        Kitchen kitchen = kitchenRepository
                .findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня пользователя не найдена"
                ));

        if (kitchen.getStatus() != KitchenStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Изменять меню может только одобренная кухня"
            );
        }

        return kitchen;
    }

    private Dish getOwnedDishForWrite(
            UUID dishId,
            UUID kitchenId
    ) {
        return dishRepository
                .findOwnedByIdForUpdate(dishId, kitchenId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Блюдо не найдено"
                ));
    }

    private Category getCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Категория не найдена"
                ));
    }

    private Set<Ingredient> getIngredients(Set<UUID> ingredientIds) {
        var ingredients = ingredientRepository.findAllById(
                ingredientIds
        );

        if (ingredients.size() != ingredientIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Один или несколько ингредиентов не найдены"
            );
        }

        return new HashSet<>(ingredients);
    }
}