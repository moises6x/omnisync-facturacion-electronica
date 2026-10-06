package org.example.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record ProgramacionPadronRequest(

        boolean activo,

        /*
         * Valores:
         *
         * SEMANAL
         * UNA_VEZ
         */
        String modo,

        /*
         * Para SEMANAL:
         *
         * LUNES
         * MARTES
         * MIERCOLES
         * JUEVES
         * VIERNES
         * SABADO
         * DOMINGO
         */
        String diaSemana,

        /*
         * Ejemplo:
         *
         * "02:00"
         */
        LocalTime hora,

        /*
         * Ejemplo:
         *
         * America/Lima
         */
        String zonaHoraria,

        /*
         * Solamente para UNA_VEZ.
         *
         * Ejemplo:
         *
         * 2026-09-25T03:30:00
         */
        LocalDateTime fechaHoraUnica

) {
}