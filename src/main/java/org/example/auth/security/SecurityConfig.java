package org.example.auth.security;



import com.fasterxml.jackson.databind.ObjectMapper;


import org.example.auth.repository.UsuarioRepository;
import org.example.service.EmpresaAccessService;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {

        /*
         * Coste 12.
         *
         * Las contraseñas nunca son reversibles.
         */
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public ApiAuthenticationFilter
    apiAuthenticationFilter(

            JwtService jwtService,

            UsuarioRepository usuarioRepository,

            EmpresaAccessService empresaAccessService,

            RateLimitService rateLimitService,

            ObjectMapper objectMapper
    ) {

        return new ApiAuthenticationFilter(

                jwtService,

                usuarioRepository,

                empresaAccessService,

                rateLimitService,

                objectMapper
        );
    }

    @Bean
    public CorsConfigurationSource
    corsConfigurationSource(

            @Value("${security.cors.allowed-origins:http://localhost:4200,http://localhost:5173}")
            String allowedOrigins
    ) {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(

                Arrays
                        .stream(
                                allowedOrigins.split(",")
                        )
                        .map(String::trim)
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .toList()
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Admin-Key",
                        "X-Empresa-RUC",
                        "X-Empresa-Key",
                        "X-Idempotency-Key"
                )
        );

        /*
         * No usamos cookies de sesión.
         */
        configuration.setAllowCredentials(
                false
        );

        configuration.setMaxAge(
                3600L
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    @Bean
    public SecurityFilterChain
    securityFilterChain(

            HttpSecurity http,

            ApiAuthenticationFilter authenticationFilter

    ) throws Exception {

        http

                .csrf(
                        csrf ->
                                csrf.disable()
                )

                .cors(
                        Customizer.withDefaults()
                )

                .httpBasic(
                        basic ->
                                basic.disable()
                )

                .formLogin(
                        form ->
                                form.disable()
                )

                .logout(
                        logout ->
                                logout.disable()
                )

                .requestCache(
                        cache ->
                                cache.disable()
                )

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .authorizeHttpRequests(
                        auth ->
                                auth

                                        .requestMatchers(
                                                HttpMethod.OPTIONS,
                                                "/**"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh",
                                                "/api/v1/auth/logout",
                                                "/api/v1/auth/bootstrap-superadmin"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                "/error"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/public/cpe/consulta"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health"
                                        )
                                        .permitAll()

                                        .anyRequest()
                                        .authenticated()
                )

                .headers(
                        headers ->
                                headers

                                        .contentTypeOptions(
                                                Customizer.withDefaults()
                                        )

                                        .frameOptions(
                                                frame ->
                                                        frame.deny()
                                        )

                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .NO_REFERRER
                                                        )
                                        )

                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts
                                                                .includeSubDomains(
                                                                        true
                                                                )
                                                                .maxAgeInSeconds(
                                                                        31536000
                                                                )
                                        )

                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'none'; "
                                                                        + "frame-ancestors 'none'; "
                                                                        + "base-uri 'none'"
                                                        )
                                        )
                )

                .addFilterBefore(
                        authenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}