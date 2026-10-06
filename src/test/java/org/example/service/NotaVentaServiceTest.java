package org.example.service;

import org.example.dto.NotaVentaRequest;
import org.example.model.NotaVenta;
import org.example.repository.NotaVentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotaVentaServiceTest {

    @Mock
    private NotaVentaRepository repository;
    @Mock
    private CorrelativoService correlativoService;

    private NotaVentaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotaVentaServiceImpl(repository, correlativoService);
        when(repository.save(any(NotaVenta.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void debeGenerarTicketInternoConCorrelativoPorTienda() {
        NotaVentaRequest request = request(7L, "10.555", "VENTA RAPIDA");
        when(correlativoService.siguiente("20100130204", "NV", "T7")).thenReturn(12);

        NotaVenta nota = service.procesarNotaVenta("20100130204", request);

        assertEquals("NV-T7-00000012", nota.getCodigoTicket());
        assertEquals("20100130204", nota.getRucEmisor());
        assertEquals(0, nota.getMontoTotal().compareTo(new BigDecimal("10.56")));
    }

    @Test
    void observacionNulaDebeUsarTextoPorDefecto() {
        NotaVentaRequest request = request(1L, "5.00", null);
        when(correlativoService.siguiente(anyString(), eq("NV"), eq("T1"))).thenReturn(1);

        NotaVenta nota = service.procesarNotaVenta("20100130204", request);
        assertEquals("Venta rápida sin comprobante SUNAT", nota.getObservacion());
    }

    private NotaVentaRequest request(Long tienda, String monto, String observacion) {
        NotaVentaRequest r = new NotaVentaRequest();
        r.setTiendaId(tienda);
        r.setMontoTotal(new BigDecimal(monto));
        r.setObservacion(observacion);
        return r;
    }
}
