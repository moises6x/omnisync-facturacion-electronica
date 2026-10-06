package org.example.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.example.util.LecturaLimitada;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class CdrParserService {

    @Value("${sunat.respuesta.max-zip-bytes:8388608}")
    private int maxZipBytes = 8 * 1024 * 1024;
    @Value("${sunat.cdr.max-xml-bytes:16777216}")
    private int maxXmlBytes = 16 * 1024 * 1024;
    @Value("${sunat.cdr.max-expanded-bytes:33554432}")
    private int maxExpandedBytes = 32 * 1024 * 1024;
    @Value("${sunat.cdr.max-entries:64}")
    private int maxEntries = 64;

    private static final String APPLICATION_RESPONSE_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2";
    private static final String CAC_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    public ResultadoCdr procesar(
            byte[] cdrZip
    ) {

        try {

            byte[] cdrXml =
                    extraerXml(
                            cdrZip
                    );

            Document doc =
                    parseSeguro(
                            cdrXml
                    );

            Element raiz = doc.getDocumentElement();
            if (!"ApplicationResponse".equals(raiz.getLocalName())
                    || !APPLICATION_RESPONSE_NS.equals(raiz.getNamespaceURI())) {
                throw new IllegalArgumentException(
                        "La raíz del CDR debe ser ApplicationResponse con el namespace UBL correcto."
                );
            }

            /*
             * Manual del programador SUNAT v2.1 (mayo 2021), pp. 23 y 38:
             * el perfil de CDR de sendBill/sendSummary tiene UNA respuesta,
             * aunque el esquema UBL genérico admita varias. No buscar por
             * local-name global ni resolver duplicados tomando el primero.
             * Esto valida la estructura interpretada, no la firma ni todo el XSD.
             */
            Element documento = unico(raiz, CAC_NS, "DocumentResponse");
            Element respuesta = unico(documento, CAC_NS, "Response");
            String responseCode = textoRequerido(respuesta, CBC_NS, "ResponseCode");
            if (!responseCode.matches("[0-9]{1,4}")) {
                throw new IllegalArgumentException("ResponseCode debe ser numérico de uno a cuatro dígitos.");
            }
            String description = textoRequerido(respuesta, CBC_NS, "Description");
            String referenceId = textoRequerido(respuesta, CBC_NS, "ReferenceID");
            Element referenciaDocumento = unico(documento, CAC_NS, "DocumentReference");
            String documentReferenceId = textoRequerido(referenciaDocumento, CBC_NS, "ID");

            // Sólo presencia/ruta: no imponer formato, normalización ni igualdad RC–CDR.
            List<String> observaciones = new ArrayList<>();
            for (Element nota : hijos(raiz, CBC_NS, "Note")) {
                String texto = textoSimple(nota);
                if (!texto.isBlank()) {
                    observaciones.add(texto);
                }
            }

            boolean aceptado =
                    "0".equals(
                            responseCode
                    );

            return new ResultadoCdr(

                    responseCode,

                    description,

                    observaciones,

                    aceptado,

                    cdrXml,

                    referenceId,
                    documentReferenceId
            );

        } catch (Exception e) {

            throw new SunatDirectSoapClient.SunatProtocolException(
                    "No se pudo procesar el CDR: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private byte[] extraerXml(
            byte[] zip
    ) throws Exception {

        if (zip == null || zip.length == 0) {
            throw new IllegalArgumentException("El CDR ZIP está vacío.");
        }
        if (maxZipBytes <= 0 || maxXmlBytes <= 0 || maxExpandedBytes <= 0 || maxEntries <= 0 || zip.length > maxZipBytes)
            throw new IllegalArgumentException("CDR ZIP excede el límite técnico o su configuración es inválida.");
        byte[] xml = null;
        int entradas = 0, expandidos = 0;
        try (
                ZipInputStream zis =
                        new ZipInputStream(
                                new ByteArrayInputStream(
                                        zip
                                )
                        )
        ) {

            ZipEntry entry;

            while (
                    (entry = zis.getNextEntry())
                            != null
            ) {

                if (++entradas > maxEntries) throw new IllegalArgumentException("CDR ZIP excede el máximo técnico de entradas.");

                if (
                        !entry.isDirectory() &&
                                entry
                                        .getName()
                                        .toLowerCase(Locale.ROOT)
                                        .endsWith(".xml")
                ) {

                    if (xml != null) {
                        throw new IllegalArgumentException("El CDR contiene múltiples XML; respuesta ambigua.");
                    }
                    xml = LecturaLimitada.leer(zis, Math.min(maxXmlBytes, maxExpandedBytes - expandidos));
                    expandidos += xml.length;
                } else {
                    // Contar también entradas ignoradas/directorios: getNextEntry drena el anterior.
                    expandidos += LecturaLimitada.descartar(zis, maxExpandedBytes - expandidos);
                }
                zis.closeEntry();
            }
        }

        if (xml == null) {
            throw new IllegalArgumentException("El CDR no contiene XML.");
        }
        return xml;
    }

    private Document parseSeguro(
            byte[] xml
    ) throws Exception {

        DocumentBuilderFactory f =
                DocumentBuilderFactory
                        .newInstance();

        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        f.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        f.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        f.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        return f
                .newDocumentBuilder()
                .parse(
                        new ByteArrayInputStream(
                                xml
                        )
                );
    }

    private Element unico(Element padre, String namespace, String localName) {
        List<Element> elementos = hijos(padre, namespace, localName);
        if (elementos.size() != 1) {
            throw new IllegalArgumentException(
                    "Se requiere exactamente un " + localName + " en " + padre.getLocalName()
                            + "; encontrados: " + elementos.size() + "."
            );
        }
        return elementos.getFirst();
    }

    private List<Element> hijos(Element padre, String namespace, String localName) {
        List<Element> resultado = new ArrayList<>();
        NodeList nodes = padre.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node nodo = nodes.item(i);
            if (nodo instanceof Element elemento
                    && namespace.equals(elemento.getNamespaceURI())
                    && localName.equals(elemento.getLocalName())) {
                resultado.add(elemento);
            }
        }
        return resultado;
    }

    private String textoRequerido(Element padre, String namespace, String localName) {
        String texto = textoSimple(unico(padre, namespace, localName));
        if (texto.isBlank()) {
            throw new IllegalArgumentException(localName + " no puede estar vacío.");
        }
        return texto;
    }

    private String textoSimple(Element elemento) {
        NodeList nodes = elemento.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                throw new IllegalArgumentException(elemento.getLocalName() + " debe contener texto simple.");
            }
        }
        return elemento.getTextContent().trim();
    }

    public record ResultadoCdr(

            String responseCode,

            String description,

            List<String> observaciones,

            boolean aceptado,

            byte[] xml,

            String referenceId,

            String documentReferenceId

    ) {

        public ResultadoCdr(String responseCode, String description, List<String> observaciones,
                boolean aceptado, byte[] xml, String referenceId) {
            this(responseCode, description, observaciones, aceptado, xml, referenceId, "");
        }

        /**
         * Constructor de compatibilidad para tests/mocks anteriores.
         * El parser real siempre exige ReferenceID en la ruta documentada.
         */
        public ResultadoCdr(
                String responseCode,
                String description,
                List<String> observaciones,
                boolean aceptado,
                byte[] xml
        ) {
            this(
                    responseCode,
                    description,
                    observaciones,
                    aceptado,
                    xml,
                    ""
            );
        }
    }
}
