package com.foodflow.catalog.dish;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface DishRepository
        extends JpaRepository<Dish, UUID>,
        JpaSpecificationExecutor<Dish> {

    @EntityGraph(attributePaths = {"category", "ingredients"})
    List<Dish> findAllByKitchenIdOrderByCreatedAtDesc(UUID kitchenId);
}