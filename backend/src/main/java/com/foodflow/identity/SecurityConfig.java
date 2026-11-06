package com.foodflow.identity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/health",
                                "/api/categories",
                                "/api/ingredients",
                                "/api/dishes",
                                "/api/dishes/**",
                                "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/cancel").hasAnyRole("CLIENT", "KITCHEN")
                        .requestMatchers("/api/preferences", "/api/preferences/**",
                                "/api/recommendations", "/api/recommendations/**",
                                "/api/cart", "/api/cart/**",
                                "/api/orders", "/api/orders/**"
                        ).hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/kitchens").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/kitchens/me").hasAnyRole("CLIENT", "KITCHEN")
                        .requestMatchers("/api/kitchens/me/dishes", "/api/kitchens/me/dishes/**",
                                "/api/kitchens/me/orders", "/api/kitchens/me/orders/**",
                                "/api/kitchens/me/reviews"
                        ).hasRole("KITCHEN")
                        .requestMatchers(HttpMethod.PATCH, "/api/kitchens/me/availability").hasRole("KITCHEN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeFailure(response, 401))
                        .accessDeniedHandler((request, response, exception) -> writeFailure(response, 403))
                )
                .formLogin(login -> login
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> writeFailure(response, 401))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                        .permitAll()
                );

        return http.build();
    }

    private static void writeFailure(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        // Fixed messages only: never return credentials or exception details.
        response.getWriter().write(status == 401
                ? "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Требуется вход с верными учётными данными\"}"
                : "{\"code\":\"ACCESS_DENIED\",\"message\":\"Доступ запрещён или неверен CSRF-токен\"}");
    }

}