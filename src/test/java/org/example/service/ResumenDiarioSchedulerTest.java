package org.example.service;

import org.example.repository.ComprobanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.Mockito.*;


/**
 * =============================================================
 * RESUMEN DIARIO SCHEDULER TEST
 * =============================================================
 *
 * Este archivo prueba la automatización del Resumen Diario.
 *
 * NO usa:
 *
 * - SUNAT real
 * - MySQL real
 * - frontend
 * - Postman
 *
 * Se prueba directamente la lógica del scheduler mediante Mockito.
 *
 *
 * =============================================================
 * ESCENARIOS PROBADOS
 * =============================================================
 *
 * 1. Scheduler apagado:
 *    no ejecuta ninguna tarea.
 *
 * 2. Cierre diario:
 *    puede procesar documentos del día actual.
 *
 * 3. Recuperación:
 *    procesa documentos de días anteriores.
 *
 * 4. Recuperación:
 *    NO procesa documentos del día actual.
 *
 * 5. Fecha futura:
 *    NO se procesa automáticamente.
 *
 * 6. Límite de resúmenes por ciclo:
 *    evita generar indefinidamente.
 *
 * 7. Recuperación de RC en estado CREADO.
 *
 * 8. Consulta automática de tickets.
 *
 * 9. Detección de envíos ENVIANDO vencidos.
 *
 * 10. Error durante generación:
 *     no deja al scheduler atrapado en un bucle.
 *
 */
@ExtendWith(MockitoExtension.class)
class ResumenDiarioSchedulerTest {


    // =========================================================
    // CONSTANTES
    // =========================================================

    private static final String RUC =
            "20100130204";


    // =========================================================
    // MOCKS
    // =========================================================

    @Mock
    private ComprobanteRepository comprobanteRepository;

    @Mock
    private ResumenDiarioService resumenDiarioService;


    // =========================================================
    // SCHEDULER BAJO PRUEBA
    // =========================================================

    private ResumenDiarioScheduler scheduler;


    // =========================================================
    // RELOJ FIJO
    // =========================================================
    //
    // 2026-09-19 14:00:00 aproximadamente
    // en America/Lima.
    //
    // Al mantener la fecha fija, los tests no dependen
    // del día real en que los ejecutemos.
    //
    // =========================================================

    private final Clock clock =
            Clock.fixed(
                    Instant.parse(
                            "2026-09-19T19:00:00Z"
                    ),
                    ZoneId.of(
                            "America/Lima"
                    )
            );


    private final LocalDate HOY =
            LocalDate.of(
                    2026,
                    9,
                    19
            );


    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    @BeforeEach
    void setUp() {

        /*
         * Creamos manualmente el Scheduler.
         *
         * Lombok genera en producción un constructor
         * con estas tres dependencias.
         */
        scheduler =
                new ResumenDiarioScheduler(
                        comprobanteRepository,
                        resumenDiarioService,
                        clock,
                        new SunatPlazoService(clock)
                );


        /*
         * Como este test NO está arrancando todo Spring,
         * los @Value no son inyectados automáticamente.
         *
         * Por eso configuramos manualmente los mismos
         * valores que usaría la aplicación.
         */

        ReflectionTestUtils.setField(
                scheduler,
                "automaticoHabilitado",
                true
        );


        ReflectionTestUtils.setField(
                scheduler,
                "maxResumenesPorCiclo",
                50
        );


        ReflectionTestUtils.setField(
                scheduler,
                "ticketBatchSize",
                30
        );


        ReflectionTestUtils.setField(
                scheduler,
                "creadosBatchSize",
                20
        );


        ReflectionTestUtils.setField(
                scheduler,
                "enviandoTimeoutMinutes",
                10
        );
    }


    // =========================================================
    // TEST 1
    // SCHEDULER APAGADO
    // =========================================================
    //
    // En BETA actualmente tenemos:
    //
    // sunat.resumen-diario.auto.enabled=false
    //
    // Por tanto ninguna tarea automática debe hacer trabajo.
    //
    // =========================================================

    @Test
    void noDebeEjecutarNadaCuandoSchedulerEstaDeshabilitado() {

        ReflectionTestUtils.setField(
                scheduler,
                "automaticoHabilitado",
                false
        );


        // =====================================================
        // EJECUTAR TODOS LOS JOBS
        // =====================================================

        scheduler.cierreDiario();

        scheduler.recuperarPendientesAnteriores();

        scheduler.recuperarCreados();

        scheduler.consultarTickets();

        scheduler.detectarEnviosInciertos();


        // =====================================================
        // VALIDAR
        // =====================================================

        /*
         * Si el automático está apagado,
         * ni siquiera debemos tocar los repositories.
         */
        verifyNoInteractions(
                comprobanteRepository
        );


        /*
         * Tampoco debemos llamar al servicio.
         */
        verifyNoInteractions(
                resumenDiarioService
        );
    }


