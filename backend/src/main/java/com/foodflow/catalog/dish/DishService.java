package com.foodflow.catalog.dish;

import com.foodflow.catalog.category.CategoryRepository;
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
    public DishResponse create(String email, DishCreateRequest request) {
        Kitchen ownedKitchen = getOwnedKitchen(email);

        Kitchen kitchen = kitchenRepository
                .findByIdForUpdate(ownedKitchen.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня не найдена"
                ));

        if (kitchen.getStatus() != KitchenStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Добавлять блюда может только одобренная кухня"
            );
        }

        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Категория не найдена"
                ));

        var ingredients =
                ingredientRepository.findAllById(request.ingredientIds());

        if (ingredients.size() != request.ingredientIds().size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Один или несколько ингредиентов не найдены"
            );
        }

        Dish dish = new Dish(
                kitchen.getId(),
                category,
                new HashSet<>(ingredients),
                request
        );

        return DishResponse.from(dishRepository.saveAndFlush(dish));
    }

    @Transactional(readOnly = true)
    public List<DishResponse> getMyMenu(String email) {
        Kitchen kitchen = getOwnedKitchen(email);

        return dishRepository
                .findAllByKitchenIdOrderByCreatedAtDesc(kitchen.getId())
                .stream()
                .map(DishResponse::from)
                .toList();
    }

    private Kitchen getOwnedKitchen(String email) {
        UUID ownerId = identityQueryService.getUserId(email);

        return kitchenRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня пользователя не найдена"
                ));
    }
}