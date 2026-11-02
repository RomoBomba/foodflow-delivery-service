package com.foodflow.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.util.Map;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        var encoder = new Pbkdf2PasswordEncoder(
                "",
                16,
                600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm
                        .PBKDF2WithHmacSHA256
        );

        return new DelegatingPasswordEncoder(
                "pbkdf2",
                Map.of("pbkdf2", encoder)
        );
    }
}