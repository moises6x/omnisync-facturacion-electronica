package org.example.admin.dto;

import jakarta.validation.constraints.Size;

public record SuspenderEmpresaRequest(

        @Size(max = 500)
        String motivo

) {
}