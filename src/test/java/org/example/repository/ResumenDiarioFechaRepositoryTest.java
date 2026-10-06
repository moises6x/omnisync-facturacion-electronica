package org.example.repository;

import jakarta.persistence.EntityManager;
import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SecuenciaResumenDiarioService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResumenDiarioFechaRepositoryTest {
    private static final String RUC = "20100130204", OTRO_RUC = "20600000001";
    private static final LocalDate HOY = LocalDate.of(2026, 10, 1), AYER = HOY.minusDays(1);
    @Autowired private ResumenDiarioRepository resumenes;
    @Autowired private ComprobanteRepository comprobantes;
    @Autowired private SecuenciaResumenDiarioRepository secuencias;
    @Autowired private SecuenciaResumenDiarioService secuenciaService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager em;
    private TransactionTemplate tx;

    @BeforeEach void preparar() { tx = new TransactionTemplate(transactionManager); }

    @AfterEach void limpiarSoloFixtures() {
        tx.executeWithoutResult(s -> {
            resumenes.findAll().stream().filter(r -> List.of(RUC, OTRO_RUC).contains(r.getRucEmisor())
                    && !r.getFechaDocumentos().isBefore(AYER.minusDays(1)) && !r.getFechaDocumentos().isAfter(HOY))
                    .forEach(resumenes::delete);
            resumenes.flush();
            em.createQuery("DELETE FROM Comprobante c WHERE c.serie='B902' AND c.rucEmisor IN :rucs")
                    .setParameter("rucs", List.of(RUC, OTRO_RUC)).executeUpdate();
            em.createQuery("DELETE FROM SecuenciaResumenDiario s WHERE s.rucEmisor IN :rucs AND s.fechaReferencia BETWEEN :desde AND :hasta")
                    .setParameter("rucs", List.of(RUC, OTRO_RUC)).setParameter("desde", AYER.minusDays(1)).setParameter("hasta", HOY.plusDays(1)).executeUpdate();
        });
    }

    @Test
    void documentosDeAyerIdArchivoIssueDateDeHoyYReferenceDateDeAyer() throws Exception {
        boleta(RUC, AYER, 1);
        Long id = crear(servicio(HOY), RUC, AYER);
        tx.executeWithoutResult(s -> {
            ResumenDiario rc = resumenes.buscarCompleto(id, RUC).orElseThrow();
            assertEquals("RC-20261001-1", rc.getIdentificador());
            assertEquals(RUC + "-RC-20261001-1.xml", rc.getNombreArchivoXml());
            assertEquals(HOY, rc.getFechaGeneracion()); assertEquals(AYER, rc.getFechaDocumentos());
            Empresa empresa = new Empresa(); empresa.setRuc(RUC); empresa.setRazonSocial("EMISOR FICTICIO");
            byte[] xml = new ResumenDiarioXmlService().construir(rc, empresa);
            try {
                var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                var doc = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
                String ns = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                assertEquals("2026-10-01", doc.getElementsByTagNameNS(ns, "IssueDate").item(0).getTextContent());
                assertEquals("2026-09-30", doc.getElementsByTagNameNS(ns, "ReferenceDate").item(0).getTextContent());
                assertEquals(rc.getIdentificador(), doc.getElementsByTagNameNS(ns, "ID").item(0).getTextContent());
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }

    @Test
    void dosFechasDocumentalesMismaGeneracionNoRepitenIdentificador() {
        boleta(RUC, AYER, 1); boleta(RUC, HOY, 2);
        Long a = crear(servicio(HOY), RUC, AYER); Long b = crear(servicio(HOY), RUC, HOY);
        assertNotEquals(resumenes.findById(a).orElseThrow().getIdentificador(), resumenes.findById(b).orElseThrow().getIdentificador());
        assertEquals(2, resumenes.findById(b).orElseThrow().getCorrelativo());
    }

    @Test
    void mismaFechaDocumentalEnDosGeneracionesRespetaUnicidadHistorica() {
        boleta(RUC, AYER, 1); Long a = crear(servicio(AYER), RUC, AYER);
        boleta(RUC, AYER, 2); Long b = crear(servicio(HOY), RUC, AYER);
        assertEquals(1, resumenes.findById(a).orElseThrow().getCorrelativo());
        assertEquals(2, resumenes.findById(b).orElseThrow().getCorrelativo());
        assertEquals("RC-20261001-2", resumenes.findById(b).orElseThrow().getIdentificador());
    }

    @Test
    void terminalLegacyNoSeRenombraNiRegeneraYSeRespetaSuCorrelativo() {
        Long antiguo = tx.execute(s -> {
            ResumenDiario rc = cabecera(RUC, AYER, 37);
            rc.setEstado(EstadoResumenDiario.ACEPTADO); rc.setXmlFirmadoBytes(new byte[]{99}); rc.setHashCpe("HASH-LEGACY");
            rc.setCdrZipBytes(new byte[]{88}); rc.setTicketSunat("TICKET-LEGACY");
            return resumenes.saveAndFlush(rc).getId();
        });
        boleta(RUC, AYER, 1); Long nuevo = crear(servicio(HOY), RUC, AYER);
        assertEquals(38, resumenes.findById(nuevo).orElseThrow().getCorrelativo());
        ResumenDiario legacy = resumenes.findById(antiguo).orElseThrow();
        assertEquals("RC-20260930-37", legacy.getIdentificador());
        assertEquals(RUC + "-RC-20260930-37.xml", legacy.getNombreArchivoXml());
        assertArrayEquals(new byte[]{99}, legacy.getXmlFirmadoBytes()); assertArrayEquals(new byte[]{88}, legacy.getCdrZipBytes());
        assertEquals("HASH-LEGACY", legacy.getHashCpe()); assertEquals("TICKET-LEGACY", legacy.getTicketSunat());
        assertEquals(EstadoResumenDiario.ACEPTADO, legacy.getEstado());
        servicio(HOY).prepararYEnviar(RUC, antiguo);
        assertEquals("RC-20260930-37", resumenes.findById(antiguo).orElseThrow().getIdentificador());
    }

    @Test
    void colisionConIdLegacyDeOtraFechaDocumentalElevaPisoSinModificarLegacy() {
        tx.executeWithoutResult(s -> {
            ResumenDiario rc = cabecera(RUC, AYER.minusDays(1), 25);
            rc.setIdentificador("RC-20261001-25"); rc.setNombreArchivoXml(RUC + "-RC-20261001-25.xml");
            resumenes.saveAndFlush(rc);
        });
        boleta(RUC, AYER, 1); Long id = crear(servicio(HOY), RUC, AYER);
        assertEquals("RC-20261001-26", resumenes.findById(id).orElseThrow().getIdentificador());
    }

    @Test
    void rcConcurrentesDeFechasDistintasNoGeneranDuplicados() throws Exception {
        boleta(RUC, AYER, 1); boleta(RUC, HOY, 2);
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { esperar(iniciar); return crear(servicio(HOY), RUC, AYER); });
            var b = executor.submit(() -> { esperar(iniciar); return crear(servicio(HOY), RUC, HOY); });
            iniciar.countDown();
            ResumenDiario primero = resumenes.findById(a.get(10, TimeUnit.SECONDS)).orElseThrow();
            ResumenDiario segundo = resumenes.findById(b.get(10, TimeUnit.SECONDS)).orElseThrow();
            assertNotEquals(primero.getIdentificador(), segundo.getIdentificador());
            assertNotEquals(primero.getCorrelativo(), segundo.getCorrelativo());
            assertEquals(HOY, primero.getFechaGeneracion()); assertEquals(HOY, segundo.getFechaGeneracion());
        }
    }

    @Test
    void rangoAgotadoRevierteTomaDeDocumentos() {
        tx.executeWithoutResult(s -> secuencias.saveAndFlush(SecuenciaResumenDiario.builder().rucEmisor(RUC)
                .fechaReferencia(HOY).ultimoCorrelativo(99999).build()));
        Long id = boleta(RUC, AYER, 1);
        assertThrows(IllegalStateException.class, () -> crear(servicio(HOY), RUC, AYER));
        assertEquals(EstadoComprobante.PENDIENTE_RESUMEN, comprobantes.findById(id).orElseThrow().getEstadoSfs());
        assertTrue(resumenes.findByRucEmisorAndFechaDocumentosOrderByCorrelativoDesc(RUC, AYER).isEmpty());
    }

    @Test
    void correlativosYArchivosSiguenAisladosPorRuc() {
        boleta(RUC, AYER, 1); boleta(OTRO_RUC, AYER, 1);
        Long a = crear(servicio(HOY), RUC, AYER); Long b = crear(servicio(HOY), OTRO_RUC, AYER);
        assertEquals(1, resumenes.findById(a).orElseThrow().getCorrelativo());
        assertEquals(1, resumenes.findById(b).orElseThrow().getCorrelativo());
        assertNotEquals(resumenes.findById(a).orElseThrow().getNombreArchivoXml(), resumenes.findById(b).orElseThrow().getNombreArchivoXml());
    }

    private ResumenDiarioService servicio(LocalDate generacion) {
        var service = new ResumenDiarioService(resumenes, comprobantes, mock(EmpresaRepository.class), secuenciaService,
                mock(ResumenDiarioXmlService.class), mock(XmlSignerService.class), mock(UblValidatorService.class),
                mock(SunatDirectSoapClient.class), mock(CdrParserService.class), mock(CryptoService.class), mock(SunatPlazoService.class),
                Clock.fixed(generacion.atTime(12, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima")),
                transactionManager, mock(RespuestaSunatRecibidaService.class));
        ReflectionTestUtils.setField(service, "maxLineasConfigurado", 500); return service;
    }

    private Long crear(ResumenDiarioService service, String ruc, LocalDate fecha) {
        return ReflectionTestUtils.invokeMethod(service, "crearResumenTransaccional", ruc, fecha);
    }

    private Long boleta(String ruc, LocalDate fecha, int numero) {
        return tx.execute(s -> comprobantes.saveAndFlush(Comprobante.builder().tiendaId(1L).rucEmisor(ruc).tipoOperacion("0101")
                .tipoComprobante("03").serie("B902").numero(numero).fechaEmision(fecha.atTime(10, 0)).moneda("PEN")
                .tipoDocCliente("1").numDocCliente("48411579").rznSocialCliente("CLIENTE FICTICIO")
                .opGravada(BigDecimal.ZERO).opExonerada(new BigDecimal("100")).opInafecta(BigDecimal.ZERO)
                .igv(BigDecimal.ZERO).montoTotal(new BigDecimal("100")).estadoSfs(EstadoComprobante.PENDIENTE_RESUMEN).build()).getId());
    }

    private static ResumenDiario cabecera(String ruc, LocalDate fecha, int numero) {
        String id = "RC-" + fecha.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + numero;
        return ResumenDiario.builder().rucEmisor(ruc).fechaDocumentos(fecha).fechaGeneracion(fecha).correlativo(numero)
                .identificador(id).nombreArchivoXml(ruc + "-" + id + ".xml").estado(EstadoResumenDiario.ACEPTADO).build();
    }

    private static void esperar(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
