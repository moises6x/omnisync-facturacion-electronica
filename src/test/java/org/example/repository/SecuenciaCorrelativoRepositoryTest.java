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
class SecuenciaCorrelativoRepositoryTest {

    @Autowired
    private SecuenciaCorrelativoRepository repository;

    @Test
    void secuenciaSoloDebeInicializarseUnaVezPorRucTipoSerie() {
        assertEquals(1, repository.inicializarSiNoExiste("20100130204", "01", "F001"));
        assertEquals(0, repository.inicializarSiNoExiste("20100130204", "01", "F001"));
    }

    @Test
    void mismaSerieDebeSerIndependienteEntreEmpresas() {
        assertEquals(1, repository.inicializarSiNoExiste("20100130204", "01", "F001"));
        assertEquals(1, repository.inicializarSiNoExiste("20600000001", "01", "F001"));

        var a = repository.obtenerSecuenciaBloqueada("20100130204", "01", "F001").orElseThrow();
        var b = repository.obtenerSecuenciaBloqueada("20600000001", "01", "F001").orElseThrow();
        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    void tipoYSerieTambienFormanParteDeLaClave() {
        assertEquals(1, repository.inicializarSiNoExiste("20100130204", "01", "F001"));
        assertEquals(1, repository.inicializarSiNoExiste("20100130204", "03", "B001"));
        assertTrue(repository.obtenerSecuenciaBloqueada("20100130204", "03", "B001").isPresent());
    }
}
