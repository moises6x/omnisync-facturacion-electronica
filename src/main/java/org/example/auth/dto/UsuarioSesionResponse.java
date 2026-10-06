package org.example.auth.dto;


public record UsuarioSesionResponse(

        Long id,

        String nombre,

        String email,

        String rol,

        String ruc,

        String tenantId,

        boolean debeCambiarPassword

) {
}
