package com.foodflow.ordering;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartItemRequest(
        @NotNull UUID dishId,
        @Min(1) @Max(99) int quantity,
        boolean confirmReplaceKitchen
) { }
