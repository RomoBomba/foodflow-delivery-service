package com.foodflow.ordering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findAllByCartIdOrderByDishIdAsc(UUID cartId);
    Optional<CartItem> findByCartIdAndDishId(UUID cartId, UUID dishId);
    void deleteByCartId(UUID cartId);
    void deleteByCartIdAndDishId(UUID cartId, UUID dishId);
}
