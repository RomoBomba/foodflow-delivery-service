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
    public KitchenResponse approve(
            UUID kitchenId,
            String moderatorEmail
    ) {
        Kitchen kitchen = getLocked(kitchenId);
        requireStatus(kitchen, KitchenStatus.PENDING);

        UUID moderatorId =
                identityQueryService.getUserId(moderatorEmail);

        identityQueryService.grantKitchenRole(kitchen.getOwnerId());
        kitchen.approve(moderatorId);

        return KitchenResponse.from(kitchen);
    }

    @Transactional
    public KitchenResponse reject(
            UUID kitchenId,
            String moderatorEmail,
            String reason
    ) {
        Kitchen kitchen = getLocked(kitchenId);
        requireStatus(kitchen, KitchenStatus.PENDING);

        UUID moderatorId =
                identityQueryService.getUserId(moderatorEmail);

        kitchen.reject(moderatorId, reason);

        return KitchenResponse.from(kitchen);
    }

    @Transactional
    public KitchenResponse block(
            UUID kitchenId,
            String moderatorEmail,
            String reason
    ) {
        Kitchen kitchen = getLocked(kitchenId);
        requireStatus(kitchen, KitchenStatus.APPROVED);

        UUID moderatorId =
                identityQueryService.getUserId(moderatorEmail);

        kitchen.block(moderatorId, reason);

        return KitchenResponse.from(kitchen);
    }

    private Kitchen getLocked(UUID kitchenId) {
        return repository.findByIdForUpdate(kitchenId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Кухня не найдена"
                ));
    }

    private void requireStatus(
            Kitchen kitchen,
            KitchenStatus expected
    ) {
        if (kitchen.getStatus() != expected) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Действие недоступно для текущего статуса кухни"
            );
        }
    }
}