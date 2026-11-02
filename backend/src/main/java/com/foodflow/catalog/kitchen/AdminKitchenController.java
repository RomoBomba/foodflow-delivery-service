package com.foodflow.catalog.kitchen;

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
    public KitchenResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }
}