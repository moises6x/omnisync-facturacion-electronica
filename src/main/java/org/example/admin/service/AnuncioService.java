package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.AnuncioActivoResponse;

import org.example.admin.repository.AnuncioEmpresaRepository;

import org.example.auth.security.SecurityContextService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnuncioService {

    private final AnuncioEmpresaRepository
            anuncioRepository;

    private final SecurityContextService
            securityContextService;


    @Transactional(readOnly = true)
    public List<AnuncioActivoResponse>
    activosMiEmpresa() {

        Long empresaId =
                securityContextService
                        .empresaIdRequerido();
        LocalDateTime ahora =
                LocalDateTime.now();
        return anuncioRepository
                .buscarActivos(
                        empresaId,
                        ahora
                )
                .stream()
                .map(
                        anuncio ->
                                new AnuncioActivoResponse(

                                        anuncio.getId(),

                                        anuncio.getTitulo(),

                                        anuncio.getMensaje(),

                                        anuncio.getTipo(),

                                        anuncio.isPantallaCompleta(),

                                        anuncio.getInicioEn(),

                                        anuncio.getFinEn()
                                )
                )
                .toList();
    }
}
