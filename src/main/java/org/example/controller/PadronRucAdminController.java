package org.example.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.dto.*;

import org.example.service.PadronRucActualizacionService;
import org.example.service.PadronRucImportService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/padron-ruc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PadronRucAdminController {

    private final PadronRucImportService importService;

    private final PadronRucActualizacionService actualizacionService;

    @PostMapping("/importar")
    public ResponseEntity<ImportacionPadronResponse>
    importar() {

        return ResponseEntity
                .accepted()
                .body(
                        importService.iniciar()
                );
    }

    @GetMapping("/importar/estado")
    public ResponseEntity<ImportacionPadronResponse>
    estado() {

        return ResponseEntity.ok(
                importService.estado()
        );
    }

    @GetMapping("/actualizacion/estado")
    public ResponseEntity<ActualizacionPadronResponse>
    estadoActualizacion() {

        return ResponseEntity.ok(
                actualizacionService.estado()
        );
    }

    @PostMapping("/actualizacion/verificar")
    public ResponseEntity<ActualizacionPadronResponse>
    verificarActualizacion() {

        return ResponseEntity.ok(
                actualizacionService.verificar()
        );
    }

    @PostMapping("/actualizacion/ejecutar")
    public ResponseEntity<ActualizacionPadronResponse>
    ejecutarActualizacion() {

        return ResponseEntity
                .accepted()
                .body(
                        actualizacionService.ejecutar()
                );
    }

    @PostMapping("/actualizacion/activar")
    public ResponseEntity<ActualizacionPadronResponse>
    activar() {

        return ResponseEntity.ok(
                actualizacionService.activar()
        );
    }

    @PostMapping("/actualizacion/desactivar")
    public ResponseEntity<ActualizacionPadronResponse>
    desactivar() {

        return ResponseEntity.ok(
                actualizacionService.desactivar()
        );
    }

    @GetMapping("/actualizacion/programacion")
    public ResponseEntity<ProgramacionPadronResponse>
    programacion() {

        return ResponseEntity.ok(
                actualizacionService.programacion()
        );
    }

    @PutMapping("/actualizacion/programacion")
    public ResponseEntity<ProgramacionPadronResponse>
    actualizarProgramacion(

            @Valid
            @RequestBody
            ProgramacionPadronRequest request
    ) {

        return ResponseEntity.ok(

                actualizacionService
                        .actualizarProgramacion(
                                request
                        )
        );
    }
}