package org.example.auth.security;


public record AppPrincipal(

        Long usuarioId,

        Long empresaId,

        String tenantId,

        String ruc,

        String email,

        String rol,

        boolean apiClient

) {
}