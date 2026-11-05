package com.foodflow.ordering;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/kitchens/me/orders")
public class KitchenOrderController {
    private final OrderService service;

    public KitchenOrderController(OrderService service) { this.service = service; }

    @GetMapping
    public List<OrderResponse> kitchenOrders(Authentication authentication) {
        return service.getKitchenOrders(authentication.getName());
    }

    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusRequest request
    ) {
        return service.changeStatusByKitchen(authentication.getName(), id, request);
    }
}
