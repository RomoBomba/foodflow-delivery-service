package com.foodflow.feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderReviewRequest(
        @NotEmpty @Size(max = 30) List<@NotNull @Valid ReviewItemRequest> items
) { }
