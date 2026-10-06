package org.example.dto;

/**
 * Respuesta administrativa al preparar una boleta previamente
 * informada para su baja mediante Resumen Diario (ConditionCode=3).
 */
public record BajaBoletaResponse(
        Long comprobanteId,
        String estado,
        String mensaje
) {
}
