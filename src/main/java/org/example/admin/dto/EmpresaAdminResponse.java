package org.example.admin.dto;

import org.example.model.EstadoEmpresa;

import java.time.LocalDateTime;

public record EmpresaAdminResponse(

        Long id,

        String tenantId,

        String ruc,

        String razonSocial,

        EstadoEmpresa estado,

        boolean configuracionSunatCompleta,

        long totalUsuarios,

        LocalDateTime suscripcionInicio,

        LocalDateTime suscripcionVence,

        long diasRestantes,

        boolean suspensionAutomatica,

        int diasGracia,

        String motivoSuspension,

        LocalDateTime createdAt

) {
}