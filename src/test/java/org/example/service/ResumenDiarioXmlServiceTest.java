package org.example.service;

import org.example.model.Empresa;
import org.example.model.ResumenDiario;
import org.example.model.ResumenDiarioDetalle;

import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * =============================================================
 * PRUEBA ESTRUCTURAL DEL SUMMARYDOCUMENTS
 * =============================================================
 *
 * Esta prueba NO llama a SUNAT.
 *
 * Su objetivo es detectar antes de arrancar el backend errores como:
 *
 * - orden incorrecto de elementos UBL
 * - namespaces incorrectos
 * - estructura que no cumple el XSD de SummaryDocuments
 * - representación incorrecta del consumidor sin identificación
 *
 * Utiliza exactamente el XSD SUNAT que ya se encuentra en resources.
 */
class ResumenDiarioXmlServiceTest {

    @Test
    void debeConstruirSummaryDocumentsValidoSegunXsd() throws Exception {

        Empresa empresa = new Empresa();
        empresa.setRuc("20123456789");
        empresa.setRazonSocial("EMPRESA BETA S.A.C.");

        ResumenDiario resumen =
                ResumenDiario.builder()
                        .rucEmisor("20123456789")
                        .fechaDocumentos(LocalDate.of(2026, 9, 19))
                        .fechaGeneracion(LocalDate.of(2026, 9, 19))
                        .correlativo(1)
                        .identificador("RC-20260919-1")
                        .nombreArchivoXml("20123456789-RC-20260919-1.xml")
                        .cantidadDocumentos(1)
                        .build();

        /*
         * Internamente el backend guarda consumidor final como:
         *
         * tipo = 0
         * número = -
         *
         * ResumenDiarioXmlService debe transformarlo a "-" / "-"
         * al construir el XML SUNAT.
         */
        ResumenDiarioDetalle detalle =
                ResumenDiarioDetalle.builder()
                        .comprobanteId(100L)
                        .numeroLinea(1)
                        .tipoComprobante("03")
                        .serie("B001")
                        .numero(1)
                        .tipoDocCliente("0")
                        .numDocCliente("-")
                        .opGravada(new BigDecimal("100.00"))
                        .opExonerada(BigDecimal.ZERO)
                        .opInafecta(BigDecimal.ZERO)
                        .igv(new BigDecimal("18.00"))
                        .montoTotal(new BigDecimal("118.00"))
                        .moneda("PEN")
                        .codigoEstado("1")
                        .build();

        resumen.addDetalle(detalle);

        ResumenDiarioXmlService service =
                new ResumenDiarioXmlService();

        byte[] xml =
                service.construir(
                        resumen,
                        empresa
                );

        assertNotNull(xml);
        assertTrue(xml.length > 0);

        String texto =
                new String(
                        xml,
                        StandardCharsets.UTF_8
                );

        assertTrue(
                texto.contains(
                        "<cbc:CustomerAssignedAccountID>-</cbc:CustomerAssignedAccountID>"
                )
        );

        assertTrue(
                texto.contains(
                        "<cbc:AdditionalAccountID>-</cbc:AdditionalAccountID>"
                )
        );

        validarContraXsdSunat(xml);
    }

    private void validarContraXsdSunat(
            byte[] xml
    ) throws Exception {

        URL xsd =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(
                                "sunat-xsd/2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd"
                        );

        assertNotNull(
                xsd,
                "No se encontró UBLPE-SummaryDocuments-1.0.xsd en resources."
        );

        SchemaFactory factory =
                SchemaFactory.newInstance(
                        XMLConstants.W3C_XML_SCHEMA_NS_URI
                );

        factory.setProperty(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        StreamSource xsdSource =
                new StreamSource(
                        xsd.openStream()
                );

        xsdSource.setSystemId(
                xsd.toExternalForm()
        );

        Schema schema =
                factory.newSchema(
                        xsdSource
                );

        schema.newValidator()
                .validate(
                        new StreamSource(
                                new ByteArrayInputStream(
                                        xml
                                )
                        )
                );
    }
}
