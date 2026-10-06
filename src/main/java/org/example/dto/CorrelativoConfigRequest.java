package org.example.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CorrelativoConfigRequest {

    @NotBlank
    @Pattern(
            regexp = "^(01|03|07)$",
            message = "Tipo de comprobante inválido"
    )
    private String tipoComprobante;

    @NotBlank
    @Pattern(
            regexp = "^[FB][A-Z0-9]{3}$",
            message = "Serie inválida"
    )
    private String serie;

    /**
     * Ejemplo: si el cliente ya llegó a F001-400,
     * configuras ultimoNumero = 400 y el siguiente será 401.
     */
    @NotNull
    @Min(0)
    @Max(99999999)
    private Integer ultimoNumero;
}
