package com.foodflow.ordering;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) { this.service = service; }

    @GetMapping
    public CartResponse get(Authentication authentication) {
        return service.get(authentication.getName());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse add(
            Authentication authentication,
            @Valid @RequestBody CartItemRequest request
    ) {
        return service.add(authentication.getName(), request);
    }

    @PatchMapping("/items/{dishId}")
    public CartResponse update(
            Authentication authentication,
            @PathVariable UUID dishId,
            @Valid @RequestBody CartQuantityRequest request
    ) {
        return service.updateQuantity(authentication.getName(), dishId, request.quantity());
    }

    @DeleteMapping("/items/{dishId}")
    public CartResponse remove(Authentication authentication, @PathVariable UUID dishId) {
        return service.remove(authentication.getName(), dishId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) {
        service.clear(authentication.getName());
    }
}
