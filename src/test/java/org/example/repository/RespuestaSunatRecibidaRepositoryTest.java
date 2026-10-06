package org.example.repository;

import org.example.model.RespuestaSunatRecibida;
import org.example.service.RespuestaSunatRecibidaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RespuestaSunatRecibidaRepositoryTest {
    private static final String RUC = "20100130204";
    @Autowired private RespuestaSunatRecibidaRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    private RespuestaSunatRecibidaService service;

    @BeforeEach
    void setUp() {
        var factory = new ProxyFactory(new RespuestaSunatRecibidaService(repository,
                Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Lima"))));
        factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(transactionManager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice);
        service = (RespuestaSunatRecibidaService) factory.getProxy();
    }

    @AfterEach
    void limpiarSoloFixturesPropios() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> repository.findAll().stream()
                .filter(r -> r.getDocumentoId().equals(900001L)).forEach(repository::delete));
    }

    @ParameterizedTest
    @EnumSource(RespuestaSunatRecibida.Flujo.class)
    void debeConservarBytesInvalidosSinInventarAceptacion(RespuestaSunatRecibida.Flujo flujo) throws Exception {
        byte[] invalida = "RESPUESTA INVALIDA NO ES ZIP NI CDR".getBytes(StandardCharsets.UTF_8);
        byte[] original = invalida.clone();
        service.conservar(RUC, flujo, 900001L, "TICKET-TEST", invalida);
        invalida[0] = 0;
        var recibidas = repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, flujo, 900001L);
        assertEquals(1, recibidas.size()); var r = recibidas.getFirst();
        assertArrayEquals(original, r.getRespuestaZipBytes()); assertEquals("TICKET-TEST", r.getTicketSunat());
        assertEquals(LocalDateTime.of(2026, 9, 30, 10, 0), r.getRecibidoEn());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)), r.getSha256());
    }

    @Test
    void debeConservarCadaRespuestaSinReemplazarNiMezclarEmpresas() {
        service.conservar(RUC, RespuestaSunatRecibida.Flujo.RC, 900001L, "TICKET-TEST", new byte[]{1});
        service.conservar(RUC, RespuestaSunatRecibida.Flujo.RC, 900001L, "TICKET-TEST", new byte[]{2});
        service.conservar("20600000001", RespuestaSunatRecibida.Flujo.RC, 900001L, "OTRO-TICKET-TEST", new byte[]{3});
        var propias = repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, RespuestaSunatRecibida.Flujo.RC, 900001L);
        assertEquals(2, propias.size()); assertArrayEquals(new byte[]{1}, propias.get(0).getRespuestaZipBytes());
        assertArrayEquals(new byte[]{2}, propias.get(1).getRespuestaZipBytes());
        assertTrue(repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, RespuestaSunatRecibida.Flujo.RA, 900001L).isEmpty());
    }

    @Test
    void rollbackDeInterpretacionNoDebeBorrarLaRespuestaRecibida() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            service.conservar(RUC, RespuestaSunatRecibida.Flujo.CPE, 900001L, null, new byte[]{42});
            throw new IllegalStateException("parser/finalización falló después de recepción");
        }));
        assertEquals(1, repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, RespuestaSunatRecibida.Flujo.CPE, 900001L).size());
    }

    @Test
    void ausenciaDeBytesNoDebeInventarUnaRespuesta() {
        service.conservar(RUC, RespuestaSunatRecibida.Flujo.CPE, 900001L, null, null);
        service.conservar(RUC, RespuestaSunatRecibida.Flujo.CPE, 900001L, null, new byte[0]);
        assertTrue(repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, RespuestaSunatRecibida.Flujo.CPE, 900001L).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"CPE,100", "CPE,101", "CPE,150", "RC,100", "RC,101", "RC,150", "RA,100", "RA,101", "RA,150"})
    void debeArchivarTicketCompletoSinTruncamiento(RespuestaSunatRecibida.Flujo flujo, int longitud) {
        String ticket = "T".repeat(longitud);
        service.conservar(RUC, flujo, 900001L, ticket, new byte[]{42});
        var recibida = repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, flujo, 900001L).getFirst();
        assertEquals(ticket, recibida.getTicketSunat());
        assertArrayEquals(new byte[]{42}, recibida.getRespuestaZipBytes());
    }

    @Test
    void ticketMayorDe150NoDebeArchivarseNiTruncarse() {
        assertThrows(IllegalArgumentException.class, () -> service.conservar(RUC,
                RespuestaSunatRecibida.Flujo.RC, 900001L, "T".repeat(151), new byte[]{42}));
        assertTrue(repository.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC,
                RespuestaSunatRecibida.Flujo.RC, 900001L).isEmpty());
    }
}
