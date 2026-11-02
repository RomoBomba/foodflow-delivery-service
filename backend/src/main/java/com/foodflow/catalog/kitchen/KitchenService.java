package com.foodflow.catalog.kitchen;

import com.foodflow.identity.IdentityQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class KitchenService {

    private final KitchenRepository repository;
    private final IdentityQueryService identityQueryService;

    public KitchenService(
            KitchenRepository repository,
            IdentityQueryService identityQueryService
    ) {
        this.repository = repository;
        this.identityQueryService = identityQueryService;
    }

    @Transactional
    public KitchenResponse apply(
            String email,
            KitchenApplicationRequest request
    ) {
        UUID ownerId = identityQueryService.getUserId(email);

        if (repository.existsByOwnerId(ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Заявка кухни уже существует"
            );
        }

        Kitchen kitchen = new Kitchen(ownerId, request);

        return KitchenResponse.from(
                repository.saveAndFlush(kitchen)
        );
    }

    @Transactional(readOnly = true)
    public KitchenResponse getMine(String email) {
        UUID ownerId = identityQueryService.getUserId(email);

        return repository.findByOwnerId(ownerId)
                .map(KitchenResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Заявка кухни не найдена"
                ));
    }
}