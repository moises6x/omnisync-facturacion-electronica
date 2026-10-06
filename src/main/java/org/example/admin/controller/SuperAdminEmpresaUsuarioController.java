package org.example.admin.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.admin.service.SuperAdminUsuarioService;

import org.example.auth.dto.ActualizarEstadoUsuarioRequest;
import org.example.auth.dto.ActualizarPermisosUsuarioRequest;
import org.example.auth.dto.CrearUsuarioRequest;
import org.example.auth.dto.PasswordTemporalRequest;
import org.example.auth.dto.UsuarioResponse;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/admin/empresas/{empresaId}/usuarios"
)
@RequiredArgsConstructor
@PreAuthorize(
        "hasRole('SUPER_ADMIN')"
)
public class SuperAdminEmpresaUsuarioController {

    private final SuperAdminUsuarioService
            usuarioService;


    // =========================================================
    // LISTAR
    // =========================================================

    @GetMapping
    public ResponseEntity<
            List<UsuarioResponse>
            >
    listar(

            @PathVariable
            Long empresaId
    ) {

        return ResponseEntity.ok(

                usuarioService.listar(
                        empresaId
                )
        );
    }


    // =========================================================
    // CREAR ADMIN / TRABAJADOR
    // =========================================================

    @PostMapping
    public ResponseEntity<UsuarioResponse>
    crear(

            @PathVariable
            Long empresaId,

            @Valid
            @RequestBody
            CrearUsuarioRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService.crear(

                        empresaId,

                        request
                )
        );
    }


    // =========================================================
    // ACTIVAR / DESACTIVAR
    // =========================================================

    @PutMapping("/{usuarioId}/estado")
    public ResponseEntity<UsuarioResponse>
    estado(

            @PathVariable
            Long empresaId,

            @PathVariable
            Long usuarioId,

            @RequestBody
            ActualizarEstadoUsuarioRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService
                        .actualizarEstado(

                                empresaId,

                                usuarioId,

                                request.activo()
                        )
        );
    }


    // =========================================================
    // PERMISOS
    // =========================================================

    @PutMapping("/{usuarioId}/permisos")
    public ResponseEntity<UsuarioResponse>
    permisos(

            @PathVariable
            Long empresaId,

            @PathVariable
            Long usuarioId,

            @RequestBody
            ActualizarPermisosUsuarioRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService
                        .actualizarPermisos(

                                empresaId,

                                usuarioId,

                                request.permisos()
                        )
        );
    }


    // =========================================================
    // RECUPERAR ACCESO / PASSWORD TEMPORAL
    // =========================================================

    @PutMapping("/{usuarioId}/password-temporal")
    public ResponseEntity<UsuarioResponse>
    passwordTemporal(

            @PathVariable
            Long empresaId,

            @PathVariable
            Long usuarioId,

            @Valid
            @RequestBody
            PasswordTemporalRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService
                        .passwordTemporal(

                                empresaId,

                                usuarioId,

                                request.passwordTemporal()
                        )
        );
    }
}