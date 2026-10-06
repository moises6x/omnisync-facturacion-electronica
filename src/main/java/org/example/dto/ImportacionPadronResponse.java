package org.example.dto;

import java.time.LocalDateTime;

public record ImportacionPadronResponse(
        String estado,
        String archivo,
        long lineasLeidas,
        long registrosValidos,
        long registrosInvalidos,
        long registrosFinales,
        LocalDateTime iniciadoEn,
        LocalDateTime finalizadoEn,
        String mensaje
) {
}
