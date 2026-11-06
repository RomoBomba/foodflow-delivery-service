package com.foodflow.recommendation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) { this.service = service; }

    @GetMapping
    public RecommendationResponse get(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.recommend(authentication.getName(), page, size);
    }

    @GetMapping("/dishes/{dishId}/compatibility")
    public DishCompatibilityResponse compatibility(
            Authentication authentication,
            @PathVariable UUID dishId
    ) {
        return service.checkCompatibility(authentication.getName(), dishId);
    }
}
