package org.example.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.auth.security.SecurityContextService;
import org.example.dto.NotaVentaRequest;

import org.example.model.NotaVenta;


import org.example.service.NotaVentaService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notas-venta")
@RequiredArgsConstructor
public class NotaVentaController {

    private final NotaVentaService notaVentaService;

    private final SecurityContextService securityContextService;

    @PostMapping("/emitir")
    @PreAuthorize(
            "hasAuthority('PERM_NOTA_VENTA_EMITIR')"
    )
    public ResponseEntity<?> registrarNotaVenta(

            @Valid
            @RequestBody
            NotaVentaRequest request
    ) {

        NotaVenta guardada =
                notaVentaService
                        .procesarNotaVenta(

                                securityContextService
                                        .rucEmpresaRequerido(),

                                request
                        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "SUCCESS",
                        "message",
                        "Nota de venta registrada correctamente.",
                        "ticketGenerado",
                        guardada.getCodigoTicket(),
                        "monto",
                        guardada.getMontoTotal()
                )
        );
    }
}