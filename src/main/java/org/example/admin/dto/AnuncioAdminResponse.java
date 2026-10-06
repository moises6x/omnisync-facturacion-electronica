package org.example.admin.dto;


import org.example.admin.model.TipoAnuncio;

import java.time.LocalDateTime;

public record AnuncioAdminResponse(

        Long id,

        Long empresaId,

        String rucEmpresa,

        String razonSocialEmpresa,

        boolean global,

        String titulo,

        String mensaje,

        TipoAnuncio tipo,

        LocalDateTime inicioEn,

        LocalDateTime finEn,

        boolean pantallaCompleta,

        boolean activo,

        Long creadoPorUsuarioId,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}
