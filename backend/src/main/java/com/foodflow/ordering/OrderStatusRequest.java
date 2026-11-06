package com.foodflow.ordering;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record OrderStatusRequest(
        @NotNull OrderStatus status,
        @NotNull @Min(0) Long expectedVersion,
        @Size(max = 500) String reason
) { }
