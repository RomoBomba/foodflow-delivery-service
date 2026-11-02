package com.foodflow.catalog.dish;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record DishCreateRequest(

        @NotBlank @Size(max = 120)
        String name,

        @NotBlank @Size(max = 1000)
        String description,

        @NotNull
        UUID categoryId,

        @NotEmpty @Size(max = 50)
        Set<@NotNull UUID> ingredientIds,

        @NotNull @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @NotNull @Positive
        Integer weightGrams,

        @NotNull @Min(0)
        Integer kcalPerPortion,

        @NotNull @Min(0)
        Integer availablePortions,

        @Size(max = 2048)
        @Pattern(regexp = "^https?://[^\\s]+$")
        String photoUrl

) {
}