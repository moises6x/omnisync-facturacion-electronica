package org.example.admin.controller;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.AnuncioActivoResponse;

import org.example.admin.service.AnuncioService;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/anuncios"
)
@RequiredArgsConstructor
public class AnuncioController {

    private final AnuncioService
            anuncioService;

    @GetMapping("/activos")
    public ResponseEntity<
            List<AnuncioActivoResponse>
            >
    activos() {

        return ResponseEntity.ok(

                anuncioService
                        .activosMiEmpresa()
        );
    }
}