package org.example.service;

import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.model.Empresa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el XML UBL que nuestro backend construye ANTES de firmarlo.
 * No llama a SUNAT ni necesita base de datos.
 */
class UblXmlBuilderServiceTest {

    private UblXmlBuilderService service;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        service = new UblXmlBuilderService();

        empresa = new Empresa();
        empresa.setRuc("20100130204");
        empresa.setRazonSocial("EMPRESA TEST SAC");
        empresa.setUbigeo("150101");
        empresa.setCodigoEstablecimiento("0000");
        empresa.setProvincia("LIMA");
        empresa.setDepartamento("LIMA");
        empresa.setDistrito("LIMA");
        empresa.setDireccionFiscal("AV TEST 123");
    }

    @Test
    void facturaDebeConstruirIdentidadFechasTipoOperacionYTotales() {
        ComprobanteRequest req = comprobante("01", "F001", "6", "20100130204", "CLIENTE SAC");
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 19, 14, 30, 45);

        String xml = texto(service.construirXmlUbl21(empresa, req, 7, totales(), fecha));

        assertTrue(xml.contains("<Invoice"));
        assertTrue(xml.contains("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"));
        assertTrue(xml.contains(">0101</cbc:ProfileID>"));
        assertTrue(xml.contains(">F001-00000007</cbc:ID>"));
        assertTrue(xml.contains(">2026-09-19</cbc:IssueDate>"));
        assertTrue(xml.contains(">14:30:45</cbc:IssueTime>"));
        assertTrue(xml.contains(">01</cbc:InvoiceTypeCode>"));
        assertTrue(xml.contains("20100130204"));
        assertTrue(xml.contains("EMPRESA TEST SAC"));
        assertTrue(xml.contains("CLIENTE SAC"));
        assertTrue(xml.contains("118.00"));
    }

    @Test
    void boletaDebeUsarTipo03YSerieB() {
        ComprobanteRequest req = comprobante("03", "B001", "1", "48411579", "CLIENTE TEST");

        String xml = texto(service.construirXmlUbl21(
                empresa, req, 15, totales(), LocalDateTime.of(2026, 9, 19, 10, 0)));

        assertTrue(xml.contains(">B001-00000015</cbc:ID>"));
        assertTrue(xml.contains(">03</cbc:InvoiceTypeCode>"));
        assertTrue(xml.contains("48411579"));
    }

    @Test
    void consumidorFinalNormalizadoDebePoderRepresentarseEnBoleta() {

        ComprobanteRequest req =
                comprobante(
                        "03",
                        "B001",
                        "0",
                        "-",
                        "CONSUMIDOR FINAL"
                );

        String xml =
                texto(
                        service.construirXmlUbl21(
                                empresa,
                                req,
                                1,
                                totales(),
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        19,
                                        9,
                                        0
                                )
                        )
                );

        /*
         * El nombre sí debe aparecer.
         */
        assertTrue(
                xml.contains(
                        "CONSUMIDOR FINAL"
                )
        );

        /*
         * El valor interno tipo=0 / numero=-
         * NO debe enviarse como identificación
         * del cliente en el XML SUNAT.
         */
        assertFalse(
                xml.contains(
                        "schemeID=\"0\""
                )
        );
    }

    @Test
    void fechaTributariaNulaDebeRechazarse() {
        ComprobanteRequest req = comprobante("01", "F001", "6", "20100130204", "CLIENTE SAC");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.construirXmlUbl21(empresa, req, 1, totales(), null));

        assertTrue(ex.getMessage().toLowerCase().contains("fecha"));
    }

    @Test
    void notaCreditoDebeIncluirDocumentoAfectadoYMotivo() {
        NotaCreditoRequest req = notaCredito("01", "FC01", "F001-00000001", "01");

        String xml = texto(service.construirXmlNotaCredito21(
                empresa, req, 2, totales(), LocalDateTime.of(2026, 9, 19, 16, 5, 6)));

        assertTrue(xml.contains("<CreditNote"));
        assertTrue(xml.contains("urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"));
        assertTrue(xml.contains(">FC01-00000002</cbc:ID>"));
        assertTrue(xml.contains(">F001-00000001</cbc:ReferenceID>"));
        assertTrue(xml.contains(">01</cbc:ResponseCode>"));
        assertTrue(xml.contains(">F001-00000001</cbc:ID>"));
        assertTrue(xml.contains(">01</cbc:DocumentTypeCode>"));
        assertTrue(xml.contains(">2026-09-19</cbc:IssueDate>"));
        assertTrue(xml.contains(">16:05:06</cbc:IssueTime>"));
    }

    @Test
    void notaCreditoDeBoletaDebeReferenciarTipo03() {
        NotaCreditoRequest req = notaCredito("03", "BC01", "B001-00000003", "07");

        String xml = texto(service.construirXmlNotaCredito21(
                empresa, req, 9, totales(), LocalDateTime.of(2026, 9, 19, 11, 0)));

        assertTrue(xml.contains(">BC01-00000009</cbc:ID>"));
        assertTrue(xml.contains(">B001-00000003</cbc:ReferenceID>"));
        assertTrue(xml.contains(">03</cbc:DocumentTypeCode>"));
        assertTrue(xml.contains(">07</cbc:ResponseCode>"));
    }

    private ComprobanteRequest comprobante(String tipo, String serie, String tipoDoc, String numeroDoc, String nombre) {
        ComprobanteRequest r = new ComprobanteRequest();
        r.setTiendaId(1L);
        r.setTipoOperacion("0101");
        r.setTipoComprobante(tipo);
        r.setSerie(serie);
        r.setFormaPago("Contado");
        r.setTipoDocCliente(tipoDoc);
        r.setNumDocCliente(numeroDoc);
        r.setRznSocialCliente(nombre);
        return r;
    }

    private NotaCreditoRequest notaCredito(String tipoAfectado, String serie, String afectado, String motivo) {
        NotaCreditoRequest r = new NotaCreditoRequest();
        r.setTiendaId(1L);
        r.setSerie(serie);
        r.setTipoComprobanteAfectado(tipoAfectado);
        r.setNumComprobanteAfectado(afectado);
        r.setCodMotivoSunat(motivo);
        r.setDescripcionMotivo("MOTIVO DE PRUEBA");
        r.setTipoDocCliente("1");
        r.setNumDocCliente("48411579");
        r.setRznSocialCliente("CLIENTE TEST");
        return r;
    }

    private CalculoTributarioService.Totales totales() {
        CalculoTributarioService.ItemCalculado item = new CalculoTributarioService.ItemCalculado(
                1,
                new BigDecimal("1.0000"),
                "NIU",
                "PRODUCTO TEST",
                new BigDecimal("100.0000"),
                new BigDecimal("118.0000"),
                new BigDecimal("100.00"),
                new BigDecimal("18.00"),
                new BigDecimal("118.00"),
                "10",
                new BigDecimal("18.00")
        );

        return new CalculoTributarioService.Totales(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("18.00"),
                new BigDecimal("118.00"),
                List.of(item)
        );
    }

    private String texto(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }
}
