package org.example.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SunatDirectSoapClientTest {

    private final SunatDirectSoapClient client = new SunatDirectSoapClient();

    @Test
    void sendBillDebeRechazarRucInvalidoAntesDeAbrirRed() {
        assertThrows(IllegalArgumentException.class,
                () -> client.enviarZipDirecto("123", "USR", "PWD", "a.zip", new byte[]{1}));
    }

    @Test
    void sendBillDebeExigirUsuarioYClaveSol() {
        assertThrows(IllegalArgumentException.class,
                () -> client.enviarZipDirecto("20100130204", " ", "PWD", "a.zip", new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> client.enviarZipDirecto("20100130204", "USR", " ", "a.zip", new byte[]{1}));
    }

    @Test
    void sendBillDebeExigirNombreZipYContenido() {
        assertThrows(IllegalArgumentException.class,
                () -> client.enviarZipDirecto("20100130204", "USR", "PWD", " ", new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> client.enviarZipDirecto("20100130204", "USR", "PWD", "a.zip", new byte[0]));
    }

    @Test
    void sendSummaryDebeAplicarMismasValidacionesDeEntrada() {
        assertThrows(IllegalArgumentException.class,
                () -> client.enviarResumenDirecto("20100130204", "USR", "PWD", "a.zip", new byte[0]));
    }

    @Test
    void getStatusDebeExigirTicket() {
        assertThrows(IllegalArgumentException.class,
                () -> client.consultarTicket("20100130204", "USR", "PWD", " "));
    }

    @Test
    void estadoTicketDebeClasificarCodigosSunat() {
        assertTrue(new SunatDirectSoapClient.EstadoTicketSunat("98", null).enProceso());
        assertTrue(new SunatDirectSoapClient.EstadoTicketSunat("0", new byte[]{1}).finalizadoCorrectamente());
        assertTrue(new SunatDirectSoapClient.EstadoTicketSunat("99", null).finalizadoConErrores());
    }
}
