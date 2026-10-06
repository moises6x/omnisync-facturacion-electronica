package org.example.service;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.model.Empresa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditoXmlTest {
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 30);
    private final UblXmlBuilderService builder = new UblXmlBuilderService();
    private ValidacionTributariaService validador;

    @BeforeEach
    void setUp() {
        validador = new ValidacionTributariaService(new ReglasTributariasProperties());
        ReflectionTestUtils.setField(validador, "clock",
                Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Lima")));
    }

    @ParameterizedTest
    @CsvSource({"119,119,3265", "118,119,3266", "118,117.99,3319", "2.010,1.005|1.005,3319"})
    void debeRechazarMontosIncoherentesEnValidacionYConstructor(String deuda, String cuotas, String regla) {
        ComprobanteRequest r = request(deuda, cuotas.split("\\|"));
        var totales = totales("118.00");
        var e = assertThrows(IllegalArgumentException.class, () -> validador.validar(r, totales));
        assertTrue(e.getMessage().contains(regla), e.getMessage());
        var xmlError = assertThrows(RuntimeException.class,
                () -> builder.construirXmlUbl21(empresa(), r, 1, totales, FECHA.atTime(10, 0)));
        assertTrue(xmlError.getMessage().contains(regla), xmlError.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.004"})
    void debeRechazarDeudaNoPositivaEnXml(String deuda) {
        assertThrows(IllegalArgumentException.class, () -> validador.validar(request(deuda, "1"), totales("118")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.004"})
    void debeRechazarCuotaNoPositivaEnXml(String cuota) {
        assertThrows(IllegalArgumentException.class, () -> validador.validar(request("1", cuota), totales("118")));
    }

    @ParameterizedTest
    @CsvSource({"118,118", "118,60|58", "2.000,1.004|1.004", "2.020,1.005|1.005", "118.004,118.004"})
    void debeCumplirEcuacionesSobrePaymentTermsFinal(String deuda, String cuotas) throws Exception {
        ComprobanteRequest r = request(deuda, cuotas.split("\\|"));
        var totales = totales("118");
        assertDoesNotThrow(() -> validador.validar(r, totales));
        byte[] xml = builder.construirXmlUbl21(empresa(), r, 1, totales, FECHA.atTime(10, 0));
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var pagos = doc.getElementsByTagNameNS("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2", "PaymentTerms");
        var xpath = XPathFactory.newInstance().newXPath();
        BigDecimal deudaXml = new BigDecimal(xpath.evaluate("*[local-name()='Amount']", pagos.item(0)));
        BigDecimal totalXml = new BigDecimal(xpath.evaluate("/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']", doc));
        assertTrue(deudaXml.signum() > 0);
        assertTrue(deudaXml.compareTo(totalXml) <= 0);
        BigDecimal suma = BigDecimal.ZERO;
        for (int i = 1; i < pagos.getLength(); i++) {
            Element pago = (Element) pagos.item(i);
            BigDecimal monto = new BigDecimal(xpath.evaluate("*[local-name()='Amount']", pago));
            assertEquals(2, monto.scale());
            assertTrue(monto.signum() > 0);
            assertTrue(monto.compareTo(totalXml) <= 0);
            assertEquals("Cuota%03d".formatted(i), xpath.evaluate("*[local-name()='PaymentMeansID']", pago));
            assertEquals(FECHA.plusDays(30).toString(), xpath.evaluate("*[local-name()='PaymentDueDate']", pago));
            suma = suma.add(monto);
        }
        assertEquals(0, suma.compareTo(deudaXml));
    }

    @Test
    void debeConservarValidacionDeFechaPosteriorALaEmision() {
        ComprobanteRequest r = request("118", "118");
        r.getCuotas().getFirst().setFechaVencimiento(FECHA);
        assertThrows(IllegalArgumentException.class, () -> validador.validar(r, totales("118")));
    }

    private ComprobanteRequest request(String deuda, String... cuotas) {
        ComprobanteRequest r = new ComprobanteRequest();
        r.setTipoOperacion("0101"); r.setTipoComprobante("01"); r.setSerie("F001");
        r.setTipoDocCliente("6"); r.setNumDocCliente("20100130204"); r.setRznSocialCliente("CLIENTE TEST SAC");
        r.setFormaPago("Credito"); r.setMontoPendientePago(new BigDecimal(deuda));
        r.setCuotas(Arrays.stream(cuotas).map(m -> {
            ComprobanteRequest.CuotaRequest c = new ComprobanteRequest.CuotaRequest();
            c.setMonto(new BigDecimal(m)); c.setFechaVencimiento(FECHA.plusDays(30)); return c;
        }).toList());
        return r;
    }

    private CalculoTributarioService.Totales totales(String total) {
        return new CalculoTributarioService.Totales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(total), List.of());
    }

    private Empresa empresa() {
        Empresa e = new Empresa(); e.setRuc("20100130204"); e.setRazonSocial("EMPRESA TEST SAC");
        e.setUbigeo("150101"); e.setCodigoEstablecimiento("0000"); e.setDepartamento("LIMA");
        e.setProvincia("LIMA"); e.setDistrito("LIMA"); e.setDireccionFiscal("AV TEST 123"); return e;
    }
}
