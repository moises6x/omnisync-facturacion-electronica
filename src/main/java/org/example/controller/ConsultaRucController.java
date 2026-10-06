package org.example.controller;

import lombok.RequiredArgsConstructor;

import org.example.dto.ConsultaRucResponse;

import org.example.service.PadronRucService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/consultas")
@RequiredArgsConstructor
public class ConsultaRucController {

    private final PadronRucService padronRucService;

    @GetMapping("/ruc/{rucCliente}")
    @PreAuthorize(
            "hasAuthority('PERM_CONSULTA_RUC')"
    )
    public ResponseEntity<ConsultaRucResponse>
    consultarRuc(

            @PathVariable
            String rucCliente
    ) {

        return ResponseEntity.ok(
                padronRucService.consultar(
                        rucCliente
                )
        );
    }
}