package org.example.dto;

public record ConsultaPublicaCpeResponse(
        boolean encontrado,
        String estado,
        String mensaje
) {}
