package org.example.service;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.model.Empresa;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionMonetariaXmlTest {
    private final CalculoTributarioService calculo = new CalculoTributarioService(new ReglasTributariasProperties());
    private final UblXmlBuilderService builder = new UblXmlBuilderService();

    static Stream<Arguments> casos() {
        return Stream.of("01", "03", "07").flatMap(tipo -> Stream.of("10", "20", "30").flatMap(afectacion ->
                Stream.of(new String[]{"1000", "0.0149"}, new String[]{"1000000", "0.1234567890"},
                        new String[]{"3.25", "1.2345678912"}, new String[]{"1000", "12.3456"})
                        .map(v -> Arguments.of(tipo, afectacion, v[0], v[1]))));
    }

    @ParameterizedTest
    @MethodSource("casos")
    void debeConservarPrecisionEnXmlFinal(String tipo, String afectacion, String cantidad, String valor) throws Exception {
        BigDecimal q = new BigDecimal(cantidad);
        BigDecimal v = new BigDecimal(valor);
        byte[] xml;
        CalculoTributarioService.Totales totales;
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 30, 10, 0);
        if ("07".equals(tipo)) {
            NotaCreditoRequest r = notaCredito(q, v, afectacion);
            totales = calculo.calcular(r, new BigDecimal("18.00"));
            xml = builder.construirXmlNotaCredito21(empresa(), r, 1, totales, fecha);
        } else {
            ComprobanteRequest r = comprobante(tipo, q, v, afectacion);
            totales = calculo.calcular(r, new BigDecimal("18.00"));
            xml = builder.construirXmlUbl21(empresa(), r, 1, totales, fecha);
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        String cac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
        Element linea = (Element) doc.getElementsByTagNameNS(cac, "07".equals(tipo) ? "CreditNoteLine" : "InvoiceLine").item(0);
        BigDecimal cantidadXml = numero(linea, "*[local-name()='InvoicedQuantity' or local-name()='CreditedQuantity']");
        BigDecimal valorXml = numero(linea, "*[local-name()='Price']/*[local-name()='PriceAmount']");
        BigDecimal baseXml = numero(linea, "*[local-name()='LineExtensionAmount']");
        BigDecimal igvXml = numero(linea, "*[local-name()='TaxTotal']/*[local-name()='TaxAmount']");
        BigDecimal precioXml = numero(linea, "*[local-name()='PricingReference']/*[local-name()='AlternativeConditionPrice']/*[local-name()='PriceAmount']");
        assertEquals(0, q.compareTo(cantidadXml));
        assertEquals(0, v.compareTo(valorXml));
        assertEquals(0, cantidadXml.multiply(valorXml).setScale(2, RoundingMode.HALF_UP).compareTo(baseXml));
        BigDecimal tasa = "10".equals(afectacion) ? new BigDecimal("0.18") : BigDecimal.ZERO;
        assertEquals(0, baseXml.multiply(tasa).setScale(2, RoundingMode.HALF_UP).compareTo(igvXml));
        assertEquals(0, v.multiply(BigDecimal.ONE.add(tasa)).setScale(10, RoundingMode.HALF_UP).compareTo(precioXml));
        BigDecimal totalXml = numero(doc.getDocumentElement(), "*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']");
        assertEquals(0, baseXml.add(igvXml).compareTo(totalXml));
        assertEquals(0, totales.total().compareTo(totalXml));
        assertTrue(valorXml.scale() <= 10);
        assertTrue(precioXml.scale() <= 10);
        assertEquals(2, baseXml.scale());
        assertEquals(2, igvXml.scale());
        if ("1000".equals(cantidad) && "0.0149".equals(valor)) {
            assertEquals(new BigDecimal("14.90"), baseXml);
            assertEquals(0, precioXml.multiply(q).setScale(2, RoundingMode.HALF_UP).compareTo(totalXml));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.12345678901", "1000000000000", "0", "-0.01"})
    void debeRechazarValorUnitarioNoRepresentableSinRedondearlo(String valor) {
        assertThrows(IllegalArgumentException.class,
                () -> calculo.calcular(comprobante("01", BigDecimal.ONE, new BigDecimal(valor), "10")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0000000001", "123.1234567890000", "999999999999"})
    void debeAceptarLimitesYCerosDecimalesNoSignificativos(String valor) {
        assertDoesNotThrow(() -> calculo.calcular(comprobante("01", BigDecimal.ONE, new BigDecimal(valor), "20")));
    }

    private BigDecimal numero(Element e, String ruta) throws Exception {
        return new BigDecimal(XPathFactory.newInstance().newXPath().evaluate(ruta, e));
    }

    private ComprobanteRequest comprobante(String tipo, BigDecimal cantidad, BigDecimal valor, String afectacion) {
        ComprobanteRequest r = new ComprobanteRequest();
        r.setTiendaId(1L); r.setTipoOperacion("0101"); r.setTipoComprobante(tipo);
        r.setSerie("03".equals(tipo) ? "B001" : "F001"); r.setFormaPago("Contado");
        r.setTipoDocCliente("6"); r.setNumDocCliente("20100130204"); r.setRznSocialCliente("CLIENTE TEST SAC");
        ComprobanteRequest.DetalleItemRequest i = new ComprobanteRequest.DetalleItemRequest();
        i.setCantidad(cantidad); i.setValorUnitario(valor); i.setTipoAfectacionIgv(afectacion);
        i.setUnidadMedida("NIU"); i.setDescripcion("PRODUCTO TEST"); r.setItems(List.of(i));
        return r;
    }

    private NotaCreditoRequest notaCredito(BigDecimal cantidad, BigDecimal valor, String afectacion) {
        NotaCreditoRequest r = new NotaCreditoRequest();
        r.setTiendaId(1L); r.setSerie("FC01"); r.setTipoComprobanteAfectado("01");
        r.setNumComprobanteAfectado("F001-00000001"); r.setCodMotivoSunat("07"); r.setDescripcionMotivo("DEVOLUCION TEST");
        r.setTipoDocCliente("6"); r.setNumDocCliente("20100130204"); r.setRznSocialCliente("CLIENTE TEST SAC");
        NotaCreditoRequest.DetalleItemRequest i = new NotaCreditoRequest.DetalleItemRequest();
        i.setCantidad(cantidad); i.setValorUnitario(valor); i.setTipoAfectacionIgv(afectacion);
        i.setUnidadMedida("NIU"); i.setDescripcion("PRODUCTO TEST"); r.setItems(List.of(i));
        return r;
    }

    private Empresa empresa() {
        Empresa e = new Empresa();
        e.setRuc("20100130204"); e.setRazonSocial("EMPRESA TEST SAC"); e.setUbigeo("150101");
        e.setCodigoEstablecimiento("0000"); e.setProvincia("LIMA"); e.setDepartamento("LIMA");
        e.setDistrito("LIMA"); e.setDireccionFiscal("AV TEST 123");
        return e;
    }
}
