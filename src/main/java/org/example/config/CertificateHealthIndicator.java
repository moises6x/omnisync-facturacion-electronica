package org.example.config;

import lombok.RequiredArgsConstructor;

import org.example.repository.EmpresaRepository;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * ============================================================
 * CERTIFICATE HEALTH INDICATOR
 * ============================================================
 *
 * Monitorea vencimiento de certificados sin convertir un problema
 * tributario de UN tenant en una caída técnica de TODA la API.
 *
 * Importante:
 * - un certificado vencido requiere atención
 * - pero no significa que Tomcat/JVM/BD estén caídos
 *
 * Por eso este indicador se mantiene UP y expone únicamente conteos
 * no sensibles. Los procesos que necesiten impedir emisión con un
 * certificado vencido deben seguir validándolo en la lógica de negocio.
 */
@Component
@RequiredArgsConstructor
public class CertificateHealthIndicator implements HealthIndicator {

    private final EmpresaRepository empresaRepository;
    private final Clock clock;

    @Override
    public Health health() {

        LocalDate hoy =
                LocalDate.now(clock);

        int vencidos = 0;
        int proximos = 0;

        for (
                var empresa :
                empresaRepository.listarCertificadosParaMonitoreo()
        ) {

            LocalDate fechaVencimiento =
                    empresa.getCertificadoVigenteHasta();

            if (fechaVencimiento == null) {
                continue;
            }

            if (fechaVencimiento.isBefore(hoy)) {

                vencidos++;

            } else if (
                    !fechaVencimiento.isAfter(
                            hoy.plusDays(30)
                    )
            ) {

                proximos++;
            }
        }

        boolean requiereAtencion =
                vencidos > 0
                        || proximos > 0;

        /*
         * Nunca exponemos RUC, razón social ni datos del certificado.
         * Sólo métricas agregadas.
         *
         * Se devuelve UP porque el vencimiento de un certificado
         * perteneciente a un cliente no debe tumbar el health técnico
         * completo de la plataforma.
         */
        return Health
                .up()
                .withDetail(
                        "requiereAtencion",
                        requiereAtencion
                )
                .withDetail(
                        "certificadosVencidos",
                        vencidos
                )
                .withDetail(
                        "certificadosProximos30Dias",
                        proximos
                )
                .build();
    }
}
