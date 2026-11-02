package com.foodflow.identity;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository repository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegistrationRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email уже используется"
            );
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password())
        );

        User saved = repository.saveAndFlush(user);

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole()
        );
    }
}