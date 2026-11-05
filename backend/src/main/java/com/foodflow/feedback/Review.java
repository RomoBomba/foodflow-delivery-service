package com.foodflow.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "dish_id", nullable = false)
    private UUID dishId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private boolean reordered;

    @Column(name = "review_text", length = 1000)
    private String reviewText;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Review() { }

    public Review(UUID orderId, UUID orderItemId, UUID userId, UUID dishId,
                  int rating, boolean reordered, String reviewText) {
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.userId = userId;
        this.dishId = dishId;
        this.rating = rating;
        this.reordered = reordered;
        this.reviewText = reviewText;
        this.visible = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getUserId() { return userId; }
    public UUID getDishId() { return dishId; }
    public int getRating() { return rating; }
    public boolean isReordered() { return reordered; }
    public String getReviewText() { return reviewText; }
    public boolean isVisible() { return visible; }
    public String getModerationReason() { return moderationReason; }
    public UUID getModeratedBy() { return moderatedBy; }
    public Instant getModeratedAt() { return moderatedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void hide(UUID moderatorId, String reason) {
        this.visible = false;
        this.moderatedBy = moderatorId;
        this.moderationReason = reason;
        this.moderatedAt = Instant.now();
    }
}
