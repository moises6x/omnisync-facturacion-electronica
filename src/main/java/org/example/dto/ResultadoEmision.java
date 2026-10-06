package org.example.dto;

public record ResultadoEmision(

        Long comprobanteId,

        String estado,

        String tipoComprobante,

        String serie,

        Integer numero,

        String mensaje

) {
}