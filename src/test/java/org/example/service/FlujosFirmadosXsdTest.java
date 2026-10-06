package org.example.service;

import org.apache.xml.security.signature.XMLSignature;
import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Artefactos ficticios efímeros generados por JDK. No SUNAT, BD, secretos reales ni contexto Spring. */
class FlujosFirmadosXsdTest {
    @TempDir static Path temporal;
    private static final String PASSWORD_TEST = "SOLO_FIXTURE_EFIMERO_123";
    private static byte[] pfx;
    private static X509Certificate certificado;
    private static Path truststore;
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 9, 30, 10, 0);

    @BeforeAll
    static void certificadosFicticios() throws Exception {
        Path ca = temporal.resolve("ca-test.p12"), emisor = temporal.resolve("emisor-test.p12");
        Path caPem = temporal.resolve("ca-test.pem"), csr = temporal.resolve("emisor-test.csr"), reply = temporal.resolve("emisor-test.pem");
        keytool("-genkeypair", "-alias", "ca-test", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=CA FICTICIA SOLO TEST",
                "-ext", "BC:critical=ca:true", "-ext", "KU:critical=keyCertSign,cRLSign", "-validity", "365", "-startdate", "-1d",
                "-keystore", ca.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD_TEST, "-noprompt");
        keytool("-exportcert", "-alias", "ca-test", "-rfc", "-keystore", ca.toString(), "-storepass", PASSWORD_TEST, "-file", caPem.toString());
        keytool("-genkeypair", "-alias", "emisor-test", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=EMISOR FICTICIO SOLO TEST,OU=20100130204,O=TEST,C=PE",
                "-ext", "KU=digitalSignature", "-validity", "365", "-startdate", "-1d", "-keystore", emisor.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD_TEST, "-noprompt");
        keytool("-certreq", "-alias", "emisor-test", "-keystore", emisor.toString(), "-storepass", PASSWORD_TEST, "-file", csr.toString());
        keytool("-gencert", "-alias", "ca-test", "-keystore", ca.toString(), "-storepass", PASSWORD_TEST, "-infile", csr.toString(),
                "-outfile", reply.toString(), "-rfc", "-ext", "BC=ca:false", "-ext", "KU=digitalSignature", "-validity", "365", "-startdate", "-1d");
        keytool("-importcert", "-alias", "ca-test", "-keystore", emisor.toString(), "-storepass", PASSWORD_TEST, "-file", caPem.toString(), "-noprompt");
        keytool("-importcert", "-alias", "emisor-test", "-keystore", emisor.toString(), "-storepass", PASSWORD_TEST, "-file", reply.toString(), "-noprompt");
        pfx = Files.readAllBytes(emisor);
        KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(pfx), PASSWORD_TEST.toCharArray());
        certificado = (X509Certificate) ks.getCertificate("emisor-test");
        KeyStore trust = KeyStore.getInstance("PKCS12"); trust.load(null, PASSWORD_TEST.toCharArray());
        trust.setCertificateEntry("ca-test", ks.getCertificate("ca-test"));
        truststore = temporal.resolve("trust-test.p12");
        try (var out = Files.newOutputStream(truststore)) { trust.store(out, PASSWORD_TEST.toCharArray()); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "03", "07-01", "07-03", "RC", "RA"})
    void debeFirmarXmlFinalValidarFirmaYXsdSinModificarEvidencia(String flujo) throws Exception {
        var resultado = firmador("BETA").firmarXml(construir(flujo), pfx, PASSWORD_TEST);
        validarXsd(flujo, resultado.xmlFirmado());
        Document doc = parse(resultado.xmlFirmado());
        XMLSignature firma = firma(doc);
        assertTrue(firma.checkSignatureValue(certificado));
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", firma.getSignedInfo().getSignatureMethodURI());
        assertEquals(1, firma.getSignedInfo().getLength());
        assertEquals("", firma.getSignedInfo().item(0).getURI());
        assertEquals(resultado.hashCpe(), firma.getSignedInfo().item(0).getDigestValue() == null ? null
                : Base64.getEncoder().encodeToString(firma.getSignedInfo().item(0).getDigestValue()));
        assertEquals(1, doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").getLength());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "03", "07-01", "07-03", "RC", "RA"})
    void alteracionPosteriorDebeInvalidarFirmaAunqueXmlSigaCumpliendoXsd(String flujo) throws Exception {
        byte[] firmado = firmador("BETA").firmarXml(construir(flujo), pfx, PASSWORD_TEST).xmlFirmado();
        Document doc = parse(firmado);
        Element id = (Element) doc.getDocumentElement().getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "ID").item(0);
        id.setTextContent("IDENTIFICADOR_ALTERADO_TEST");
        var out = new ByteArrayOutputStream();
        var transform = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transform.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(out));
        validarXsd(flujo, out.toByteArray());
        assertFalse(firma(doc).checkSignatureValue(certificado));
    }

    @Test
    void cadenaControladaDebePermitirFirmaConValidacionesProductivasSinRed() throws Exception {
        var resultado = firmador("PRODUCCION").firmarXml(construir("01"), pfx, PASSWORD_TEST);
        validarXsd("01", resultado.xmlFirmado());
        assertTrue(firma(parse(resultado.xmlFirmado())).checkSignatureValue(certificado));
    }

    @Test
    void certificadoControladoDeOtraEmpresaDebeSerRechazadoEnProduccion() {
        byte[] ajeno = new String(construir("01"), java.nio.charset.StandardCharsets.UTF_8)
                .replace("20100130204", "20600000001").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> firmador("PRODUCCION").firmarXml(ajeno, pfx, PASSWORD_TEST));
    }

    private XmlSignerService firmador(String ambiente) {
        var s = new XmlSignerService(); ReflectionTestUtils.setField(s, "environment", ambiente);
        ReflectionTestUtils.setField(s, "minRsaBits", 2048); ReflectionTestUtils.setField(s, "trustStorePath", truststore.toString());
        ReflectionTestUtils.setField(s, "trustStorePassword", PASSWORD_TEST); ReflectionTestUtils.setField(s, "trustStoreType", "PKCS12");
        s.inicializar(); return s;
    }

    private void validarXsd(String flujo, byte[] xml) {
        var v = new UblValidatorService(new DefaultResourceLoader()); ReflectionTestUtils.setField(v, "xsdEnabled", true);
        ReflectionTestUtils.setField(v, "invoiceXsd", "classpath:sunat-xsd/2.1/maindoc/UBL-Invoice-2.1.xsd");
        ReflectionTestUtils.setField(v, "creditNoteXsd", "classpath:sunat-xsd/2.1/maindoc/UBL-CreditNote-2.1.xsd");
        ReflectionTestUtils.setField(v, "summaryXsd", "classpath:sunat-xsd/2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd");
        ReflectionTestUtils.setField(v, "voidedXsd", "classpath:sunat-xsd/2.0/maindoc/UBLPE-VoidedDocuments-1.0.xsd");
        if (flujo.startsWith("07")) v.validarNotaCredito(xml);
        else if ("RC".equals(flujo)) v.validarResumenDiario(xml);
        else if ("RA".equals(flujo)) v.validarComunicacionBaja(xml);
        else v.validarFactura(xml);
    }

    private byte[] construir(String flujo) {
        Empresa e = Empresa.builder().ruc("20100130204").razonSocial("EMPRESA TEST SAC").ubigeo("150101")
                .codigoEstablecimiento("0000").provincia("LIMA").departamento("LIMA").distrito("LIMA").direccionFiscal("AV TEST 123").build();
        var calculo = new CalculoTributarioService(new ReglasTributariasProperties());
        var builder = new UblXmlBuilderService();
        if (flujo.startsWith("07")) {
            String origen = flujo.substring(3);
            var r = new NotaCreditoRequest(); r.setTiendaId(1L); r.setSerie("03".equals(origen)?"BC01":"FC01");
            r.setTipoComprobanteAfectado(origen); r.setNumComprobanteAfectado("03".equals(origen)?"B001-1":"F001-1");
            r.setCodMotivoSunat("07"); r.setDescripcionMotivo("DEVOLUCION TEST"); r.setTipoDocCliente("6");
            r.setNumDocCliente(e.getRuc()); r.setRznSocialCliente("CLIENTE TEST SAC");
            var i = new NotaCreditoRequest.DetalleItemRequest(); i.setCantidad(new BigDecimal("1000")); i.setValorUnitario(new BigDecimal("0.0149"));
            i.setTipoAfectacionIgv("10"); i.setUnidadMedida("NIU"); i.setDescripcion("PRODUCTO TEST"); r.setItems(List.of(i));
            return builder.construirXmlNotaCredito21(e,r,1,calculo.calcular(r,new BigDecimal("18")),FECHA);
        }
        if ("RA".equals(flujo)) {
            var b = ComunicacionBaja.builder().rucEmisor(e.getRuc()).fechaDocumentos(FECHA.toLocalDate()).fechaGeneracion(FECHA.toLocalDate())
                    .identificador("RA-20260930-1").build();
            b.addDetalle(ComunicacionBajaDetalle.builder().comprobanteId(1L).numeroLinea(1).tipoComprobante("01").serie("F001")
                    .numero(1).motivo("ERROR DE EMISION NO OTORGADO TEST").build());
            return new ComunicacionBajaXmlService().construir(b,e);
        }
        if ("RC".equals(flujo)) {
            var r = ResumenDiario.builder().rucEmisor(e.getRuc()).fechaDocumentos(FECHA.toLocalDate()).fechaGeneracion(FECHA.toLocalDate())
                    .identificador("RC-20260930-1").correlativo(1).build();
            r.addDetalle(ResumenDiarioDetalle.builder().comprobanteId(1L).numeroLinea(1).tipoComprobante("03").serie("B001").numero(1)
                    .tipoDocCliente("0").numDocCliente("-").moneda("PEN").codigoEstado("1").opGravada(new BigDecimal("100"))
                    .opExonerada(BigDecimal.ZERO).opInafecta(BigDecimal.ZERO).igv(new BigDecimal("18")).montoTotal(new BigDecimal("118")).build());
            return new ResumenDiarioXmlService().construir(r,e);
        }
        var r = new ComprobanteRequest(); r.setTiendaId(1L); r.setTipoOperacion("0101"); r.setTipoComprobante(flujo);
        r.setSerie("03".equals(flujo)?"B001":"F001"); r.setFormaPago("Contado"); r.setTipoDocCliente("6");
        r.setNumDocCliente(e.getRuc()); r.setRznSocialCliente("CLIENTE TEST SAC");
        var i = new ComprobanteRequest.DetalleItemRequest(); i.setCantidad(new BigDecimal("1000")); i.setValorUnitario(new BigDecimal("0.0149"));
        i.setTipoAfectacionIgv("10"); i.setUnidadMedida("NIU"); i.setDescripcion("PRODUCTO TEST"); r.setItems(List.of(i));
        return builder.construirXmlUbl21(e,r,1,calculo.calcular(r,new BigDecimal("18")),FECHA);
    }

    private Document parse(byte[] xml) throws Exception {
        var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }
    private XMLSignature firma(Document doc) throws Exception {
        return new XMLSignature((Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0), "", true);
    }
    private static void keytool(String... args) throws Exception {
        String ejecutable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        var command = new ArrayList<String>(); command.add(Path.of(System.getProperty("java.home"), "bin", ejecutable).toString());
        command.addAll(Arrays.asList(args));
        var p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(temporal.resolve("keytool-test.log").toFile()).start();
        if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); fail("Timeout generando certificado ficticio de test."); }
        assertEquals(0, p.exitValue(), "keytool no pudo generar el fixture temporal controlado; no contiene credenciales reales.");
    }
}
