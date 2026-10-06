package org.example.admin.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.*;

import org.example.admin.service.SuperAdminEmpresaService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/admin/empresas"
)
@RequiredArgsConstructor
@PreAuthorize(
        "hasRole('SUPER_ADMIN')"
)
public class SuperAdminEmpresaController {

    private final SuperAdminEmpresaService
            empresaService;


    // =========================================================
    // LISTAR
    // =========================================================

    @GetMapping
    public ResponseEntity<
            List<EmpresaAdminResponse>
            >
    listar() {

        return ResponseEntity.ok(
                empresaService.listar()
        );
    }


    // =========================================================
    // DETALLE
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<
            EmpresaAdminResponse
            >
    obtener(

            @PathVariable
            Long id
    ) {

        return ResponseEntity.ok(
                empresaService.obtener(
                        id
                )
        );
    }


    // =========================================================
    // CREAR
    // =========================================================

    @PostMapping
    public ResponseEntity<
            EmpresaAdminResponse
            >
    crear(

            @Valid
            @RequestBody
            CrearEmpresaRequest request
    ) {

        return ResponseEntity.ok(
                empresaService.crear(
                        request
                )
        );
    }


    // =========================================================
    // SUSPENDER
    // =========================================================

    @PutMapping("/{id}/suspender")
    public ResponseEntity<
            EmpresaAdminResponse
            >
    suspender(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            SuspenderEmpresaRequest request
    ) {

        return ResponseEntity.ok(
                empresaService.suspender(
                        id,
                        request
                )
        );
    }


    // =========================================================
    // REACTIVAR
    // =========================================================

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<
            EmpresaAdminResponse
            >
    reactivar(

            @PathVariable
            Long id
    ) {

        return ResponseEntity.ok(
                empresaService.reactivar(
                        id
                )
        );
    }


    // =========================================================
    // RENOVAR
    // =========================================================

    @PostMapping("/{id}/renovar")
    public ResponseEntity<
            EmpresaAdminResponse
            >
    renovar(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            RenovarSuscripcionRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey
    ) {

        return ResponseEntity.ok(
                empresaService.renovar(
                        id,
                        request, idempotencyKey
                )
        );
    }
}
