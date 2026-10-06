package org.example.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Solicitud administrativa para cambiar la tasa IGV de una empresa.
 *
 * La tasa nunca se toma desde una factura/boleta como fuente de verdad.
 * Sólo un SUPER_ADMIN puede modificar esta configuración.
 */
public record ConfigurarTributacionEmpresaRequest(

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 3, fraction = 2)
        BigDecimal igvPorcentaje,

        @NotBlank
        @Size(max = 500)
        String motivo

) {
}
