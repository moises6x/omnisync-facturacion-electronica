package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.auth.exception.EmpresaSuspendidaException;

import org.example.model.Empresa;
import org.example.model.EstadoEmpresa;

import org.example.repository.EmpresaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmpresaAccessService {

    private final EmpresaRepository
            empresaRepository;

    private final ApiKeyService
            apiKeyService;


    // =========================================================
    // AUTENTICACIÓN POR API KEY
    // =========================================================

    public Empresa validar(
            String ruc,
            String empresaApiKey
    ) {

        Empresa empresa =
                empresaRepository
                        .findByRuc(
                                ruc
                        )
                        .orElseThrow(
                                () ->
                                        new SecurityException(
                                                "Empresa no autorizada."
                                        )
                        );


        /*
         * IMPORTANTE:
         *
         * Primero validamos la API Key.
         *
         * Así alguien que solamente conozca el RUC
         * no puede descubrir si la empresa está
         * activa o suspendida.
         */
        if (
                !apiKeyService.coincide(
                        empresaApiKey,
                        empresa.getApiKeyHash()
                )
        ) {

            throw new SecurityException(
                    "Credenciales de empresa inválidas."
            );
        }


        validarEmpresaActiva(
                empresa
        );


        return empresa;
    }


    // =========================================================
    // VALIDAR ESTADO DE EMPRESA
    // =========================================================

    public void validarEmpresaActiva(
            Empresa empresa
    ) {

        if (
                empresa == null
        ) {

            throw new SecurityException(
                    "Empresa no encontrada."
            );
        }


        if (
                empresa.getEstadoPlataforma()
                        != EstadoEmpresa.ACTIVA
        ) {

            String mensaje =
                    "La empresa se encuentra suspendida temporalmente.";


            if (
                    empresa.getMotivoSuspension()
                            != null
                            &&
                            !empresa
                                    .getMotivoSuspension()
                                    .isBlank()
            ) {

                mensaje +=
                        " Motivo: "
                                +
                                empresa.getMotivoSuspension();
            }


            throw new EmpresaSuspendidaException(
                    mensaje
            );
        }
    }
}