package com.foodflow.identity;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@Profile("local")
public class LocalAdminInitializer implements CommandLineRunner {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public LocalAdminInitializer(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String email = environment.getProperty("ADMIN_EMAIL");
        String password = environment.getProperty("ADMIN_PASSWORD");

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return;
        }

        String normalizedEmail =
                email.strip().toLowerCase(Locale.ROOT);

        var existing = repository.findByEmail(normalizedEmail);

        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                throw new IllegalStateException(
                        "ADMIN_EMAIL занят пользователем без роли ADMIN"
                );
            }
            return;
        }

        if (password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD должен содержать 12–128 символов"
            );
        }

        repository.save(User.createAdmin(
                normalizedEmail,
                passwordEncoder.encode(password)
        ));
    }
}