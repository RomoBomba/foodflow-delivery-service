package com.foodflow.identity;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        UserRole role
) {
}