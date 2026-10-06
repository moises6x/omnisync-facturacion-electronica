package org.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenFacturacionResponse(

        String periodo,

        LocalDate desde,

        LocalDate hasta,

        long totalComprobantes,

        long facturas,

        long boletas,

        long notasCredito,

        long aceptados,

        long pendientes,

        long rechazados,

        long errores,

        BigDecimal montoFacturas,

        BigDecimal montoBoletas,

        BigDecimal montoNotasCredito,

        BigDecimal ventasBrutas,

        BigDecimal ventasNetas

) {
}