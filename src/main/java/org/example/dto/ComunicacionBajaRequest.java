package org.example.dto;

import jakarta.validation.constraints.*;

public record ComunicacionBajaRequest(
        @NotNull Long comprobanteId,
        @NotBlank @Size(max=500) String motivo
) {}
