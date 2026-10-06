package org.example.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.auth.security.SecurityContextService;
import org.example.dto.*;


import org.example.service.ComprobantePdfService;
import org.example.service.FacturacionService;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/facturacion")
@RequiredArgsConstructor
public class FacturacionController {

    private final FacturacionService facturacionService;

    private final SecurityContextService securityContextService;
    private final ComprobantePdfService comprobantePdfService;
    @PostMapping("/emitir")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_EMITIR')"
    )
    public ResponseEntity<ResultadoEmision> emitir(

            @RequestHeader("X-Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            ComprobanteRequest request
    ) {

        String ruc =
                securityContextService
                        .rucEmpresaRequerido();

        return ResponseEntity
                .accepted()
                .body(
                        facturacionService
                                .procesarComprobante(

                                        ruc,

                                        idempotencyKey,

                                        request
                                )
                );
    }

    @PostMapping("/nota-credito")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_EMITIR')"
    )
    public ResponseEntity<ResultadoEmision> notaCredito(

            @RequestHeader("X-Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            NotaCreditoRequest request
    ) {

        String ruc =
                securityContextService
                        .rucEmpresaRequerido();

        return ResponseEntity
                .accepted()
                .body(
                        facturacionService
                                .procesarNotaCredito(

                                        ruc,

                                        idempotencyKey,

                                        request
                                )
                );
    }

    @GetMapping("/{id}/estado")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<ResultadoEmision>
    consultarEstado(

            @PathVariable
            Long id
    ) {

        return ResponseEntity.ok(

                facturacionService
                        .consultarEstado(

                                securityContextService
                                        .rucEmpresaRequerido(),

                                id
                        )
        );
    }

    @GetMapping("/comprobantes")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<
            PaginaResponse<ComprobanteListadoResponse>
            >
    listarComprobantes(

            @RequestParam(required = false)
            String tipo,

            @RequestParam(required = false)
            String estado,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate desde,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate hasta,

            @RequestParam(defaultValue = "0")
            int pagina,

            @RequestParam(defaultValue = "50")
            int tamanio
    ) {

        return ResponseEntity.ok(

                facturacionService
                        .listarComprobantes(

                                securityContextService
                                        .rucEmpresaRequerido(),

                                tipo,

                                estado,

                                desde,

                                hasta,

                                pagina,

                                tamanio
                        )
        );
    }
// =========================================================
// PDF DEL COMPROBANTE
// =========================================================

    @GetMapping(
            value = "/{id}/pdf",
            produces = "application/pdf"
    )
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<byte[]> pdf(

            @PathVariable
            Long id
    ) {

        String ruc =
                securityContextService
                        .rucEmpresaRequerido();


        byte[] pdf =
                comprobantePdfService
                        .generar(
                                ruc,
                                id
                        );


        return ResponseEntity
                .ok()

                .header(
                        "Content-Disposition",
                        "attachment; filename=\"comprobante-"
                                +
                                id
                                +
                                ".pdf\""
                )

                .header(
                        "Cache-Control",
                        "no-store"
                )

                .body(
                        pdf
                );
    }
    @GetMapping("/resumen")
    @PreAuthorize(
            "hasAuthority('PERM_FACTURACION_CONSULTAR')"
    )
    public ResponseEntity<ResumenFacturacionResponse>
    resumen(

            @RequestParam(defaultValue = "MES")
            String periodo,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate desde,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate hasta
    ) {

        return ResponseEntity.ok(

                facturacionService
                        .obtenerResumen(

                                securityContextService
                                        .rucEmpresaRequerido(),

                                periodo,

                                desde,

                                hasta
                        )
        );
    }
}