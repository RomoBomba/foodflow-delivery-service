package com.foodflow.identity;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class IdentityQueryService {

    private final UserRepository repository;

    public IdentityQueryService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UUID getUserId(String email) {
        return repository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Пользователь не найден"
                ));
    }

    @Transactional
    public void grantKitchenRole(UUID userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Владелец кухни не найден"
                ));

        if (user.getRole() != UserRole.CLIENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Владелец должен иметь роль клиента"
            );
        }

        user.grantKitchenRole();
    }
}