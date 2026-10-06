package org.example.service;

import org.example.repository.ComprobanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvioSunatRecoverySchedulerTest {

    @Mock private ComprobanteRepository repository;
    @Mock private EnvioSunatService envioSunatService;

    private EnvioSunatRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Executor directo = Runnable::run;
        scheduler = new EnvioSunatRecoveryScheduler(repository, envioSunatService, directo,
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-30T15:00:00Z"), java.time.ZoneId.of("America/Lima")));
        ReflectionTestUtils.setField(scheduler, "batchSize", 20);
    }

    @Test
    void sinPendientesNoDebeEnviarNada() {
        when(repository.buscarIdsPendientesEnvio(any(Pageable.class))).thenReturn(List.of());
        scheduler.recuperarPendientes();
        verifyNoInteractions(envioSunatService);
    }

    @Test
    void debeClasificarVencidosSinEnviarlosYUsarClockCentral() {
        when(repository.marcarEnviosVencidosComoInciertos(any())).thenReturn(3);
        when(repository.buscarIdsPendientesEnvio(any(Pageable.class))).thenReturn(List.of());
        scheduler.recuperarPendientes();
        verify(repository).marcarEnviosVencidosComoInciertos(java.time.LocalDateTime.of(2026, 9, 30, 9, 50));
        verifyNoInteractions(envioSunatService);
    }

    @Test
    void debeEnviarTodosLosIdsPendientesEncontrados() {
        when(repository.buscarIdsPendientesEnvio(any(Pageable.class))).thenReturn(List.of(10L, 11L, 12L));
        scheduler.recuperarPendientes();
        verify(envioSunatService).enviar(10L);
        verify(envioSunatService).enviar(11L);
        verify(envioSunatService).enviar(12L);
    }

    @Test
    void batchDebeAcotarseAMaximo100() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 999);
        when(repository.buscarIdsPendientesEnvio(any(Pageable.class))).thenReturn(List.of());
        scheduler.recuperarPendientes();

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarIdsPendientesEnvio(captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void batchCeroDebeNormalizarseAUno() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 0);
        when(repository.buscarIdsPendientesEnvio(any(Pageable.class))).thenReturn(List.of());
        scheduler.recuperarPendientes();

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarIdsPendientesEnvio(captor.capture());
        assertEquals(1, captor.getValue().getPageSize());
    }
}
