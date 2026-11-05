package com.foodflow.ordering;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return service.create(authentication.getName(), idempotencyKey, request);
    }

    @GetMapping
    public List<OrderResponse> myOrders(Authentication authentication) {
        return service.getMyOrders(authentication.getName());
    }

    @GetMapping("/{id}")
    public OrderResponse get(Authentication authentication, @PathVariable UUID id) {
        return service.getMine(authentication.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication authentication, @PathVariable UUID id) {
        return service.cancelByCustomer(authentication.getName(), id);
    }
}
