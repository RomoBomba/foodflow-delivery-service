package com.foodflow.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health",
                                "/api/categories",
                                "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health",
                                "/api/categories",
                                "/api/ingredients",
                                "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/kitchens"
                        ).hasRole("CLIENT")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/kitchens/me"
                        ).hasAnyRole("CLIENT", "KITCHEN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/kitchens/me/dishes",
                                "/api/kitchens/me/dishes/**"
                        ).hasRole("KITCHEN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        response.setStatus(401)
                        )
                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        response.setStatus(403)
                        )
                )
                .formLogin(login -> login
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(
                                (request, response, authentication) ->
                                        response.setStatus(204)
                        )
                        .failureHandler(
                                (request, response, exception) ->
                                        response.setStatus(401)
                        )
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(
                                (request, response, authentication) ->
                                        response.setStatus(204)
                        )
                        .permitAll()
                );

        return http.build();
    }
}