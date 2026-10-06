package org.example.security;

import org.example.auth.security.AppPrincipal;
import org.example.auth.security.SecurityContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprueba que los servicios de negocio obtengan el tenant/RUC únicamente
 * desde un AppPrincipal autenticado y no desde datos arbitrarios del request.
 */
class SecurityContextServiceTest {

    private final SecurityContextService service = new SecurityContextService();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinAutenticacionDebeRechazar() {
        assertThrows(SecurityException.class, service::principal);
    }

    @Test
    void principalDeTipoIncorrectoDebeRechazar() {
        var auth = new UsernamePasswordAuthenticationToken("texto", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(SecurityException.class, service::principal);
    }

    @Test
    void debeRecuperarUsuarioEmpresaYRucDelPrincipal() {
        AppPrincipal p = new AppPrincipal(
                10L, 20L, "tenant-20", "20100130204", "user@test.com", "EMPRESA_ADMIN", false);
        autenticar(p);

        assertSame(p, service.principal());
        assertEquals(10L, service.usuarioId());
        assertEquals(20L, service.empresaIdRequerido());
        assertEquals("20100130204", service.rucEmpresaRequerido());
    }

    @Test
    void sesionSinRucDebeRechazarRucEmpresa() {
        AppPrincipal p = new AppPrincipal(1L, null, null, null, "admin@test.com", "SUPER_ADMIN", false);
        autenticar(p);

        assertThrows(SecurityException.class, service::rucEmpresaRequerido);
    }

    @Test
    void sesionSinEmpresaIdDebeRechazarEmpresa() {
        AppPrincipal p = new AppPrincipal(1L, null, null, null, "admin@test.com", "SUPER_ADMIN", false);
        autenticar(p);

        assertThrows(SecurityException.class, service::empresaIdRequerido);
    }

    private void autenticar(AppPrincipal principal) {
        var auth = new UsernamePasswordAuthenticationToken(principal, "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
