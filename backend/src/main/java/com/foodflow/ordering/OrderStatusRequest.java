package com.foodflow.ordering;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderStatusRequest(
        @NotNull OrderStatus status,
        @Size(max = 500) String reason
) { }
