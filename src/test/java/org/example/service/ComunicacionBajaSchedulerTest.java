package org.example.service;

import org.example.model.ComunicacionBaja;
import org.example.model.EstadoComunicacionBaja;
import org.example.repository.ComunicacionBajaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ComunicacionBajaSchedulerTest {
    @Test void primerLoteDefectuosoNoImpideTicketsPosteriores(){
        var repository=mock(ComunicacionBajaRepository.class);var service=mock(ComunicacionBajaService.class);
        var scheduler=new ComunicacionBajaScheduler(repository,service,Clock.systemUTC());
        var tickets=java.util.stream.LongStream.rangeClosed(1,31).mapToObj(id->ComunicacionBaja.builder().id(id).rucEmisor("20100130204").build()).toList();
        when(repository.buscarPendientes(any(),any())).thenAnswer(i->{org.springframework.data.domain.Pageable p=i.getArgument(1);return tickets.stream().skip(p.getOffset()).limit(p.getPageSize()).toList();});
        doThrow(new IllegalStateException("FALLO LOCAL FICTICIO")).when(service).consultarTicket("20100130204",1L);
        scheduler.consultarTickets();scheduler.consultarTickets();
        verify(service).consultarTicket("20100130204",31L);
    }
    @Test
    void debeRecuperarCreadoSinReenviarInciertosYContinuarTrasUnFallo() {
        var repository = mock(ComunicacionBajaRepository.class);
        var service = mock(ComunicacionBajaService.class);
        var scheduler = new ComunicacionBajaScheduler(repository, service,
                Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Lima")));
        when(repository.marcarEnviosVencidosComoInciertos(any())).thenReturn(2);
        when(repository.buscarCreadasSinTicket(any(Pageable.class))).thenReturn(List.of(
                ComunicacionBaja.builder().id(1L).rucEmisor("20100130204").estado(EstadoComunicacionBaja.CREADO).build(),
                ComunicacionBaja.builder().id(2L).rucEmisor("20600000001").estado(EstadoComunicacionBaja.CREADO).build()));
        doThrow(new IllegalStateException("fallo local simulado")).when(service).prepararYEnviar("20100130204", 1L);
        scheduler.recuperarCreadas();
        verify(repository).marcarEnviosVencidosComoInciertos(LocalDateTime.of(2026, 9, 30, 9, 50));
        verify(service).prepararYEnviar("20100130204", 1L);
        verify(service).prepararYEnviar("20600000001", 2L);
        verifyNoMoreInteractions(service);
    }
}
