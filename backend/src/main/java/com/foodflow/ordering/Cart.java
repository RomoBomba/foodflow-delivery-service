package com.foodflow.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() { }

    public Cart(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getKitchenId() { return kitchenId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setKitchenId(UUID kitchenId) {
        this.kitchenId = kitchenId;
        this.updatedAt = Instant.now();
    }

    public void touch() { this.updatedAt = Instant.now(); }
}
