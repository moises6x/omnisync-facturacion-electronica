package org.example.service;

import org.example.dto.ConsultaPublicaCpeRequest;
import org.example.model.EstadoComprobante;
import org.example.repository.ComprobanteRepository;
import org.example.repository.ConsultaPublicaCpeProjection;
import org.junit.jupiter.api.Test;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConsultaPublicaCpeServiceTestt {
    @Test
    void datosExactosDevuelvenValido(){
        var repo=mock(ComprobanteRepository.class); var p=mock(ConsultaPublicaCpeProjection.class); var svc=new ConsultaPublicaCpeService(repo);
        when(p.getFechaEmision()).thenReturn(LocalDateTime.of(2026,9,19,10,0));
        when(p.getMontoTotal()).thenReturn(new BigDecimal("118.00")); when(p.getTipoDocCliente()).thenReturn("6"); when(p.getNumDocCliente()).thenReturn("20600000001"); when(p.getEstadoSfs()).thenReturn(EstadoComprobante.ACEPTADO);
        when(repo.buscarParaConsultaPublica("20100130204","01","F001",1)).thenReturn(Optional.of(p));
        var r=svc.consultar(new ConsultaPublicaCpeRequest("20100130204","01","F001",1, LocalDate.of(2026,9,19),new BigDecimal("118.00"),"6","20600000001"));
        assertTrue(r.encontrado()); assertEquals("VALIDO",r.estado());
    }
    @Test void importeIncorrectoNoExponeDocumento(){
        var repo=mock(ComprobanteRepository.class); var p=mock(ConsultaPublicaCpeProjection.class); var svc=new ConsultaPublicaCpeService(repo);
        when(p.getFechaEmision()).thenReturn(LocalDateTime.of(2026,9,19,10,0)); when(p.getMontoTotal()).thenReturn(new BigDecimal("50.00"));
        when(repo.buscarParaConsultaPublica("20100130204","03","B001",1)).thenReturn(Optional.of(p));
        assertFalse(svc.consultar(new ConsultaPublicaCpeRequest("20100130204","03","B001",1,LocalDate.of(2026,9,19),new BigDecimal("51.00"),null,null)).encontrado());
    }
}
