package org.example.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.example.dto.NotaCreditoRequest;
import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sólo MySQL desechable. Prueba emisión real con idempotencia/repositorios/locks y firma ficticia, sin SOAP. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SaldoNotaCreditoService.class, CdrParserService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaldoNotaCreditoRepositoryTest {
    private static final String RUC = "20100130204", OTRO_RUC = "20600000001";
    @Autowired private ComprobanteRepository repository;
    @Autowired private SolicitudEmisionRepository solicitudes;
    @Autowired private SecuenciaCorrelativoRepository secuencias;
    @Autowired private ResumenDiarioRepository resumenes;
    @Autowired private SaldoNotaCreditoService saldo;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager em;
    private TransactionTemplate tx;
    private FacturacionServiceImpl facturacion;

    @BeforeEach
    void preparar() {
        tx = new TransactionTemplate(transactionManager);
        var empresas = mock(EmpresaRepository.class);
        for (String ruc : List.of(RUC, OTRO_RUC)) {
            Empresa empresa = new Empresa(); empresa.setRuc(ruc); empresa.setIgvPorcentaje(new BigDecimal("18.00"));
            empresa.setPfxBytes(new byte[]{1}); empresa.setPfxPassword("FICTICIO");
            empresa.setUsuarioSol("FICTICIO"); empresa.setClaveSol("FICTICIO");
            when(empresas.findByRuc(ruc)).thenReturn(Optional.of(empresa));
        }
        var calculo = mock(CalculoTributarioService.class);
        when(calculo.calcular(any(NotaCreditoRequest.class), any(BigDecimal.class))).thenAnswer(i -> {
            NotaCreditoRequest r = i.getArgument(0);
            return new CalculoTributarioService.Totales(r.getOpGravada(), r.getOpExonerada(), r.getOpInafecta(), r.getIgv(), r.getTotal(), List.of());
        });
        var xml = mock(UblXmlBuilderService.class);
        when(xml.construirXmlNotaCredito21(any(), any(), anyInt(), any(), any())).thenReturn(new byte[]{1});
        var firma = mock(XmlSignerService.class);
        when(firma.firmarXml(any(), any(), any())).thenReturn(new XmlSignerService.ResultadoFirma(new byte[]{2}, "FICTICIO"));
        var crypto = mock(CryptoService.class);
        when(crypto.descifrarPfx(anyString(), any())).thenReturn(new byte[]{3});
        when(crypto.descifrarPfxPassword(anyString(), any())).thenReturn("FICTICIO");
        facturacion = new FacturacionServiceImpl(repository, empresas, new CorrelativoService(secuencias),
                new IdempotencyService(solicitudes, new ObjectMapper()), calculo, mock(ValidacionTributariaService.class),
                saldo, xml, mock(UblValidatorService.class), firma, crypto, mock(ApplicationEventPublisher.class),
                Clock.fixed(Instant.parse("2026-10-01T17:00:00Z"), ZoneId.of("America/Lima")));
        tx.executeWithoutResult(s -> repository.saveAndFlush(original(RUC)));
    }

    @AfterEach
    void limpiarFixturesPropios() {
        tx.executeWithoutResult(s -> {
            resumenes.findAll().stream().filter(r -> "RC-20261001-99001".equals(r.getIdentificador())).forEach(resumenes::delete);
            resumenes.flush();
            em.createQuery("DELETE FROM SolicitudEmision s WHERE s.idempotencyKey LIKE 'NC-SALDO-%'").executeUpdate();
            em.createQuery("DELETE FROM Comprobante c WHERE c.serie IN ('F901','FC90','B901','BC90') AND c.rucEmisor IN :rucs")
                    .setParameter("rucs", List.of(RUC, OTRO_RUC)).executeUpdate();
            em.createQuery("DELETE FROM SecuenciaCorrelativo s WHERE s.serie IN ('FC90','BC90') AND s.rucEmisor IN :rucs")
                    .setParameter("rucs", List.of(RUC, OTRO_RUC)).executeUpdate();
        });
    }

    @Test
    void original100DosNc70ConcurrentesSoloUnaReservaInclusoConSnapshotPrevio() throws Exception {
        var tomado = new CountDownLatch(1); var snapshot = new CountDownLatch(1); var permitirCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var a = executor.submit(() -> tx.execute(s -> {
                    repository.bloquearOriginalNotaCredito(RUC, "01", "F901", 1).orElseThrow();
                    var resultado = facturacion.procesarNotaCredito(RUC, "NC-SALDO-A", request("70", "09"));
                    tomado.countDown(); esperar(permitirCommit);
                    return resultado.comprobanteId();
                }));
                esperar(tomado);
                var b = executor.submit(() -> {
                    assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> {
                        // Establecer un snapshot viejo ANTES de que A confirme, como puede ocurrir en emisión.
                        assertEquals(1, repository.count());
                        snapshot.countDown();
                        return facturacion.procesarNotaCredito(RUC, "NC-SALDO-B", request("70", "09"));
                    }));
                    return true;
                });
                esperar(snapshot);
                assertThrows(TimeoutException.class, () -> b.get(200, TimeUnit.MILLISECONDS));
                permitirCommit.countDown();
                assertNotNull(a.get(10, TimeUnit.SECONDS)); assertTrue(b.get(10, TimeUnit.SECONDS));
                tx.executeWithoutResult(s -> {
                    var notas = repository.bloquearNotasCreditoDelOriginal(RUC, "01", "F901", 1);
                    assertEquals(1, notas.size()); assertEquals(0, new BigDecimal("70").compareTo(notas.getFirst().getTotal()));
                    assertEquals("PENDIENTE_ENVIO", notas.getFirst().getEstado());
                    assertTrue(solicitudes.obtenerBloqueada(RUC, "NC-SALDO-B").isEmpty());
                });
            } finally { permitirCommit.countDown(); }
        }
    }

    @Test
    void idempotenciaConcurrenteGeneraUnaSolaNcYReserva() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> emitir = () -> {
                esperar(inicio);
                return tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-IDEM", request("70", "09")).comprobanteId());
            };
            var a = executor.submit(emitir); var b = executor.submit(emitir); inicio.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        tx.executeWithoutResult(s -> assertEquals(1, repository.bloquearNotasCreditoDelOriginal(RUC, "01", "F901", 1).size()));
    }

    @Test
    void multiplesParcialesYReplayNoDuplicanConsumo() {
        var a = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-30", request("30", "09")));
        var b = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-70", request("70", "09")));
        assertNotEquals(a.comprobanteId(), b.comprobanteId());
        assertEquals(a.comprobanteId(), tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-30", request("30", "09"))).comprobanteId());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-EXCESO", request("1", "09"))));
    }

    @Test
    void aceptacionConvierteReservaEnConsumoSinRestituirSaldo() {
        Long id = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-ACEPTAR", request("70", "09")).comprobanteId());
        repository.marcarEnviando(id);
        Comprobante resultado = repository.findById(id).orElseThrow(); resultado.setEstadoSfs(EstadoComprobante.ACEPTADO);
        saldo.guardarResultadoIndividual(resultado);
        assertEquals(EstadoComprobante.ACEPTADO, repository.findById(id).orElseThrow().getEstadoSfs());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-OTRA", request("31", "09"))));
        assertDoesNotThrow(() -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-RESTO", request("30", "09"))));
    }

    @Test
    void timeoutConservaReservaYNoPuedeReenviarse() {
        Long id = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-TIMEOUT", request("70", "09")).comprobanteId());
        repository.marcarEnviando(id);
        Comprobante resultado = repository.findById(id).orElseThrow(); resultado.setEstadoSfs(EstadoComprobante.ERROR_ENVIO_INCIERTO);
        saldo.guardarResultadoIndividual(resultado);
        assertEquals(0, repository.marcarEnviando(id));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-DESPUES", request("70", "09"))));
    }

    @Test
    void ncDeOtroRucNoReduceSaldoNiPermiteAccederAlOriginalAjeno() {
        tx.executeWithoutResult(s -> repository.saveAndFlush(original(OTRO_RUC)));
        tx.execute(s -> facturacion.procesarNotaCredito(OTRO_RUC, "NC-SALDO-AJENA", request("100", "09")));
        assertDoesNotThrow(() -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-PROPIA", request("100", "09"))));
        tx.executeWithoutResult(s -> {
            Long id = repository.bloquearOriginalNotaCredito(RUC, "01", "F901", 1).orElseThrow().getId();
            assertTrue(repository.bloquearPorIdYRuc(id, OTRO_RUC).isEmpty());
        });
    }

    @Test
    void proyeccionBloqueanteNoReutilizaEntidadAntiguaDelPersistenceContext() {
        Long id = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-LECTURA", request("70", "09")).comprobanteId());
        tx.executeWithoutResult(s -> {
            Comprobante viejo = repository.findById(id).orElseThrow(); assertEquals(EstadoComprobante.PENDIENTE_ENVIO, viejo.getEstadoSfs());
            em.createNativeQuery("UPDATE comprobante SET estado_sfs='ACEPTADO' WHERE id=:id").setParameter("id", id).executeUpdate();
            var actual = repository.bloquearNotasCreditoDelOriginal(RUC, "01", "F901", 1).getFirst();
            assertEquals("ACEPTADO", actual.getEstado());
            assertEquals(EstadoComprobante.PENDIENTE_ENVIO, viejo.getEstadoSfs());
        });
    }

    @Test
    void falloDespuesDeGuardarRevierteNcIdempotenciaYCorrelativo() {
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
            facturacion.procesarNotaCredito(RUC, "NC-SALDO-ROLLBACK", request("70", "09"));
            throw new IllegalStateException("fallo controlado antes del commit");
        }));
        var resultado = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-ROLLBACK", request("70", "09")));
        assertEquals(1, resultado.numero());
        tx.executeWithoutResult(s -> assertEquals(1, repository.bloquearNotasCreditoDelOriginal(RUC, "01", "F901", 1).size()));
    }

    @Test
    void cambioDePayloadConMismaClaveSeRechaza() {
        tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-PAYLOAD", request("30", "09")));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-PAYLOAD", request("40", "09"))));
    }

    @Test
    void rechazoTerminalValidoLiberaPeroRespuestaTardiaNoPuedeReabrirlo() throws Exception {
        Long id = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-RECHAZAR", request("100", "09")).comprobanteId());
        repository.marcarEnviando(id);
        Comprobante resultado = repository.findById(id).orElseThrow();
        byte[] xml = ("<ApplicationResponse xmlns='urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2' "
                + "xmlns:cac='urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2' "
                + "xmlns:cbc='urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2'>"
                + "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>FC90-1</cbc:ReferenceID>"
                + "<cbc:ResponseCode>2100</cbc:ResponseCode><cbc:Description>RECHAZADO</cbc:Description>"
                + "</cac:Response><cac:DocumentReference><cbc:ID>FC90-1</cbc:ID></cac:DocumentReference>"
                + "</cac:DocumentResponse></ApplicationResponse>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("R.xml")); zip.write(xml); zip.closeEntry();
        }
        resultado.setEstadoSfs(EstadoComprobante.RECHAZADO); resultado.setCodigoRespuestaSunat("2100");
        resultado.setCdrZipBytes(bytes.toByteArray()); resultado.setCdrXmlBytes(xml);
        saldo.guardarResultadoIndividual(resultado);
        assertEquals(0, repository.marcarEnviando(id));
        assertDoesNotThrow(() -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-TRAS-RECHAZO", request("100", "01"))));
        resultado.setEstadoSfs(EstadoComprobante.ACEPTADO);
        saldo.guardarResultadoIndividual(resultado);
        assertEquals(EstadoComprobante.RECHAZADO, repository.findById(id).orElseThrow().getEstadoSfs());
    }

    @Test
    void confirmacionDebeEsperarElMismoBloqueoDelOriginal() throws Exception {
        Long id = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-BLOQUEO", request("70", "09")).comprobanteId());
        repository.marcarEnviando(id);
        Comprobante resultado = repository.findById(id).orElseThrow(); resultado.setEstadoSfs(EstadoComprobante.ACEPTADO);
        var tomado = new CountDownLatch(1); var liberar = new CountDownLatch(1); var iniciado = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var emision = executor.submit(() -> tx.execute(s -> {
                    repository.bloquearOriginalNotaCredito(RUC, "01", "F901", 1).orElseThrow();
                    tomado.countDown(); esperar(liberar); return true;
                }));
                esperar(tomado);
                var confirmacion = executor.submit(() -> { iniciado.countDown(); saldo.guardarResultadoIndividual(resultado); return true; });
                esperar(iniciado);
                assertThrows(TimeoutException.class, () -> confirmacion.get(200, TimeUnit.MILLISECONDS));
                liberar.countDown(); assertTrue(emision.get(10, TimeUnit.SECONDS)); assertTrue(confirmacion.get(10, TimeUnit.SECONDS));
            } finally { liberar.countDown(); }
        }
        assertEquals(EstadoComprobante.ACEPTADO, repository.findById(id).orElseThrow().getEstadoSfs());
    }

    @Test
    void rcRechazadoYLiberacionManualConservanReservaDeNc() {
        tx.executeWithoutResult(s -> {
            Comprobante o = repository.bloquearOriginalNotaCredito(RUC, "01", "F901", 1).orElseThrow();
            o.setTipoComprobante("03"); o.setSerie("B901"); repository.saveAndFlush(o);
        });
        NotaCreditoRequest r = request("70", "09"); r.setTipoComprobanteAfectado("03"); r.setNumComprobanteAfectado("B901-1"); r.setSerie("BC90");
        Long ncId = tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-RC", r).comprobanteId());
        Long rcId = tx.execute(s -> {
            assertEquals(1, repository.marcarEnResumen(ncId, RUC));
            ResumenDiario rc = ResumenDiario.builder().rucEmisor(RUC).fechaDocumentos(LocalDate.of(2026, 10, 1))
                    .fechaGeneracion(LocalDate.of(2026, 10, 1)).correlativo(99001).identificador("RC-20261001-99001")
                    .nombreArchivoXml(RUC + "-RC-20261001-99001.xml").estado(EstadoResumenDiario.TICKET_GENERADO).ticketSunat("TICKET-FICTICIO").build();
            rc.addDetalle(ResumenDiarioDetalle.builder().comprobanteId(ncId).numeroLinea(1).tipoComprobante("07").serie("BC90").numero(1)
                    .tipoDocCliente("1").numDocCliente("48411579").opGravada(BigDecimal.ZERO).opExonerada(new BigDecimal("70"))
                    .opInafecta(BigDecimal.ZERO).igv(BigDecimal.ZERO).montoTotal(new BigDecimal("70")).build());
            return resumenes.saveAndFlush(rc).getId();
        });
        var empresa = new Empresa(); empresa.setRuc(RUC); empresa.setPfxBytes(new byte[]{1}); empresa.setPfxPassword("FICTICIO");
        empresa.setUsuarioSol("FICTICIO"); empresa.setClaveSol("FICTICIO");
        var empresas = mock(EmpresaRepository.class); when(empresas.findByRuc(RUC)).thenReturn(Optional.of(empresa));
        var client = mock(SunatDirectSoapClient.class);
        when(client.consultarTicket(eq(RUC), any(), any(), eq("TICKET-FICTICIO")))
                .thenReturn(new SunatDirectSoapClient.EstadoTicketSunat("0", new byte[]{42}));
        var parser = mock(CdrParserService.class);
        when(parser.procesar(any())).thenReturn(new CdrParserService.ResultadoCdr("2100", "RECHAZADO", List.of(), false, new byte[]{2}));
        var servicioRc = new ResumenDiarioService(resumenes, repository, empresas, mock(SecuenciaResumenDiarioService.class),
                mock(ResumenDiarioXmlService.class), mock(XmlSignerService.class), mock(UblValidatorService.class), client, parser,
                mock(CryptoService.class), mock(SunatPlazoService.class), Clock.system(ZoneId.of("America/Lima")), transactionManager,
                mock(RespuestaSunatRecibidaService.class));
        servicioRc.consultarTicketInterno(RUC, rcId);
        assertEquals(EstadoResumenDiario.RECHAZADO, resumenes.findById(rcId).orElseThrow().getEstado());
        assertEquals(EstadoComprobante.EN_RESUMEN, repository.findById(ncId).orElseThrow().getEstadoSfs());
        NotaCreditoRequest exceso = request("70", "09"); exceso.setTipoComprobanteAfectado("03"); exceso.setNumComprobanteAfectado("B901-1"); exceso.setSerie("BC90");
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-RC-EXCESO", exceso)));
        servicioRc.liberarDocumentos(RUC, rcId);
        assertEquals(EstadoComprobante.PENDIENTE_RESUMEN, repository.findById(ncId).orElseThrow().getEstadoSfs());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> facturacion.procesarNotaCredito(RUC, "NC-SALDO-RC-LIBERADO", exceso)));
    }

    private static Comprobante original(String ruc) {
        return Comprobante.builder().tiendaId(1L).rucEmisor(ruc).tipoOperacion("0101").tipoComprobante("01").serie("F901").numero(1)
                .fechaEmision(LocalDateTime.of(2026, 10, 1, 10, 0)).moneda("PEN").tipoDocCliente("1").numDocCliente("48411579")
                .rznSocialCliente("CLIENTE TEST").opGravada(BigDecimal.ZERO).opExonerada(new BigDecimal("100"))
                .opInafecta(BigDecimal.ZERO).igv(BigDecimal.ZERO).igvPorcentaje(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("100")).estadoSfs(EstadoComprobante.ACEPTADO).build();
    }

    private static NotaCreditoRequest request(String importe, String motivo) {
        var r = new NotaCreditoRequest(); r.setTiendaId(1L); r.setSerie("FC90"); r.setTipoComprobanteAfectado("01");
        r.setNumComprobanteAfectado("F901-1"); r.setCodMotivoSunat(motivo); r.setDescripcionMotivo("PRUEBA CONTROLADA");
        r.setTipoDocCliente("1"); r.setNumDocCliente("48411579"); r.setRznSocialCliente("CLIENTE TEST");
        r.setOpGravada(BigDecimal.ZERO); r.setOpExonerada(new BigDecimal(importe)); r.setOpInafecta(BigDecimal.ZERO);
        r.setIgv(BigDecimal.ZERO); r.setTotal(new BigDecimal(importe)); r.setItems(List.of()); return r;
    }

    private static void esperar(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
