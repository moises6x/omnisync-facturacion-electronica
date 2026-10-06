
package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.repository.ComprobanteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;

/**
 * =============================================================
 * SCHEDULER DE RESUMEN DIARIO
 * =============================================================
 *
 * Evita depender de que una persona recuerde enviar manualmente boletas.
 *
 * Tiene cuatro responsabilidades:
 *
 * 1. Al cierre del día genera RC para los pendientes del día actual.
 * 2. Cada hora recupera pendientes de días anteriores.
 * 3. Reanuda resúmenes CREADO que quedaron guardados tras un reinicio.
 * 4. Consulta tickets sendSummary mediante getStatus hasta obtener CDR.
 *
 * Ninguna operación mezcla empresas: cada grupo se procesa por su RUC.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumenDiarioScheduler {

    private final ComprobanteRepository comprobanteRepository;

    private final ResumenDiarioService resumenDiarioService;

    private final Clock clock;
    private final SunatPlazoService sunatPlazoService;

    @Value("${sunat.resumen-diario.auto.enabled:true}")
    private boolean automaticoHabilitado;

    @Value("${sunat.resumen-diario.max-resumenes-por-ciclo:50}")
    private int maxResumenesPorCiclo;

    @Value("${sunat.resumen-diario.ticket-batch-size:30}")
    private int ticketBatchSize;

    @Value("${sunat.resumen-diario.creados-batch-size:20}")
    private int creadosBatchSize;

    @Value("${sunat.resumen-diario.enviando-timeout-minutes:10}")
    private int enviandoTimeoutMinutes;
    private String ultimoRucProcesado;

    // =========================================================
    // CIERRE DIARIO
    // =========================================================

    /**
     * Procesa pendientes incluyendo el día actual.
     *
     * Por defecto corre a las 23:50 hora Perú. Si existen más de 500
     * documentos, genera varios correlativos hasta el límite configurado
     * por ciclo.
     */
    @Scheduled(
            cron = "${sunat.resumen-diario.generacion-cron:0 50 23 * * *}",
            zone = "${sunat.resumen-diario.zone:America/Lima}"
    )
    public void cierreDiario() {

        if (!automaticoHabilitado) {
            return;
        }

        procesarPendientes(
                true
        );
    }

    // =========================================================
    // RECUPERACIÓN HORARIA
    // =========================================================

    /**
     * Cada hora procesa únicamente fechas anteriores a hoy.
     *
     * Esto permite recuperarse de:
     *
     * - servidor apagado durante el cierre
     * - reinicio
     * - mantenimiento
     * - caída temporal de la aplicación
     */
    @Scheduled(
            cron = "${sunat.resumen-diario.recovery-cron:0 15 * * * *}",
            zone = "${sunat.resumen-diario.zone:America/Lima}"
    )
    public void recuperarPendientesAnteriores() {

        if (!automaticoHabilitado) {
            return;
        }

        procesarPendientes(
                false
        );
    }

    // =========================================================
    // RECUPERAR RESÚMENES CREADOS SIN ENVIAR
    // =========================================================

    @Scheduled(
            fixedDelayString = "${sunat.resumen-diario.creados-delay-ms:120000}",
            initialDelayString = "${sunat.resumen-diario.creados-initial-delay-ms:60000}"
    )
    public void recuperarCreados() {

        if (!automaticoHabilitado) {
            return;
        }

        try {
            resumenDiarioService
                    .recuperarResumenesCreados(
                            creadosBatchSize
                    );

        } catch (Exception e) {
            log.error(
                    "Error recuperando resúmenes CREADO: {}",
                    mensajeSeguro(e)
            );
        }
    }

    // =========================================================
    // CONSULTAR TICKETS
    // =========================================================

    @Scheduled(
            fixedDelayString = "${sunat.resumen-diario.ticket-delay-ms:60000}",
            initialDelayString = "${sunat.resumen-diario.ticket-initial-delay-ms:45000}"
    )
    public void consultarTickets() {

        if (!automaticoHabilitado) {
            return;
        }

        try {
            resumenDiarioService
                    .consultarTicketsPendientes(
                            ticketBatchSize
                    );

        } catch (Exception e) {
            log.error(
                    "Error en scheduler de tickets de Resumen Diario: {}",
                    mensajeSeguro(e)
            );
        }
    }

    // =========================================================
    // ENVIANDO VENCIDO -> INCIERTO
    // =========================================================

    @Scheduled(
            fixedDelayString = "${sunat.resumen-diario.enviando-watch-delay-ms:300000}",
            initialDelayString = "${sunat.resumen-diario.enviando-watch-initial-delay-ms:120000}"
    )
    public void detectarEnviosInciertos() {

        if (!automaticoHabilitado) {
            return;
        }

        try {
            int cambiados =
                    resumenDiarioService
                            .marcarEnviosVencidosComoInciertos(
                                    enviandoTimeoutMinutes
                            );

            if (cambiados > 0) {
                log.warn(
                        "{} Resumen(es) Diario quedaron como ERROR_ENVIO_INCIERTO y requieren revisión manual.",
                        cambiados
                );
            }

        } catch (Exception e) {
            log.error(
                    "Error revisando resúmenes ENVIANDO vencidos: {}",
                    mensajeSeguro(e)
            );
        }
    }

    // =========================================================
    // PROCESAR PENDIENTES
    // =========================================================

    private synchronized void procesarPendientes(
            boolean incluirHoy
    ) {

        LocalDate hoy =
                LocalDate.now(clock);

        int generados = 0;

        List<String> rucs = new java.util.ArrayList<>(comprobanteRepository.buscarRucsConPendientesResumen());
        int previo=rucs.indexOf(ultimoRucProcesado);
        if(previo>=0 && !rucs.isEmpty())java.util.Collections.rotate(rucs,-((previo+1)%rucs.size()));

        for (String ruc : rucs) {
            ultimoRucProcesado=ruc;

            // No cambiar el estado de los vencidos ni ampliar el plazo para enviarlos.
            LocalDateTime desde = sunatPlazoService.fechaMinimaResumen().atStartOfDay();

            while (
                    generados
                            < Math.max(
                            1,
                            maxResumenesPorCiclo
                    )
            ) {

                LocalDateTime fechaHora =
                        comprobanteRepository
                                .buscarFechaMasAntiguaPendienteResumenDesde(
                                        ruc,
                                        desde
                                );

                if (fechaHora == null) {
                    break;
                }

                LocalDate fecha =
                        fechaHora.toLocalDate();

                if (fecha.isAfter(hoy)) {

                    log.error(
                            "La empresa {} tiene PENDIENTE_RESUMEN con fecha futura {}. No se procesa automáticamente.",
                            ruc,
                            fecha
                    );

                    break;
                }

                if (!incluirHoy && !fecha.isBefore(hoy)) {
                    break;
                }

                try {

                    boolean creado =
                            resumenDiarioService
                                    .generarYEnviarAutomatico(
                                            ruc,
                                            fecha
                                    );

                    if (!creado) {
                        desde = fecha.plusDays(1).atStartOfDay();
                        continue;
                    }

                    generados++;

                } catch (Exception e) {

                    log.error(
                            "No se pudo generar Resumen Diario automático para empresa {} fecha {}: {}",
                            ruc,
                            fecha,
                            mensajeSeguro(e)
                    );

                    /*
                     * No insistir sobre la misma fecha fallida; continuar con
                     * fechas posteriores de la misma empresa en este ciclo.
                     */
                    desde = fecha.plusDays(1).atStartOfDay();
                }
            }

            if (generados >= Math.max(1, maxResumenesPorCiclo)) {
                break;
            }
        }

        if (generados > 0) {
            log.info(
                    "Scheduler de Resumen Diario generó {} archivo(s) RC.",
                    generados
            );
        }
    }

    private String mensajeSeguro(
            Throwable e
    ) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.getClass().getSimpleName();
    }
}

