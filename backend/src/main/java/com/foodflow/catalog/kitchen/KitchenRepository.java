package com.foodflow.catalog.kitchen;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenRepository
        extends JpaRepository<Kitchen, UUID> {

    boolean existsByOwnerId(UUID ownerId);

    Optional<Kitchen> findByOwnerId(UUID ownerId);

    List<Kitchen> findAllByStatusOrderByCreatedAtAsc(
            KitchenStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from Kitchen k where k.id = :id")
    Optional<Kitchen> findByIdForUpdate(@Param("id") UUID id);
}