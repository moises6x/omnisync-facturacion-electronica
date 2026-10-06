package org.example.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConfiguracionTributariaEmpresaResponse(

        Long empresaId,
        String ruc,
        String razonSocial,
        BigDecimal igvPorcentaje,
        LocalDateTime configuradoEn,
        String motivo

) {
}
