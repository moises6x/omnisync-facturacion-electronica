package org.example.auth.controller;


import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;


import org.example.auth.dto.CrearPropietarioRequest;
import org.example.auth.dto.UsuarioResponse;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/empresas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminUsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping("/{ruc}/propietario")
    public ResponseEntity<UsuarioResponse>
    crearPropietario(

            @PathVariable
            String ruc,

            @Valid
            @RequestBody
            CrearPropietarioRequest request
    ) {

        return ResponseEntity.ok(

                usuarioService
                        .crearPropietario(
                                ruc,
                                request
                        )
        );
    }
}
