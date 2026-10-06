package org.example.service;

import org.example.model.SecuenciaCorrelativo;
import org.example.repository.SecuenciaCorrelativoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelativoServiceTest {

    @Mock
    private SecuenciaCorrelativoRepository repository;

    private CorrelativoService service;

    @BeforeEach
    void setUp() {
        service = new CorrelativoService(repository);
    }

    @Test
    void siguienteDebeInicializarBloquearIncrementarYGuardar() {
        SecuenciaCorrelativo secuencia = secuencia("20100130204", "01", "F001", 7);
        when(repository.obtenerSecuenciaBloqueada("20100130204", "01", "F001"))
                .thenReturn(Optional.of(secuencia));

        int siguiente = service.siguiente("20100130204", "01", "F001");

        assertEquals(8, siguiente);
        assertEquals(8, secuencia.getUltimoNumero());
        verify(repository).inicializarSiNoExiste("20100130204", "01", "F001");
        verify(repository).save(secuencia);
    }

    @Test
    void siguienteDebeRechazarSecuenciaAgotada() {
        SecuenciaCorrelativo secuencia = secuencia("20100130204", "01", "F001", 99_999_999);
        when(repository.obtenerSecuenciaBloqueada(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(secuencia));
        assertThrows(IllegalStateException.class,
                () -> service.siguiente("20100130204", "01", "F001"));
    }

    @Test
    void configurarFacturaDebeExigirSerieF() {
        assertThrows(IllegalArgumentException.class,
                () -> service.configurarInicial("20100130204", "01", "B001", 100));
    }

    @Test
    void configurarBoletaDebeExigirSerieB() {
        assertThrows(IllegalArgumentException.class,
                () -> service.configurarInicial("20100130204", "03", "F001", 100));
    }

    @Test
    void configurarNotaCreditoDebeAceptarSerieFoB() {
        SecuenciaCorrelativo a = secuencia("20100130204", "07", "FC01", 0);
        when(repository.obtenerSecuenciaBloqueada("20100130204", "07", "FC01"))
                .thenReturn(Optional.of(a));
        assertEquals(11, service.configurarInicial("20100130204", "07", "FC01", 10));
    }

    @Test
    void noDebePermitirReconfigurarSecuenciaYaUsada() {

        SecuenciaCorrelativo secuencia =
                secuencia(
                        "20100130204",
                        "01",
                        "F001",
                        5
                );

        when(
                repository.obtenerSecuenciaBloqueada(
                        anyString(),
                        anyString(),
                        anyString()
                )
        )
                .thenReturn(
                        Optional.of(secuencia)
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.configurarInicial(
                                "20100130204",
                                "01",
                                "F001",
                                100
                        )
        );
    }

    @Test
    void configurarInicialDebeGuardarUltimoNumeroYRetornarSiguiente() {
        SecuenciaCorrelativo secuencia = secuencia("20100130204", "01", "F001", 0);
        when(repository.obtenerSecuenciaBloqueada("20100130204", "01", "F001"))
                .thenReturn(Optional.of(secuencia));

        int siguiente = service.configurarInicial("20100130204", "01", "F001", 1000);

        assertEquals(1001, siguiente);
        assertEquals(1000, secuencia.getUltimoNumero());
        verify(repository).saveAndFlush(secuencia);
    }

    private SecuenciaCorrelativo secuencia(String ruc, String tipo, String serie, int ultimo) {
        return SecuenciaCorrelativo.builder()
                .rucEmisor(ruc)
                .tipoComprobante(tipo)
                .serie(serie)
                .ultimoNumero(ultimo)
                .build();
    }
}
