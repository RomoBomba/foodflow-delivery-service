package com.foodflow.catalog.dish;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @PutMapping("/{id}")
    public DishResponse update(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody DishCreateRequest request
    ) {
        return service.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        service.archive(authentication.getName(), id);
    }
}