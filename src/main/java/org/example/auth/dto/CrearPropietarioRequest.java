package org.example.auth.dto;

import jakarta.validation.constraints.*;

public record CrearPropietarioRequest(

        @NotBlank
        @Size(max = 150)
        String nombre,

        @NotBlank
        @Email
        @Size(max = 200)
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String passwordTemporal

) {
}
