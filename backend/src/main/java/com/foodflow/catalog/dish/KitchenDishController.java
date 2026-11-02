package com.foodflow.catalog.dish;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kitchens/me/dishes")
public class KitchenDishController {

    private final DishService service;

    public KitchenDishController(DishService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DishResponse create(
            Authentication authentication,
            @Valid @RequestBody DishCreateRequest request
    ) {
        return service.create(authentication.getName(), request);
    }

    @GetMapping
    public List<DishResponse> getMyMenu(Authentication authentication) {
        return service.getMyMenu(authentication.getName());
    }
}