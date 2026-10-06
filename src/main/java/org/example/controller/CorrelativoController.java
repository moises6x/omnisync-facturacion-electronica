package org.example.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.example.auth.security.SecurityContextService;
import org.example.dto.CorrelativoConfigRequest;



import org.example.service.CorrelativoService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/correlativos")
@RequiredArgsConstructor
public class CorrelativoController {

    private final CorrelativoService correlativoService;

    private final SecurityContextService securityContextService;

    @PostMapping("/configurar-inicial")
    @PreAuthorize(
            "hasAuthority('PERM_CORRELATIVOS_CONFIGURAR')"
    )
    public ResponseEntity<?> configurarInicial(

            @Valid
            @RequestBody
            CorrelativoConfigRequest request
    ) {

        int siguiente =
                correlativoService
                        .configurarInicial(

                                securityContextService
                                        .rucEmpresaRequerido(),

                                request.getTipoComprobante(),

                                request.getSerie(),

                                request.getUltimoNumero()
                        );

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "SUCCESS",
                        "serie",
                        request.getSerie(),
                        "ultimoNumeroRegistrado",
                        request.getUltimoNumero(),
                        "siguienteNumero",
                        siguiente
                )
        );
    }
}