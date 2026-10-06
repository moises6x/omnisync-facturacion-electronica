package org.example.service;

import org.example.event.ComprobanteCreadoEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ComprobanteEnvioListenerTest {

    @Test
    void debeDelegarComprobanteAlServicioSunat() {
        EnvioSunatService envio = mock(EnvioSunatService.class);
        ComprobanteEnvioListener listener = new ComprobanteEnvioListener(envio);
        listener.enviar(new ComprobanteCreadoEvent(55L));
        verify(envio).enviar(55L);
    }
}
