package com.foodflow.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "dish_id", nullable = false)
    private UUID dishId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_at_add", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtAdd;

    protected CartItem() { }

    public CartItem(UUID cartId, UUID dishId, int quantity, BigDecimal priceAtAdd) {
        this.cartId = cartId;
        this.dishId = dishId;
        this.quantity = quantity;
        this.priceAtAdd = priceAtAdd;
    }

    public UUID getId() { return id; }
    public UUID getCartId() { return cartId; }
    public UUID getDishId() { return dishId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPriceAtAdd() { return priceAtAdd; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
