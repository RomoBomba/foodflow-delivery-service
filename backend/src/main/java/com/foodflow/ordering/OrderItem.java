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
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "dish_id", nullable = false)
    private UUID dishId;

    @Column(name = "dish_name", nullable = false, length = 120)
    private String dishName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;

    @Column(name = "kcal_per_portion", nullable = false)
    private int kcalPerPortion;

    protected OrderItem() { }

    public OrderItem(UUID orderId, UUID dishId, String dishName, BigDecimal unitPrice,
                     int quantity, int weightGrams, int kcalPerPortion) {
        this.orderId = orderId;
        this.dishId = dishId;
        this.dishName = dishName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.weightGrams = weightGrams;
        this.kcalPerPortion = kcalPerPortion;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getDishId() { return dishId; }
    public String getDishName() { return dishName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public int getWeightGrams() { return weightGrams; }
    public int getKcalPerPortion() { return kcalPerPortion; }
}
