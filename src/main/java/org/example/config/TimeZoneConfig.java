
package org.example.config;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * =============================================================
 * TIME ZONE CONFIG
 * =============================================================
 *
 * Centraliza la zona horaria utilizada por el backend.
 *
 * Para un sistema de facturación peruano es importante que:
 *
 * - fecha de emisión
 * - agrupación del Resumen Diario
 * - schedulers
 * - auditoría
 *
 * trabajen con la misma zona horaria.
 *
 * El servidor puede ejecutarse físicamente en UTC o en otro país,
 * por eso no conviene depender de la zona horaria del sistema operativo.
 */
@Configuration
public class TimeZoneConfig {

    @Value("${app.time-zone:America/Lima}")
    private String timeZoneId;

    private ZoneId zoneId;

    /**
     * Se ejecuta al arrancar Spring y fija la zona por defecto de la JVM.
     *
     * Esto protege el código existente que todavía utiliza:
     *
     * LocalDateTime.now()
     *
     * sin pasar explícitamente un ZoneId.
     */
    @PostConstruct
    public void configurarZonaHoraria() {

        zoneId = ZoneId.of(timeZoneId);

        TimeZone.setDefault(
                TimeZone.getTimeZone(zoneId)
        );
    }

    /**
     * Clock central para código nuevo.
     *
     * En los siguientes bloques podremos inyectarlo en servicios
     * tributarios y además facilitar pruebas automatizadas de fechas.
     */
    @Bean
    public Clock applicationClock() {
        return Clock.system(zoneId);
    }

    /**
     * ZoneId reutilizable por servicios que necesiten trabajar
     * explícitamente con la zona tributaria del Perú.
     */
    @Bean
    public ZoneId applicationZoneId() {
        return zoneId;
    }
}