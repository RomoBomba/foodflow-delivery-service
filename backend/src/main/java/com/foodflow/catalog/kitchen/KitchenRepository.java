package com.foodflow.catalog.kitchen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KitchenRepository
        extends JpaRepository<Kitchen, UUID> {

    boolean existsByOwnerId(UUID ownerId);

    Optional<Kitchen> findByOwnerId(UUID ownerId);
}