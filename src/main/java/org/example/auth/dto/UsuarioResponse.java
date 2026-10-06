package org.example.auth.dto;


import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.PermisoUsuario;
import org.example.auth.model.RolUsuario;
import org.example.model.*;

import java.time.LocalDateTime;
import java.util.Set;

public record UsuarioResponse(

        Long id,

        String nombre,

        String email,

        String rucEmpresa,

        RolUsuario rol,

        EstadoUsuario estado,

        Set<PermisoUsuario> permisos,

        boolean debeCambiarPassword,

        LocalDateTime ultimoLogin,

        LocalDateTime createdAt

) {
}
