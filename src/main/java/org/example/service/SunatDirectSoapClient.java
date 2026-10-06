
package org.example.service;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.example.util.LecturaLimitada;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.Base64;
import java.util.Locale;

/**
 * =============================================================
 * SUNAT DIRECT SOAP CLIENT
 * =============================================================
 *
 * Cliente SOAP mínimo utilizado por el backend para comunicarse
 * directamente con BillService de SUNAT.
 *
 * Soporta:
 *
 * - sendBill    -> Facturas y NC de Factura
 * - sendSummary -> Resumen Diario / comunicaciones asíncronas
 * - getStatus   -> consulta del ticket obtenido por sendSummary
 *
 * No almacena credenciales. Las recibe únicamente durante
 * la llamada y las escribe dentro del WS-Security Header.
 */
@Component
public class SunatDirectSoapClient {

    private static final String SOAP_NS =
            "http://schemas.xmlsoap.org/soap/envelope/";

    // Límites operativos, no tamaños normativos SUNAT.
    @Value("${sunat.respuesta.max-soap-bytes:16777216}")
    private int maxSoapBytes = 16 * 1024 * 1024;
    @Value("${sunat.respuesta.max-zip-bytes:8388608}")
    private int maxZipBytes = 8 * 1024 * 1024;

    private static final String SERVICE_NS =
            "http://service.sunat.gob.pe";

    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";


    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    @Value("${sunat.environment:BETA}")
    private String environment;

    @Value("${sunat.billservice.url}")
    private String sunatUrl;

    @Value("${sunat.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${sunat.read-timeout:20000}")
    private int readTimeout;


    // =========================================================
    // VALIDACIÓN DE ENDPOINT AL ARRANCAR
    // =========================================================

