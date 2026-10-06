package org.example.auth.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordTemporalRequest(

        @NotBlank
        @Size(min = 12, max = 128)
        String passwordTemporal

) {
}