    // =========================================================
    // TEST 2
    // CIERRE DIARIO INCLUYE HOY
    // =========================================================
    //
    // cierreDiario()
    //
    // debe poder tomar comprobantes cuya fecha sea HOY.
    //
    // =========================================================

    @Test
    void cierreDiarioDebeProcesarPendientesDelDiaActual() {

        LocalDateTime fechaPendiente =
                HOY.atTime(
                        10,
                        30
                );


        when(
                comprobanteRepository
                        .buscarRucsConPendientesResumen()
        ).thenReturn(
                List.of(
                        RUC
                )
        );


        /*
         * Primera consulta:
         *
         * existe una boleta pendiente.
         *
         * Segunda consulta:
         *
         * ya no quedan documentos.
         */
        when(
                comprobanteRepository
                        .buscarFechaMasAntiguaPendienteResumenDesde(
                                eq(RUC), any(LocalDateTime.class)
                        )
        ).thenReturn(
                fechaPendiente,
                null
        );


        when(
                resumenDiarioService
                        .generarYEnviarAutomatico(
                                RUC,
                                HOY
                        )
        ).thenReturn(
                true
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.cierreDiario();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                times(1)
        ).generarYEnviarAutomatico(
                RUC,
                HOY
        );
    }


    // =========================================================
    // TEST 3
    // RECUPERACIÓN DE DÍAS ANTERIORES
    // =========================================================
    //
    // recuperarPendientesAnteriores()
    //
    // está pensado para:
    //
    // - servidor apagado
    // - caída temporal
    // - mantenimiento
    //
    // Debe recuperar documentos anteriores a HOY.
    //
    // =========================================================

    @Test
    void debeRecuperarPendientesDeDiasAnteriores() {

        LocalDate ayer =
                HOY.minusDays(
                        1
                );


        LocalDateTime fechaPendiente =
                ayer.atTime(
                        18,
                        30
                );


        when(
                comprobanteRepository
                        .buscarRucsConPendientesResumen()
        ).thenReturn(
                List.of(
                        RUC
                )
        );


        when(
                comprobanteRepository
                        .buscarFechaMasAntiguaPendienteResumenDesde(
                                eq(RUC), any(LocalDateTime.class)
                        )
        ).thenReturn(
                fechaPendiente,
                null
        );


        when(
                resumenDiarioService
                        .generarYEnviarAutomatico(
                                RUC,
                                ayer
                        )
        ).thenReturn(
                true
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.recuperarPendientesAnteriores();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                times(1)
        ).generarYEnviarAutomatico(
                RUC,
                ayer
        );
    }


    // =========================================================
    // TEST 4
    // RECOVERY NO DEBE TOMAR HOY
    // =========================================================
    //
    // El job horario de recuperación sólo trabaja
    // con días ANTERIORES.
    //
    // Los documentos de hoy esperan el cierre diario.
    //
    // =========================================================

