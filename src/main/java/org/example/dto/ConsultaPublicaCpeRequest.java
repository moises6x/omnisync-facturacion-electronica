package org.example.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos de contraste: no permite consultar un CPE sólo con un ID enumerable. */
public record ConsultaPublicaCpeRequest(
        @NotBlank @Pattern(regexp="\\d{11}") String rucEmisor,
        @NotBlank @Pattern(regexp="01|03|07") String tipoComprobante,
        @NotBlank @Pattern(regexp="[A-Za-z0-9]{4}") String serie,
        @NotNull @Positive Integer numero,
        @NotNull LocalDate fechaEmision,
        @NotNull @DecimalMin("0.00") BigDecimal importeTotal,
        String tipoDocReceptor,
        String numDocReceptor
) {}
