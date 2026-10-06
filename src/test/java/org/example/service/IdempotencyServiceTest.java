package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.SolicitudEmision;
import org.example.repository.SolicitudEmisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private SolicitudEmisionRepository repository;

    private ObjectMapper mapper;
    private IdempotencyService service;

    record Peticion(String producto, int cantidad) {}

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new IdempotencyService(repository, mapper);
    }

    @Test
    void debeExigirIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service.iniciar("20100130204", " ", new Peticion("A", 1)));
    }

    @Test
    void solicitudNuevaDebeRetornarYaProcesadaFalse() throws Exception {
        Peticion request = new Peticion("A", 1);
        SolicitudEmision solicitud = solicitud("20100130204", "KEY-1", hash(request), null);
        when(repository.obtenerBloqueada("20100130204", "KEY-1"))
                .thenReturn(Optional.of(solicitud));

        var contexto = service.iniciar("20100130204", "KEY-1", request);

        assertFalse(contexto.yaProcesada());
        assertSame(solicitud, contexto.solicitud());
        verify(repository).inicializarSiNoExiste(eq("20100130204"), eq("KEY-1"), anyString());
    }

    @Test
    void solicitudYaVinculadaDebeRetornarYaProcesadaTrue() throws Exception {
        Peticion request = new Peticion("A", 1);
        SolicitudEmision solicitud = solicitud("20100130204", "KEY-1", hash(request), 77L);
        when(repository.obtenerBloqueada(anyString(), anyString())).thenReturn(Optional.of(solicitud));

        var contexto = service.iniciar("20100130204", "KEY-1", request);
        assertTrue(contexto.yaProcesada());
    }

    @Test
    void mismaKeyConPayloadDistintoDebeRechazarse() throws Exception {
        SolicitudEmision solicitud = solicitud(
                "20100130204", "KEY-1", hash(new Peticion("A", 1)), null
        );
        when(repository.obtenerBloqueada(anyString(), anyString())).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalArgumentException.class,
                () -> service.iniciar("20100130204", "KEY-1", new Peticion("B", 2)));
    }

    @Test
    void vincularComprobanteDebePersistirRelacion() {
        SolicitudEmision solicitud = solicitud("20100130204", "KEY", "a".repeat(64), null);
        service.vincularComprobante(solicitud, 99L);
        assertEquals(99L, solicitud.getComprobanteId());
        assertEquals("CREADO", solicitud.getEstado());
        verify(repository).save(solicitud);
    }

    private SolicitudEmision solicitud(String ruc, String key, String hash, Long comprobanteId) {
        return SolicitudEmision.builder()
                .rucEmisor(ruc)
                .idempotencyKey(key)
                .requestHash(hash)
                .comprobanteId(comprobanteId)
                .estado("PROCESANDO")
                .build();
    }

    private String hash(Object value) throws Exception {
        byte[] json = mapper.writeValueAsBytes(value);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    }
}
