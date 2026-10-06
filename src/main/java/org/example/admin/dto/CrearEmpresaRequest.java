package org.example.admin.dto;

import jakarta.validation.constraints.*;

public record CrearEmpresaRequest(

        @NotBlank
        @Pattern(
                regexp = "^(10|15|17|20)[0-9]{9}$",
                message = "RUC inválido"
        )
        String ruc,

        @NotBlank
        @Size(max = 250)
        String razonSocial,

        @Size(max = 500)
        String direccionFiscal,

        @Pattern(
                regexp = "^$|^[0-9]{6}$",
                message = "Ubigeo inválido"
        )
        String ubigeo,

        @Size(max = 100)
        String departamento,

        @Size(max = 100)
        String provincia,

        @Size(max = 100)
        String distrito,

        @NotNull
        @Min(1)
        @Max(24)
        Integer mesesIniciales

) {
}