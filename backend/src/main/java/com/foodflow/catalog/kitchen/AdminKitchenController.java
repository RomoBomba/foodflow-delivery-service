package com.foodflow.catalog.kitchen;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/kitchens")
public class AdminKitchenController {

    private final KitchenModerationService service;

    public AdminKitchenController(KitchenModerationService service) {
        this.service = service;
    }

    @GetMapping
    public List<KitchenResponse> findByStatus(
            @RequestParam(defaultValue = "PENDING")
            KitchenStatus status
    ) {
        return service.findByStatus(status);
    }

    @PostMapping("/{id}/approve")
    public KitchenResponse approve(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return service.approve(id, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    public KitchenResponse reject(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody KitchenModerationRequest request
    ) {
        return service.reject(
                id,
                authentication.getName(),
                request.reason()
        );
    }

    @PostMapping("/{id}/block")
    public KitchenResponse block(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody KitchenModerationRequest request
    ) {
        return service.block(
                id,
                authentication.getName(),
                request.reason()
        );
    }
}