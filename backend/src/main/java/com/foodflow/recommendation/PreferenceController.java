package com.foodflow.recommendation;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public PreferenceResponse get(Authentication authentication) {
        return service.get(authentication.getName());
    }

    @PutMapping
    public PreferenceResponse update(
            Authentication authentication,
            @Valid @RequestBody PreferenceRequest request
    ) {
        return service.update(authentication.getName(), request);
    }
}
