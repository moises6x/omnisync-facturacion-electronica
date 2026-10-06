package org.example.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SolicitudEmisionRepositoryTest {

    @Autowired
    private SolicitudEmisionRepository repository;

    @Test
    void mismaKeySoloDebeInicializarseUnaVezPorEmpresa() {
        String hash = "a".repeat(64);
        assertEquals(1, repository.inicializarSiNoExiste("20100130204", "KEY-1", hash));
        assertEquals(0, repository.inicializarSiNoExiste("20100130204", "KEY-1", hash));
    }

    @Test
    void mismaKeyPuedeExistirEnEmpresasDiferentes() {
        assertEquals(1, repository.inicializarSiNoExiste("20100130204", "KEY-X", "a".repeat(64)));
        assertEquals(1, repository.inicializarSiNoExiste("20600000001", "KEY-X", "b".repeat(64)));

        assertEquals("20100130204", repository.obtenerBloqueada("20100130204", "KEY-X").orElseThrow().getRucEmisor());
        assertEquals("20600000001", repository.obtenerBloqueada("20600000001", "KEY-X").orElseThrow().getRucEmisor());
    }

    @Test
    void consultaBloqueadaNoDebeCruzarEmpresa() {
        repository.inicializarSiNoExiste("20100130204", "KEY-A", "a".repeat(64));
        assertTrue(repository.obtenerBloqueada("20600000001", "KEY-A").isEmpty());
    }
}