    @PostConstruct
    public void validarEndpoint() {

        try {

            URI uri =
                    URI.create(
                            sunatUrl
                    );

            if (
                    !"https".equalsIgnoreCase(
                            uri.getScheme()
                    )
            ) {

                throw new IllegalStateException(
                        "El endpoint SUNAT debe utilizar HTTPS."
                );
            }

            String host =
                    uri.getHost();

            if (
                    host == null
                            ||
                            host.isBlank()
            ) {

                throw new IllegalStateException(
                        "Endpoint SUNAT inválido."
                );
            }

            if (
                    esProduccion()
                            &&
                            !"e-factura.sunat.gob.pe"
                                    .equalsIgnoreCase(
                                            host
                                    )
            ) {

                throw new IllegalStateException(
                        "PERFIL PRODUCCIÓN está intentando utilizar un endpoint que no es SUNAT PRODUCCIÓN."
                );
            }

            if (
                    !esProduccion()
                            &&
                            !"e-beta.sunat.gob.pe"
                                    .equalsIgnoreCase(
                                            host
                                    )
            ) {

                throw new IllegalStateException(
                        "PERFIL BETA está intentando utilizar un endpoint que no es SUNAT BETA."
                );
            }

        } catch (IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Configuración del endpoint SUNAT inválida.",
                    e
            );
        }
    }


    // =========================================================
    // SENDBILL
    // =========================================================

    /**
     * Envía un comprobante individual.
     *
     * SUNAT devuelve directamente el ZIP de la CDR.
     */
    public byte[] enviarZipDirecto(

            String ruc,

            String usuarioSol,

            String claveSol,

            String nombreZip,

            byte[] zipBytes
    ) {

        validarCredencialesYArchivo(
                ruc,
                usuarioSol,
                claveSol,
                nombreZip,
                zipBytes
        );

        String username =
                construirUsername(
                        ruc,
                        usuarioSol
                );

        try {

            byte[] soap =
                    construirSoapArchivo(
                            "sendBill",
                            username,
                            claveSol,
                            nombreZip,
                            zipBytes
                    );

            byte[] respuesta =
                    ejecutarSoap(
                            "urn:sendBill",
                            soap
                    );

            return extraerBase64Obligatorio(
                    respuesta,
                    "applicationResponse"
            );

        } catch (
                SunatSoapFaultException
                |
                SunatTransportException
                |
                SunatProtocolException e
        ) {

            throw e;

        } catch (Exception e) {

            throw new SunatProtocolException(
                    "No se pudo procesar la respuesta sendBill de SUNAT: "
                            + mensajeSeguro(e),
                    e
            );
        }
    }


    // =========================================================
    // SENDSUMMARY
    // =========================================================

    /**
     * Envía un Resumen Diario.
     *
     * A diferencia de sendBill, SUNAT NO devuelve la CDR en esta
     * llamada. Devuelve un ticket que posteriormente debe consultarse
     * mediante getStatus.
     */
    public String enviarResumenDirecto(

            String ruc,

            String usuarioSol,

            String claveSol,

            String nombreZip,

            byte[] zipBytes
    ) {

        validarCredencialesYArchivo(
                ruc,
                usuarioSol,
                claveSol,
                nombreZip,
                zipBytes
        );

        String username =
                construirUsername(
                        ruc,
                        usuarioSol
                );

        try {

            byte[] soap =
                    construirSoapArchivo(
                            "sendSummary",
                            username,
                            claveSol,
                            nombreZip,
                            zipBytes
                    );

            byte[] respuesta =
                    ejecutarSoap(
                            "urn:sendSummary",
                            soap
                    );

            String ticket =
                    extraerTextoObligatorio(
                            respuesta,
                            "ticket"
                    );

            if (ticket.isBlank()) {

                throw new SunatProtocolException(
                        "SUNAT devolvió un ticket vacío para sendSummary."
                );
            }

            org.example.util.TicketSunat.validarLongitud(ticket);
            return ticket;

        } catch (
                SunatSoapFaultException
                |
                SunatTransportException
                |
                SunatProtocolException e
        ) {

            throw e;

        } catch (Exception e) {

            throw new SunatProtocolException(
                    "No se pudo procesar la respuesta sendSummary de SUNAT: "
                            + mensajeSeguro(e),
                    e
            );
        }
    }


    // =========================================================
    // GETSTATUS
    // =========================================================

    /**
     * Consulta el estado de un ticket asíncrono.
     *
     * SUNAT documenta principalmente:
     *
     * 0  = proceso terminado correctamente
     * 98 = en proceso
     * 99 = proceso terminado con errores
     *
     * Cuando el proceso finaliza, content puede contener el ZIP
     * con la CDR.
     */
    public EstadoTicketSunat consultarTicket(

            String ruc,

            String usuarioSol,

            String claveSol,

            String ticket
    ) {

        validarCredenciales(
                ruc,
                usuarioSol,
                claveSol
        );

        if (
                ticket == null
                        ||
                        ticket.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Ticket SUNAT requerido."
            );
        }

        org.example.util.TicketSunat.validarLongitud(ticket);

        String username =
                construirUsername(
                        ruc,
                        usuarioSol
                );

        try {

            byte[] soap =
                    construirSoapGetStatus(
                            username,
                            claveSol,
                            ticket.trim()
                    );

            byte[] respuesta =
                    ejecutarSoap(
                            "urn:getStatus",
                            soap
                    );

            Document doc =
                    parseSeguro(
                            respuesta
                    );

            Element status = hijoUnico(respuestaOperacion(doc, "getStatusResponse"), "", "status");

            String statusCode =
                    texto(
                            status,
                            "statusCode"
                    );

            if (
                    statusCode == null
                            ||
                            statusCode.isBlank()
            ) {

                throw new SunatProtocolException(
                        "SUNAT no devolvió statusCode para el ticket."
                );
            }

            if (!java.util.Set.of("0", "98", "99").contains(statusCode.trim())) {
                throw new SunatProtocolException("SUNAT devolvió un statusCode no reconocido para getStatus.");
            }

            byte[] content =
                    base64Opcional(
                            status,
                            "content"
                    );

            return new EstadoTicketSunat(
                    statusCode.trim(),
                    content
            );

        } catch (
                SunatSoapFaultException
                |
                SunatTransportException
                |
                SunatProtocolException e
        ) {

            throw e;

        } catch (Exception e) {

            throw new SunatProtocolException(
                    "No se pudo procesar getStatus de SUNAT: "
                            + mensajeSeguro(e),
                    e
            );
        }
    }


    // =========================================================
    // HTTP / SOAP
    // =========================================================

    private byte[] ejecutarSoap(

            String soapAction,

            byte[] soap
    ) {

        HttpURLConnection conn =
                null;

        try {

            URL url =
                    URI
                            .create(
                                    sunatUrl
                            )
                            .toURL();

            conn =
                    (HttpURLConnection)
                            url.openConnection();

            conn.setRequestMethod(
                    "POST"
            );

            conn.setDoOutput(
                    true
            );

            // Un solo intento de transporte: el POST no debe repetirse ante EOF/reset,
            // autenticación o redirects. Streaming evita el replay del cuerpo por el JDK.
            conn.setInstanceFollowRedirects(false);
            conn.setFixedLengthStreamingMode(soap.length);

            conn.setConnectTimeout(
                    connectTimeout
            );

            conn.setReadTimeout(
                    readTimeout
            );

            conn.setRequestProperty(
                    "Content-Type",
                    "text/xml; charset=utf-8"
            );

            conn.setRequestProperty(
                    "SOAPAction",
                    soapAction
            );

            conn.setRequestProperty(
                    "Accept",
                    "text/xml"
            );

            try (
                    OutputStream os =
                            conn.getOutputStream()
            ) {

                os.write(
                        soap
                );
            }

            int httpCode =
                    conn.getResponseCode();

            InputStream stream =
                    httpCode >= 200
                            &&
                            httpCode < 300
                            ?
                            conn.getInputStream()
                            :
                            conn.getErrorStream();

            byte[] respuesta;
            try (InputStream entrada = stream) {
                if (maxSoapBytes <= 0 || conn.getContentLengthLong() > maxSoapBytes)
                    throw new SunatProtocolException("Respuesta SOAP excede el límite técnico configurado.");
                respuesta = entrada == null ? new byte[0] : LecturaLimitada.leer(entrada, maxSoapBytes);
            }

            FaultInfo fault;
            try {
                fault = extraerFaultInfo(respuesta);
            } catch (SunatProtocolException e) {
                if (httpCode < 200 || httpCode >= 300) {
                    throw new SunatTransportException("SUNAT respondió HTTP " + httpCode + ". Error HTTP no SOAP; requiere revisión técnica.");
                }
                throw e;
            }

            if (fault != null) {

                throw new SunatSoapFaultException(
                        fault.faultCode(),
                        fault.faultString(),
                        httpCode
                );
            }

            if (
                    httpCode < 200
                            ||
                            httpCode >= 300
            ) {

                throw new SunatTransportException(
                        "SUNAT respondió HTTP "
                                + httpCode
                                + ". Error HTTP no SOAP; requiere revisión técnica."
                );
            }

            if (
                    respuesta.length == 0
            ) {

                throw new SunatProtocolException(
                        "SUNAT devolvió una respuesta HTTP vacía."
                );
            }

            return respuesta;

        } catch (SunatSoapFaultException e) {

            throw e;

        } catch (SunatProtocolException e) {

            throw e;

        } catch (SunatTransportException e) {

            throw e;

        } catch (IllegalArgumentException e) {
            throw new SunatProtocolException("Respuesta SOAP inválida o supera el límite técnico de lectura.", e);
        } catch (IOException e) {

            throw new SunatTransportException(
                    "Error de red comunicándose con SUNAT: "
                            + mensajeSeguro(e),
                    e
            );

        } catch (Exception e) {

            throw new SunatTransportException(
                    "Error comunicándose con SUNAT: "
                            + mensajeSeguro(e),
                    e
            );

        } finally {

            if (conn != null) {
                conn.disconnect();
            }
        }
    }


    // =========================================================
    // SOAP CON ARCHIVO
    // =========================================================

    private byte[] construirSoapArchivo(

            String operacion,

            String usuario,

            String password,

            String nombreZip,

            byte[] zip
    ) throws Exception {

        ByteArrayOutputStream baos =
                new ByteArrayOutputStream();

        XMLOutputFactory outputFactory =
                XMLOutputFactory.newFactory();

        XMLStreamWriter w =
                outputFactory.createXMLStreamWriter(
                        baos,
                        StandardCharsets.UTF_8.name()
                );

        w.writeStartDocument(
                "UTF-8",
                "1.0"
        );

        iniciarEnvelope(
                w,
                usuario,
                password
        );

        w.writeStartElement(
                "soapenv",
                "Body",
                SOAP_NS
        );

        w.writeStartElement(
                "ser",
                operacion,
                SERVICE_NS
        );

        w.writeStartElement(
                "fileName"
        );

        w.writeCharacters(
                nombreZip
        );

        w.writeEndElement();

        w.writeStartElement(
                "contentFile"
        );

        w.writeCharacters(
                Base64
                        .getEncoder()
                        .encodeToString(
                                zip
                        )
        );

        w.writeEndElement();

        w.writeEndElement(); // operación
        w.writeEndElement(); // Body
        w.writeEndElement(); // Envelope

        w.writeEndDocument();
        w.flush();
        w.close();

        return baos.toByteArray();
    }


    // =========================================================
    // SOAP GETSTATUS
    // =========================================================

    private byte[] construirSoapGetStatus(

            String usuario,

            String password,

            String ticket
    ) throws Exception {

        ByteArrayOutputStream baos =
                new ByteArrayOutputStream();

        XMLStreamWriter w =
                XMLOutputFactory
                        .newFactory()
                        .createXMLStreamWriter(
                                baos,
                                StandardCharsets.UTF_8.name()
                        );

        w.writeStartDocument(
                "UTF-8",
                "1.0"
        );

        iniciarEnvelope(
                w,
                usuario,
                password
        );

        w.writeStartElement(
                "soapenv",
                "Body",
                SOAP_NS
        );

        w.writeStartElement(
                "ser",
                "getStatus",
                SERVICE_NS
        );

        w.writeStartElement(
                "ticket"
        );

        w.writeCharacters(
                ticket
        );

        w.writeEndElement();

        w.writeEndElement(); // getStatus
        w.writeEndElement(); // Body
        w.writeEndElement(); // Envelope

        w.writeEndDocument();
        w.flush();
        w.close();

        return baos.toByteArray();
    }


    // =========================================================
    // ENVELOPE + WS SECURITY
    // =========================================================

    private void iniciarEnvelope(

            XMLStreamWriter w,

            String usuario,

            String password
    ) throws Exception {

        w.writeStartElement(
                "soapenv",
                "Envelope",
                SOAP_NS
        );

        w.writeNamespace(
                "soapenv",
                SOAP_NS
        );

        w.writeNamespace(
                "ser",
                SERVICE_NS
        );

        w.writeNamespace(
                "wsse",
                WSSE_NS
        );

        w.writeStartElement(
                "soapenv",
                "Header",
                SOAP_NS
        );

        w.writeStartElement(
                "wsse",
                "Security",
                WSSE_NS
        );

        w.writeStartElement(
                "wsse",
                "UsernameToken",
                WSSE_NS
        );

        w.writeStartElement(
                "wsse",
                "Username",
                WSSE_NS
        );

        w.writeCharacters(
                usuario
        );

        w.writeEndElement();

        w.writeStartElement(
                "wsse",
                "Password",
                WSSE_NS
        );

        w.writeCharacters(
                password
        );

        w.writeEndElement();

        w.writeEndElement(); // UsernameToken
        w.writeEndElement(); // Security
        w.writeEndElement(); // Header
    }


    // =========================================================
    // RESPUESTAS
    // =========================================================

    private byte[] extraerBase64Obligatorio(

            byte[] soap,

            String localName
    ) throws Exception {

        Document doc =
                parseSeguro(
                        soap
                );

        String valor =
                texto(
                        respuestaOperacion(doc, "sendBillResponse"),
                        localName
                );

        if (
                valor == null
                        ||
                        valor.isBlank()
        ) {

            throw new SunatProtocolException(
                    "SUNAT no devolvió "
                            + localName
                            + "."
            );
        }

        try {

            return Base64
                    .getDecoder().decode(validarBase64Limitado(valor));

        } catch (IllegalArgumentException e) {

            throw new SunatProtocolException(
                    "SUNAT devolvió "
                            + localName
                            + " con Base64 inválido.",
                    e
            );
        }
    }


    private String extraerTextoObligatorio(

            byte[] soap,

            String localName
    ) throws Exception {

        Document doc =
                parseSeguro(
                        soap
                );

        String valor =
                texto(
                        respuestaOperacion(doc, "ticket".equals(localName) ? "sendSummaryResponse" : "sendBillResponse"),
                        localName
                );

        if (valor == null) {

            throw new SunatProtocolException(
                    "SUNAT no devolvió "
                            + localName
                            + "."
            );
        }

        return valor.trim();
    }


    private byte[] base64Opcional(

            Element padre,

            String localName
    ) {

        String valor =
                texto(
                        padre,
                        localName
                );

        if (
                valor == null
                        ||
                        valor.isBlank()
        ) {

            return null;
        }

        try {

            return Base64
                    .getDecoder().decode(validarBase64Limitado(valor));

        } catch (IllegalArgumentException e) {

            throw new SunatProtocolException(
                    "SUNAT devolvió content con Base64 inválido.",
                    e
            );
        }
    }


    private String validarBase64Limitado(String valor) {
        String compacto = valor.replaceAll("[ \\t\\r\\n]", "");
        long estimado = (long) compacto.length() * 3 / 4;
        if (compacto.endsWith("==")) estimado -= 2;
        else if (compacto.endsWith("=")) estimado--;
        if (maxZipBytes <= 0 || estimado > maxZipBytes)
            throw new SunatProtocolException("ZIP SUNAT excede el límite técnico configurado.");
        return compacto;
    }

    private String texto(Element padre, String localName) {
        Element campo = hijoOpcional(padre, "", localName);
        if (campo == null) return null;
        NodeList nodos = campo.getChildNodes();
        for (int i=0; i<nodos.getLength(); i++) {
            if (nodos.item(i) instanceof Element) throw new SunatProtocolException("El campo SOAP " + localName + " debe ser texto simple.");
        }
        return campo.getTextContent();
    }

    private Element hijoOpcional(Element padre, String namespace, String nombre) {
        Element resultado = null;
        NodeList nodos = padre.getChildNodes();
        for (int i=0; i<nodos.getLength(); i++) {
            if (nodos.item(i) instanceof Element e && nombre.equals(e.getLocalName())
                    && namespace.equals(e.getNamespaceURI()==null?"":e.getNamespaceURI())) {
                if (resultado!=null) throw new SunatProtocolException("Campo SOAP duplicado: " + nombre);
                resultado=e;
            }
        }
        return resultado;
    }

    private Element hijoUnico(Element padre, String namespace, String nombre) {
        Element hijo = hijoOpcional(padre, namespace, nombre);
        if (hijo==null) throw new SunatProtocolException("Falta campo SOAP contextual: " + nombre);
        return hijo;
    }

    private Element cargaSoap(Document doc) {
        Element raiz=doc.getDocumentElement();
        if (!"Envelope".equals(raiz.getLocalName()) || !SOAP_NS.equals(raiz.getNamespaceURI()))
            throw new SunatProtocolException("La respuesta debe ser un Envelope SOAP 1.1.");
        Element body=hijoUnico(raiz, SOAP_NS, "Body");
        Element carga=null;
        NodeList nodos=body.getChildNodes();
        for (int i=0; i<nodos.getLength(); i++) if(nodos.item(i) instanceof Element e){
            if(carga!=null) throw new SunatProtocolException("Body SOAP ambiguo con múltiples respuestas.");
            carga=e;
        }
        if(carga==null) throw new SunatProtocolException("Body SOAP vacío.");
        return carga;
    }

    private Element respuestaOperacion(Document doc, String operacion) {
        Element carga=cargaSoap(doc);
        if (!operacion.equals(carga.getLocalName()) || !SERVICE_NS.equals(carga.getNamespaceURI()))
            throw new SunatProtocolException("La respuesta SOAP no corresponde a " + operacion + ".");
        return carga;
    }


    // =========================================================
    // SOAP FAULT
    // =========================================================

    private FaultInfo extraerFaultInfo(
            byte[] xml
    ) {

        if (
                xml == null
                        ||
                        xml.length == 0
        ) {

            return null;
        }

        try {

            Document doc =
                    parseSeguro(
                            xml
                    );

            Element fault=cargaSoap(doc);
            if(!"Fault".equals(fault.getLocalName()) || !SOAP_NS.equals(fault.getNamespaceURI())) return null;

            String faultCode =
                    texto(
                            fault,
                            "faultcode"
                    );

            String faultString =
                    texto(
                            fault,
                            "faultstring"
                    );

            if (
                    faultString == null
                            ||
                            faultString.isBlank()
            ) {

                faultString =
                        "SUNAT devolvió SOAP Fault sin descripción.";
            }

            return new FaultInfo(
                    faultCode == null
                            ? ""
                            : faultCode.trim(),
                    faultString.trim()
            );

        } catch (SunatProtocolException e) {
            throw e;
        } catch (Exception ignored) {

            return null;
        }
    }


    // =========================================================
    // PARSER XML SEGURO
    // =========================================================

    private Document parseSeguro(
            byte[] xml
    ) throws Exception {

        DocumentBuilderFactory f =
                DocumentBuilderFactory.newInstance();

        f.setNamespaceAware(
                true
        );

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

        f.setXIncludeAware(
                false
        );

        f.setExpandEntityReferences(
                false
        );

        f.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        f.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                ""
        );

        var builder = f.newDocumentBuilder();
        // Los diagnósticos SAX pueden contener texto recibido. No imprimirlo en stderr.
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        return builder.parse(new ByteArrayInputStream(xml));
    }


    // =========================================================
    // VALIDACIONES
    // =========================================================

    private void validarCredencialesYArchivo(

            String ruc,

            String usuarioSol,

            String claveSol,

            String nombreZip,

            byte[] zipBytes
    ) {

        validarCredenciales(
                ruc,
                usuarioSol,
                claveSol
        );

        if (
                nombreZip == null
                        ||
                        nombreZip.isBlank()
                        ||
                        !nombreZip
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .endsWith(
                                        ".zip"
                                )
        ) {

            throw new IllegalArgumentException(
                    "Nombre ZIP SUNAT inválido."
            );
        }

        if (
                zipBytes == null
                        ||
                        zipBytes.length == 0
        ) {

            throw new IllegalArgumentException(
                    "Contenido ZIP SUNAT requerido."
            );
        }
    }


    private void validarCredenciales(

            String ruc,

            String usuarioSol,

            String claveSol
    ) {

        if (
                ruc == null
                        ||
                        !ruc.matches(
                                "^[0-9]{11}$"
                        )
        ) {

            throw new IllegalArgumentException(
                    "RUC SUNAT inválido."
            );
        }

        if (
                usuarioSol == null
                        ||
                        usuarioSol.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Usuario SOL requerido."
            );
        }

        if (
                claveSol == null
                        ||
                        claveSol.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Clave SOL requerida."
            );
        }
    }


    // =========================================================
    // USERNAME SOL
    // =========================================================

    private String construirUsername(

            String ruc,

            String usuarioSol
    ) {

        String usuario =
                usuarioSol.trim();

        /*
         * Compatibilidad con instalaciones anteriores en las que
         * accidentalmente se hubiera almacenado RUC + usuario SOL.
         */
        if (
                usuario.startsWith(
                        ruc
                )
                        &&
                        usuario.length()
                                >
                                ruc.length()
        ) {

            return usuario;
        }

        return ruc + usuario;
    }


    // =========================================================
    // UTILIDADES
    // =========================================================

    private String mensajeSeguro(
            Throwable e
    ) {

        if (
                e == null
                        ||
                        e.getMessage() == null
                        ||
                        e.getMessage().isBlank()
        ) {

            return e == null
                    ? "error desconocido"
                    : e.getClass().getSimpleName();
        }

        return e.getClass().getSimpleName();
    }


    // =========================================================
    // ENVIRONMENT
    // =========================================================

    private boolean esProduccion() {

        String valor =
                environment == null
                        ?
                        ""
                        :
                        environment
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );

        return valor.equals(
                "PRODUCCION"
        )
                ||
                valor.equals(
                        "PRODUCTION"
                )
                ||
                valor.equals(
                        "PROD"
                );
    }


    // =========================================================
    // TIPOS DE RESPUESTA / EXCEPCIONES
    // =========================================================

    public record EstadoTicketSunat(

            String statusCode,

            byte[] contentZip
    ) {

        public boolean enProceso() {
            return "98".equals(statusCode);
        }

        public boolean finalizadoCorrectamente() {
            return "0".equals(statusCode);
        }

        public boolean finalizadoConErrores() {
            return "99".equals(statusCode);
        }
    }


    private record FaultInfo(

            String faultCode,

            String faultString
    ) {
    }


    /**
     * SUNAT recibió una petición SOAP y devolvió explícitamente Fault.
     *
     * No se considera automáticamente "CDR rechazada" porque un Fault
     * también puede corresponder a autenticación, formato del request,
     * endpoint u otra condición previa al procesamiento del CPE.
     */
    public static class SunatSoapFaultException
            extends RuntimeException {

        private final String faultCode;
        private final String faultString;
        private final int httpStatus;

        public SunatSoapFaultException(

                String faultCode,

                String faultString,

                int httpStatus
        ) {

            super("SUNAT HTTP " + httpStatus + ": SOAP Fault [" + codigoFaultSeguro(faultCode) + "].");

            this.faultCode =
                    codigoFaultSeguro(faultCode);

            this.faultString =
                    "Respuesta SOAP Fault; requiere revisión técnica.";

            this.httpStatus =
                    httpStatus;
        }

        public String getFaultCode() {
            return faultCode;
        }

        public String getFaultString() {
            return faultString;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        private static String codigoFaultSeguro(String code) {
            if (code == null) return "NO_ESPECIFICADO";
            String local = code.substring(code.indexOf(':') + 1);
            if (local.matches("(?:Client|Server|VersionMismatch|MustUnderstand)(?:\\.[0-9]{1,8})?")) return local;
            if (local.matches("[0-9]{1,8}")) return local;
            return "NO_RECONOCIDO";
        }
    }


    /**
     * Error de red, timeout, DNS, TLS o respuesta HTTP no SOAP.
     */
    public static class SunatTransportException
            extends RuntimeException {

        public SunatTransportException(
                String message
        ) {
            super(message);
        }

        public SunatTransportException(
                String message,
                Throwable cause
        ) {
            // Conservar diagnóstico técnico sin texto remoto ni cadena original.
            super(message, causaTecnica(cause));
        }
    }


    /**
     * SUNAT respondió, pero la respuesta no cumple la estructura
     * que el cliente esperaba.
     */
    public static class SunatProtocolException
            extends RuntimeException {

        public SunatProtocolException(
                String message
        ) {
            super(message);
        }

        public SunatProtocolException(
                String message,
                Throwable cause
        ) {
            // No propagar diagnósticos del parser con fragmentos del body.
            super(message, causaTecnica(cause));
        }
    }

    private static Throwable causaTecnica(Throwable cause) {
        return cause == null ? null : new IllegalStateException("Tipo de causa: " + cause.getClass().getSimpleName());
    }
}
