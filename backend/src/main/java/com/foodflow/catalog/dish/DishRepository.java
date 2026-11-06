package com.foodflow.catalog.dish;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DishRepository
        extends JpaRepository<Dish, UUID>,
        JpaSpecificationExecutor<Dish> {

    @EntityGraph(attributePaths = {"category", "ingredients"})
    List<Dish> findAllByKitchenIdOrderByCreatedAtDesc(UUID kitchenId);

    @EntityGraph(attributePaths = {"category", "ingredients"})
    List<Dish> findAllByArchivedFalse();

    // A scalar lookup must not populate the persistence context before the row lock.
    @Query("select d.kitchenId from Dish d where d.id = :id")
    Optional<UUID> findKitchenIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dish d where d.id = :id")
    Optional<Dish> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from Dish d
            where d.id = :id and d.kitchenId = :kitchenId
            """)
    Optional<Dish> findOwnedByIdForUpdate(
            @Param("id") UUID id,
            @Param("kitchenId") UUID kitchenId
    );
}
