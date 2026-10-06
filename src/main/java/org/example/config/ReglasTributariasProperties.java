package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "tributacion")
@Data
public class ReglasTributariasProperties {

    /*
     * IGV actual usado por el backend.
     *
     * Por ahora lo mantenemos configurable.
     */
    private BigDecimal igvPorcentaje =
            new BigDecimal("18.00");


    /*
     * Tasas que esta versión permite asignar a una empresa.
     *
     * La plataforma NO decide automáticamente si una empresa
     * califica legalmente para una tasa especial; esa condición
     * debe verificarse antes de configurarla.
     */
    private Set<BigDecimal> igvPorcentajesPermitidos =
            new LinkedHashSet<>(
                    Set.of(
                            new BigDecimal("18.00"),
                            new BigDecimal("10.50")
                    )
            );


    /*
     * Monto a partir del cual una boleta
     * requiere identificar al cliente.
     */
    private BigDecimal boletaIdentificacionUmbral =
            new BigDecimal("700.00");


    /*
     * =========================================================
     * TIPOS DE OPERACIÓN SUNAT SOPORTADOS
     * =========================================================
     *
     * Ahora el tipo de operación viene dinámicamente
     * desde el request.
     *
     * Sin embargo, por seguridad NO permitimos cualquier código.
     *
     * Por ahora este backend sólo ha implementado
     * y probado correctamente:
     *
     * 0101 = Venta interna
     *
     * Más adelante podremos agregar:
     *
     * 0102
     * 0103
     * etc.
     *
     * únicamente cuando implementemos y probemos
     * sus reglas UBL correspondientes.
     */
    private Set<String> tiposOperacionSoportados =
            new LinkedHashSet<>(
                    Set.of(
                            "0101"
                    )
            );
}
