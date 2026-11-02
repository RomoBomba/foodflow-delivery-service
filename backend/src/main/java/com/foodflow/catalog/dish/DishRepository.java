package com.foodflow.catalog.dish;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DishRepository extends JpaRepository<Dish, UUID> {

    @EntityGraph(attributePaths = {"category", "ingredients"})
    List<Dish> findAllByKitchenIdOrderByCreatedAtDesc(UUID kitchenId);
}