package com.foodflow.ordering;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(
        @NotBlank @Size(min = 10, max = 500) String deliveryAddress,
        @NotBlank @Size(max = 25) String phone,
        @Size(max = 500) String comment,
        @NotEmpty @Size(max = 30) List<@NotNull @Valid OrderLineRequest> items,
        Boolean acceptCurrentPrices,
        @Pattern(regexp = "[a-f0-9]{64}") String confirmedPriceFingerprint
) {
    public OrderCreateRequest {
        acceptCurrentPrices = Boolean.TRUE.equals(acceptCurrentPrices);
    }
}
