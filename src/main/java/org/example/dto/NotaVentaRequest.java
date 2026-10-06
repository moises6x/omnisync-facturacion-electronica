package org.example.dto;



import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class NotaVentaRequest {

    @NotNull(message = "El ID de la tienda es obligatorio")
    private Long tiendaId;

    @NotNull(message = "El monto total es obligatorio")
    @Positive(message = "El monto total debe ser mayor a cero")
    private BigDecimal montoTotal;

    private String observacion;
}
