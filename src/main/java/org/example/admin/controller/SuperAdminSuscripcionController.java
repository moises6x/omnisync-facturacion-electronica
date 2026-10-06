package org.example.admin.controller;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.ProcesamientoSuscripcionesResponse;

import org.example.admin.service.SuscripcionAutomaticaService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/v1/admin/suscripciones"
)
@RequiredArgsConstructor
@PreAuthorize(
        "hasRole('SUPER_ADMIN')"
)
public class SuperAdminSuscripcionController {

    private final SuscripcionAutomaticaService
            suscripcionService;


    @PostMapping("/procesar")
    public ResponseEntity<
            ProcesamientoSuscripcionesResponse
            >
    procesar() {

        return ResponseEntity.ok(

                suscripcionService
                        .procesar()
        );
    }
}