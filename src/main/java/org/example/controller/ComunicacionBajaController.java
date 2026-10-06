package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.auth.security.SecurityContextService;
import org.example.dto.ComunicacionBajaRequest;
import org.example.dto.ComunicacionBajaResponse;
import org.example.service.ComunicacionBajaService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/comunicaciones-baja") @RequiredArgsConstructor
public class ComunicacionBajaController {
    private final ComunicacionBajaService service;
    private final SecurityContextService security;
    @PostMapping @PreAuthorize("hasAuthority('PERM_FACTURACION_EMITIR')")
    public ResponseEntity<ComunicacionBajaResponse> crear(@Valid @RequestBody ComunicacionBajaRequest req){ return ResponseEntity.accepted().body(service.crearYEnviar(security.rucEmpresaRequerido(),req)); }
    @GetMapping @PreAuthorize("hasAuthority('PERM_FACTURACION_CONSULTAR')")
    public List<ComunicacionBajaResponse> listar(){ return service.listar(security.rucEmpresaRequerido()); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('PERM_FACTURACION_CONSULTAR')")
    public ComunicacionBajaResponse obtener(@PathVariable Long id){ return service.obtener(security.rucEmpresaRequerido(),id); }
    @PostMapping("/{id}/consultar-sunat") @PreAuthorize("hasAuthority('PERM_FACTURACION_CONSULTAR')")
    public ComunicacionBajaResponse consultar(@PathVariable Long id){ return service.consultarSunat(security.rucEmpresaRequerido(),id); }
    @PostMapping("/{id}/reintentar-envio") @PreAuthorize("hasAuthority('PERM_FACTURACION_EMITIR')")
    public ResponseEntity<ComunicacionBajaResponse> reintentar(@PathVariable Long id){
        return ResponseEntity.accepted().body(service.reintentarEnvio(security.rucEmpresaRequerido(),id));
    }
}
