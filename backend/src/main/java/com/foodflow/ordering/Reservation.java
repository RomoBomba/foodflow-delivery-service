package com.foodflow.ordering;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;
    @Column(nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    protected Reservation() { }

    public Reservation(OrderItem item) {
        orderItemId = item.getId();
        quantity = item.getQuantity();
        status = ReservationStatus.ACTIVE;
    }

    public UUID getOrderItemId() { return orderItemId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }

    public void finish(ReservationStatus target) {
        if (status != ReservationStatus.ACTIVE || target == ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be released or consumed");
        }
        status = target;
    }
}
