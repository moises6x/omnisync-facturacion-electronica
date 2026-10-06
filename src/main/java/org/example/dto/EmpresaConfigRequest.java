package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;


@Data
public class EmpresaConfigRequest {

    // =========================================================
    // CREDENCIALES SUNAT
    // =========================================================
    //
    // IMPORTANTE:
    //
    // NO recibimos:
    //
    // - RUC
    // - empresaId
    // - tenantId
    //
    // La empresa siempre se obtiene del JWT.
    //
    // =========================================================

    @NotBlank(
            message = "El usuario SOL es obligatorio"
    )
    @Size(
            max = 100,
            message = "El usuario SOL supera el tamaño permitido"
    )
    private String usuarioSol;


    @NotBlank(
            message = "La Clave SOL es obligatoria"
    )
    @Size(
            max = 200,
            message = "La Clave SOL supera el tamaño permitido"
    )
    private String claveSol;


    @Size(
            max = 500,
            message = "La contraseña del certificado supera el tamaño permitido"
    )
    private String pfxPassword;
}