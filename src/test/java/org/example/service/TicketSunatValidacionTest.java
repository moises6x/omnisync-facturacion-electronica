package org.example.service;

import jakarta.validation.Validation;
import org.example.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TicketSunatValidacionTest {
    @ParameterizedTest
    @ValueSource(ints = {100, 101, 150, 151})
    void entidadesCompartenLimiteSinTruncamiento(int longitud) {
        String ticket = "T".repeat(longitud);
        var rc = ResumenDiario.builder().ticketSunat(ticket).build();
        var ra = ComunicacionBaja.builder().ticketSunat(ticket).build();
        var recibida = RespuestaSunatRecibida.builder().ticketSunat(ticket).build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Object entidad : new Object[]{rc, ra, recibida}) {
                assertEquals(longitud <= 150, factory.getValidator().validateProperty(entidad, "ticketSunat").isEmpty());
            }
        }
        assertEquals(ticket, rc.getTicketSunat()); assertEquals(ticket, ra.getTicketSunat());
        assertEquals(ticket, recibida.getTicketSunat());
    }
}
