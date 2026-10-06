package org.example.repository;

import org.example.auth.service.AuditoriaService;
import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MySQL desechable; sin contexto completo, certificados, SUNAT ni padrón. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComunicacionBajaRepositoryTest {
    private static final String RUC = "20100130204";
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 30);
    @Autowired private ComunicacionBajaRepository bajas;
    @Autowired private ComprobanteRepository documentos;
    @Autowired private RespuestaSunatRecibidaRepository respuestas;
    @Autowired private PlatformTransactionManager transactionManager;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final List<Long> idsDocumentos = new ArrayList<>();
    private final List<Long> idsBajas = new ArrayList<>();
    private TransactionTemplate tx;
    private SunatDirectSoapClient sunat;
    private CdrParserService parser;
    private AuditoriaService auditoria;
    private ComunicacionBajaService service;
    private ComunicacionBajaRepository repositorioServicio;
    private ComunicacionBajaXmlService xml;
    private XmlSignerService signer;
    private CryptoService crypto;
    private SunatPlazoService plazo;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        sunat = mock(SunatDirectSoapClient.class); parser = mock(CdrParserService.class);
        auditoria = mock(AuditoriaService.class);
        repositorioServicio = mock(ComunicacionBajaRepository.class, org.mockito.AdditionalAnswers.delegatesTo(bajas));
        EmpresaRepository empresas = mock(EmpresaRepository.class);
        Empresa e = new Empresa(); e.setRuc(RUC); e.setUsuarioSol("CIF_TEST"); e.setClaveSol("CIF_TEST");
        e.setPfxBytes(new byte[]{1}); e.setPfxPassword("CIF_TEST");
        when(empresas.findByRuc(RUC)).thenReturn(Optional.of(e));
        lenient().when(empresas.bloquearPorRuc(RUC)).thenReturn(Optional.of(e));
        crypto = mock(CryptoService.class);
        xml = mock(ComunicacionBajaXmlService.class); signer = mock(XmlSignerService.class);
        plazo = mock(SunatPlazoService.class);
        when(crypto.descifrarUsuarioSol(anyString(), anyString())).thenReturn("SOL_TEST");
        when(crypto.descifrarClaveSol(anyString(), anyString())).thenReturn("SOL_TEST");
        service = new ComunicacionBajaService(repositorioServicio, documentos, empresas,
                mock(SecuenciaComunicacionBajaService.class), xml,
                signer, mock(UblValidatorService.class), sunat, parser, crypto,
                plazo, auditoria,
                Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Lima")), transactionManager,
                servicioRespuestas());
    }

    @AfterEach
    void limpiarSoloFixturesPropios() {
        tx.executeWithoutResult(s -> {
            respuestas.findAll().stream().filter(r -> r.getRucEmisor().equals(RUC) && r.getFlujo()==RespuestaSunatRecibida.Flujo.RA
                    && idsBajas.contains(r.getDocumentoId())).forEach(respuestas::delete);
            idsBajas.forEach(bajas::deleteById); bajas.flush();
            idsDocumentos.forEach(documentos::deleteById); documentos.flush();
        });
    }

    @Test
    void aceptacionDebeConfirmarDocumentosYEvidenciaJuntosSinRedEnTransaccion() {
        Long c1 = documento(RUC, 81), c2 = documento(RUC, 82);
        Long id = baja(c1, c2); respuesta(true);
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            comprobarAceptacion(id, c1, c2); // la auditoría observa el commit, no una intención
            return null;
        }).when(auditoria).registrarSistema(anyString(), anyString(), anyString(), anyString());
        service.consultarTicket(RUC, id);
        comprobarAceptacion(id, c1, c2);
        verify(auditoria).registrarSistema(eq(RUC), eq("COMUNICACION_BAJA_ACEPTADA"), eq("OK"), anyString());
    }

    @Test
    void falloAlGuardarCdrDebeRevertirTodasLasAnulaciones() {
        Long c1 = documento(RUC, 81), c2 = documento(RUC, 82);
        Long id = baja(c1, c2); respuesta(true);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fallo de persistencia simulado"))
                .when(repositorioServicio).saveAndFlush(any());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, () -> service.consultarTicket(RUC, id));
        comprobarSinFinalizar(id, c1, c2);
        assertEquals(1, respuestas.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(RUC, RespuestaSunatRecibida.Flujo.RA, id).size());
        verifyNoInteractions(auditoria);
    }

    @Test
    void documentoDeOtraEmpresaDebeAbortarYRevertirTambienElPrimero() {
        Long c1 = documento(RUC, 81), c2 = documento("20600000001", 82);
        Long id = baja(c1, c2); respuesta(true);
        assertThrows(IllegalStateException.class, () -> service.consultarTicket(RUC, id));
        comprobarSinFinalizar(id, c1, c2);
        verifyNoInteractions(auditoria);
    }

    @Test
    void rechazoDebeGuardarCdrSinAnularDocumentos() {
        Long c1 = documento(RUC, 81); Long id = baja(c1); respuesta(false);
        service.consultarTicket(RUC, id);
        tx.executeWithoutResult(s -> {
            ComunicacionBaja b = bajas.findById(id).orElseThrow();
            assertEquals(EstadoComunicacionBaja.RECHAZADO, b.getEstado());
            assertArrayEquals(new byte[]{1}, b.getCdrZipBytes());
            assertNotNull(b.getFinalizadoEn());
            assertEquals(EstadoComprobante.ACEPTADO, documentos.findById(c1).orElseThrow().getEstadoSfs());
        });
    }

    @Test
    void estado98TardioNoDebeReabrirBajaFinalizada() {
        Long c1 = documento(RUC, 81); Long id = baja(c1);
        when(sunat.consultarTicket(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            tx.executeWithoutResult(s -> {
                ComunicacionBaja b = bajas.bloquearPorIdYRuc(id, RUC).orElseThrow();
                b.setEstado(EstadoComunicacionBaja.ACEPTADO); b.setCdrZipBytes(new byte[]{42});
                bajas.saveAndFlush(b);
            });
            return new SunatDirectSoapClient.EstadoTicketSunat("98", null);
        });
        service.consultarTicket(RUC, id);
        tx.executeWithoutResult(s -> {
            ComunicacionBaja b = bajas.findById(id).orElseThrow();
            assertEquals(EstadoComunicacionBaja.ACEPTADO, b.getEstado());
            assertArrayEquals(new byte[]{42}, b.getCdrZipBytes());
            assertEquals(0, b.getIntentosConsulta());
        });
    }

    @Test
    void sendSummaryDebeUsarEvidenciaConfirmadaFueraDeTransaccion() throws Exception {
        Long id = baja(documento(RUC, 81)); prepararEnvio(id);
        when(sunat.enviarResumenDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ComunicacionBaja b = bajas.findById(id).orElseThrow();
            assertEquals(EstadoComunicacionBaja.ENVIANDO, b.getEstado());
            assertEquals("HASH-TEST", b.getHashCpe()); assertEquals(1, b.getIntentosEnvio());
            try (var z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(inv.getArgument(4)))) {
                assertNotNull(z.getNextEntry()); assertArrayEquals(b.getXmlFirmadoBytes(), z.readAllBytes());
            }
            return "TICKET-TEST";
        });
        when(sunat.consultarTicket(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new SunatDirectSoapClient.EstadoTicketSunat("98", null));
        service.prepararYEnviar(RUC, id);
        assertEquals(EstadoComunicacionBaja.EN_PROCESO, bajas.findById(id).orElseThrow().getEstado());
    }

    @Test
    void excepcionInesperadaTrasAdquirirEnvioDebeSerInciertaYNoReenviable() {
        Long id = baja(documento(RUC, 81)); prepararEnvio(id);
        when(sunat.enviarResumenDirecto(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("respuesta desconocida simulada"));
        service.prepararYEnviar(RUC, id); service.prepararYEnviar(RUC, id);
        assertEquals(EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO, bajas.findById(id).orElseThrow().getEstado());
        verify(sunat, times(1)).enviarResumenDirecto(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void errorDeAuditoriaPosteriorNoDebeDeshacerAceptacionNiConvertirlaEnEnvioReintentable() {
        Long c = documento(RUC, 81); Long id = baja(c); prepararEnvio(id); respuesta(true);
        when(sunat.enviarResumenDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn("TICKET-TEST");
        doThrow(new IllegalStateException("auditoría no disponible simulada")).when(auditoria)
                .registrarSistema(anyString(), anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> service.prepararYEnviar(RUC, id), "Una auditoría posterior no cambia el resultado fiscal confirmado");
        comprobarAceptacion(id, c);
        service.prepararYEnviar(RUC, id);
        verify(sunat, times(1)).enviarResumenDirecto(anyString(), anyString(), anyString(), anyString(), any());
    }

    private void prepararEnvio(Long id) {
        tx.executeWithoutResult(s -> {
            ComunicacionBaja b = bajas.findById(id).orElseThrow(); b.setEstado(EstadoComunicacionBaja.CREADO); b.setTicketSunat(null);
            bajas.saveAndFlush(b);
        });
        when(xml.construir(any(), any())).thenReturn(new byte[]{1});
        when(crypto.descifrarPfx(anyString(), any())).thenReturn(new byte[]{1});
        when(crypto.descifrarPfxPassword(anyString(), anyString())).thenReturn("PFX_TEST");
        when(signer.firmarXml(any(), any(), anyString())).thenReturn(new XmlSignerService.ResultadoFirma(new byte[]{2}, "HASH-TEST"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"Client.3205","Server","DESCONOCIDO"})
    void faultTrasPostSinTicketNoSeReenvia(String codigo){
        Long id=baja(documento(RUC,81));prepararEnvio(id);
        when(sunat.enviarResumenDirecto(anyString(),anyString(),anyString(),anyString(),any()))
                .thenThrow(new SunatDirectSoapClient.SunatSoapFaultException(codigo,"FICTICIO",500));
        service.prepararYEnviar(RUC,id);
        assertEquals(EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO,bajas.findById(id).orElseThrow().getEstado());
        assertNull(bajas.findById(id).orElseThrow().getTicketSunat());
        assertThrows(IllegalStateException.class,()->service.reintentarEnvio(RUC,id));service.prepararYEnviar(RUC,id);
        verify(sunat,times(1)).enviarResumenDirecto(anyString(),anyString(),anyString(),anyString(),any());
        verify(sunat,never()).consultarTicket(anyString(),anyString(),anyString(),anyString());
    }
    @Test void faultLocalAntesDeRedSeRecuperaSinRecrearRa(){
        Long id=baja(documento(RUC,81));prepararEnvio(id);
        when(xml.construir(any(),any())).thenThrow(new SunatDirectSoapClient.SunatSoapFaultException("Server","FICTICIO",500));
        service.prepararYEnviar(RUC,id);
        assertEquals(EstadoComunicacionBaja.ERROR_ENVIO,bajas.findById(id).orElseThrow().getEstado());
        assertEquals(0,bajas.findById(id).orElseThrow().getIntentosEnvio());verifyNoInteractions(sunat);
        doReturn(new byte[]{1}).when(xml).construir(any(),any());
        when(sunat.enviarResumenDirecto(anyString(),anyString(),anyString(),anyString(),any())).thenReturn("TICKET-TEST");
        when(sunat.consultarTicket(anyString(),anyString(),anyString(),anyString())).thenReturn(new SunatDirectSoapClient.EstadoTicketSunat("98",null));
        assertEquals(id,service.reintentarEnvio(RUC,id).id());
        assertEquals(EstadoComunicacionBaja.EN_PROCESO,bajas.findById(id).orElseThrow().getEstado());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"0","98","99","error"})
    void respuestaTardiaDeTicketCambiadoNoModificaRa(String tipo){
        Long c=documento(RUC,81),id=baja(c);
        when(sunat.consultarTicket(anyString(),anyString(),anyString(),eq("TICKET-TEST"))).thenAnswer(i->{
            tx.executeWithoutResult(s->bajas.bloquearPorIdYRuc(id,RUC).orElseThrow().setTicketSunat("TICKET-NUEVO"));
            if(tipo.equals("error"))throw new SunatDirectSoapClient.SunatTransportException("FICTICIO");
            return new SunatDirectSoapClient.EstadoTicketSunat(tipo,tipo.equals("0")?new byte[]{1}:null);
        });
        if(tipo.equals("0"))when(parser.procesar(any())).thenReturn(new CdrParserService.ResultadoCdr("0","FICTICIO",List.of(),true,new byte[]{2},"RA-20260930-81"));
        service.consultarTicket(RUC,id);
        assertEquals("TICKET-NUEVO",bajas.findById(id).orElseThrow().getTicketSunat());comprobarSinFinalizar(id,c);verifyNoInteractions(auditoria);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void cdrMultibyteExcesivoNoImpideTransicionNiRecortaEvidencia(boolean aceptado){
        Long c=documento(RUC,81),id=baja(c);respuesta(aceptado);
        String texto="😀".repeat(20000);byte[] evidencia=texto.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(evidencia.length>65535);
        when(parser.procesar(any())).thenReturn(new CdrParserService.ResultadoCdr(aceptado?"0":"2000",texto,List.of(),aceptado,evidencia,"RA-20260930-81"));
        service.consultarTicket(RUC,id);
        var b=bajas.findById(id).orElseThrow();assertEquals(aceptado?EstadoComunicacionBaja.ACEPTADO:EstadoComunicacionBaja.RECHAZADO,b.getEstado());
        assertArrayEquals(evidencia,b.getCdrXmlBytes());assertArrayEquals(new byte[]{1},b.getCdrZipBytes());
        assertTrue(b.getMensajeSunat().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<65535);
    }

    @Test
    void recoveryDeRaDebeBloquearSoloEnviandoVencidoSinTicket() {
        Long id = baja(documento(RUC, 81));
        tx.executeWithoutResult(s -> {
            ComunicacionBaja b = bajas.findById(id).orElseThrow(); b.setEstado(EstadoComunicacionBaja.ENVIANDO); b.setTicketSunat(null);
            b.setXmlFirmadoBytes(new byte[]{42}); b.setHashCpe("HASH-PRESERVADO"); bajas.saveAndFlush(b);
        });
        envejecerBaja(id);
        assertEquals(1, bajas.marcarEnviosVencidosComoInciertos(FECHA.atTime(9, 50)));
        ComunicacionBaja b = bajas.findById(id).orElseThrow();
        assertEquals(EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO, b.getEstado());
        assertArrayEquals(new byte[]{42}, b.getXmlFirmadoBytes()); assertEquals("HASH-PRESERVADO", b.getHashCpe());
        assertTrue(bajas.buscarCreadasSinTicket(org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());
        service.prepararYEnviar(RUC, id); verifyNoInteractions(sunat);
    }

    @Test
    void recoveryDeRaNoDebeClasificarComoInciertoUnTicketExistente() {
        Long id = baja(documento(RUC, 81));
        tx.executeWithoutResult(s -> bajas.findById(id).orElseThrow().setEstado(EstadoComunicacionBaja.ENVIANDO));
        envejecerBaja(id);
        assertEquals(0, bajas.marcarEnviosVencidosComoInciertos(FECHA.atTime(9, 50)));
        assertEquals("TICKET-TEST", bajas.findById(id).orElseThrow().getTicketSunat());
    }

    @Test
    void raCreadaFueraDePlazoNoDebeTocarRedNiFirmar() {
        Long id = baja(documento(RUC, 81));
        tx.executeWithoutResult(s -> { ComunicacionBaja b = bajas.findById(id).orElseThrow(); b.setEstado(EstadoComunicacionBaja.CREADO); b.setTicketSunat(null); });
        assertEquals(List.of(id), bajas.buscarCreadasSinTicket(org.springframework.data.domain.PageRequest.of(0, 20)).stream().map(ComunicacionBaja::getId).toList());
        doThrow(new SunatPlazoService.FueraDePlazoSunatException("plazo configurado vencido"))
                .when(plazo).validarBaja(FECHA);
        service.prepararYEnviar(RUC, id);
        assertEquals(EstadoComunicacionBaja.FUERA_DE_PLAZO, bajas.findById(id).orElseThrow().getEstado());
        verifyNoInteractions(sunat, signer, xml);
    }

    private void envejecerBaja(Long id) {
        tx.executeWithoutResult(s -> entityManager.createNativeQuery("UPDATE comunicacion_baja SET updated_at=:fecha WHERE id=:id")
                .setParameter("fecha", FECHA.atTime(9, 0)).setParameter("id", id).executeUpdate());
    }

    private RespuestaSunatRecibidaService servicioRespuestas() {
        var target = new RespuestaSunatRecibidaService(respuestas,
                Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Lima")));
        var factory = new org.springframework.aop.framework.ProxyFactory(target); factory.setProxyTargetClass(true);
        var advice = new org.springframework.transaction.interceptor.TransactionInterceptor();
        advice.setTransactionManager(transactionManager);
        advice.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        factory.addAdvice(advice); return (RespuestaSunatRecibidaService) factory.getProxy();
    }

    private void respuesta(boolean aceptado) {
        when(sunat.consultarTicket(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SOAP fuera de transacción");
            return new SunatDirectSoapClient.EstadoTicketSunat("0", new byte[]{1});
        });
        when(parser.procesar(any())).thenReturn(new CdrParserService.ResultadoCdr(aceptado ? "0" : "2000",
                "RESULTADO TEST", List.of(), aceptado, new byte[]{2}, "RA-20260930-81"));
    }

    private void comprobarAceptacion(Long id, Long... documentosIds) {
        tx.executeWithoutResult(s -> {
            ComunicacionBaja b = bajas.findById(id).orElseThrow();
            assertEquals(EstadoComunicacionBaja.ACEPTADO, b.getEstado());
            assertArrayEquals(new byte[]{1}, b.getCdrZipBytes());
            assertArrayEquals(new byte[]{2}, b.getCdrXmlBytes());
            assertNotNull(b.getFinalizadoEn()); assertEquals(1, b.getIntentosConsulta());
            for (Long c : documentosIds) assertEquals(EstadoComprobante.ANULADO, documentos.findById(c).orElseThrow().getEstadoSfs());
        });
    }

    private void comprobarSinFinalizar(Long id, Long... documentosIds) {
        tx.executeWithoutResult(s -> {
            ComunicacionBaja b = bajas.findById(id).orElseThrow();
            assertEquals(EstadoComunicacionBaja.TICKET_GENERADO, b.getEstado());
            assertNull(b.getCdrZipBytes()); assertNull(b.getCdrXmlBytes()); assertNull(b.getFinalizadoEn());
            assertEquals(0, b.getIntentosConsulta());
            for (Long c : documentosIds) assertEquals(EstadoComprobante.ACEPTADO, documentos.findById(c).orElseThrow().getEstadoSfs());
        });
    }

    private Long documento(String ruc, int numero) {
        Long id = tx.execute(s -> documentos.saveAndFlush(Comprobante.builder().tiendaId(1L).rucEmisor(ruc)
                .tipoOperacion("0101").tipoComprobante("01").serie("F081").numero(numero)
                .fechaEmision(FECHA.atTime(10, 0)).moneda("PEN").formaPago("Contado")
                .tipoDocCliente("6").numDocCliente(RUC).rznSocialCliente("CLIENTE TEST SAC")
                .opGravada(new BigDecimal("100")).opExonerada(BigDecimal.ZERO).opInafecta(BigDecimal.ZERO)
                .igv(new BigDecimal("18")).montoTotal(new BigDecimal("118")).estadoSfs(EstadoComprobante.ACEPTADO).build()).getId());
        idsDocumentos.add(id); return id;
    }

    private Long baja(Long... documentosIds) {
        Long id = tx.execute(s -> {
            ComunicacionBaja b = ComunicacionBaja.builder().rucEmisor(RUC).fechaDocumentos(FECHA).fechaGeneracion(FECHA)
                    .correlativo(81).identificador("RA-20260930-81").nombreArchivoXml("TEST-RA.xml")
                    .ticketSunat("TICKET-TEST").estado(EstadoComunicacionBaja.TICKET_GENERADO).build();
            int linea = 1;
            for (Long c : documentosIds) b.addDetalle(ComunicacionBajaDetalle.builder().comprobanteId(c)
                    .numeroLinea(linea++).tipoComprobante("01").serie("F081").numero(81).motivo("TEST").build());
            return bajas.saveAndFlush(b).getId();
        });
        idsBajas.add(id); return id;
    }
}
