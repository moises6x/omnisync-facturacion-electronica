package org.example.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyServiceTest {

    private final PasswordPolicyService service = new PasswordPolicyService();

    @Test
    void passwordNuloDebeRechazarse() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validar(null, "user@test.com"));
    }

    @Test
    void debeExigirEntre12Y128Caracteres() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validar("Corta1!", "user@test.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validar("A1!" + "x".repeat(126), "user@test.com"));
    }

    @Test
    void debeRechazarPatronesDebiles() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validar("administrador", "user@test.com"));
    }

    @Test
    void noDebeContenerUsuarioDelEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validar("MoisEs-2026-Segura!", "moises@test.com"));
    }

    @Test
    void debeRechazarPasswordDemasiadoRepetitivo() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validar("aaaaaaaaaaaa", "user@test.com"));
    }

    @Test
    void passwordComplejoDebePasar() {
        assertDoesNotThrow(
                () -> service.validar("Nube#48-Rio!2026", "usuario@test.com"));
    }
}
