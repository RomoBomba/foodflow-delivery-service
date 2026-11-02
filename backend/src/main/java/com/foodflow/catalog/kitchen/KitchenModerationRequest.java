package com.foodflow.catalog.kitchen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KitchenModerationRequest(

        @NotBlank
        @Size(max = 500)
        String reason

) {
    public KitchenModerationRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}