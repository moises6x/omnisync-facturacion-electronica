package org.example.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.example.dto.EmpresaConfigRequest;
import org.example.service.EmpresaConfiguracionService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final ObjectMapper objectMapper;

    private final Validator validator;

    private final EmpresaConfiguracionService
            empresaConfiguracionService;


    // =========================================================
    // CONFIGURACIÓN SUNAT DE MI EMPRESA
    // =========================================================
    //
    // IMPORTANTE:
    //
    // Ya NO se recibe X-Admin-Key.
    //
    // La empresa se obtiene exclusivamente desde
    // el usuario autenticado mediante JWT.
    //
    // Aunque el frontend intente enviar el RUC
    // de otra empresa, el backend no utilizará
    // ese RUC para seleccionar la empresa.
    //
    // =========================================================

    @PostMapping(
            value = "/configurar",
            consumes = "multipart/form-data"
    )
    @PreAuthorize(
            "hasAnyAuthority(" +
                    "'ROLE_EMPRESA_DUENO'," +
                    "'EMPRESA_CONFIGURAR'," +
                    "'PERM_EMPRESA_CONFIGURAR'" +
                    ")"
    )
    public ResponseEntity<?> configurarEmpresa(

            @RequestPart("datos")
            String datosJson,

            @RequestPart(
                    value = "pfxFile",
                    required = false
            )
            MultipartFile pfxFile
    ) {

        EmpresaConfigRequest request =
                convertirYValidar(
                        datosJson
                );


        Map<String, Object> respuesta =
                empresaConfiguracionService
                        .configurarSunat(
                                request,
                                pfxFile
                        );


        return ResponseEntity.ok(
                respuesta
        );
    }


    // =========================================================
    // ROTAR API KEY DE MI EMPRESA
    // =========================================================
    //
    // Tampoco recibe RUC.
    //
    // La empresa sale de la sesión JWT.
    //
    // La nueva API Key se muestra UNA sola vez.
    //
    // =========================================================

    @PostMapping("/rotar-api-key")
    @PreAuthorize(
            "hasAnyAuthority(" +
                    "'ROLE_EMPRESA_DUENO'," +
                    "'EMPRESA_API_KEY_ROTAR'," +
                    "'PERM_EMPRESA_API_KEY_ROTAR'" +
                    ")"
    )
    public ResponseEntity<?> rotarApiKey() {

        Map<String, Object> respuesta =
                empresaConfiguracionService
                        .rotarApiKey();


        return ResponseEntity.ok(
                respuesta
        );
    }


    // =========================================================
    // CONVERTIR + VALIDAR JSON
    // =========================================================

    private EmpresaConfigRequest convertirYValidar(
            String datosJson
    ) {

        final EmpresaConfigRequest request;

        try {

            request =
                    objectMapper.readValue(
                            datosJson,
                            EmpresaConfigRequest.class
                    );

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "El JSON enviado en 'datos' es inválido."
            );
        }


        Set<ConstraintViolation<EmpresaConfigRequest>>
                errores =
                validator.validate(
                        request
                );


        if (!errores.isEmpty()) {

            String mensajeErrores =
                    errores
                            .stream()
                            .map(
                                    error ->
                                            error
                                                    .getPropertyPath()
                                                    +
                                                    ": "
                                                    +
                                                    error
                                                            .getMessage()
                            )
                            .collect(
                                    Collectors.joining(
                                            " | "
                                    )
                            );


            throw new IllegalArgumentException(
                    mensajeErrores
            );
        }


        return request;
    }
}