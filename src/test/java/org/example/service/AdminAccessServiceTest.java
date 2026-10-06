package org.example.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminAccessServiceTest {

    private static final String KEY = "12345678901234567890123456789012";

    @Test
    void constructorDebeExigirMinimo32Caracteres() {
        assertThrows(IllegalStateException.class, () -> new AdminAccessService("corta"));
    }

    @Test
    void debeAceptarCredencialCorrecta() {
        AdminAccessService service = new AdminAccessService(KEY);
        assertDoesNotThrow(() -> service.validar(KEY));
    }

    @Test
    void debeRechazarCredencialIncorrecta() {
        AdminAccessService service = new AdminAccessService(KEY);
        assertThrows(SecurityException.class, () -> service.validar("otra-clave-123456789012345678901234"));
    }

    @Test
    void debeRechazarCredencialVaciaONula() {
        AdminAccessService service = new AdminAccessService(KEY);
        assertThrows(SecurityException.class, () -> service.validar(null));
        assertThrows(SecurityException.class, () -> service.validar(" "));
    }
}
