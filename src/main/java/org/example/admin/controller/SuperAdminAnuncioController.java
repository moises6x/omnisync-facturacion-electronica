package org.example.admin.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.ActualizarEstadoAnuncioRequest;
import org.example.admin.dto.AnuncioAdminResponse;
import org.example.admin.dto.CrearActualizarAnuncioRequest;

import org.example.admin.service.SuperAdminAnuncioService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/admin/anuncios"
)
@RequiredArgsConstructor
@PreAuthorize(
        "hasRole('SUPER_ADMIN')"
)
public class SuperAdminAnuncioController {

    private final SuperAdminAnuncioService
            anuncioService;


    // =========================================================
    // LISTAR
    // =========================================================

    @GetMapping
    public ResponseEntity<
            List<AnuncioAdminResponse>
            >
    listar() {

        return ResponseEntity.ok(
                anuncioService.listar()
        );
    }


    // =========================================================
    // DETALLE
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<
            AnuncioAdminResponse
            >
    obtener(

            @PathVariable
            Long id
    ) {

        return ResponseEntity.ok(
                anuncioService.obtener(
                        id
                )
        );
    }


    // =========================================================
    // CREAR
    // =========================================================

    @PostMapping
    public ResponseEntity<
            AnuncioAdminResponse
            >
    crear(

            @Valid
            @RequestBody
            CrearActualizarAnuncioRequest request
    ) {

        return ResponseEntity.ok(

                anuncioService
                        .crear(
                                request
                        )
        );
    }


    // =========================================================
    // EDITAR
    // =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<
            AnuncioAdminResponse
            >
    actualizar(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            CrearActualizarAnuncioRequest request
    ) {

        return ResponseEntity.ok(

                anuncioService
                        .actualizar(
                                id,
                                request
                        )
        );
    }


    // =========================================================
    // ACTIVAR / DESACTIVAR
    // =========================================================

    @PutMapping("/{id}/estado")
    public ResponseEntity<
            AnuncioAdminResponse
            >
    cambiarEstado(

            @PathVariable
            Long id,

            @RequestBody
            ActualizarEstadoAnuncioRequest request
    ) {

        return ResponseEntity.ok(

                anuncioService
                        .cambiarEstado(

                                id,

                                request.activo()
                        )
        );
    }
}
