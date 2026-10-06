package org.example.auth.dto;


import org.example.auth.model.PermisoUsuario;

import java.util.Set;

public record ActualizarPermisosUsuarioRequest(
        Set<PermisoUsuario> permisos
) {
}
