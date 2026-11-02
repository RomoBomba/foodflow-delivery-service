package com.foodflow.catalog.kitchen;

import com.foodflow.identity.IdentityQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class KitchenModerationService {

    private final KitchenRepository repository;
    private final IdentityQueryService identityQueryService;

    public KitchenModerationService(
            KitchenRepository repository,
            IdentityQueryService identityQueryService
    ) {
        this.repository = repository;
        this.identityQueryService = identityQueryService;
    }

    @Transactional(readOnly = true)
    public List<KitchenResponse> findByStatus(KitchenStatus status) {
        return repository.findAllByStatusOrderByCreatedAtAsc(status)
                .stream()
                .map(KitchenResponse::from)
                .toList();
    }

    @Transactional
    public KitchenResponse approve(UUID kitchenId) {
        Kitchen kitchen = repository.findByIdForUpdate(kitchenId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня не найдена"
                ));

        if (kitchen.getStatus() != KitchenStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Одобрить можно только заявку на проверке"
            );
        }

        identityQueryService.grantKitchenRole(kitchen.getOwnerId());
        kitchen.approve();

        return KitchenResponse.from(kitchen);
    }
}