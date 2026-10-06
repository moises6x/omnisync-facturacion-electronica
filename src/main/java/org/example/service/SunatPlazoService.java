package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * =============================================================
 * SUNAT PLAZO SERVICE
 * =============================================================
 *
 * Centraliza los plazos operativos que esta versión del backend
 * necesita controlar antes de intentar una comunicación con SUNAT.
 *
 * IMPORTANTE:
 * - No sustituye las reglas SUNAT del XML/XSL/XSD.
 * - Impide que un worker reenvíe automáticamente documentos que ya
 *   están fuera del plazo configurado.
 * - Los valores son configurables para que un cambio normativo pueda
 *   desplegarse sin recompilar toda la aplicación.
 */
@Service
public class SunatPlazoService {

    private final Clock clock;

    @Value("${sunat.plazos.envio-individual-dias:3}")
    private int envioIndividualDias = 3;

    @Value("${sunat.plazos.resumen-diario-dias:7}")
    private int resumenDiarioDias = 7;

    @Value("${sunat.plazos.baja-dias:7}")
    private int bajaDias = 7;

    public SunatPlazoService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Factura 01 y Nota de Crédito 07 asociada a factura.
     *
     * Si se emite el día D, el último día permitido por esta
     * configuración es D + N días calendario, inclusive.
     */
    public void validarEnvioIndividual(LocalDateTime fechaEmision) {

        if (fechaEmision == null) {
            throw new IllegalArgumentException("Fecha de emisión requerida.");
        }

        LocalDate emision = fechaEmision.toLocalDate();
        LocalDate hoy = LocalDate.now(clock);

        if (emision.isAfter(hoy)) {
            throw new IllegalArgumentException(
                    "La fecha de emisión no puede estar en el futuro."
            );
        }

        LocalDate limite = emision.plusDays(envioIndividualDias);

        if (hoy.isAfter(limite)) {
            throw new FueraDePlazoSunatException(
                    "El comprobante está fuera del plazo de envío individual a SUNAT. "
                            + "Fecha de emisión: " + emision
                            + ", fecha límite: " + limite
                            + ", fecha actual: " + hoy + "."
            );
        }
    }

    /**
     * Boletas y notas vinculadas incluidas en Resumen Diario.
     */
    public void validarResumenDiario(LocalDate fechaDocumentos) {
        validarFechaConLimite(
                fechaDocumentos,
                resumenDiarioDias,
                "Resumen Diario"
        );
    }

    /**
     * Comunicación de baja de numeración cuando corresponda.
     */
    public void validarBaja(LocalDate fechaDocumento) {
        validarFechaConLimite(
                fechaDocumento,
                bajaDias,
                "Comunicación de Baja"
        );
    }

    public long diasTranscurridos(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("Fecha requerida.");
        }
        return ChronoUnit.DAYS.between(fecha, LocalDate.now(clock));
    }

    public LocalDate fechaLimiteEnvioIndividual(LocalDate fechaEmision) {
        return fechaEmision.plusDays(envioIndividualDias);
    }

    public LocalDate fechaLimiteResumen(LocalDate fechaDocumentos) {
        return fechaDocumentos.plusDays(resumenDiarioDias);
    }

    /** Ventana operativa actual; no altera ni certifica la vigencia legal del plazo configurado. */
    public LocalDate fechaMinimaResumen() {
        return LocalDate.now(clock).minusDays(resumenDiarioDias);
    }

    public LocalDate fechaLimiteBaja(LocalDate fechaDocumento) {
        return fechaDocumento.plusDays(bajaDias);
    }

    private void validarFechaConLimite(
            LocalDate fecha,
            int dias,
            String nombreFlujo
    ) {

        if (fecha == null) {
            throw new IllegalArgumentException("Fecha requerida para " + nombreFlujo + ".");
        }

        LocalDate hoy = LocalDate.now(clock);

        if (fecha.isAfter(hoy)) {
            throw new IllegalArgumentException(
                    "La fecha del documento no puede estar en el futuro."
            );
        }

        LocalDate limite = fecha.plusDays(dias);

        if (hoy.isAfter(limite)) {
            throw new FueraDePlazoSunatException(
                    nombreFlujo
                            + " fuera del plazo configurado. Fecha del documento: "
                            + fecha
                            + ", fecha límite: "
                            + limite
                            + ", fecha actual: "
                            + hoy
                            + "."
            );
        }
    }

    public static class FueraDePlazoSunatException
            extends IllegalStateException {

        public FueraDePlazoSunatException(String message) {
            super(message);
        }
    }
}
