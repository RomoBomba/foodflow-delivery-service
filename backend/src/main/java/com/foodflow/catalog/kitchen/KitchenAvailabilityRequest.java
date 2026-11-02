package com.foodflow.catalog.kitchen;

import jakarta.validation.constraints.NotNull;

public record KitchenAvailabilityRequest(
        @NotNull Boolean acceptingOrders
) {
}