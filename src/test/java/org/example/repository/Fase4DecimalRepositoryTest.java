package org.example.repository;

import jakarta.persistence.EntityManager;
import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class Fase4DecimalRepositoryTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    private static byte[] pfx;
    private static final String PASSWORD = "Certificado-Ficticio-4!";
    private final CalculoTributarioService calculo = new CalculoTributarioService(new ReglasTributariasProperties());

    @BeforeAll static void certificadoControlado() throws Exception {
        Path dir = Files.createTempDirectory("fase4-decimal-");
        Path cert = dir.resolve("test.p12");
        String bin = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        var p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", bin).toString(),
                "-genkeypair", "-alias", "emisor", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=EMISOR FICTICIO,OU=20100130204", "-storetype", "PKCS12", "-keystore", cert.toString(),
                "-storepass", PASSWORD, "-keypass", PASSWORD, "-noprompt").redirectErrorStream(true).start();
        assertTrue(p.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, p.exitValue());
        pfx = Files.readAllBytes(cert); Files.delete(cert); Files.delete(dir);
    }

    @ParameterizedTest @CsvSource({"0.0001,123.1234567891,10", "3.2500,1.2345678912,10",
            "99999999.9999,0.0000000001,20", "1,999999999999,20", "1,0.0100000001,30"})
    void requestCalculoMysqlReloadYXmlFirmadoSinRecorte(String cantidad, String valor, String afectacion) throws Exception {
        assertEquals("bd_facturacion_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        var request = request(cantidad, valor, afectacion);
        var totales = calculo.calcular(request);
        request.setOpGravada(totales.opGravada());request.setOpExonerada(totales.opExonerada());request.setOpInafecta(totales.opInafecta());
        request.setIgv(totales.igv());request.setTotal(totales.total());
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
            assertTrue(factory.getValidator().validate(request).isEmpty(),"DTO completo válido, incluida descripción500");
        }
        var item = totales.items().getFirst();
        var c = Fase3HistoricosRepositoryTest.doc(401); c.setTipoComprobante("01"); c.setSerie("F994");
        c.setOpGravada(totales.opGravada()); c.setOpExonerada(totales.opExonerada()); c.setOpInafecta(totales.opInafecta());
        c.setIgv(totales.igv()); c.setMontoTotal(totales.total()); em.persist(c);
        var d = DetalleComprobante.builder().comprobante(c).itemOrden(1).descripcion(request.getItems().getFirst().getDescripcion())
                .unidadMedida("NIU").cantidad(item.cantidad()).valorUnitario(item.valorUnitario()).precioUnitario(item.precioUnitario())
                .igvItem(item.igv()).porcentajeIgv(item.porcentajeIgv()).tipoAfectacionIgv(afectacion).montoTotalItem(item.total()).build();
        em.persist(d); em.flush(); Long id = d.getId(); em.clear();
        var leido = em.find(DetalleComprobante.class, id);
        assertEquals(0, item.cantidad().compareTo(leido.getCantidad()));
        assertEquals(0, item.valorUnitario().compareTo(leido.getValorUnitario()));
        assertEquals(0, item.precioUnitario().compareTo(leido.getPrecioUnitario()));
        assertEquals(0, item.igv().compareTo(leido.getIgvItem())); assertEquals(500, leido.getDescripcion().length());
        request.getItems().getFirst().setCantidad(leido.getCantidad()); request.getItems().getFirst().setValorUnitario(leido.getValorUnitario());
        var empresa = Empresa.builder().ruc(c.getRucEmisor()).razonSocial("EMISOR FICTICIO").ubigeo("150101")
                .codigoEstablecimiento("0000").departamento("LIMA").provincia("LIMA").distrito("LIMA").direccionFiscal("AV TEST").build();
        byte[] xml = new UblXmlBuilderService().construirXmlUbl21(empresa, request, 401, calculo.calcular(request), LocalDateTime.of(2026,10,2,10,0));
        var signer = new XmlSignerService(); ReflectionTestUtils.setField(signer, "environment", "BETA");
        var firmado = signer.firmarXml(xml, pfx, PASSWORD);
        var f = javax.xml.parsers.DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        var dom = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(firmado.xmlFirmado()));
        var firma = new org.apache.xml.security.signature.XMLSignature((org.w3c.dom.Element)
                dom.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0), "");
        assertTrue(firma.checkSignatureValue(firma.getKeyInfo().getX509Certificate()));
        var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        assertEquals(0, item.valorUnitario().compareTo(new BigDecimal(xpath.evaluate(
                "//*[local-name()='InvoiceLine']/*[local-name()='Price']/*[local-name()='PriceAmount']", dom))));
    }

    @ParameterizedTest @ValueSource(strings={"0.00001","100000000","1.00001"})
    void cantidadNoRepresentableSeRechazaAntesDeMysql(String cantidad) {
        assertThrows(IllegalArgumentException.class, () -> calculo.calcular(request(cantidad,"1","20")));
    }
    @Test void overflowMonetarioSeRechazaAntesDeMysql() {
        assertThrows(IllegalArgumentException.class, () -> calculo.calcular(request("2","999999999999","20")));
    }
    @Test void metadataCoincideConContratoAmpliado() {
        for (String columna : List.of("valor_unitario","precio_unitario")) {
            assertEquals(22, jdbc.queryForObject("SELECT NUMERIC_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='detalle_comprobante' AND COLUMN_NAME=?", Integer.class,columna));
            assertEquals(10, jdbc.queryForObject("SELECT NUMERIC_SCALE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='detalle_comprobante' AND COLUMN_NAME=?", Integer.class,columna));
        }
        assertEquals(500, jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='detalle_comprobante' AND COLUMN_NAME='descripcion'", Integer.class));
    }
    private static ComprobanteRequest request(String cantidad,String valor,String afectacion) {
        var r = new ComprobanteRequest(); r.setTiendaId(1L); r.setTipoComprobante("01"); r.setSerie("F994"); r.setFormaPago("Contado");
        r.setTipoDocCliente("6"); r.setNumDocCliente("20100130204"); r.setRznSocialCliente("CLIENTE FICTICIO");
        var i = new ComprobanteRequest.DetalleItemRequest(); i.setCantidad(new BigDecimal(cantidad)); i.setValorUnitario(new BigDecimal(valor));
        i.setDescripcion("D".repeat(500)); i.setUnidadMedida("NIU"); i.setTipoAfectacionIgv(afectacion); r.setItems(List.of(i)); return r;
    }
}
