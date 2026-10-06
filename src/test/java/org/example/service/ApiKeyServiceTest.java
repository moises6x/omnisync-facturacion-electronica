package org.example.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyServiceTest {

    private final ApiKeyService service = new ApiKeyService();

    @Test
    void debeGenerarApiKeyNoVaciaYUrlSafe() {
        String key = service.generarApiKey();
        assertNotNull(key);
        assertFalse(key.isBlank());
        assertFalse(key.contains("="));
        assertTrue(key.matches("^[A-Za-z0-9_-]+$"));
    }

    @Test
    void apiKeysGeneradasDebenSerDistintas() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100; i++) keys.add(service.generarApiKey());
        assertEquals(100, keys.size());
    }

    @Test
    void hashDebeSerSha256HexDe64Caracteres() {
        String hash = service.hash("clave");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"));
    }

    @Test
    void coincideDebeAceptarClaveCorrecta() {
        String key = service.generarApiKey();
        assertTrue(service.coincide(key, service.hash(key)));
    }

    @Test
    void coincideDebeRechazarNuloVacioOClaveIncorrecta() {
        String hash = service.hash("correcta");
        assertFalse(service.coincide(null, hash));
        assertFalse(service.coincide("", hash));
        assertFalse(service.coincide("otra", hash));
        assertFalse(service.coincide("correcta", null));
    }
}
