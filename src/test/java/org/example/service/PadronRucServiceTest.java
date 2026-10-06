package org.example.service;

import org.example.model.PadronRuc;
import org.example.repository.PadronRucRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PadronRucServiceTest {

    @Mock
    private PadronRucRepository repository;

    private PadronRucService service;

    @BeforeEach
    void setUp() {
        service = new PadronRucService(repository);
    }

    @Test
    void rucVacioDebeRechazarse() {
        assertThrows(IllegalArgumentException.class, () -> service.consultar(" "));
    }

    @Test
    void rucConDigitoVerificadorIncorrectoDebeRechazarse() {
        assertThrows(IllegalArgumentException.class, () -> service.consultar("20100130205"));
    }

    @Test
    void rucValidoNoEncontradoDebeResponderEncontradoFalse() {
        when(repository.findById("20100130204")).thenReturn(Optional.empty());
        var response = service.consultar("20100130204");
        assertFalse(response.encontrado());
        assertEquals("20100130204", response.ruc());
    }

    @Test
    void debeMapearActivoYHabido() {
        PadronRuc p = new PadronRuc();
        p.setRuc("20100130204");
        p.setRazonSocial(" EMPRESA TEST ");
        p.setEstado("ACTIVO");
        p.setCondicion("HABIDO");
        p.setUbigeo("150101");
        p.setTipoVia("AV.");
        p.setNombreVia("PRUEBA");
        p.setNumero("123");
        when(repository.findById("20100130204")).thenReturn(Optional.of(p));

        var response = service.consultar("20100130204");
        assertTrue(response.encontrado());
        assertTrue(response.activo());
        assertTrue(response.habido());
        assertEquals("EMPRESA TEST", response.razonSocial());
        assertNotNull(response.direccion());
    }
}
