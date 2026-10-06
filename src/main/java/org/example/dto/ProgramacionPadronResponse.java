package org.example.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record ProgramacionPadronResponse(

        boolean activo,

        String modo,

        String diaSemana,

        LocalTime hora,

        String zonaHoraria,

        LocalDateTime fechaHoraUnica,

        LocalDateTime proximaEjecucion,

        String mensaje

) {
}