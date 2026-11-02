package com.foodflow.catalog.dish;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/dishes")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public DishPageResponse findAll(
            @RequestParam(required = false)
            UUID kitchenId,

            @RequestParam(required = false)
            UUID categoryId,

            @RequestParam(required = false)
            @DecimalMin("0")
            BigDecimal minPrice,

            @RequestParam(required = false)
            @DecimalMin("0")
            BigDecimal maxPrice,

            @RequestParam(defaultValue = "false")
            boolean availableOnly,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100)
            int size,

            @RequestParam(defaultValue = "priceAsc")
            String sort
    ) {
        return service.findAll(
                kitchenId,
                categoryId,
                minPrice,
                maxPrice,
                availableOnly,
                page,
                size,
                sort
        );
    }

    @GetMapping("/{id}")
    public CatalogDishResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }
}