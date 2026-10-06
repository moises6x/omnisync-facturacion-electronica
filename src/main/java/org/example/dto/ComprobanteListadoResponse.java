package org.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ComprobanteListadoResponse(

        Long id,

        String tipoComprobante,

        String tipoDescripcion,

        String serie,

        Integer numero,

        String numeroCompleto,

        LocalDateTime fechaEmision,

        String tipoDocCliente,

        String numDocCliente,

        String cliente,

        BigDecimal total,

        String estado,

        String mensajeSunat

) {
}
