package org.example.admin.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RenovarSuscripcionRequest(

        @NotNull
        @Min(1)
        @Max(24)
        Integer meses,

        @NotNull
        @PositiveOrZero
        BigDecimal monto,

        @Size(max = 500)
        String observacion

) {
}