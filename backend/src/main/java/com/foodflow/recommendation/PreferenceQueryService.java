package com.foodflow.recommendation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PreferenceQueryService {
    private final PreferenceRepository repository;
    public PreferenceQueryService(PreferenceRepository repository) { this.repository = repository; }
    public Set<UUID> excludedIngredients(UUID userId) {
        return repository.findById(userId).map(value -> Set.copyOf(value.getExcludedIngredientIds())).orElse(Set.of());
    }
}
