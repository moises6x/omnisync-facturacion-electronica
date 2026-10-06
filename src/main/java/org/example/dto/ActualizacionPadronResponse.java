package org.example.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActualizacionPadronResponse(

        boolean automaticaActiva,

        LocalDate versionLocal,

        LocalDate versionSunat,

        boolean actualizacionDisponible,

        boolean actualizacionEnCurso,

        String estadoImportacion,

        LocalDateTime ultimaVerificacion,

        LocalDateTime ultimaDescarga,

        LocalDateTime proximaRevision,

        String mensaje

) {
}
