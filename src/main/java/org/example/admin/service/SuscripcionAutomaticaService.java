package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.ProcesamientoSuscripcionesResponse;

import org.example.admin.model.AnuncioEmpresa;
import org.example.admin.model.SuscripcionEmpresa;
import org.example.admin.model.TipoAnuncio;

import org.example.admin.repository.AnuncioEmpresaRepository;
import org.example.admin.repository.SuscripcionEmpresaRepository;

import org.example.model.Empresa;
import org.example.model.EstadoEmpresa;

import org.example.repository.EmpresaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuscripcionAutomaticaService {

    private static final DateTimeFormatter
            CODIGO_FECHA =

            DateTimeFormatter.ofPattern(
                    "yyyyMMddHHmmss"
            );


    private static final DateTimeFormatter
            FECHA_VISIBLE =

            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy HH:mm"
            );


    private final SuscripcionEmpresaRepository
            suscripcionRepository;

    private final AnuncioEmpresaRepository
            anuncioRepository;

    private final EmpresaRepository
            empresaRepository;


    // =========================================================
    // PROCESAR SUSCRIPCIONES
    // =========================================================

    @Transactional
    public ProcesamientoSuscripcionesResponse
    procesar() {

        LocalDateTime ahora =
                LocalDateTime.now();


        /*
         * Solamente necesitamos revisar empresas cuyo
         * vencimiento ocurra dentro de los próximos
         * tres días o ya haya ocurrido.
         */
        LocalDateTime hasta =
                ahora.plusHours(
                        72
                );


        List<Long>
                suscripciones =

                suscripcionRepository
                        .buscarEmpresasParaRevision(

                                EstadoEmpresa.ACTIVA,

                                hasta
                        );


        int avisosGenerados =
                0;


        int empresasSuspendidas =
                0;


        for (
                Long empresaId :
                suscripciones
        ) {

            // Mismo lock distribuido que renovar/suspender/configurar; recotejar tras esperar.
            Empresa empresa = empresaRepository.bloquearPorId(empresaId).orElseThrow();
            SuscripcionEmpresa suscripcion = suscripcionRepository.bloquearPorEmpresa(empresaId).orElseThrow();
            if(empresa.getEstadoPlataforma()!=EstadoEmpresa.ACTIVA || suscripcion.getVenceEn().isAfter(hasta)) continue;


            if (
                    empresa == null
            ) {

                continue;
            }


            LocalDateTime vence =
                    suscripcion.getVenceEn();


            LocalDateTime limite =
                    suscripcion
                            .limiteConGracia();


            // =================================================
            // YA SUPERÓ VENCIMIENTO + GRACIA
            // =================================================

            if (
                    !ahora.isBefore(
                            limite
                    )
            ) {

                /*
                 * Si la suspensión automática está apagada,
                 * solamente dejamos aviso.
                 */
                if (
                        !suscripcion
                                .isSuspensionAutomatica()
                ) {

                    avisosGenerados +=
                            crearAvisoVencido(
                                    empresa,
                                    suscripcion,
                                    ahora
                            );

                    continue;
                }


                empresa.setEstadoPlataforma(
                        EstadoEmpresa.SUSPENDIDA
                );


                empresa.setSuspendidaEn(
                        ahora
                );


                empresa.setMotivoSuspension(

                        "Suscripción vencida. "
                                +
                                "Renueve el servicio para continuar utilizando la plataforma."
                );


                empresaRepository.save(
                        empresa
                );


// =========================================================
// YA NO DEBEMOS MOSTRAR:
// - faltan 3 días
// - falta 1 día
// - período de gracia
//
// La empresa ya está suspendida.
// =========================================================

                anuncioRepository
                        .desactivarAvisosPreviosSuscripcion(
                                empresa.getId(),
                                ahora
                        );


                avisosGenerados +=
                        crearAvisoSuspension(
                                empresa,
                                suscripcion,
                                ahora
                        );


                empresasSuspendidas++;

                continue;
            }


            // =================================================
            // YA VENCIÓ, PERO ESTÁ EN DÍAS DE GRACIA
            // =================================================

            if (
                    !ahora.isBefore(
                            vence
                    )
            ) {

                avisosGenerados +=
                        crearAvisoGracia(

                                empresa,

                                suscripcion,

                                ahora,

                                limite
                        );


                continue;
            }


            // =================================================
            // TODAVÍA NO VENCIÓ
            // =================================================

            Duration restante =
                    Duration.between(
                            ahora,
                            vence
                    );


            long minutos =
                    restante.toMinutes();


            // =================================================
            // MENOS DE 24 HORAS
            // =================================================

            if (
                    minutos <=
                            24L * 60L
            ) {

                avisosGenerados +=
                        crearAvisoUnDia(

                                empresa,

                                suscripcion,

                                ahora
                        );


                continue;
            }


            // =================================================
            // MENOS DE 3 DÍAS
            // =================================================

            if (
                    minutos <=
                            72L * 60L
            ) {

                avisosGenerados +=
                        crearAvisoTresDias(

                                empresa,

                                suscripcion,

                                ahora
                        );
            }
        }


        empresaRepository.flush();


        return new ProcesamientoSuscripcionesResponse(

                ahora,

                suscripciones.size(),

                avisosGenerados,

                empresasSuspendidas
        );
    }


    // =========================================================
    // AVISO 3 DÍAS
    // =========================================================

    private int crearAvisoTresDias(

            Empresa empresa,

            SuscripcionEmpresa suscripcion,

            LocalDateTime ahora
    ) {

        String codigo =
                codigo(

                        "SUSCRIPCION_3_DIAS",

                        empresa,

                        suscripcion
                );


        if (
                anuncioRepository
                        .existeCodigoActual(
                                codigo
                        )
        ) {

            return 0;
        }


        AnuncioEmpresa anuncio =
                AnuncioEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        .codigoSistema(
                                codigo
                        )

                        .titulo(
                                "Tu suscripción vencerá pronto"
                        )

                        .mensaje(

                                "La suscripción de "
                                        +
                                        empresa.getRazonSocial()
                                        +
                                        " vence el "
                                        +
                                        fecha(
                                                suscripcion
                                                        .getVenceEn()
                                        )
                                        +
                                        ". Renueva antes del vencimiento para evitar interrupciones."
                        )

                        .tipo(
                                TipoAnuncio.INFO
                        )

                        .inicioEn(
                                ahora
                        )

                        .finEn(
                                suscripcion
                                        .getVenceEn()
                        )

                        .pantallaCompleta(
                                false
                        )

                        .activo(
                                true
                        )

                        .build();


        anuncioRepository.save(
                anuncio
        );


        return 1;
    }


    // =========================================================
    // AVISO 1 DÍA
    // =========================================================

    private int crearAvisoUnDia(

            Empresa empresa,

            SuscripcionEmpresa suscripcion,

            LocalDateTime ahora
    ) {

        String codigo =
                codigo(

                        "SUSCRIPCION_1_DIA",

                        empresa,

                        suscripcion
                );


        if (
                anuncioRepository
                        .existeCodigoActual(
                                codigo
                        )
        ) {

            return 0;
        }


        AnuncioEmpresa anuncio =
                AnuncioEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        .codigoSistema(
                                codigo
                        )

                        .titulo(
                                "Tu suscripción vence en menos de 24 horas"
                        )

                        .mensaje(

                                "El servicio de "
                                        +
                                        empresa.getRazonSocial()
                                        +
                                        " vence el "
                                        +
                                        fecha(
                                                suscripcion
                                                        .getVenceEn()
                                        )
                                        +
                                        ". Realiza la renovación para mantener el servicio activo."
                        )

                        .tipo(
                                TipoAnuncio.INFO
                        )

                        .inicioEn(
                                ahora
                        )

                        .finEn(
                                suscripcion
                                        .getVenceEn()
                        )

                        /*
                         * Este aviso sí puede ser mucho
                         * más visible en Angular.
                         */
                        .pantallaCompleta(
                                true
                        )

                        .activo(
                                true
                        )

                        .build();


        anuncioRepository.save(
                anuncio
        );


        return 1;
    }


    // =========================================================
    // PERÍODO DE GRACIA
    // =========================================================

    private int crearAvisoGracia(

            Empresa empresa,

            SuscripcionEmpresa suscripcion,

            LocalDateTime ahora,

            LocalDateTime limite
    ) {

        String codigo =
                codigo(

                        "SUSCRIPCION_GRACIA",

                        empresa,

                        suscripcion
                );


        if (
                anuncioRepository
                        .existeCodigoActual(
                                codigo
                        )
        ) {

            return 0;
        }


        AnuncioEmpresa anuncio =
                AnuncioEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        .codigoSistema(
                                codigo
                        )

                        .titulo(
                                "Suscripción vencida"
                        )

                        .mensaje(

                                "La suscripción venció el "
                                        +
                                        fecha(
                                                suscripcion
                                                        .getVenceEn()
                                        )
                                        +
                                        ". El período de gracia finaliza el "
                                        +
                                        fecha(
                                                limite
                                        )
                                        +
                                        "."
                        )

                        .tipo(
                                TipoAnuncio.INFO
                        )

                        .inicioEn(
                                ahora
                        )

                        .finEn(
                                limite
                        )

                        .pantallaCompleta(
                                true
                        )

                        .activo(
                                true
                        )

                        .build();


        anuncioRepository.save(
                anuncio
        );


        return 1;
    }


    // =========================================================
    // VENCIDA PERO SIN SUSPENSIÓN AUTOMÁTICA
    // =========================================================

    private int crearAvisoVencido(

            Empresa empresa,

            SuscripcionEmpresa suscripcion,

            LocalDateTime ahora
    ) {

        String codigo =
                codigo(

                        "SUSCRIPCION_VENCIDA_AVISO",

                        empresa,

                        suscripcion
                );


        if (
                anuncioRepository
                        .existeCodigoActual(
                                codigo
                        )
        ) {

            return 0;
        }


        anuncioRepository.save(

                AnuncioEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        .codigoSistema(
                                codigo
                        )

                        .titulo(
                                "Suscripción vencida"
                        )

                        .mensaje(

                                "La suscripción venció el "
                                        +
                                        fecha(
                                                suscripcion
                                                        .getVenceEn()
                                        )
                                        +
                                        ". La suspensión automática se encuentra desactivada."
                        )

                        .tipo(
                                TipoAnuncio.INFO
                        )

                        .inicioEn(
                                ahora
                        )

                        .pantallaCompleta(
                                true
                        )

                        .activo(
                                true
                        )

                        .build()
        );


        return 1;
    }


    // =========================================================
    // AVISO DE SUSPENSIÓN
    // =========================================================

    private int crearAvisoSuspension(

            Empresa empresa,

            SuscripcionEmpresa suscripcion,

            LocalDateTime ahora
    ) {

        String codigo =
                codigo(

                        "SUSCRIPCION_SUSPENDIDA",

                        empresa,

                        suscripcion
                );


        if (
                anuncioRepository
                        .existeCodigoActual(
                                codigo
                        )
        ) {

            return 0;
        }


        AnuncioEmpresa anuncio =
                AnuncioEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        .codigoSistema(
                                codigo
                        )

                        .titulo(
                                "Servicio temporalmente suspendido"
                        )

                        .mensaje(

                                "La suscripción de "
                                        +
                                        empresa.getRazonSocial()
                                        +
                                        " venció el "
                                        +
                                        fecha(
                                                suscripcion
                                                        .getVenceEn()
                                        )
                                        +
                                        ". Comuníquese con administración para realizar la renovación."
                        )

                        .tipo(
                                TipoAnuncio.INFO
                        )

                        .inicioEn(
                                ahora
                        )

                        /*
                         * No tiene fin porque permanece
                         * hasta que se renueve/reactive.
                         */
                        .finEn(
                                null
                        )

                        .pantallaCompleta(
                                true
                        )

                        .activo(
                                true
                        )

                        .build();


        anuncioRepository.save(
                anuncio
        );


        return 1;
    }


    // =========================================================
    // CÓDIGO ÚNICO
    // =========================================================

    private String codigo(

            String tipo,

            Empresa empresa,

            SuscripcionEmpresa suscripcion
    ) {

        return tipo
                +
                "_EMPRESA_"
                +
                empresa.getId()
                +
                "_VENCE_"
                +
                suscripcion
                        .getVenceEn()
                        .format(
                                CODIGO_FECHA
                        );
    }


    private String fecha(
            LocalDateTime fecha
    ) {

        return fecha.format(
                FECHA_VISIBLE
        );
    }
}
