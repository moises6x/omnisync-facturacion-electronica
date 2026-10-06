package org.example.auth.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.auth.dto.*;


import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me() {

        return ResponseEntity.ok(
                usuarioService.me()
        );
    }

    @GetMapping
    @PreAuthorize(
            "hasAuthority('PERM_USUARIOS_GESTIONAR')"
    )
    public ResponseEntity<?> listar() {

        return ResponseEntity.ok(
                usuarioService.listarMiEmpresa()
        );
    }

    @PostMapping
    @PreAuthorize(
            "hasAuthority('PERM_USUARIOS_GESTIONAR')"
    )
    public ResponseEntity<UsuarioResponse> crear(

            @Valid
            @RequestBody
            CrearUsuarioRequest request
    ) {

        return ResponseEntity.ok(
                usuarioService.crear(
                        request
                )
        );
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize(
            "hasAuthority('PERM_USUARIOS_GESTIONAR')"
    )
    public ResponseEntity<UsuarioResponse> estado(

            @PathVariable
            Long id,

            @RequestBody
            ActualizarEstadoUsuarioRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService
                        .actualizarEstado(

                                id,

                                request.activo()
                        )
        );
    }

    @PutMapping("/{id}/permisos")
    @PreAuthorize(
            "hasAuthority('PERM_USUARIOS_GESTIONAR')"
    )
    public ResponseEntity<UsuarioResponse> permisos(

            @PathVariable
            Long id,

            @RequestBody
            ActualizarPermisosUsuarioRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService
                        .actualizarPermisos(

                                id,

                                request.permisos()
                        )
        );
    }

    @PutMapping("/{id}/password-temporal")
    @PreAuthorize(
            "hasAuthority('PERM_USUARIOS_GESTIONAR')"
    )
    public ResponseEntity<?> passwordTemporal(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            PasswordTemporalRequest request
    ) {

        usuarioService.passwordTemporal(

                id,

                request.passwordTemporal()
        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "SUCCESS",
                        "message",
                        "Contraseña temporal asignada. El usuario deberá cambiarla al iniciar sesión."
                )
        );
    }
}