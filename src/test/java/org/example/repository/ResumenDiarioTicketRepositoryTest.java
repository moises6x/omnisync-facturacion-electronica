package org.example.repository;

import org.example.auth.service.AuditoriaService;
import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MySQL exclusivo de pruebas; SOAP simulado y evidencia ZIP real, sin contexto de aplicación completo. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RespuestaSunatRecibidaService.class, ResumenDiarioTicketRepositoryTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResumenDiarioTicketRepositoryTest {
    private static final String A = "20100130204", B = "20600000001";
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 1);
    @TestConfiguration static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-01T17:00:00Z"), ZoneId.of("America/Lima")); }
    }
    @Autowired private ResumenDiarioRepository resumenes;
    @Autowired private EmpresaRepository empresas;
    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private RespuestaSunatRecibidaRepository respuestas;
    @Autowired private RespuestaSunatRecibidaService evidencia;
    @Autowired private PlatformTransactionManager tm;
    @Autowired private Clock clock;
    private TransactionTemplate tx;
    private SunatDirectSoapClient client;
    private AuditoriaService auditoria;
    private ResumenDiarioService service;
    private final List<Long> rcIds = new ArrayList<>(), empresaIds = new ArrayList<>(), documentoIds = new ArrayList<>();

    @BeforeEach void preparar() {
        tx = new TransactionTemplate(tm);
        client = mock(SunatDirectSoapClient.class);
        auditoria = mock(AuditoriaService.class);
        for (String ruc : List.of(A, B)) {
            empresaIds.add(tx.execute(s -> empresas.saveAndFlush(Empresa.builder().ruc(ruc)
                    .razonSocial("EMISOR FICTICIO").pfxBytes(new byte[]{1}).pfxPassword("FICTICIO")
                    .usuarioSol("FICTICIO").claveSol("FICTICIO").build()).getId()));
        }
        service = servicio(comprobantes);
    }

    @AfterEach void limpiarSoloFixtures() {
        tx.executeWithoutResult(s -> {
            for (Long id : rcIds) {
                for (String ruc : List.of(A, B)) {
                    respuestas.deleteAll(respuestas.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(ruc, RespuestaSunatRecibida.Flujo.RC, id));
                }
                resumenes.deleteById(id);
            }
            resumenes.flush();
            documentoIds.forEach(comprobantes::deleteById);
            empresaIds.forEach(empresas::deleteById);
        });
    }

    @Test void dosRcMismoTenantSeFinalizanSoloPorSuPropioTicket() throws Exception {
        Long uno = rc(A, 1, "AAA111"), dos = rc(A, 2, "AAA222");
        responder("AAA111", "0", cdr("0", "RB-20261001-1", "OTRA-REFERENCIA", ""));
        responder("AAA222", "0", cdr("2335", "RC-20261001-2", "OTRO-ID", ""));
        service.consultarTicketInterno(A, uno);
        assertEquals(EstadoResumenDiario.ACEPTADO, leer(uno).getEstado());
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(dos).getEstado());
        service.consultarTicketInterno(A, dos);
        assertEquals(EstadoResumenDiario.RECHAZADO, leer(dos).getEstado());
        assertEquals("0", leer(uno).getCodigoRespuestaSunat());
        assertEquals("AAA111", leer(uno).getTicketSunat());
        verify(client).consultarTicket(eq(A), any(), any(), eq("AAA111"));
        verify(client).consultarTicket(eq(A), any(), any(), eq("AAA222"));
    }

    @Test void sendSummaryPersisteSuTicketAntesDeGetStatusSinLocksDuranteSoap() {
        Long id = rc(A, 1, null);
        tx.executeWithoutResult(s -> resumenes.bloquearPorIdYRuc(id, A).orElseThrow().setEstado(EstadoResumenDiario.CREADO));
        prepararFirmaSimulada();
        when(client.enviarResumenDirecto(eq(A), any(), any(), eq(A + "-RC-20261001-1.zip"), any())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(EstadoResumenDiario.ENVIANDO, leer(id).getEstado());
            assertNull(leer(id).getTicketSunat());
            return "AAA111";
        });
        when(client.consultarTicket(eq(A), any(), any(), eq("AAA111"))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("AAA111", leer(id).getTicketSunat());
            assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(id).getEstado());
            return new SunatDirectSoapClient.EstadoTicketSunat("98", null);
        });
        service.prepararYEnviar(A, id);
        assertEquals(EstadoResumenDiario.PROCESANDO, leer(id).getEstado());
        assertEquals("AAA111", leer(id).getTicketSunat());
        verify(client).enviarResumenDirecto(eq(A), any(), any(), eq(A + "-RC-20261001-1.zip"), any());
        verify(client).consultarTicket(eq(A), any(), any(), eq("AAA111"));
    }

    @Test void ticketDuplicadoDevueltoPorSendSummaryNoSeAsociaNiSeReenvia() {
        Long duenio = rc(A, 1, "AAA111"), segundo = rc(A, 2, null);
        tx.executeWithoutResult(s -> resumenes.bloquearPorIdYRuc(segundo, A).orElseThrow().setEstado(EstadoResumenDiario.CREADO));
        prepararFirmaSimulada();
        when(client.enviarResumenDirecto(eq(A), any(), any(), anyString(), any())).thenReturn("AAA111");
        service.prepararYEnviar(A, segundo);
        assertEquals(EstadoResumenDiario.ERROR_ENVIO_INCIERTO, leer(segundo).getEstado());
        assertNull(leer(segundo).getTicketSunat());
        assertEquals("AAA111", leer(duenio).getTicketSunat());
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(duenio).getEstado());
        service.prepararYEnviar(A, segundo);
        verify(client, times(1)).enviarResumenDirecto(eq(A), any(), any(), anyString(), any());
        verify(client, never()).consultarTicket(any(), any(), any(), any());
    }

    @Test void dosTenantsConIdentificadoresSimilaresNoSeCruzan() throws Exception {
        Long uno = rc(A, 1, "AAA111"), dos = rc(B, 1, "BBB111");
        assertEquals(leer(uno).getIdentificador(), leer(dos).getIdentificador());
        responder("BBB111", "0", cdr("0", "RB-20261001-1", "DOCUMENTO", ""));
        service.consultarTicketInterno(B, dos);
        assertEquals(EstadoResumenDiario.ACEPTADO, leer(dos).getEstado());
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(uno).getEstado());
        assertThrows(IllegalArgumentException.class, () -> service.consultarTicketInterno(B, uno));
        verify(client).consultarTicket(eq(B), any(), any(), eq("BBB111"));
        verifyNoMoreInteractions(client);
    }

    @Test void consultaRepetidaYServicioRecreadoNoReaplicanCdr() throws Exception {
        Long id = rc(A, 1, "AAA111");
        responder("AAA111", "0", cdr("0", "RB-20261001-1", "DOCUMENTO", ""));
        service.consultarTicketInterno(A, id);
        byte[] original = leer(id).getCdrZipBytes();
        servicio(comprobantes).consultarTicketInterno(A, id);
        service.consultarTicketInterno(A, id);
        assertEquals(1, leer(id).getIntentosConsulta());
        assertArrayEquals(original, leer(id).getCdrZipBytes());
        assertEquals(1, evidencias(id).size());
        verify(client, times(1)).consultarTicket(any(), any(), any(), eq("AAA111"));
        verify(auditoria, times(1)).registrarSistema(eq(A), eq("CDR_RESUMEN_DIARIO"), eq("ACEPTADO"), anyString());
    }

    @Test void dosWorkersConResultadoSimultaneoSoloHacenUnaTransicion() throws Exception {
        Long id = rc(A, 1, "AAA111");
        byte[] zip = cdr("0", "RB-20261001-1", "DISTINTO", "");
        var ambasEnSoap = new CountDownLatch(2);
        when(client.consultarTicket(eq(A), any(), any(), eq("AAA111"))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ambasEnSoap.countDown(); esperar(ambasEnSoap);
            return new SunatDirectSoapClient.EstadoTicketSunat("0", zip);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var uno = executor.submit(() -> service.consultarTicketInterno(A, id));
            var dos = executor.submit(() -> service.consultarTicketInterno(A, id));
            uno.get(15, TimeUnit.SECONDS); dos.get(15, TimeUnit.SECONDS);
        }
        assertEquals(EstadoResumenDiario.ACEPTADO, leer(id).getEstado());
        assertEquals(1, leer(id).getIntentosConsulta());
        assertEquals(2, evidencias(id).size()); // Ambas recepciones se preservan, no se purgan duplicados.
        verify(auditoria, times(1)).registrarSistema(eq(A), eq("CDR_RESUMEN_DIARIO"), eq("ACEPTADO"), anyString());
    }

    @Test void estado98PersisteYDespuesElMismoTicketPuedeAceptar() throws Exception {
        Long id = rc(A, 1, "AAA111");
        responder("AAA111", "98", null);
        service.consultarTicketInterno(A, id);
        assertEquals(EstadoResumenDiario.PROCESANDO, leer(id).getEstado());
        assertEquals("98", leer(id).getCodigoRespuestaSunat());
        assertNull(leer(id).getFinalizadoEn());
        responder("AAA111", "0", cdr("0", "RB-20261001-1", "DISTINTO", ""));
        service.consultarTicketInterno(A, id);
        assertEquals(EstadoResumenDiario.ACEPTADO, leer(id).getEstado());
        assertEquals(2, leer(id).getIntentosConsulta());
    }

    @Test void estado99SinCdrConservaRechazoExistente() {
        Long id = rc(A, 1, "AAA111"); responder("AAA111", "99", null);
        service.consultarTicketInterno(A, id);
        assertEquals(EstadoResumenDiario.RECHAZADO, leer(id).getEstado());
        assertEquals("99", leer(id).getCodigoRespuestaSunat());
    }

    @Test void aceptacionConObservacionNoDependeDeReferencias() throws Exception {
        Long id = rc(A, 1, "AAA111");
        responder("AAA111", "0", cdr("0", "RB-20261001-1", "DISTINTO", "<cbc:Note>OBSERVACION</cbc:Note>"));
        service.consultarTicketInterno(A, id);
        assertEquals(EstadoResumenDiario.ACEPTADO_CON_OBSERVACION, leer(id).getEstado());
        assertEquals("0", leer(id).getCodigoRespuestaSunat());
    }

    @Test void referenciasDistintasYBytesOriginalesQuedanAuditablesSinTransformaciones() throws Exception {
        Long id = rc(A, 1, "AAA111"); byte[] zip = cdr("0", "RB-20261001-1", "OTRO-DOCUMENTO", "");
        responder("AAA111", "0", zip);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ResumenDiarioService.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        try {
            service.consultarTicketInterno(A, id);
            assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains("CDR_REFERENCE_MISMATCH")
                    && e.getFormattedMessage().contains("RB-20261001-1") && e.getFormattedMessage().contains("OTRO-DOCUMENTO")));
            assertTrue(logs.list.stream().noneMatch(e -> e.getFormattedMessage().contains("ApplicationResponse")
                    || e.getFormattedMessage().contains("FICTICIO")));
        } finally { logger.detachAppender(logs); logs.stop(); }
        assertEquals(EstadoResumenDiario.ACEPTADO, leer(id).getEstado());
        assertArrayEquals(zip, leer(id).getCdrZipBytes());
        var bytes = evidencias(id).getFirst();
        assertArrayEquals(zip, bytes.getRespuestaZipBytes());
        assertEquals("AAA111", bytes.getTicketSunat()); assertEquals(A, bytes.getRucEmisor());
        var parsed = new CdrParserService().procesar(bytes.getRespuestaZipBytes());
        assertEquals("RB-20261001-1", parsed.referenceId());
        assertEquals("OTRO-DOCUMENTO", parsed.documentReferenceId());
        assertArrayEquals(parsed.xml(), leer(id).getCdrXmlBytes());
    }

    @ParameterizedTest @ValueSource(strings = {"corrupto", "ausente", "vacio", "duplicado"})
    void cdrInvalidoNuncaAceptaYPreservaRespuesta(String caso) throws Exception {
        Long id = rc(A, 1, "AAA111");
        String xml = xml("0", "REFERENCIA", "DOCUMENTO", "");
        xml = switch (caso) {
            case "corrupto" -> "<ApplicationResponse>";
            case "ausente" -> xml.replace("<cbc:ReferenceID>REFERENCIA</cbc:ReferenceID>", "");
            case "vacio" -> xml.replace("REFERENCIA", " ");
            default -> xml.replace("</cac:Response>", "<cbc:ReferenceID>OTRA</cbc:ReferenceID></cac:Response>");
        };
        byte[] zip = zip(xml); responder("AAA111", "0", zip);
        service.consultarTicketInterno(A, id);
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA, leer(id).getEstado());
        assertNull(leer(id).getCdrZipBytes()); assertNull(leer(id).getFinalizadoEn());
        assertArrayEquals(zip, evidencias(id).getFirst().getRespuestaZipBytes());
        verifyNoInteractions(auditoria);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "98", "99", "error"})
    void respuestaDeTicketAnteriorNoActualizaTicketReasignadoExternamente(String status) throws Exception {
        Long id = rc(A, 1, "AAA111"); byte[] zip = cdr("0", "RB-20261001-1", "DOCUMENTO", "");
        when(client.consultarTicket(eq(A), any(), any(), eq("AAA111"))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            // Corrupción externa simulada, no API autorizada de reasignación.
            tx.executeWithoutResult(s -> resumenes.bloquearPorIdYRuc(id, A).orElseThrow().setTicketSunat("AAA222"));
            if (status.equals("error")) throw new SunatDirectSoapClient.SunatTransportException("FALLO SIMULADO");
            return new SunatDirectSoapClient.EstadoTicketSunat(status, status.equals("0") ? zip : null);
        });
        service.consultarTicketInterno(A, id);
        assertEquals("AAA222", leer(id).getTicketSunat());
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(id).getEstado());
        assertEquals(0, leer(id).getIntentosConsulta());
        assertNull(leer(id).getCdrZipBytes());
        if (status.equals("0")) assertEquals("AAA111", evidencias(id).getFirst().getTicketSunat());
        verifyNoInteractions(auditoria);
    }

    @Test void duplicadosHistoricosNoEligenPrimerRcNiConsultanSunat() {
        Long uno = rc(A, 1, "AAA111"), dos = rc(A, 2, "AAA111");
        service.consultarTicketInterno(A, uno); service.consultarTicketInterno(A, dos);
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(uno).getEstado());
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(dos).getEstado());
        verifyNoInteractions(client, auditoria);
    }

    @Test void conflictosAntiguosNoDebenMonopolizarLoteAcotadoNiCruzarTenants() {
        Long uno = rc(A, 1, "CONFLICTO"), dos = rc(A, 2, "CONFLICTO");
        Long tres = rc(A, 3, "CONFLICTO2"), cuatro = rc(A, 4, "CONFLICTO2");
        Long validoOtroTenant = rc(B, 1, "CONFLICTO"); // Mismo texto en otro tenant es válido.
        Long valido = rc(A, 5, "VALIDO");
        var estados = EnumSet.of(EstadoResumenDiario.TICKET_GENERADO, EstadoResumenDiario.PROCESANDO, EstadoResumenDiario.ERROR_CONSULTA);
        var lote = resumenes.buscarPendientesConsulta(estados, org.springframework.data.domain.PageRequest.of(0, 1));
        assertEquals(List.of(validoOtroTenant), lote.stream().map(ResumenDiario::getId).toList());
        responder("CONFLICTO", "98", null); responder("VALIDO", "98", null);
        service.consultarTicketsPendientes(1);
        assertEquals(EstadoResumenDiario.PROCESANDO, leer(validoOtroTenant).getEstado());
        assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(valido).getEstado());
        verify(client, times(1)).consultarTicket(eq(B), any(), any(), eq("CONFLICTO"));
        for (Long id : List.of(uno, dos, tres, cuatro)) {
            assertEquals(EstadoResumenDiario.TICKET_GENERADO, leer(id).getEstado());
            assertEquals(0, leer(id).getIntentosConsulta());
        }
        service.consultarTicketsPendientes(1);
        assertEquals(EstadoResumenDiario.PROCESANDO, leer(valido).getEstado());
        verify(client, times(1)).consultarTicket(eq(A), any(), any(), eq("VALIDO"));
        verifyNoMoreInteractions(client);
    }

    @Test void ticketExistenteNoPuedeReasignarseDesdeServicio() {
        Long id = rc(A, 1, "AAA111");
        assertThrows(IllegalStateException.class, () -> registrar(A, id, "AAA222"));
        assertEquals("AAA111", leer(id).getTicketSunat());
    }

    @Test void lockEmpresaYRucDeCabeceraNoPermitenAsociacionCruzada() {
        Long id = rc(A, 1, null);
        assertThrows(NoSuchElementException.class, () -> registrar(B, id, "BBB111"));
        assertNull(leer(id).getTicketSunat());
        tx.executeWithoutResult(s -> assertTrue(empresas.bloquearPorRuc("20999999999").isEmpty()));
    }

    @Test void asociacionesConcurrentesDelMismoTicketSeSerializanInclusoConSnapshotPrevio() throws Exception {
        Long uno = rc(A, 1, null), dos = rc(A, 2, null);
        var empresaTomada = new CountDownLatch(1); var snapshotLeido = new CountDownLatch(1); var liberar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var ganador = executor.submit(() -> tx.execute(s -> {
                    empresas.bloquearPorRuc(A).orElseThrow(); empresaTomada.countDown(); esperar(liberar);
                    return registrar(A, uno, "AAA111");
                }));
                esperar(empresaTomada);
                var perdedor = executor.submit(() -> tx.execute(s -> {
                    assertFalse(resumenes.existsByRucEmisorAndTicketSunatAndIdNot(A, "AAA111", dos));
                    snapshotLeido.countDown();
                    return registrar(A, dos, "AAA111");
                }));
                esperar(snapshotLeido);
                assertThrows(TimeoutException.class, () -> perdedor.get(200, TimeUnit.MILLISECONDS));
                liberar.countDown(); assertTrue(ganador.get(10, TimeUnit.SECONDS));
                var fallo = assertThrows(ExecutionException.class, () -> perdedor.get(10, TimeUnit.SECONDS));
                assertInstanceOf(IllegalStateException.class, fallo.getCause());
            } finally { liberar.countDown(); }
        }
        assertEquals("AAA111", leer(uno).getTicketSunat()); assertNull(leer(dos).getTicketSunat());
        assertEquals(EstadoResumenDiario.ENVIANDO, leer(dos).getEstado());
    }

    @Test void falloDePersistenciaRevierteCabeceraYDocumentosPeroNoEvidenciaRecibida() throws Exception {
        Long id = rc(A, 1, "AAA111");
        Long doc = tx.execute(s -> comprobantes.saveAndFlush(Comprobante.builder().tiendaId(1L).rucEmisor(A)
                .tipoOperacion("0101").tipoComprobante("03").serie("B903").numero(1).fechaEmision(FECHA.atTime(10, 0))
                .moneda("PEN").tipoDocCliente("1").numDocCliente("48411579").rznSocialCliente("CLIENTE FICTICIO")
                .opGravada(BigDecimal.ZERO).opExonerada(BigDecimal.TEN).opInafecta(BigDecimal.ZERO)
                .igv(BigDecimal.ZERO).montoTotal(BigDecimal.TEN).estadoSfs(EstadoComprobante.EN_RESUMEN).build()).getId());
        documentoIds.add(doc);
        tx.executeWithoutResult(s -> resumenes.bloquearPorIdYRuc(id, A).orElseThrow().addDetalle(ResumenDiarioDetalle.builder()
                .comprobanteId(doc).numeroLinea(1).tipoComprobante("03").serie("B903").numero(1).tipoDocCliente("1")
                .numDocCliente("48411579").opGravada(BigDecimal.ZERO).opExonerada(BigDecimal.TEN)
                .opInafecta(BigDecimal.ZERO).igv(BigDecimal.ZERO).montoTotal(BigDecimal.TEN).build()));
        var fallando = mock(ComprobanteRepository.class);
        when(fallando.actualizarEstadoDesdeResumen(eq(A), anyList(), any(), anyString(), anyString())).thenAnswer(inv -> {
            comprobantes.actualizarEstadoDesdeResumen(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4));
            throw new IllegalStateException("Fallo simulado después de escribir documentos y cabecera");
        });
        byte[] zip = cdr("0", "RB-20261001-1", "DOCUMENTO", ""); responder("AAA111", "0", zip);
        servicio(fallando).consultarTicketInterno(A, id);
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA, leer(id).getEstado());
        assertNull(leer(id).getCdrZipBytes()); assertNull(leer(id).getCodigoRespuestaSunat()); assertNull(leer(id).getFinalizadoEn());
        assertEquals(EstadoComprobante.EN_RESUMEN, comprobantes.findById(doc).orElseThrow().getEstadoSfs());
        assertArrayEquals(zip, evidencias(id).getFirst().getRespuestaZipBytes());
        verifyNoInteractions(auditoria);
        servicio(comprobantes).consultarTicketInterno(A, id); // Recuperación segura usando el mismo ticket.
        assertEquals(EstadoResumenDiario.ACEPTADO, leer(id).getEstado());
        assertEquals(EstadoComprobante.ACEPTADO, comprobantes.findById(doc).orElseThrow().getEstadoSfs());
    }

    @Test void ticketRecibidoPeroCommitFallidoNoSeInventaNiSeReenviaTrasRecrearServicio() {
        Long id=rc(A,1,null);
        tx.executeWithoutResult(s -> resumenes.bloquearPorIdYRuc(id,A).orElseThrow().setEstado(EstadoResumenDiario.CREADO));
        prepararFirmaSimulada();
        when(client.enviarResumenDirecto(eq(A),any(),any(),anyString(),any())).thenReturn("TICKET-RECIBIDO-NO-CONFIRMADO");
        var fallando=mock(ResumenDiarioRepository.class, org.mockito.AdditionalAnswers.delegatesTo(resumenes));
        doAnswer(inv -> {
            ResumenDiario cabecera=inv.getArgument(0);
            resumenes.saveAndFlush(cabecera);
            if(cabecera.getTicketSunat()!=null) throw new IllegalStateException("Caída simulada antes de commit ticket");
            return cabecera;
        }).when(fallando).save(any(ResumenDiario.class));
        ReflectionTestUtils.setField(service,"resumenRepository",fallando);
        service.prepararYEnviar(A,id);
        assertNull(leer(id).getTicketSunat());
        assertArrayEquals(new byte[]{2},leer(id).getXmlFirmadoBytes());
        assertEquals(EstadoResumenDiario.ERROR_ENVIO_INCIERTO,leer(id).getEstado());
        servicio(comprobantes).prepararYEnviar(A,id);
        verify(client,times(1)).enviarResumenDirecto(eq(A),any(),any(),anyString(),any());
        verify(client,never()).consultarTicket(any(),any(),any(),any());
    }

    @Test void cdrRecibidoAntesDeCommitBrutoPuedeRecuperarsePorTicketSinSendSummary() throws Exception {
        Long id=rc(A,1,"TICKET-PERSISTIDO");byte[] zip=cdr("0","RB-20261001-1","DOCUMENTO","");
        responder("TICKET-PERSISTIDO","0",zip);
        var archivoFallando=mock(RespuestaSunatRecibidaService.class);
        doThrow(new IllegalStateException("Caída simulada antes de primer commit bruto"))
                .when(archivoFallando).conservar(eq(A),eq(RespuestaSunatRecibida.Flujo.RC),eq(id),anyString(),same(zip));
        ReflectionTestUtils.setField(service,"respuestaSunatRecibidaService",archivoFallando);
        service.consultarTicketInterno(A,id);
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA,leer(id).getEstado());
        assertNull(leer(id).getCdrZipBytes());assertTrue(evidencias(id).isEmpty());
        var reiniciado=servicio(comprobantes);
        reiniciado.consultarTicketInterno(A,id);
        reiniciado.consultarTicketInterno(A,id);
        assertEquals(EstadoResumenDiario.ACEPTADO,leer(id).getEstado());
        assertArrayEquals(zip,evidencias(id).getFirst().getRespuestaZipBytes());
        verify(client,times(2)).consultarTicket(eq(A),any(),any(),eq("TICKET-PERSISTIDO"));
        verify(client,never()).enviarResumenDirecto(any(),any(),any(),any(),any());
        verify(auditoria,times(1)).registrarSistema(eq(A),eq("CDR_RESUMEN_DIARIO"),eq("ACEPTADO"),anyString());
    }

    @Test void parserFallidoTrasCommitBrutoPermiteConciliacionOfflineControladaSinOtraLlamada() throws Exception {
        Long id=rc(A,1,"TICKET-PERSISTIDO");byte[] zip=cdr("0","RB-20261001-1","DOCUMENTO","");
        responder("TICKET-PERSISTIDO","0",zip);
        var parserFallando=mock(CdrParserService.class);
        when(parserFallando.procesar(zip)).thenThrow(new IllegalStateException("Parser transitoriamente no disponible"));
        ReflectionTestUtils.setField(service,"cdrParserService",parserFallando);
        service.consultarTicketInterno(A,id);
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA,leer(id).getEstado());
        assertNull(leer(id).getCdrZipBytes());
        var archivo=evidencias(id).getFirst();assertArrayEquals(zip,archivo.getRespuestaZipBytes());
        var resultado=new CdrParserService().procesar(archivo.getRespuestaZipBytes());
        assertTrue(resultado.aceptado());assertEquals("0",resultado.responseCode());
        assertEquals("TICKET-PERSISTIDO",archivo.getTicketSunat());
        verify(client,times(1)).consultarTicket(eq(A),any(),any(),eq("TICKET-PERSISTIDO"));
        verifyNoInteractions(auditoria);
        // Diagnóstico offline NO aplica una transición fiscal ni afirma aceptación persistida.
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA,leer(id).getEstado());
        // Ensayo manual controlado: reutilizar finalizador existente, sin nueva API ni replay automático.
        var offline=servicio(comprobantes);
        ReflectionTestUtils.invokeMethod(offline,"finalizarConCdr",A,id,archivo.getTicketSunat(),archivo.getRespuestaZipBytes(),resultado);
        ReflectionTestUtils.invokeMethod(offline,"finalizarConCdr",A,id,archivo.getTicketSunat(),archivo.getRespuestaZipBytes(),resultado);
        assertEquals(EstadoResumenDiario.ACEPTADO,leer(id).getEstado());
        assertArrayEquals(zip,leer(id).getCdrZipBytes());assertArrayEquals(resultado.xml(),leer(id).getCdrXmlBytes());
        assertEquals(1,evidencias(id).size());
        verify(client,times(1)).consultarTicket(eq(A),any(),any(),eq("TICKET-PERSISTIDO"));
        verify(auditoria,times(1)).registrarSistema(eq(A),eq("CDR_RESUMEN_DIARIO"),eq("ACEPTADO"),anyString());
    }

    private ResumenDiarioService servicio(ComprobanteRepository docs) {
        var service = new ResumenDiarioService(resumenes, docs, empresas, mock(SecuenciaResumenDiarioService.class),
                mock(ResumenDiarioXmlService.class), mock(XmlSignerService.class), mock(UblValidatorService.class), client,
                new CdrParserService(), mock(CryptoService.class), mock(SunatPlazoService.class), clock, tm, evidencia);
        ReflectionTestUtils.setField(service, "auditoriaService", auditoria);
        return service;
    }
    @Test void falloLocalDePrimerTicketNoImpideSiguienteTicketEnOtroCiclo(){
        Long primero=rc(A,96,"TICKET-DEFECTUOSO");Long segundo=rc(B,96,"TICKET-VALIDO");
        tx.executeWithoutResult(s->empresas.bloquearPorRuc(A).orElseThrow().setUsuarioSol(null));
        when(client.consultarTicket(eq(B),any(),any(),eq("TICKET-VALIDO")))
                .thenReturn(new SunatDirectSoapClient.EstadoTicketSunat("98",null));
        service.consultarTicketsPendientes(1);service.consultarTicketsPendientes(1);
        verify(client).consultarTicket(eq(B),any(),any(),eq("TICKET-VALIDO"));
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA,leer(primero).getEstado());
        assertEquals(EstadoResumenDiario.PROCESANDO,leer(segundo).getEstado());
    }
    @Test void errorEnvioHistoricoConIntentoNoPermiteReintentoNiLiberacion(){
        Long id=rc(A,97,null);
        tx.executeWithoutResult(s->{var r=resumenes.bloquearPorIdYRuc(id,A).orElseThrow();r.setEstado(EstadoResumenDiario.ERROR_ENVIO);r.setIntentosEnvio(1);});
        assertThrows(IllegalStateException.class,()->service.reintentarEnvio(A,id));
        service.prepararYEnviar(A,id);verifyNoInteractions(client);
        assertThrows(IllegalStateException.class,()->service.liberarDocumentos(A,id));
        assertEquals(EstadoResumenDiario.ERROR_ENVIO,leer(id).getEstado());
    }
    @Test void faultLocalAnteriorARedPermiteReprocesarMismaCabecera(){
        Long id=rc(A,98,null);tx.executeWithoutResult(s->resumenes.bloquearPorIdYRuc(id,A).orElseThrow().setEstado(EstadoResumenDiario.CREADO));
        prepararFirmaSimulada();
        var xml=(ResumenDiarioXmlService)ReflectionTestUtils.getField(service,"xmlService");
        doThrow(new SunatDirectSoapClient.SunatSoapFaultException("Server","FICTICIO",500)).when(xml).construir(any(),any());
        service.prepararYEnviar(A,id);assertEquals(EstadoResumenDiario.ERROR_VALIDACION,leer(id).getEstado());
        assertEquals(0,leer(id).getIntentosEnvio());verifyNoInteractions(client);
        doReturn(new byte[]{1}).when(xml).construir(any(),any());
        when(client.enviarResumenDirecto(eq(A),any(),any(),anyString(),any())).thenReturn("TICKET-RECUPERADO");
        when(client.consultarTicket(eq(A),any(),any(),eq("TICKET-RECUPERADO"))).thenReturn(new SunatDirectSoapClient.EstadoTicketSunat("98",null));
        assertEquals(id,service.reintentarEnvio(A,id).id());
        assertEquals(EstadoResumenDiario.PROCESANDO,leer(id).getEstado());
    }
    @Test void updateIncompletoRevierteFinalizacionYConservaZipParaConciliacion()throws Exception{
        Long id=rc(A,99,"TICKET-INCOMPLETO");
        Long doc=tx.execute(s->{var c=Fase3HistoricosRepositoryTest.doc(99);c.setRucEmisor(A);c.setEstadoSfs(EstadoComprobante.PENDIENTE_RESUMEN);
            c=comprobantes.saveAndFlush(c);resumenes.bloquearPorIdYRuc(id,A).orElseThrow().addDetalle(Fase3HistoricosRepositoryTest.detalle(c.getId()));return c.getId();});
        documentoIds.add(doc);responder("TICKET-INCOMPLETO","0",cdr("0","RB-20261001-99","OTRA-REFERENCIA",""));
        service.consultarTicketInterno(A,id);
        assertEquals(EstadoResumenDiario.ERROR_CONSULTA,leer(id).getEstado());assertNull(leer(id).getCdrXmlBytes());
        assertEquals(EstadoComprobante.PENDIENTE_RESUMEN,comprobantes.findById(doc).orElseThrow().getEstadoSfs());
        assertEquals(1,evidencias(id).size());verifyNoInteractions(auditoria);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Server", "Client.3205", "DESCONOCIDO"})
    void faultPosteriorNoPermiteReintentoNiWorkerSinTicket(String codigo) {
        Long id = rc(A, 91, null);
        tx.executeWithoutResult(s -> resumenes.bloquearPorIdYRuc(id, A).orElseThrow().setEstado(EstadoResumenDiario.CREADO));
        prepararFirmaSimulada();
        when(client.enviarResumenDirecto(eq(A), any(), any(), anyString(), any()))
                .thenThrow(new SunatDirectSoapClient.SunatSoapFaultException(codigo, "FICTICIO", 500));
        service.prepararYEnviar(A, id);
        assertEquals(EstadoResumenDiario.ERROR_ENVIO_INCIERTO, leer(id).getEstado());
        assertNull(leer(id).getTicketSunat());
        assertThrows(IllegalStateException.class, () -> servicio(comprobantes).reintentarEnvio(A, id));
        servicio(comprobantes).prepararYEnviar(A, id);
        verify(client, times(1)).enviarResumenDirecto(eq(A), any(), any(), anyString(), any());
        verify(client, never()).consultarTicket(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reintentar", "liberar", "nuevo"})
    void liberacionSeSerializaConOtraInstancia(String operacion) throws Exception {
        Long id = rc(A, 92, null);
        Long doc = tx.execute(s -> {
            var c = Fase3HistoricosRepositoryTest.doc(92); c.setRucEmisor(A); c.setEstadoSfs(EstadoComprobante.EN_RESUMEN);
            c = comprobantes.saveAndFlush(c);
            var r = resumenes.bloquearPorIdYRuc(id, A).orElseThrow(); r.setEstado(EstadoResumenDiario.ERROR_ENVIO);
            r.addDetalle(Fase3HistoricosRepositoryTest.detalle(c.getId())); return c.getId();
        });
        documentoIds.add(doc);
        var dentro = new CountDownLatch(1); var continuar = new CountDownLatch(1); var segunda = new CountDownLatch(1);
        var docs = mock(ComprobanteRepository.class, org.mockito.AdditionalAnswers.delegatesTo(comprobantes));
        doAnswer(inv -> {
            dentro.countDown(); esperar(continuar);
            return comprobantes.liberarDocumentosDelResumen(A, id, List.of(doc));
        }).when(docs).liberarDocumentosDelResumen(A, id, List.of(doc));
        var uno = servicio(docs); var dos = servicio(comprobantes);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var f1 = pool.submit(() -> uno.liberarDocumentos(A, id)); esperar(dentro);
                var f2 = pool.submit(() -> {
                    segunda.countDown();
                    if (operacion.equals("nuevo")) {
                        return tx.execute(s -> comprobantes.marcarEnResumen(doc, A));
                    }
                    assertThrows(IllegalStateException.class, () -> {
                        if (operacion.equals("liberar")) dos.liberarDocumentos(A, id); else dos.reintentarEnvio(A, id);
                    });
                    return 0;
                });
                esperar(segunda);
                // El nuevo RC no puede tomar un documento aún EN_RESUMEN; las otras operaciones esperan cabecera.
                if (!operacion.equals("nuevo")) assertThrows(TimeoutException.class, () -> f2.get(200, TimeUnit.MILLISECONDS));
                continuar.countDown(); f1.get(10, TimeUnit.SECONDS);
                int tomado = f2.get(10, TimeUnit.SECONDS);
                assertEquals(EstadoResumenDiario.LIBERADO, leer(id).getEstado());
                if (operacion.equals("nuevo") && tomado == 1) {
                    assertEquals(EstadoComprobante.EN_RESUMEN, comprobantes.findById(doc).orElseThrow().getEstadoSfs());
                    assertThrows(IllegalStateException.class, () -> uno.liberarDocumentos(A, id));
                } else assertEquals(EstadoComprobante.PENDIENTE_RESUMEN, comprobantes.findById(doc).orElseThrow().getEstadoSfs());
            } finally { continuar.countDown(); }
        }
        verify(client, never()).enviarResumenDirecto(any(), any(), any(), any(), any());
    }

    @Test void noLiberaDocumentosConOtroPropietarioActivo() {
        Long id = rc(A, 93, null), otro = rc(A, 94, "OTRO-TICKET");
        Long doc = tx.execute(s -> {
            var c = Fase3HistoricosRepositoryTest.doc(93); c.setRucEmisor(A); c.setEstadoSfs(EstadoComprobante.EN_RESUMEN);
            c = comprobantes.saveAndFlush(c);
            var r = resumenes.bloquearPorIdYRuc(id, A).orElseThrow(); r.setEstado(EstadoResumenDiario.ERROR_ENVIO);
            r.addDetalle(Fase3HistoricosRepositoryTest.detalle(c.getId()));
            resumenes.bloquearPorIdYRuc(otro, A).orElseThrow().addDetalle(Fase3HistoricosRepositoryTest.detalle(c.getId()));
            return c.getId();
        });
        documentoIds.add(doc);
        assertThrows(IllegalStateException.class, () -> service.liberarDocumentos(A, id));
        assertEquals(EstadoResumenDiario.ERROR_ENVIO, leer(id).getEstado());
        assertEquals(EstadoComprobante.EN_RESUMEN, comprobantes.findById(doc).orElseThrow().getEstadoSfs());
    }
    private void prepararFirmaSimulada() {
        var xml = (ResumenDiarioXmlService) ReflectionTestUtils.getField(service, "xmlService");
        var signer = (XmlSignerService) ReflectionTestUtils.getField(service, "xmlSignerService");
        when(xml.construir(any(), any())).thenReturn(new byte[]{1});
        when(signer.firmarXml(any(), any(), any())).thenReturn(new XmlSignerService.ResultadoFirma(new byte[]{2}, "HASH-FICTICIO"));
    }
    private Long rc(String ruc, int numero, String ticket) {
        String identificador = "RC-20261001-" + numero;
        Long id = tx.execute(s -> resumenes.saveAndFlush(ResumenDiario.builder().rucEmisor(ruc).fechaDocumentos(FECHA)
                .fechaGeneracion(FECHA).correlativo(numero).identificador(identificador).nombreArchivoXml(ruc + "-" + identificador + ".xml")
                .estado(ticket == null ? EstadoResumenDiario.ENVIANDO : EstadoResumenDiario.TICKET_GENERADO).ticketSunat(ticket).build()).getId());
        rcIds.add(id); return id;
    }
    private boolean registrar(String ruc, Long id, String ticket) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "registrarTicket", ruc, id, ticket));
    }
    private ResumenDiario leer(Long id) { return resumenes.findById(id).orElseThrow(); }
    private List<RespuestaSunatRecibida> evidencias(Long id) {
        return respuestas.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(A, RespuestaSunatRecibida.Flujo.RC, id);
    }
    private void responder(String ticket, String status, byte[] zip) {
        when(client.consultarTicket(anyString(), any(), any(), eq(ticket))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new SunatDirectSoapClient.EstadoTicketSunat(status, zip);
        });
    }
    private static byte[] cdr(String codigo, String referencia, String documento, String notas) throws Exception {
        return zip(xml(codigo, referencia, documento, notas));
    }
    private static String xml(String codigo, String referencia, String documento, String notas) {
        return """
                <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                  xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                  xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  %s<cac:DocumentResponse><cac:Response><cbc:ReferenceID>%s</cbc:ReferenceID>
                  <cbc:ResponseCode>%s</cbc:ResponseCode><cbc:Description>RESULTADO FICTICIO</cbc:Description></cac:Response>
                  <cac:DocumentReference><cbc:ID>%s</cbc:ID></cac:DocumentReference></cac:DocumentResponse>
                </ApplicationResponse>
                """.formatted(notas, referencia, codigo, documento);
    }
    private static byte[] zip(String xml) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("R.xml")); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return out.toByteArray();
    }
    private static void esperar(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
