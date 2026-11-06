package com.foodflow.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "kitchen_id", nullable = false)
    private UUID kitchenId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod = "CASH_ON_DELIVERY";

    @Column(length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "reservation_released", nullable = false)
    private boolean reservationReleased;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerOrder() { }

    public CustomerOrder(
            UUID customerId,
            UUID kitchenId,
            String idempotencyKey,
            String requestFingerprint,
            String deliveryAddress,
            String phone,
            String comment,
            BigDecimal totalPrice
    ) {
        this.customerId = customerId;
        this.kitchenId = kitchenId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.deliveryAddress = deliveryAddress;
        this.phone = phone;
        this.comment = comment;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.CREATED;
        this.reservationReleased = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getKitchenId() { return kitchenId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getPhone() { return phone; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getComment() { return comment; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public boolean isReservationReleased() { return reservationReleased; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void changeStatus(OrderStatus status, String reason) {
        this.status = status;
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markReservationReleased() {
        if (reservationReleased) {
            throw new IllegalStateException("Reservation was already released");
        }
        reservationReleased = true;
    }
}
