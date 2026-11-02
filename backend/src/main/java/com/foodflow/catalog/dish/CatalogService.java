package com.foodflow.catalog.dish;

import com.foodflow.catalog.kitchen.Kitchen;
import com.foodflow.catalog.kitchen.KitchenRepository;
import com.foodflow.catalog.kitchen.KitchenStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class CatalogService {

    private final DishRepository dishRepository;
    private final KitchenRepository kitchenRepository;

    public CatalogService(
            DishRepository dishRepository,
            KitchenRepository kitchenRepository
    ) {
        this.dishRepository = dishRepository;
        this.kitchenRepository = kitchenRepository;
    }

    public DishPageResponse findAll(
            UUID kitchenId,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean availableOnly,
            int page,
            int size,
            String sort
    ) {
        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Минимальная цена не должна превышать максимальную"
            );
        }

        Sort ordering = switch (sort) {
            case "priceAsc" -> Sort.by(
                    Sort.Order.asc("price"),
                    Sort.Order.asc("id")
            );
            case "priceDesc" -> Sort.by(
                    Sort.Order.desc("price"),
                    Sort.Order.asc("id")
            );
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Допустимая сортировка: priceAsc или priceDesc"
            );
        };

        var specification = publicDishes(
                kitchenId,
                categoryId,
                minPrice,
                maxPrice,
                availableOnly
        );

        var result = dishRepository.findAll(
                specification,
                PageRequest.of(page, size, ordering)
        );

        var kitchenIds = result.getContent().stream()
                .map(Dish::getKitchenId)
                .distinct()
                .toList();

        var kitchens = kitchenRepository.findAllById(kitchenIds)
                .stream()
                .collect(Collectors.toMap(
                        Kitchen::getId,
                        Function.identity()
                ));

        var content = result.getContent().stream()
                .map(dish -> CatalogDishResponse.from(
                        dish,
                        kitchens.get(dish.getKitchenId())
                ))
                .toList();

        return new DishPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public CatalogDishResponse findById(UUID id) {
        Specification<Dish> specification = publicDishes(
                null, null, null, null, false
        );

        specification = specification.and(
                (root, query, cb) -> cb.equal(root.get("id"), id)
        );

        Dish dish = dishRepository.findOne(specification)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Блюдо не найдено"
                ));

        Kitchen kitchen = kitchenRepository
                .findById(dish.getKitchenId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня не найдена"
                ));

        return CatalogDishResponse.from(dish, kitchen);
    }

    private Specification<Dish> publicDishes(
            UUID kitchenId,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean availableOnly
    ) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            predicates.add(cb.isFalse(root.get("archived")));

            var kitchenQuery = query.subquery(UUID.class);
            var kitchen = kitchenQuery.from(Kitchen.class);

            var kitchenPredicates = new ArrayList<Predicate>();
            kitchenPredicates.add(cb.equal(
                    kitchen.get("status"),
                    KitchenStatus.APPROVED
            ));

            if (availableOnly) {
                kitchenPredicates.add(
                        cb.isTrue(kitchen.get("acceptingOrders"))
                );
                predicates.add(cb.greaterThan(
                        root.<Integer>get("availablePortions"),
                        0
                ));
            }

            kitchenQuery
                    .select(kitchen.<UUID>get("id"))
                    .where(kitchenPredicates.toArray(Predicate[]::new));

            predicates.add(root.get("kitchenId").in(kitchenQuery));

            if (kitchenId != null) {
                predicates.add(cb.equal(
                        root.get("kitchenId"), kitchenId
                ));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(
                        root.get("category").get("id"), categoryId
                ));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.<BigDecimal>get("price"), minPrice
                ));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.<BigDecimal>get("price"), maxPrice
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}