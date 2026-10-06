package org.example.admin.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.example.admin.model.TipoAnuncio;

import java.time.LocalDateTime;

public record CrearActualizarAnuncioRequest(

        /*
         * NULL = todas las empresas.
         *
         * Con ID = únicamente esa empresa.
         */
        Long empresaId,

        @NotBlank
        @Size(max = 150)
        String titulo,

        @NotBlank
        @Size(max = 5000)
        String mensaje,

        @NotNull
        TipoAnuncio tipo,

        @NotNull
        LocalDateTime inicioEn,

        LocalDateTime finEn,

        boolean pantallaCompleta

) {
}