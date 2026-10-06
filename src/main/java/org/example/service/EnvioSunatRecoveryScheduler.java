
package org.example.service;

import lombok.extern.slf4j.Slf4j;

import org.example.repository.ComprobanteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.PageRequest;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * =============================================================
 * ENVÍO SUNAT RECOVERY SCHEDULER
 * =============================================================
 *
 * Recupera comprobantes que quedaron en PENDIENTE_ENVIO.
 *
 * Caso que resuelve:
 *
 * 1. MySQL hace COMMIT de una factura.
 * 2. El servidor cae antes de ejecutar el @Async AFTER_COMMIT.
 * 3. La factura queda persistida, pero nadie intenta enviarla.
 * 4. Este scheduler la detecta y vuelve a disparar el envío.
 *
 * MUY IMPORTANTE:
 *
 * NO reintenta ERROR_ENVIO automáticamente.
 * Un timeout puede haber ocurrido después de que SUNAT recibió
 * el documento. Ese caso requiere una estrategia de conciliación
 * específica y no un reenvío ciego.
 */
@Component
@Slf4j
public class EnvioSunatRecoveryScheduler {

    private final ComprobanteRepository comprobanteRepository;

    private final EnvioSunatService envioSunatService;

    private final Executor sunatExecutor;
    private final Clock clock;

    public EnvioSunatRecoveryScheduler(
            ComprobanteRepository comprobanteRepository,
            EnvioSunatService envioSunatService,
            @Qualifier("sunatExecutor") Executor sunatExecutor,
            Clock clock
    ) {
        this.comprobanteRepository = comprobanteRepository;
        this.envioSunatService = envioSunatService;
        this.sunatExecutor = sunatExecutor;
        this.clock = clock;
    }

    @Value("${sunat.envio-recovery.batch-size:20}")
    private int batchSize;

    @Value("${sunat.envio-recovery.enviando-timeout-minutes:10}")
    private int enviandoTimeoutMinutes = 10;


    @Scheduled(
            fixedDelayString = "${sunat.envio-recovery.delay-ms:60000}",
            initialDelayString = "${sunat.envio-recovery.initial-delay-ms:30000}"
    )
    public void recuperarPendientes() {

        int inciertos = comprobanteRepository.marcarEnviosVencidosComoInciertos(
                LocalDateTime.now(clock).minusMinutes(Math.max(1, enviandoTimeoutMinutes)));
        if (inciertos > 0) log.warn("{} CPE ENVIANDO vencidos requieren conciliación; no se reenvían.", inciertos);

        int limite =
                Math.max(
                        1,
                        Math.min(
                                batchSize,
                                100
                        )
                );

        List<Long> ids =
                comprobanteRepository
                        .buscarIdsPendientesEnvio(
                                PageRequest.of(
                                        0,
                                        limite
                                )
                        );

        if (ids.isEmpty()) {
            return;
        }

        log.info(
                "Recovery SUNAT encontró {} comprobante(s) PENDIENTE_ENVIO.",
                ids.size()
        );

        for (Long id : ids) {

            try {

                sunatExecutor.execute(
                        () ->
                                envioSunatService.enviar(
                                        id
                                )
                );

            } catch (Exception e) {

                /*
                 * Si la cola del executor estuviera temporalmente
                 * llena, el comprobante seguirá PENDIENTE_ENVIO y
                 * será tomado en la siguiente ejecución.
                 */
                log.warn(
                        "No se pudo encolar el comprobante {} para recovery SUNAT: {}",
                        id,
                        e.getMessage()
                );
            }
        }
    }
}
