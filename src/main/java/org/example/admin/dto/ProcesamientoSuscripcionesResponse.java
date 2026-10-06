package org.example.admin.dto;

import java.time.LocalDateTime;

public record ProcesamientoSuscripcionesResponse(

        LocalDateTime ejecutadoEn,

        int suscripcionesRevisadas,

        int avisosGenerados,

        int empresasSuspendidas

) {
}