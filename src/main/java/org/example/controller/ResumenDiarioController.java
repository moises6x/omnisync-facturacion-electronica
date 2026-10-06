
package org.example.controller;

import lombok.RequiredArgsConstructor;

import org.example.auth.security.SecurityContextService;
import org.example.dto.ResumenDiarioResponse;
import org.example.dto.BajaBoletaResponse;

import org.example.service.ResumenDiarioService;
import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * =============================================================
 * RESUMEN DIARIO CONTROLLER
 * =============================================================
 *
 * Endpoints administrativos de la empresa autenticada.
 *
 * El RUC nunca se recibe desde el navegador: siempre se obtiene del
 * SecurityContext para conservar el aislamiento multiempresa.
 */
@RestController
@RequestMapping("/api/v1/resumenes-diarios")
@RequiredArgsConstructor
public class ResumenDiarioController {

    private final ResumenDiarioService resumenDiarioService;

    private final SecurityContextService securityContextService;

    // =========================================================
    // GENERAR MANUALMENTE
    // =========================================================

    @PostMapping
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_EMITIR')"
    )
    public ResponseEntity<ResumenDiarioResponse> generar(

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate fecha
    ) {

        String ruc =
                securityContextService
                        .rucEmpresaRequerido();

        return ResponseEntity
                .accepted()
                .body(
                        resumenDiarioService
                                .generarYEnviar(
                                        ruc,
                                        fecha
                                )
                );
    }

    // =========================================================
    // BAJA DE BOLETA YA INFORMADA Y NO OTORGADA
    // =========================================================

    @PostMapping("/comprobantes/{id}/baja-no-otorgada")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_EMITIR')"
    )
    public ResponseEntity<BajaBoletaResponse> bajaBoletaNoOtorgada(
            @PathVariable Long id
    ) {

        return ResponseEntity.accepted().body(
                resumenDiarioService.solicitarBajaBoletaNoOtorgada(
                        securityContextService.rucEmpresaRequerido(),
                        id
                )
        );
    }

    // =========================================================
    // LISTAR
    // =========================================================

    @GetMapping
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<List<ResumenDiarioResponse>> listar(

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate fecha
    ) {

        return ResponseEntity.ok(
                resumenDiarioService
                        .listar(
                                securityContextService
                                        .rucEmpresaRequerido(),
                                fecha
                        )
        );
    }

    // =========================================================
    // DETALLE / ESTADO
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<ResumenDiarioResponse> obtener(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                resumenDiarioService
                        .obtener(
                                securityContextService
                                        .rucEmpresaRequerido(),
                                id
                        )
        );
    }

    // =========================================================
    // CONSULTAR TICKET SUNAT AHORA
    // =========================================================

    @PostMapping("/{id}/consultar-sunat")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<ResumenDiarioResponse> consultarSunat(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                resumenDiarioService
                        .consultarSunat(
                                securityContextService
                                        .rucEmpresaRequerido(),
                                id
                        )
        );
    }

    // =========================================================
    // REINTENTAR ENVÍO SEGURO
    // =========================================================

    @PostMapping("/{id}/reintentar")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_EMITIR')"
    )
    public ResponseEntity<ResumenDiarioResponse> reintentar(
            @PathVariable Long id
    ) {

        return ResponseEntity
                .accepted()
                .body(
                        resumenDiarioService
                                .reintentarEnvio(
                                        securityContextService
                                                .rucEmpresaRequerido(),
                                        id
                                )
                );
    }

    // =========================================================
    // LIBERAR DOCUMENTOS DE UN RC FALLIDO/RECHAZADO
    // =========================================================

    @PostMapping("/{id}/liberar")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_EMITIR')"
    )
    public ResponseEntity<ResumenDiarioResponse> liberar(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                resumenDiarioService
                        .liberarDocumentos(
                                securityContextService
                                        .rucEmpresaRequerido(),
                                id
                        )
        );
    }
}
