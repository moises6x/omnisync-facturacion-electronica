package org.example.security;

import org.example.auth.model.PermisoUsuario;
import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;
import org.example.auth.security.SecurityAuthorities;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAuthoritiesTest {

    @Test
    void propietarioDebeTenerTodosLosPermisos() {
        Usuario u = usuario(RolUsuario.EMPRESA_DUENO, Set.of());
        assertEquals(PermisoUsuario.values().length,
                SecurityAuthorities.permisosEfectivos(u).size());
    }

    @Test
    void superAdminDebeTenerTodosLosPermisos() {
        Usuario u = usuario(RolUsuario.SUPER_ADMIN, Set.of());
        assertEquals(PermisoUsuario.values().length,
                SecurityAuthorities.permisosEfectivos(u).size());
    }

    @Test
    void trabajadorSoloDebeRecibirPermisosPermitidos() {
        Usuario u = usuario(
                RolUsuario.TRABAJADOR,
                Set.of(PermisoUsuario.FACTURACION_EMITIR, PermisoUsuario.USUARIOS_GESTIONAR)
        );
        var permisos = SecurityAuthorities.permisosEfectivos(u);
        assertTrue(permisos.contains(PermisoUsuario.FACTURACION_EMITIR));
        assertFalse(permisos.contains(PermisoUsuario.USUARIOS_GESTIONAR));
    }

    @Test
    void authoritiesDebenIncluirRolYPermisos() {
        Usuario u = usuario(RolUsuario.TRABAJADOR, Set.of(PermisoUsuario.CONSULTA_RUC));
        Collection<? extends GrantedAuthority> auth = SecurityAuthorities.paraUsuario(u);
        assertTrue(auth.stream().anyMatch(a -> a.getAuthority().equals("ROLE_TRABAJADOR")));
        assertTrue(auth.stream().anyMatch(a -> a.getAuthority().equals("PERM_CONSULTA_RUC")));
    }

    @Test
    void apiClientDebeTenerPermisosOperativosSinPermisosAdministrativos() {
        var auth = SecurityAuthorities.paraApiClient();
        assertTrue(auth.stream().anyMatch(a -> a.getAuthority().equals("ROLE_API_CLIENT")));
        assertTrue(auth.stream().anyMatch(a -> a.getAuthority().equals("PERM_FACTURACION_EMITIR")));
        assertFalse(auth.stream().anyMatch(a -> a.getAuthority().equals("PERM_USUARIOS_GESTIONAR")));
    }

    private Usuario usuario(RolUsuario rol, Set<PermisoUsuario> permisos) {
        Usuario u = new Usuario();
        u.setRol(rol);
        u.setPermisos(permisos);
        return u;
    }
}
