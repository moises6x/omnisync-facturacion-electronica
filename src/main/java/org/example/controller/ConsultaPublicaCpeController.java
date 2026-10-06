package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.example.dto.ConsultaPublicaCpeRequest;
import org.example.dto.ConsultaPublicaCpeResponse;
import org.example.service.ConsultaPublicaCpeService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta pública de validez de CPE.
 *
 * El rate limit NO se ejecuta aquí porque ya se aplica de forma
 * centralizada en ApiAuthenticationFilter antes de llegar al
 * controlador. Dejarlo también aquí consumía dos veces la misma
 * ventana de rate limit por cada consulta pública.
 */
@RestController
@RequestMapping("/api/public/cpe")
@RequiredArgsConstructor
public class ConsultaPublicaCpeController {

    private final ConsultaPublicaCpeService service;

    @PostMapping("/consulta")
    public ResponseEntity<ConsultaPublicaCpeResponse> consultar(
            @Valid
            @RequestBody
            ConsultaPublicaCpeRequest request
    ) {

        return ResponseEntity.ok(
                service.consultar(request)
        );
    }
}
