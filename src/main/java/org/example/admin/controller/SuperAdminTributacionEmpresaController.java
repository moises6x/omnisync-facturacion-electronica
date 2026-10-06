package org.example.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.example.admin.dto.ConfiguracionTributariaEmpresaResponse;
import org.example.admin.dto.ConfigurarTributacionEmpresaRequest;
import org.example.admin.service.ConfiguracionTributariaEmpresaService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración tributaria sensible.
 *
 * Por seguridad, esta primera versión sólo permite modificarla al SUPER_ADMIN.
 */
@RestController
@RequestMapping(
        "/api/v1/admin/empresas/{empresaId}/tributacion"
)
@RequiredArgsConstructor
@PreAuthorize(
        "hasRole('SUPER_ADMIN')"
)
public class SuperAdminTributacionEmpresaController {

    private final ConfiguracionTributariaEmpresaService service;

    @GetMapping
    public ResponseEntity<ConfiguracionTributariaEmpresaResponse> obtener(
            @PathVariable Long empresaId
    ) {

        return ResponseEntity.ok(
                service.obtener(empresaId)
        );
    }

    @PutMapping
    public ResponseEntity<ConfiguracionTributariaEmpresaResponse> actualizar(
            @PathVariable Long empresaId,
            @Valid
            @RequestBody
            ConfigurarTributacionEmpresaRequest request
    ) {

        return ResponseEntity.ok(
                service.actualizar(
                        empresaId,
                        request
                )
        );
    }
}
