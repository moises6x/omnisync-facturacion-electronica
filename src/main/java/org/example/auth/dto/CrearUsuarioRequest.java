package org.example.auth.dto;


import jakarta.validation.constraints.*;
import org.example.auth.model.PermisoUsuario;
import org.example.auth.model.RolUsuario;


import java.util.Set;

public record CrearUsuarioRequest(

        @NotBlank
        @Size(max = 150)
        String nombre,

        @NotBlank
        @Email
        @Size(max = 200)
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String passwordTemporal,

        @NotNull
        RolUsuario rol,

        Set<PermisoUsuario> permisos

) {
}
