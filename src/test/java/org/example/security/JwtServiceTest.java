package org.example.security;

import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;
import org.example.auth.security.JwtService;
import org.example.model.Empresa;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void constructorDebeRechazarBase64InvalidoOSecretoCorto() {
        assertThrows(IllegalStateException.class, () -> new JwtService("***", "issuer", 30));
        String corto = Base64.getEncoder().encodeToString("corto".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> new JwtService(corto, "issuer", 30));
    }

    @Test
    void debeGenerarYValidarTokenConTenantYRuc() {
        JwtService service = new JwtService(SECRET, "omnisync-test", 30);
        Usuario u = usuarioConEmpresa();

        String token = service.generar(u);
        var jwt = service.validar(token);

        assertEquals("1", jwt.getSubject());
        assertEquals("admin@test.local", jwt.getClaimAsString("email"));
        assertEquals("EMPRESA_ADMIN", jwt.getClaimAsString("role"));
        assertEquals("tenant-test", jwt.getClaimAsString("tenantId"));
        assertEquals("20100130204", jwt.getClaimAsString("ruc"));
        assertEquals(4L, ((Number) jwt.getClaim("ver")).longValue());
    }

    @Test
    void usuarioSinEmpresaNoDebeIncluirTenantNiRuc() {
        JwtService service = new JwtService(SECRET, "omnisync-test", 30);
        Usuario u = new Usuario();
        u.setId(2L);
        u.setEmail("root@test.local");
        u.setRol(RolUsuario.SUPER_ADMIN);
        u.setTokenVersion(1);

        var jwt = service.validar(service.generar(u));
        assertNull(jwt.getClaim("tenantId"));
        assertNull(jwt.getClaim("ruc"));
    }

    @Test
    void tokenDeOtroIssuerDebeSerRechazado() {
        JwtService emisor = new JwtService(SECRET, "issuer-a", 30);
        JwtService receptor = new JwtService(SECRET, "issuer-b", 30);
        String token = emisor.generar(usuarioConEmpresa());
        assertThrows(JwtException.class, () -> receptor.validar(token));
    }

    @Test
    void accessExpiresSecondsDebeCoincidirConMinutos() {
        JwtService service = new JwtService(SECRET, "omnisync-test", 30);
        assertEquals(1800L, service.accessExpiresSeconds());
    }

    private Usuario usuarioConEmpresa() {
        Empresa e = new Empresa();
        e.setRuc("20100130204");
        e.setTenantId("tenant-test");

        Usuario u = new Usuario();
        u.setId(1L);
        u.setEmail("admin@test.local");
        u.setRol(RolUsuario.EMPRESA_ADMIN);
        u.setTokenVersion(4);
        u.setEmpresa(e);
        return u;
    }
}
