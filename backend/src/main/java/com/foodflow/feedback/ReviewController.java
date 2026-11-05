package com.foodflow.feedback;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) { this.service = service; }

    @PostMapping("/api/orders/{orderId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReviewResponse> create(
            Authentication authentication,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderReviewRequest request
    ) {
        return service.create(authentication.getName(), orderId, request);
    }

    @GetMapping("/api/dishes/{dishId}/reviews")
    public List<ReviewResponse> publicReviews(@PathVariable UUID dishId) {
        return service.findVisibleByDish(dishId);
    }

    @GetMapping("/api/kitchens/me/reviews")
    public List<ReviewResponse> kitchenReviews(Authentication authentication) {
        return service.findKitchenReviews(authentication.getName());
    }

    @GetMapping("/api/admin/reviews")
    public List<ReviewResponse> adminList(@RequestParam(required = false) Boolean visible) {
        return service.findForAdmin(visible);
    }

    @PostMapping("/api/admin/reviews/{reviewId}/hide")
    public ReviewResponse hide(
            Authentication authentication,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewModerationRequest request
    ) {
        return service.hide(authentication.getName(), reviewId, request.reason());
    }
}
