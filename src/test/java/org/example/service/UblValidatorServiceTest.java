package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class UblValidatorServiceTest {

    private UblValidatorService service;

    @BeforeEach
    void setUp() {
        service = new UblValidatorService(mock(ResourceLoader.class));
        ReflectionTestUtils.setField(service, "xsdEnabled", false);
    }

    @Test
    void xmlBienFormadoDebePasarCuandoXsdEstaDeshabilitado() {
        byte[] xml = "<Invoice xmlns=\"urn:test\"><ID>1</ID></Invoice>"
                .getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> service.validarFactura(xml));
    }

    @Test
    void xmlNuloOVacioDebeRechazarse() {
        assertThrows(IllegalArgumentException.class, () -> service.validarFactura(null));
        assertThrows(IllegalArgumentException.class, () -> service.validarFactura(new byte[0]));
    }

    @Test
    void xmlMalFormadoDebeRechazarse() {
        byte[] xml = "<Invoice><ID>1</Invoice>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> service.validarFactura(xml));
    }

    @Test
    void doctypeDebeBloquearseParaEvitarXxe() {
        String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><Invoice>&xxe;</Invoice>";
        assertThrows(IllegalArgumentException.class,
                () -> service.validarFactura(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void lasTresFamiliasDebenAplicarParserSeguro() {
        byte[] xml = "<root/>".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> service.validarFactura(xml));
        assertDoesNotThrow(() -> service.validarNotaCredito(xml));
        assertDoesNotThrow(() -> service.validarResumenDiario(xml));
    }
}