    @Test
    void recuperacionHorariaNoDebeProcesarDocumentosDeHoy() {

        when(
                comprobanteRepository
                        .buscarRucsConPendientesResumen()
        ).thenReturn(
                List.of(
                        RUC
                )
        );


        when(
                comprobanteRepository
                        .buscarFechaMasAntiguaPendienteResumenDesde(
                                eq(RUC), any(LocalDateTime.class)
                        )
        ).thenReturn(
                HOY.atTime(
                        8,
                        0
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.recuperarPendientesAnteriores();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                never()
        ).generarYEnviarAutomatico(
                anyString(),
                any(LocalDate.class)
        );
    }


    // =========================================================
    // TEST 5
    // FECHA FUTURA
    // =========================================================
    //
    // Un error de datos nunca debe provocar que el scheduler
    // genere automáticamente un RC futuro.
    //
    // =========================================================

    @Test
    void noDebeProcesarAutomaticamenteUnaFechaFutura() {

        LocalDate manana =
                HOY.plusDays(
                        1
                );


        when(
                comprobanteRepository
                        .buscarRucsConPendientesResumen()
        ).thenReturn(
                List.of(
                        RUC
                )
        );


        when(
                comprobanteRepository
                        .buscarFechaMasAntiguaPendienteResumenDesde(
                                eq(RUC), any(LocalDateTime.class)
                        )
        ).thenReturn(
                manana.atTime(
                        8,
                        0
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.cierreDiario();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                never()
        ).generarYEnviarAutomatico(
                anyString(),
                any(LocalDate.class)
        );
    }


    // =========================================================
    // TEST 6
    // LÍMITE DE RC POR CICLO
    // =========================================================
    //
    // Supongamos que existen muchísimos comprobantes.
    //
    // Para evitar que un ciclo monopolice el servidor,
    // maxResumenesPorCiclo limita cuántos RC
    // puede generar una sola ejecución.
    //
    // En este test:
    //
    // máximo = 3
    //
    // Aunque siempre existan pendientes,
    // solamente se deben generar 3.
    //
    // =========================================================

    @Test
    void debeRespetarMaximoDeResumenesPorCiclo() {

        ReflectionTestUtils.setField(
                scheduler,
                "maxResumenesPorCiclo",
                3
        );


        LocalDate ayer =
                HOY.minusDays(
                        1
                );


        when(
                comprobanteRepository
                        .buscarRucsConPendientesResumen()
        ).thenReturn(
                List.of(
                        RUC
                )
        );


        /*
         * Simulamos que siempre siguen existiendo pendientes
         * de la misma fecha.
         */
        when(
                comprobanteRepository
                        .buscarFechaMasAntiguaPendienteResumenDesde(
                                eq(RUC), any(LocalDateTime.class)
                        )
        ).thenReturn(
                ayer.atTime(
                        12,
                        0
                )
        );


        when(
                resumenDiarioService
                        .generarYEnviarAutomatico(
                                RUC,
                                ayer
                        )
        ).thenReturn(
                true
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.cierreDiario();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                times(3)
        ).generarYEnviarAutomatico(
                RUC,
                ayer
        );
    }


    // =========================================================
    // TEST 7
    // RECUPERAR RC CREADOS
    // =========================================================
    //
    // Escenario:
    //
    // BD guardó:
    //
    // estado = CREADO
    //
    // y el servidor se apagó antes de sendSummary.
    //
    // El scheduler debe pedir al servicio que los recupere.
    //
    // =========================================================

    @Test
    void debeRecuperarResumenesCreadosPendientesDeEnvio() {

        ReflectionTestUtils.setField(
                scheduler,
                "creadosBatchSize",
                20
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.recuperarCreados();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                times(1)
        ).recuperarResumenesCreados(
                20
        );
    }


    // =========================================================
    // TEST 8
    // CONSULTAR TICKETS
    // =========================================================
    //
    // sendSummary es asíncrono.
    //
    // Por tanto el scheduler debe seguir consultando:
    //
    // getStatus(ticket)
    //
    // hasta obtener resultado final.
    //
    // =========================================================

    @Test
    void debeConsultarTicketsPendientesAutomaticamente() {

        ReflectionTestUtils.setField(
                scheduler,
                "ticketBatchSize",
                30
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.consultarTickets();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                times(1)
        ).consultarTicketsPendientes(
                30
        );
    }


    // =========================================================
    // TEST 9
    // ENVIANDO VENCIDO
    // =========================================================
    //
    // Escenario delicado:
    //
    // estado = ENVIANDO
    //
    // pasa demasiado tiempo
    //
    // no sabemos si SUNAT recibió o no el sendSummary.
    //
    // Por seguridad se debe marcar:
    //
    // ERROR_ENVIO_INCIERTO
    //
    // y NO reenviar ciegamente.
    //
    // =========================================================

    @Test
    void debeDetectarEnviosVencidosComoInciertos() {

        ReflectionTestUtils.setField(
                scheduler,
                "enviandoTimeoutMinutes",
                10
        );


        when(
                resumenDiarioService
                        .marcarEnviosVencidosComoInciertos(
                                10
                        )
        ).thenReturn(
                2
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        scheduler.detectarEnviosInciertos();


        // =====================================================
        // VALIDAR
        // =====================================================

        verify(
                resumenDiarioService,
                times(1)
        ).marcarEnviosVencidosComoInciertos(
                10
        );
    }


    // =========================================================
    // TEST 10
    // ERROR DURANTE GENERACIÓN AUTOMÁTICA
    // =========================================================
    //
    // Una empresa puede tener un problema:
    //
    // - certificado
    // - XML
    // - credenciales
    // - SUNAT
    //
    // El scheduler NO debe quedarse llamando infinitamente
    // al mismo documento dentro del mismo ciclo.
    //
    // También NO debe lanzar la excepción hasta tumbar
    // el proceso programado.
    //
    // =========================================================

    @Test
    void errorDuranteGeneracionNoDebeCrearBucleInfinito() {

        LocalDate ayer =
                HOY.minusDays(
                        1
                );


        when(
                comprobanteRepository
                        .buscarRucsConPendientesResumen()
        ).thenReturn(
                List.of(
                        RUC
                )
        );


        when(
                comprobanteRepository
                        .buscarFechaMasAntiguaPendienteResumenDesde(
                                eq(RUC), any(LocalDateTime.class)
                        )
        ).thenReturn(
                ayer.atTime(
                        9,
                        0
                ),
                null
        );


        when(
                resumenDiarioService
                        .generarYEnviarAutomatico(
                                RUC,
                                ayer
                        )
        ).thenThrow(
                new IllegalStateException(
                        "Error SUNAT simulado"
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================
        //
        // El scheduler captura internamente la excepción.
        //
        // =====================================================

        assertDoesNotThrow(
                () ->
                        scheduler.cierreDiario()
        );


        // =====================================================
        // VALIDAR
        // =====================================================
        //
        // Debe intentarlo solamente una vez
        // durante este ciclo.
        //
        // =====================================================

        verify(
                resumenDiarioService,
                times(1)
        ).generarYEnviarAutomatico(
                RUC,
                ayer
        );
    }

    @Test void tenantConBacklogPermanenteNoImpideProgresoDeOtroEnSiguienteCiclo(){
        ReflectionTestUtils.setField(scheduler,"maxResumenesPorCiclo",1);
        when(comprobanteRepository.buscarRucsConPendientesResumen()).thenReturn(List.of("20100130204","20600000001"));
        when(comprobanteRepository.buscarFechaMasAntiguaPendienteResumenDesde(anyString(),any())).thenReturn(HOY.minusDays(1).atStartOfDay());
        when(resumenDiarioService.generarYEnviarAutomatico(anyString(),any())).thenReturn(true);
        scheduler.cierreDiario();scheduler.cierreDiario();
        verify(resumenDiarioService).generarYEnviarAutomatico("20600000001",HOY.minusDays(1));
    }

    @Test
    void debeConsultarSoloVentanaDelPlazoActualSinModificarVencidos() {
        when(comprobanteRepository.buscarRucsConPendientesResumen()).thenReturn(List.of(RUC));
        when(comprobanteRepository.buscarFechaMasAntiguaPendienteResumenDesde(eq(RUC), any()))
                .thenReturn(HOY.minusDays(1).atTime(10, 0), null);
        when(resumenDiarioService.generarYEnviarAutomatico(RUC, HOY.minusDays(1))).thenReturn(true);
        scheduler.recuperarPendientesAnteriores();
        verify(comprobanteRepository, times(2)).buscarFechaMasAntiguaPendienteResumenDesde(RUC, HOY.minusDays(7).atStartOfDay());
        verify(resumenDiarioService).generarYEnviarAutomatico(RUC, HOY.minusDays(1));
        verifyNoMoreInteractions(resumenDiarioService);
    }

    @Test
    void fechaFallidaNoDebeBloquearFechaPosteriorDeMismaEmpresa() {
        LocalDate fallida = HOY.minusDays(2), siguiente = HOY.minusDays(1);
        when(comprobanteRepository.buscarRucsConPendientesResumen()).thenReturn(List.of(RUC));
        when(comprobanteRepository.buscarFechaMasAntiguaPendienteResumenDesde(eq(RUC), any()))
                .thenReturn(fallida.atTime(10, 0), siguiente.atTime(10, 0), null);
        when(resumenDiarioService.generarYEnviarAutomatico(RUC, fallida)).thenThrow(new IllegalStateException("fallo de esa fecha"));
        when(resumenDiarioService.generarYEnviarAutomatico(RUC, siguiente)).thenReturn(true);
        scheduler.recuperarPendientesAnteriores();
        verify(resumenDiarioService, times(1)).generarYEnviarAutomatico(RUC, fallida);
        verify(resumenDiarioService, times(1)).generarYEnviarAutomatico(RUC, siguiente);
        verify(comprobanteRepository, times(2)).buscarFechaMasAntiguaPendienteResumenDesde(RUC, fallida.plusDays(1).atStartOfDay());
    }

    @Test
    void fechaSinDocumentosDisponiblesNoDebeBloquearFechaPosterior() {
        LocalDate agotada = HOY.minusDays(2), siguiente = HOY.minusDays(1);
        when(comprobanteRepository.buscarRucsConPendientesResumen()).thenReturn(List.of(RUC));
        when(comprobanteRepository.buscarFechaMasAntiguaPendienteResumenDesde(eq(RUC), any()))
                .thenReturn(agotada.atTime(10, 0), siguiente.atTime(10, 0), null);
        when(resumenDiarioService.generarYEnviarAutomatico(RUC, agotada)).thenReturn(false);
        when(resumenDiarioService.generarYEnviarAutomatico(RUC, siguiente)).thenReturn(true);
        scheduler.recuperarPendientesAnteriores();
        verify(resumenDiarioService).generarYEnviarAutomatico(RUC, agotada);
        verify(resumenDiarioService).generarYEnviarAutomatico(RUC, siguiente);
    }
}
