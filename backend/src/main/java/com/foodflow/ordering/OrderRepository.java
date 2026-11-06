package com.foodflow.ordering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {
    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
    List<CustomerOrder> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<CustomerOrder> findAllByKitchenIdOrderByCreatedAtDesc(UUID kitchenId);
    Optional<CustomerOrder> findByIdAndCustomerId(UUID id, UUID customerId);
    Optional<CustomerOrder> findByIdAndKitchenId(UUID id, UUID kitchenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id and o.customerId = :ownerId")
    Optional<CustomerOrder> findByIdAndCustomerForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id and o.kitchenId = :kitchenId")
    Optional<CustomerOrder> findByIdAndKitchenForUpdate(@Param("id") UUID id, @Param("kitchenId") UUID kitchenId);
}
