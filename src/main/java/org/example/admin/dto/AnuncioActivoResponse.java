package org.example.admin.dto;

import org.example.admin.model.TipoAnuncio;

import java.time.LocalDateTime;

public record AnuncioActivoResponse(

        Long id,

        String titulo,

        String mensaje,

        TipoAnuncio tipo,

        boolean pantallaCompleta,

        LocalDateTime inicioEn,

        LocalDateTime finEn

) {
}