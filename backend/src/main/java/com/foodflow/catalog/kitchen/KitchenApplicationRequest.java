package com.foodflow.catalog.kitchen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KitchenApplicationRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Size(max = 1000)
        String description,

        @NotBlank
        @Size(max = 500)
        String contacts,

        @NotBlank
        @Size(max = 200)
        String deliveryArea

) {
}