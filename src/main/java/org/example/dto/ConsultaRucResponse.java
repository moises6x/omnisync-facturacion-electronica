package org.example.dto;

public record ConsultaRucResponse(
        boolean encontrado,
        String ruc,
        String razonSocial,
        String estado,
        String condicion,
        String ubigeo,
        String direccion,
        boolean activo,
        boolean habido
) {
}
