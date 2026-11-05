package com.foodflow.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewItemRequest(
        @NotNull UUID orderItemId,
        @Min(1) @Max(5) int rating,
        @NotNull Boolean reordered,
        @Size(max = 1000) String text
) { }
