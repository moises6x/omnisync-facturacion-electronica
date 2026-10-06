package org.example.auth.controller;


import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.auth.dto.*;
import org.example.auth.security.SecurityContextService;
import org.example.auth.service.AuthService;
import org.example.dto.*;



import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final SecurityContextService
            securityContextService;

    @PostMapping("/bootstrap-superadmin")
    public ResponseEntity<?> bootstrap(

            @RequestHeader("X-Admin-Key")
            String adminKey,

            @Valid
            @RequestBody
            BootstrapSuperAdminRequest request
    ) {

        return ResponseEntity.ok(

                authService
                        .bootstrapSuperAdmin(
                                adminKey,
                                request
                        )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(

            @Valid
            @RequestBody
            LoginRequest request
    ) {

        return ResponseEntity.ok(
                authService.login(
                        request
                )
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(

            @Valid
            @RequestBody
            RefreshRequest request
    ) {

        return ResponseEntity.ok(

                authService.refresh(
                        request.refreshToken()
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(

            @Valid
            @RequestBody
            RefreshRequest request
    ) {

        authService.logout(
                request.refreshToken()
        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "SUCCESS",
                        "message",
                        "Sesión cerrada."
                )
        );
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll() {

        authService.logoutAll(
                securityContextService
                        .usuarioId()
        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "SUCCESS",
                        "message",
                        "Todas las sesiones fueron cerradas."
                )
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(

            @Valid
            @RequestBody
            ChangePasswordRequest request
    ) {

        authService.cambiarPassword(

                securityContextService
                        .usuarioId(),

                request
        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "SUCCESS",
                        "message",
                        "Contraseña modificada. Inicie sesión nuevamente."
                )
        );
    }
}
