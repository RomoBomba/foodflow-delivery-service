package com.foodflow.catalog.kitchen;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kitchens")
public class KitchenController {

    private final KitchenService service;

    public KitchenController(KitchenService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KitchenResponse apply(
            Authentication authentication,
            @Valid @RequestBody KitchenApplicationRequest request
    ) {
        return service.apply(authentication.getName(), request);
    }

    @GetMapping("/me")
    public KitchenResponse getMine(Authentication authentication) {
        return service.getMine(authentication.getName());
    }

    @PatchMapping("/me/availability")
    public KitchenResponse changeAvailability(
            Authentication authentication,
            @Valid @RequestBody KitchenAvailabilityRequest request
    ) {
        return service.changeAvailability(
                authentication.getName(),
                request.acceptingOrders()
        );
    }
}