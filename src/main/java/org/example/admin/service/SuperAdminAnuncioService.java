package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.AnuncioAdminResponse;
import org.example.admin.dto.CrearActualizarAnuncioRequest;

import org.example.admin.model.AnuncioEmpresa;

import org.example.admin.repository.AnuncioEmpresaRepository;

import org.example.auth.security.SecurityContextService;

import org.example.model.Empresa;

import org.example.repository.EmpresaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuperAdminAnuncioService {

    private final AnuncioEmpresaRepository
            anuncioRepository;

    private final EmpresaRepository
            empresaRepository;

    private final SecurityContextService
            securityContextService;


    // =========================================================
    // LISTAR TODOS LOS ANUNCIOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<AnuncioAdminResponse> listar() {

        return anuncioRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(
                        this::convertir
                )
                .toList();
    }


    // =========================================================
    // OBTENER ANUNCIO
    // =========================================================
    @Transactional(readOnly = true)
    public AnuncioAdminResponse obtener(
            Long id
    ) {

        AnuncioEmpresa anuncio =
                buscarAnuncio(
                        id
                );


        return convertir(
                anuncio
        );
    }


    // =========================================================
    // CREAR
    // =========================================================

    @Transactional
    public AnuncioAdminResponse crear(
            CrearActualizarAnuncioRequest request
    ) {

        validarFechas(
                request
        );


        Empresa empresa =
                buscarEmpresaOpcional(
                        request.empresaId()
                );


        AnuncioEmpresa anuncio =
                AnuncioEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        /*
                         * NULL porque es un anuncio manual.
                         *
                         * codigoSistema queda reservado para
                         * anuncios automáticos del backend.
                         */
                        .codigoSistema(
                                null
                        )

                        .titulo(
                                limpiarRequerido(
                                        request.titulo()
                                )
                        )

                        .mensaje(
                                limpiarRequerido(
                                        request.mensaje()
                                )
                        )

                        .tipo(
                                request.tipo()
                        )

                        .inicioEn(
                                request.inicioEn()
                        )

                        .finEn(
                                request.finEn()
                        )

                        .pantallaCompleta(
                                request.pantallaCompleta()
                        )

                        .activo(
                                true
                        )

                        .creadoPorUsuarioId(

                                securityContextService
                                        .usuarioId()
                        )

                        .build();


        anuncio =
                anuncioRepository
                        .saveAndFlush(
                                anuncio
                        );


        return convertir(
                anuncio
        );
    }


    // =========================================================
    // EDITAR
    // =========================================================

    @Transactional
    public AnuncioAdminResponse actualizar(

            Long id,

            CrearActualizarAnuncioRequest request
    ) {

        validarFechas(
                request
        );


        AnuncioEmpresa anuncio =
                buscarAnuncio(
                        id
                );


        /*
         * No permitimos modificar anuncios automáticos
         * del sistema desde este endpoint.
         *
         * Esos anuncios los administra la lógica de
         * suscripciones.
         */
        if (
                anuncio.getCodigoSistema() != null
                        &&
                        !anuncio
                                .getCodigoSistema()
                                .isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Este anuncio fue generado automáticamente por el sistema y no puede editarse manualmente."
            );
        }


        Empresa empresa =
                buscarEmpresaOpcional(
                        request.empresaId()
                );


        anuncio.setEmpresa(
                empresa
        );


        anuncio.setTitulo(
                limpiarRequerido(
                        request.titulo()
                )
        );


        anuncio.setMensaje(
                limpiarRequerido(
                        request.mensaje()
                )
        );


        anuncio.setTipo(
                request.tipo()
        );


        anuncio.setInicioEn(
                request.inicioEn()
        );


        anuncio.setFinEn(
                request.finEn()
        );


        anuncio.setPantallaCompleta(
                request.pantallaCompleta()
        );


        anuncio =
                anuncioRepository
                        .saveAndFlush(
                                anuncio
                        );


        return convertir(
                anuncio
        );
    }


    // =========================================================
    // ACTIVAR / DESACTIVAR
    // =========================================================

    @Transactional
    public AnuncioAdminResponse cambiarEstado(

            Long id,

            boolean activo
    ) {

        AnuncioEmpresa anuncio =
                buscarAnuncio(
                        id
                );


        /*
         * Los anuncios automáticos pueden desactivarse,
         * pero no editarse.
         *
         * Esto permite que SUPER_ADMIN pueda ocultar
         * manualmente uno si existe algún problema.
         */
        anuncio.setActivo(
                activo
        );


        anuncio =
                anuncioRepository
                        .saveAndFlush(
                                anuncio
                        );


        return convertir(
                anuncio
        );
    }


    // =========================================================
    // VALIDAR FECHAS
    // =========================================================

    private void validarFechas(
            CrearActualizarAnuncioRequest request
    ) {

        if (
                request.finEn() != null
                        &&
                        !request
                                .finEn()
                                .isAfter(
                                        request.inicioEn()
                                )
        ) {

            throw new IllegalArgumentException(
                    "La fecha de finalización debe ser posterior a la fecha de inicio."
            );
        }
    }


    // =========================================================
    // EMPRESA OPCIONAL
    // =========================================================

    private Empresa buscarEmpresaOpcional(
            Long empresaId
    ) {

        /*
         * NULL = anuncio global.
         */
        if (
                empresaId == null
        ) {

            return null;
        }


        return empresaRepository
                .findById(
                        empresaId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Empresa no encontrada."
                                )
                );
    }


    // =========================================================
    // BUSCAR ANUNCIO
    // =========================================================

    private AnuncioEmpresa buscarAnuncio(
            Long id
    ) {

        return anuncioRepository
                .findById(
                        id
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Anuncio no encontrado."
                                )
                );
    }


    // =========================================================
    // RESPONSE
    // =========================================================

    private AnuncioAdminResponse convertir(
            AnuncioEmpresa anuncio
    ) {

        Empresa empresa =
                anuncio.getEmpresa();


        return new AnuncioAdminResponse(

                anuncio.getId(),

                empresa != null
                        ? empresa.getId()
                        : null,

                empresa != null
                        ? empresa.getRuc()
                        : null,

                empresa != null
                        ? empresa.getRazonSocial()
                        : null,

                empresa == null,

                anuncio.getTitulo(),

                anuncio.getMensaje(),

                anuncio.getTipo(),

                anuncio.getInicioEn(),

                anuncio.getFinEn(),

                anuncio.isPantallaCompleta(),

                anuncio.isActivo(),

                anuncio.getCreadoPorUsuarioId(),

                anuncio.getCreatedAt(),

                anuncio.getUpdatedAt()
        );
    }


    // =========================================================
    // STRING
    // =========================================================

    private String limpiarRequerido(
            String valor
    ) {

        if (
                valor == null
                        ||
                        valor.trim().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "El texto no puede estar vacío."
            );
        }


        return valor.trim();
    }
}
