package com.foodflow.catalog.kitchen;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchens")
public class Kitchen {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 500)
    private String contacts;

    @Column(name = "delivery_area", nullable = false, length = 200)
    private String deliveryArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KitchenStatus status;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    protected Kitchen() {
    }

    public Kitchen(UUID ownerId, KitchenApplicationRequest request) {
        this.ownerId = ownerId;
        this.name = request.name().strip();
        this.description = request.description().strip();
        this.contacts = request.contacts().strip();
        this.deliveryArea = request.deliveryArea().strip();
        this.status = KitchenStatus.PENDING;
        this.acceptingOrders = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContacts() {
        return contacts;
    }

    public String getDeliveryArea() {
        return deliveryArea;
    }

    public KitchenStatus getStatus() {
        return status;
    }

    public boolean isAcceptingOrders() {
        return acceptingOrders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void approve(UUID moderatorId) {
        this.status = KitchenStatus.APPROVED;
        recordModeration(moderatorId, null);
    }

    void reject(UUID moderatorId, String reason) {
        this.status = KitchenStatus.REJECTED;
        this.acceptingOrders = false;
        recordModeration(moderatorId, reason);
    }

    void block(UUID moderatorId, String reason) {
        this.status = KitchenStatus.BLOCKED;
        this.acceptingOrders = false;
        recordModeration(moderatorId, reason);
    }

    private void recordModeration(UUID moderatorId, String reason) {
        this.moderatedBy = moderatorId;
        this.moderationReason = reason;
        this.moderatedAt = Instant.now();
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }
}