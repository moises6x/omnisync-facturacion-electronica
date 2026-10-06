package org.example.repository;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato preparado para metadata futura: fixtures, no ejecución de la constraint propuesta. */
class TicketSunatMetadataTest {
    record ColumnaIndice(String columna, int posicion, boolean noUnico, Integer prefijo) {}

    static boolean esUniqueEmpresaTicket(List<ColumnaIndice> columnas) {
        return columnas.size() == 2
                && columnas.stream().allMatch(c -> !c.noUnico() && c.prefijo() == null)
                && columnas.stream().anyMatch(c -> c.posicion() == 1 && c.columna().equals("ruc_emisor"))
                && columnas.stream().anyMatch(c -> c.posicion() == 2 && c.columna().equals("ticket_sunat"));
    }

    @Test void metadataCompuestaCorrectaCumpleContrato() {
        assertTrue(esUniqueEmpresaTicket(List.of(new ColumnaIndice("ruc_emisor", 1, false, null),
                new ColumnaIndice("ticket_sunat", 2, false, null))));
    }
    @Test void unicidadGlobalNoCumpleContrato() {
        assertFalse(esUniqueEmpresaTicket(List.of(new ColumnaIndice("ticket_sunat", 1, false, null))));
    }
    @Test void indiceNoUnicoNoAcreditaConstraint() {
        assertFalse(esUniqueEmpresaTicket(List.of(new ColumnaIndice("ruc_emisor", 1, true, null),
                new ColumnaIndice("ticket_sunat", 2, true, null))));
    }
    @Test void prefijosNoAcreditanUnicidadDelTicketCompleto() {
        assertFalse(esUniqueEmpresaTicket(List.of(new ColumnaIndice("ruc_emisor", 1, false, null),
                new ColumnaIndice("ticket_sunat", 2, false, 100))));
    }
}
