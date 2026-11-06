package com.foodflow.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewModerationRequest(
        @NotBlank @Size(max = 500) String reason
) {
    public ReviewModerationRequest {
        if (reason != null) reason = reason.strip();
    }
}
