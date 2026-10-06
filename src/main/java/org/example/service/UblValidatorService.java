
package org.example.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * =============================================================
 * UBL VALIDATOR SERVICE
 * =============================================================
 *
 * Valida primero que el XML sea seguro y esté bien formado y,
 * cuando la validación XSD está habilitada, comprueba la estructura
 * contra los esquemas que se encuentran dentro del proyecto.
 *
 * Soporta actualmente:
 *
 * - Factura / Boleta UBL 2.1
 * - Nota de Crédito UBL 2.1
 * - Resumen Diario SummaryDocuments UBL 2.0
 */
@Service
@RequiredArgsConstructor
public class UblValidatorService {

    private final ResourceLoader resourceLoader;

    @Value("${sunat.validation.xsd.enabled:true}")
    private boolean xsdEnabled;

    @Value("${sunat.validation.xsd.invoice}")
    private String invoiceXsd;

    @Value("${sunat.validation.xsd.credit-note}")
    private String creditNoteXsd;

    @Value("${sunat.validation.xsd.summary:classpath:sunat-xsd/2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd}")
    private String summaryXsd;

    @Value("${sunat.validation.xsd.voided:classpath:sunat-xsd/2.0/maindoc/UBLPE-VoidedDocuments-1.0.xsd}")
    private String voidedXsd;


    // =========================================================
    // FACTURA / BOLETA UBL 2.1
    // =========================================================

    public void validarFactura(
            byte[] xml
    ) {

        validar(
                xml,
                invoiceXsd,
                "Factura/Boleta UBL 2.1"
        );
    }


    // =========================================================
    // NOTA DE CRÉDITO UBL 2.1
    // =========================================================

    public void validarNotaCredito(
            byte[] xml
    ) {

        validar(
                xml,
                creditNoteXsd,
                "Nota de Crédito UBL 2.1"
        );
    }


    // =========================================================
    // RESUMEN DIARIO UBL 2.0
    // =========================================================

    public void validarResumenDiario(
            byte[] xml
    ) {

        validar(
                xml,
                summaryXsd,
                "Resumen Diario UBL 2.0"
        );
    }


    // =========================================================
    // COMUNICACIÓN DE BAJA UBL 2.0
    // =========================================================

    public void validarComunicacionBaja(
            byte[] xml
    ) {
        validar(
                xml,
                voidedXsd,
                "Comunicación de Baja UBL 2.0"
        );
    }


    // =========================================================
    // VALIDACIÓN PRINCIPAL
    // =========================================================

    private void validar(

            byte[] xml,

            String xsd,

            String nombreDocumento
    ) {

        validarXmlBienFormado(
                xml,
                nombreDocumento
        );

        if (xsdEnabled) {

            validarXsd(
                    xml,
                    xsd,
                    nombreDocumento
            );
        }
    }


    // =========================================================
    // XML BIEN FORMADO / XXE
    // =========================================================

    private void validarXmlBienFormado(

            byte[] xml,

            String nombreDocumento
    ) {

        if (
                xml == null
                        ||
                        xml.length == 0
        ) {

            throw new IllegalArgumentException(
                    nombreDocumento + ": XML vacío."
            );
        }

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(
                    true
            );

            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );

            factory.setXIncludeAware(
                    false
            );

            factory.setExpandEntityReferences(
                    false
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

            factory
                    .newDocumentBuilder()
                    .parse(
                            new ByteArrayInputStream(
                                    xml
                            )
                    );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    nombreDocumento
                            + " mal formado: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // =========================================================
    // XSD
    // =========================================================

    private void validarXsd(

            byte[] xml,

            String location,

            String nombreDocumento
    ) {

        try {

            if (
                    location == null
                            ||
                            location.isBlank()
            ) {

                throw new IllegalStateException(
                        "No se configuró el XSD para "
                                + nombreDocumento
                );
            }

            Resource resource =
                    resourceLoader.getResource(
                            location
                    );

            if (!resource.exists()) {

                throw new IllegalStateException(
                        "No se encontró el XSD SUNAT para "
                                + nombreDocumento
                                + ": "
                                + location
                );
            }

            SchemaFactory factory =
                    SchemaFactory.newInstance(
                            XMLConstants.W3C_XML_SCHEMA_NS_URI
                    );

            factory.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            /*
             * Los XSD UBL incluyen/importan archivos relativos
             * del mismo paquete. Permitimos únicamente recursos
             * locales file/jar, nunca http/https.
             */
            factory.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "file,jar"
            );

            Schema schema;

            try (
                    InputStream xsdInput =
                            resource.getInputStream()
            ) {

                StreamSource xsdSource =
                        new StreamSource(
                                xsdInput
                        );

                xsdSource.setSystemId(
                        resource
                                .getURL()
                                .toExternalForm()
                );

                schema =
                        factory.newSchema(
                                xsdSource
                        );
            }

            Validator validator =
                    schema.newValidator();

            validator.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            validator.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "file,jar"
            );

            validator.validate(
                    new StreamSource(
                            new ByteArrayInputStream(
                                    xml
                            )
                    )
            );

        } catch (SAXException e) {

            throw new IllegalArgumentException(
                    nombreDocumento
                            + " no cumple el XSD configurado: "
                            + e.getMessage(),
                    e
            );

        } catch (IllegalArgumentException e) {

            throw e;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error validando XSD de "
                            + nombreDocumento
                            + ": "
                            + e.getMessage(),
                    e
            );
        }
    }
}
