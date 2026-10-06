package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.auth.security.SecurityContextService;

import org.example.dto.EmpresaConfigRequest;

import org.example.model.Empresa;

import org.example.repository.EmpresaRepository;
import org.example.util.RucUtils;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class EmpresaConfiguracionService {

    private static final long MAX_PFX_BYTES =
            2L * 1024L * 1024L;


    private final EmpresaRepository
            empresaRepository;

    private final SecurityContextService
            securityContextService;

    private final EmpresaAccessService
            empresaAccessService;

    private final CryptoService
            cryptoService;

    private final XmlSignerService
            xmlSignerService;

    private final ApiKeyService
            apiKeyService;


    // =========================================================
    // CONFIGURACIÓN SUNAT
    // =========================================================

    @Transactional
    public Map<String, Object> configurarSunat(

            EmpresaConfigRequest request,

            MultipartFile pfxFile
    ) {

        // =====================================================
        // 1. EMPRESA DESDE JWT
        // =====================================================

        Empresa empresa =
                obtenerEmpresaSesion();


        empresaAccessService
                .validarEmpresaActiva(
                        empresa
                );


        String rucEmpresa =
                empresa.getRuc();


        if (
                !RucUtils.esValido(
                        rucEmpresa
                )
        ) {

            throw new IllegalStateException(
                    "El RUC asociado a la empresa es inválido."
            );
        }


        // =====================================================
        // 2. NORMALIZAR USUARIO SOL
        // =====================================================
        //
        // SunatDirectSoapClient posteriormente construye:
        //
        // RUC + USUARIO_SOL
        //
        // Por eso almacenamos únicamente el código de usuario.
        //
        // BETA:
        //
        // almacenar:
        //
        // MODDATOS
        //
        // NO:
        //
        // 20100130204MODDATOS
        //
        // =====================================================

        String usuarioSol =
                normalizarUsuarioSol(

                        rucEmpresa,

                        request.getUsuarioSol()
                );


        // =====================================================
        // 3. CIFRAR CREDENCIALES
        // =====================================================

        empresa.setUsuarioSol(

                cryptoService
                        .cifrarUsuarioSol(
                                rucEmpresa,
                                usuarioSol
                        )
        );


        empresa.setClaveSol(

                cryptoService
                        .cifrarClaveSol(

                                rucEmpresa,

                                request
                                        .getClaveSol()
                        )
        );


        // =====================================================
        // 4. CERTIFICADO
        // =====================================================

        boolean certificadoNuevo =

                pfxFile != null
                        &&
                        !pfxFile.isEmpty();


        boolean certificadoExistente =

                empresa.getPfxBytes() != null
                        &&
                        empresa
                                .getPfxBytes()
                                .length
                                >
                                0;


        if (
                !certificadoNuevo
                        &&
                        !certificadoExistente
        ) {

            throw new IllegalArgumentException(
                    "Debe adjuntar un certificado digital P12/PFX."
            );
        }


        if (certificadoNuevo) {

            procesarCertificado(

                    empresa,

                    request,

                    pfxFile,

                    rucEmpresa
            );
        }


        // =====================================================
        // 5. GUARDAR
        // =====================================================

        Empresa guardada =
                empresaRepository
                        .saveAndFlush(
                                empresa
                        );


        // =====================================================
        // 6. RESPUESTA
        // =====================================================

        Map<String, Object> respuesta =
                new LinkedHashMap<>();


        respuesta.put(
                "status",
                "SUCCESS"
        );


        respuesta.put(
                "message",
                "Configuración SUNAT guardada correctamente."
        );


        respuesta.put(
                "ruc",
                guardada.getRuc()
        );


        respuesta.put(
                "tenantId",
                guardada.getTenantId()
        );


        respuesta.put(
                "configuracionSunat",
                guardada.tieneConfiguracionSunat()
        );


        respuesta.put(
                "certificadoTitular",
                guardada.getCertificadoTitular()
        );


        respuesta.put(
                "certificadoEmisor",
                guardada.getCertificadoEmisor()
        );


        respuesta.put(
                "certificadoVigenteDesde",
                guardada.getCertificadoVigenteDesde()
        );


        respuesta.put(
                "certificadoVigenteHasta",
                guardada.getCertificadoVigenteHasta()
        );


        return respuesta;
    }


    // =========================================================
    // ROTAR API KEY
    // =========================================================

    @Transactional
    public Map<String, Object> rotarApiKey() {

        Empresa empresa =
                obtenerEmpresaSesion();


        empresaAccessService
                .validarEmpresaActiva(
                        empresa
                );


        String nuevaApiKey =
                apiKeyService
                        .generarApiKey();


        empresa.setApiKeyHash(

                apiKeyService
                        .hash(
                                nuevaApiKey
                        )
        );


        empresaRepository
                .saveAndFlush(
                        empresa
                );


        Map<String, Object> respuesta =
                new LinkedHashMap<>();


        respuesta.put(
                "status",
                "SUCCESS"
        );


        respuesta.put(
                "message",
                "API key rotada correctamente."
        );


        respuesta.put(
                "ruc",
                empresa.getRuc()
        );


        respuesta.put(
                "empresaApiKey",
                nuevaApiKey
        );


        respuesta.put(
                "advertencia",
                "Guarde esta API key ahora. No se volverá a mostrar."
        );


        return respuesta;
    }


    // =========================================================
    // EMPRESA AUTENTICADA
    // =========================================================

    private Empresa obtenerEmpresaSesion() {

        Long empresaId =
                securityContextService
                        .empresaIdRequerido();


        String rucSesion =
                securityContextService
                        .rucEmpresaRequerido();


        Empresa empresa =
                empresaRepository
                        .bloquearPorId(
                                empresaId
                        )
                        .orElseThrow(
                                () ->
                                        new SecurityException(
                                                "La empresa asociada a la sesión no existe."
                                        )
                        );


        if (
                !rucSesion.equals(
                        empresa.getRuc()
                )
        ) {

            throw new SecurityException(
                    "La sesión de empresa es inconsistente."
            );
        }


        return empresa;
    }


    // =========================================================
    // CERTIFICADO
    // =========================================================

    private void procesarCertificado(

            Empresa empresa,

            EmpresaConfigRequest request,

            MultipartFile pfxFile,

            String rucEmpresa
    ) {

        if (
                pfxFile.getSize()
                        >
                        MAX_PFX_BYTES
        ) {

            throw new IllegalArgumentException(
                    "El certificado P12/PFX supera el tamaño máximo permitido de 2 MB."
            );
        }


        if (
                request.getPfxPassword() == null
                        ||
                        request
                                .getPfxPassword()
                                .isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Debe indicar la contraseña del certificado P12/PFX."
            );
        }


        byte[] pfxPlano =
                null;


        try {

            pfxPlano =
                    pfxFile.getBytes();


            // =================================================
            // VALIDACIÓN SEGÚN AMBIENTE
            // =================================================
            //
            // BETA:
            //
            // permite certificado autofirmado.
            //
            //
            // PRODUCCIÓN:
            //
            // XmlSignerService comprobará:
            //
            // - vigencia
            // - clave privada
            // - RSA
            // - RUC dentro del OU
            // - no autofirmado
            // - cadena de confianza
            //
            // =================================================

            XmlSignerService.CertificadoInfo info =

                    xmlSignerService
                            .validarPfx(

                                    pfxPlano,

                                    request
                                            .getPfxPassword(),

                                    rucEmpresa
                            );


            // =================================================
            // CIFRAR PFX
            // =================================================

            empresa.setPfxBytes(

                    cryptoService
                            .cifrarPfx(

                                    rucEmpresa,

                                    pfxPlano
                            )
            );


            // =================================================
            // CIFRAR PASSWORD
            // =================================================

            empresa.setPfxPassword(

                    cryptoService
                            .cifrarPfxPassword(

                                    rucEmpresa,

                                    request
                                            .getPfxPassword()
                            )
            );


            // =================================================
            // METADATOS PÚBLICOS
            // =================================================

            empresa.setCertificadoTitular(
                    info.titular()
            );


            empresa.setCertificadoEmisor(
                    info.emisor()
            );


            empresa.setCertificadoVigenteDesde(
                    info.vigenteDesde()
            );


            empresa.setCertificadoVigenteHasta(
                    info.vigenteHasta()
            );


        } catch (IllegalArgumentException e) {

            throw e;


        } catch (Exception e) {

            throw new IllegalStateException(
                    "No se pudo procesar el certificado P12/PFX.",
                    e
            );


        } finally {

            if (pfxPlano != null) {

                Arrays.fill(
                        pfxPlano,
                        (byte) 0
                );
            }
        }
    }


    // =========================================================
    // NORMALIZAR USUARIO SOL
    // =========================================================

    private String normalizarUsuarioSol(

            String ruc,

            String usuarioSol
    ) {

        if (
                usuarioSol == null
                        ||
                        usuarioSol.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "El usuario SOL es obligatorio."
            );
        }


        String normalizado =
                usuarioSol.trim();


        /*
         * Compatibilidad con formularios o registros antiguos
         * que hayan enviado:
         *
         * RUC + usuario
         *
         * Ejemplo:
         *
         * 20100130204MODDATOS
         *
         * Lo transformamos a:
         *
         * MODDATOS
         */

        if (
                normalizado.startsWith(ruc)
                        &&
                        normalizado.length()
                                >
                                ruc.length()
        ) {

            normalizado =
                    normalizado.substring(
                            ruc.length()
                    );
        }


        return normalizado;
    }
}
