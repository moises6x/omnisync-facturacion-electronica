package org.example.auth.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank
        @Size(max = 128)
        String passwordActual,

        @NotBlank
        @Size(min = 12, max = 128)
        String passwordNueva

) {
}
