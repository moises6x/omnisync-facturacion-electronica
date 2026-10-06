
package org.example.service;
import org.example.util.ImportesXml;

import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.model.Empresa;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UblXmlBuilderService {

    // =========================================================
    // NAMESPACES UBL 2.1
    // =========================================================

    private static final String NS_INVOICE =
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";

    private static final String NS_CREDIT =
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";

    private static final String NS_CAC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    private static final String NS_CBC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private static final String NS_EXT =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";


    // =========================================================
    // TIPO DE OPERACIÓN SUNAT
    // =========================================================
    // Ya NO está hardcodeado. Se recibe desde ComprobanteRequest
    // y ValidacionTributariaService sólo permite los códigos que
    // esta versión del backend realmente soporta y ha probado.
    // Actualmente: 0101 = Venta interna.
    // =========================================================

    // =========================================================
    // FACTURA / BOLETA
    // =========================================================

    public byte[] construirXmlUbl21(

            Empresa empresa,

            ComprobanteRequest req,

            int numero,

            CalculoTributarioService.Totales totales,

            LocalDateTime fechaEmision

    ) {

        try {

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();


            XMLStreamWriter w =
                    XMLOutputFactory
                            .newFactory()
                            .createXMLStreamWriter(
                                    baos,
                                    StandardCharsets.UTF_8.name()
                            );


            // =================================================
            // FECHA Y HORA TRIBUTARIA
            // =================================================
            //
            // La fecha se calcula UNA sola vez en FacturacionServiceImpl
            // usando el Clock central de America/Lima.
            //
            // Esa misma fecha se utiliza para:
            //
            // - XML
            // - columna comprobante.fecha_emision
            // - agrupación del Resumen Diario
            //
            // Evitamos que un cambio de segundo/zona horaria pueda dejar
            // XML y BD en días diferentes.
            // =================================================

            if (fechaEmision == null) {
                throw new IllegalArgumentException(
                        "La fecha de emisión tributaria es obligatoria."
                );
            }

            String fecha =
                    fechaEmision
                            .toLocalDate()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "yyyy-MM-dd"
                                    )
                            );

            String hora =
                    fechaEmision
                            .toLocalTime()
                            .withNano(0)
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "HH:mm:ss"
                                    )
                            );


            // =================================================
            // SERIE + CORRELATIVO
            //
            // Ejemplo:
            //
            // F001-00000001
            // =================================================

            String id =
                    String.format(
                            "%s-%08d",
                            req.getSerie(),
                            numero
                    );


            // =================================================
            // INICIO XML
            // =================================================

            w.writeStartDocument(
                    "UTF-8",
                    "1.0"
            );


            // =================================================
            // INVOICE
            // =================================================

            w.writeStartElement(
                    "",
                    "Invoice",
                    NS_INVOICE
            );


            // =================================================
            // NAMESPACES
            // =================================================

            w.writeDefaultNamespace(
                    NS_INVOICE
            );


            w.writeNamespace(
                    "cac",
                    NS_CAC
            );


            w.writeNamespace(
                    "cbc",
                    NS_CBC
            );


            w.writeNamespace(
                    "ext",
                    NS_EXT
            );


            // =================================================
            // EXTENSIÓN PARA FIRMA DIGITAL
            // =================================================
            //
            // Se crea vacía.
            //
            // XmlSignerService después insertará:
            //
            // <ds:Signature>
            //
            // dentro de ExtensionContent.
            // =================================================

            w.writeStartElement(
                    "ext",
                    "UBLExtensions",
                    NS_EXT
            );


            w.writeStartElement(
                    "ext",
                    "UBLExtension",
                    NS_EXT
            );


            w.writeStartElement(
                    "ext",
                    "ExtensionContent",
                    NS_EXT
            );


            w.writeEndElement();
            // ExtensionContent


            w.writeEndElement();
            // UBLExtension


            w.writeEndElement();
            // UBLExtensions


            // =================================================
            // VERSIÓN UBL
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "UBLVersionID",
                    NS_CBC,
                    "2.1"
            );


            // =================================================
            // CUSTOMIZATION SUNAT
            // =================================================

            w.writeStartElement(
                    "cbc",
                    "CustomizationID",
                    NS_CBC
            );


            w.writeAttribute(
                    "schemeAgencyName",
                    "PE:SUNAT"
            );


            w.writeCharacters(
                    "2.0"
            );


            w.writeEndElement();


            // =================================================
            // TIPO DE OPERACIÓN
            // =================================================
            //
            // El valor viene dinámicamente en req.getTipoOperacion().
            // Actualmente el backend permite 0101 = Venta interna.
            // =================================================

            w.writeStartElement(
                    "cbc",
                    "ProfileID",
                    NS_CBC
            );


            w.writeAttribute(
                    "schemeName",
                    "SUNAT:Identificador de Tipo de Operación"
            );


            w.writeAttribute(
                    "schemeAgencyName",
                    "PE:SUNAT"
            );


            w.writeAttribute(
                    "schemeURI",
                    "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo17"
            );


            w.writeCharacters(
                    req.getTipoOperacion()
            );


            w.writeEndElement();


            // =================================================
            // ID DEL COMPROBANTE
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "ID",
                    NS_CBC,
                    id
            );


            // =================================================
            // FECHA
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "IssueDate",
                    NS_CBC,
                    fecha
            );


            // =================================================
            // HORA
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "IssueTime",
                    NS_CBC,
                    hora
            );


            // =================================================
            // TIPO DE COMPROBANTE
            // =================================================
            //
            // 01 = Factura
            // 03 = Boleta
            //
            // listID:
            //
            // 0101 = Venta Interna
            //
            // Esto corrigió el error SUNAT 3205.
            //
            // listID usa el mismo tipo de operación validado.
            // =================================================

            w.writeStartElement(
                    "cbc",
                    "InvoiceTypeCode",
                    NS_CBC
            );


            w.writeAttribute(
                    "listID",
                    req.getTipoOperacion()
            );


            w.writeAttribute(
                    "listAgencyName",
                    "PE:SUNAT"
            );


            w.writeAttribute(
                    "listName",
                    "Tipo de Documento"
            );


            w.writeAttribute(
                    "listURI",
                    "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01"
            );


            w.writeAttribute(
                    "name",
                    "Tipo de Operacion"
            );


            w.writeAttribute(
                    "listSchemeURI",
                    "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo51"
            );


            w.writeCharacters(
                    req.getTipoComprobante()
            );


            w.writeEndElement();


            // =================================================
            // MONEDA
            // =================================================

            escribirMoneda(
                    w
            );


            // =================================================
            // CANTIDAD DE ITEMS
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "LineCountNumeric",
                    NS_CBC,
                    String.valueOf(
                            totales.items().size()
                    )
            );


            // =================================================
            // REFERENCIA FIRMA
            // =================================================

            escribirBloqueFirma(
                    w,
                    empresa
            );


            // =================================================
            // EMISOR
            // =================================================

            escribirEmisor(
                    w,
                    empresa
            );


            // =================================================
            // CLIENTE
            // =================================================

            escribirCliente(
                    w,
                    req.getTipoDocCliente(),
                    req.getNumDocCliente(),
                    req.getRznSocialCliente()
            );


            // =================================================
            // FORMA DE PAGO
            // =================================================

            escribirFormaPago(
                w,
                req,
                totales.total()
            );


            // =================================================
            // IMPUESTOS
            // =================================================

            escribirTaxTotalDocumento(
                    w,
                    totales
            );


            // =================================================
            // TOTALES
            // =================================================

            escribirTotalesMonetarios(
                    w,
                    totales
            );


            // =================================================
            // ITEMS
            // =================================================

            for (
                    CalculoTributarioService.ItemCalculado item :
                    totales.items()
            ) {

                escribirInvoiceLine(
                        w,
                        item
                );
            }


            // =================================================
            // CERRAR INVOICE
            // =================================================

            w.writeEndElement();


            w.writeEndDocument();


            w.flush();

            w.close();


            return baos.toByteArray();


        } catch (Exception e) {

            throw new RuntimeException(
                    "Error construyendo UBL 2.1: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // =========================================================
    // NOTA DE CRÉDITO
    // =========================================================

    public byte[] construirXmlNotaCredito21(

            Empresa empresa,

            NotaCreditoRequest req,

            int numero,

            CalculoTributarioService.Totales totales,

            LocalDateTime fechaEmision

    ) {

        try {

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();


            XMLStreamWriter w =
                    XMLOutputFactory
                            .newFactory()
                            .createXMLStreamWriter(
                                    baos,
                                    StandardCharsets.UTF_8.name()
                            );


            // =================================================
            // FECHA / HORA TRIBUTARIA
            // =================================================

            if (fechaEmision == null) {
                throw new IllegalArgumentException(
                        "La fecha de emisión tributaria de la Nota de Crédito es obligatoria."
                );
            }

            String fecha =
                    fechaEmision
                            .toLocalDate()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "yyyy-MM-dd"
                                    )
                            );

            String hora =
                    fechaEmision
                            .toLocalTime()
                            .withNano(0)
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            "HH:mm:ss"
                                    )
                            );


            // =================================================
            // SERIE / CORRELATIVO
            // =================================================

            String id =
                    String.format(
                            "%s-%08d",
                            req.getSerie(),
                            numero
                    );


            // =================================================
            // INICIO XML
            // =================================================

            w.writeStartDocument(
                    "UTF-8",
                    "1.0"
            );


            // =================================================
            // CREDIT NOTE
            // =================================================

            w.writeStartElement(
                    "",
                    "CreditNote",
                    NS_CREDIT
            );


            w.writeDefaultNamespace(
                    NS_CREDIT
            );


            w.writeNamespace(
                    "cac",
                    NS_CAC
            );


            w.writeNamespace(
                    "cbc",
                    NS_CBC
            );


            w.writeNamespace(
                    "ext",
                    NS_EXT
            );


            // =================================================
            // EXTENSION PARA FIRMA
            // =================================================

            w.writeStartElement(
                    "ext",
                    "UBLExtensions",
                    NS_EXT
            );


            w.writeStartElement(
                    "ext",
                    "UBLExtension",
                    NS_EXT
            );


            w.writeStartElement(
                    "ext",
                    "ExtensionContent",
                    NS_EXT
            );


            w.writeEndElement();

            w.writeEndElement();

            w.writeEndElement();


            // =================================================
            // UBL VERSION
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "UBLVersionID",
                    NS_CBC,
                    "2.1"
            );


            // =================================================
            // CUSTOMIZATION
            // =================================================

            w.writeStartElement(
                    "cbc",
                    "CustomizationID",
                    NS_CBC
            );


            w.writeAttribute(
                    "schemeAgencyName",
                    "PE:SUNAT"
            );


            w.writeCharacters(
                    "2.0"
            );


            w.writeEndElement();


            // =================================================
            // ID NC
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "ID",
                    NS_CBC,
                    id
            );


            // =================================================
            // FECHA
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "IssueDate",
                    NS_CBC,
                    fecha
            );


            // =================================================
            // HORA
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "IssueTime",
                    NS_CBC,
                    hora
            );


            // =================================================
            // MONEDA
            // =================================================

            escribirMoneda(
                    w
            );


            // =================================================
            // MOTIVO NOTA DE CRÉDITO
            // =================================================

            w.writeStartElement(
                    "cac",
                    "DiscrepancyResponse",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "ReferenceID",
                    NS_CBC,
                    req.getNumComprobanteAfectado()
            );


            elemento(
                    w,
                    "cbc",
                    "ResponseCode",
                    NS_CBC,
                    req.getCodMotivoSunat()
            );


            elemento(
                    w,
                    "cbc",
                    "Description",
                    NS_CBC,
                    req.getDescripcionMotivo()
            );


            w.writeEndElement();


            // =================================================
            // DOCUMENTO AFECTADO
            // =================================================

            w.writeStartElement(
                    "cac",
                    "BillingReference",
                    NS_CAC
            );


            w.writeStartElement(
                    "cac",
                    "InvoiceDocumentReference",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "ID",
                    NS_CBC,
                    req.getNumComprobanteAfectado()
            );


            elemento(
                    w,
                    "cbc",
                    "DocumentTypeCode",
                    NS_CBC,
                    req.getTipoComprobanteAfectado()
            );


            w.writeEndElement();

            w.writeEndElement();


            // =================================================
            // FIRMA
            // =================================================

            escribirBloqueFirma(
                    w,
                    empresa
            );


            // =================================================
            // EMISOR
            // =================================================

            escribirEmisor(
                    w,
                    empresa
            );


            // =================================================
            // CLIENTE
            // =================================================

            escribirCliente(
                    w,
                    req.getTipoDocCliente(),
                    req.getNumDocCliente(),
                    req.getRznSocialCliente()
            );


            // =================================================
            // IMPUESTOS
            // =================================================

            escribirTaxTotalDocumento(
                    w,
                    totales
            );


            // =================================================
            // TOTALES
            // =================================================

            escribirTotalesMonetarios(
                    w,
                    totales
            );


            // =================================================
            // ITEMS
            // =================================================

            for (
                    CalculoTributarioService.ItemCalculado item :
                    totales.items()
            ) {

                escribirCreditLine(
                        w,
                        item
                );
            }


            // =================================================
            // CERRAR CREDIT NOTE
            // =================================================

            w.writeEndElement();


            w.writeEndDocument();


            w.flush();

            w.close();


            return baos.toByteArray();


        } catch (Exception e) {

            throw new RuntimeException(
                    "Error construyendo Nota de Crédito UBL 2.1: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // =========================================================
    // EMISOR
    // =========================================================

    private void escribirEmisor(

            XMLStreamWriter w,

            Empresa empresa

    ) throws Exception {


        // =====================================================
        // SUPPLIER
        // =====================================================

        w.writeStartElement(
                "cac",
                "AccountingSupplierParty",
                NS_CAC
        );


        w.writeStartElement(
                "cac",
                "Party",
                NS_CAC
        );


        // =====================================================
        // RUC DEL EMISOR
        // =====================================================
        //
        // schemeID = 6
        //
        // 6 = RUC.
        // =====================================================

        w.writeStartElement(
                "cac",
                "PartyIdentification",
                NS_CAC
        );


        w.writeStartElement(
                "cbc",
                "ID",
                NS_CBC
        );


        w.writeAttribute(
                "schemeID",
                "6"
        );


        w.writeAttribute(
                "schemeName",
                "Documento de Identidad"
        );


        w.writeAttribute(
                "schemeAgencyName",
                "PE:SUNAT"
        );


        w.writeAttribute(
                "schemeURI",
                "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06"
        );


        w.writeCharacters(
                empresa.getRuc()
        );


        w.writeEndElement();


        w.writeEndElement();


        // =====================================================
        // NOMBRE COMERCIAL
        // =====================================================

        if (
                empresa.getRazonSocial() != null
                        &&
                        !empresa.getRazonSocial().isBlank()
        ) {

            w.writeStartElement(
                    "cac",
                    "PartyName",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "Name",
                    NS_CBC,
                    empresa.getRazonSocial()
            );


            w.writeEndElement();
        }


        // =====================================================
        // ENTIDAD LEGAL
        // =====================================================

        w.writeStartElement(
                "cac",
                "PartyLegalEntity",
                NS_CAC
        );


        // =====================================================
        // RAZÓN SOCIAL
        // =====================================================

        elemento(
                w,
                "cbc",
                "RegistrationName",
                NS_CBC,
                empresa.getRazonSocial()
        );


        // =====================================================
        // DIRECCIÓN FISCAL
        // =====================================================

        w.writeStartElement(
                "cac",
                "RegistrationAddress",
                NS_CAC
        );


        // =====================================================
        // UBIGEO
        // =====================================================

        if (
                empresa.getUbigeo() != null
                        &&
                        !empresa.getUbigeo().isBlank()
        ) {

            w.writeStartElement(
                    "cbc",
                    "ID",
                    NS_CBC
            );


            w.writeAttribute(
                    "schemeName",
                    "Ubigeos"
            );


            w.writeAttribute(
                    "schemeAgencyName",
                    "PE:INEI"
            );


            w.writeCharacters(
                    empresa.getUbigeo()
            );


            w.writeEndElement();
        }


        // =====================================================
        // CÓDIGO ESTABLECIMIENTO
        // =====================================================
        //
        // BETA:
        //
        // 0000 = domicilio fiscal principal.
        //
        // PRODUCCIÓN:
        //
        // deberá salir de la configuración de cada
        // establecimiento/sucursal.
        // =====================================================

        w.writeStartElement(
                "cbc",
                "AddressTypeCode",
                NS_CBC
        );


        w.writeAttribute(
                "listAgencyName",
                "PE:SUNAT"
        );


        w.writeAttribute(
                "listName",
                "Establecimientos anexos"
        );


        w.writeCharacters(
                empresa.getCodigoEstablecimiento() != null
                        && !empresa.getCodigoEstablecimiento().isBlank()
                        ? empresa.getCodigoEstablecimiento()
                        : "0000"
        );


        w.writeEndElement();


        // =====================================================
        // PROVINCIA
        // =====================================================

        if (
                empresa.getProvincia() != null
                        &&
                        !empresa.getProvincia().isBlank()
        ) {

            elemento(
                    w,
                    "cbc",
                    "CityName",
                    NS_CBC,
                    empresa.getProvincia()
            );
        }


        // =====================================================
        // DEPARTAMENTO
        // =====================================================

        if (
                empresa.getDepartamento() != null
                        &&
                        !empresa.getDepartamento().isBlank()
        ) {

            elemento(
                    w,
                    "cbc",
                    "CountrySubentity",
                    NS_CBC,
                    empresa.getDepartamento()
            );
        }


        // =====================================================
        // DISTRITO
        // =====================================================

        if (
                empresa.getDistrito() != null
                        &&
                        !empresa.getDistrito().isBlank()
        ) {

            elemento(
                    w,
                    "cbc",
                    "District",
                    NS_CBC,
                    empresa.getDistrito()
            );
        }


        // =====================================================
        // DIRECCIÓN
        // =====================================================

        if (
                empresa.getDireccionFiscal() != null
                        &&
                        !empresa.getDireccionFiscal().isBlank()
        ) {

            w.writeStartElement(
                    "cac",
                    "AddressLine",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "Line",
                    NS_CBC,
                    empresa.getDireccionFiscal()
            );


            w.writeEndElement();
        }


        // =====================================================
        // PAÍS
        // =====================================================

        w.writeStartElement(
                "cac",
                "Country",
                NS_CAC
        );


        w.writeStartElement(
                "cbc",
                "IdentificationCode",
                NS_CBC
        );


        w.writeAttribute(
                "listID",
                "ISO 3166-1"
        );


        w.writeAttribute(
                "listAgencyName",
                "United Nations Economic Commission for Europe"
        );


        w.writeAttribute(
                "listName",
                "Country"
        );


        w.writeCharacters(
                "PE"
        );


        w.writeEndElement();


        w.writeEndElement();
        // Country


        w.writeEndElement();
        // RegistrationAddress


        w.writeEndElement();
        // PartyLegalEntity


        w.writeEndElement();
        // Party


        w.writeEndElement();
        // AccountingSupplierParty
    }


    // =========================================================
    // CLIENTE
    // =========================================================

    private void escribirCliente(

            XMLStreamWriter w,

            String tipoDocumento,

            String numeroDocumento,

            String nombre

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "AccountingCustomerParty",
                NS_CAC
        );


        w.writeStartElement(
                "cac",
                "Party",
                NS_CAC
        );


        // =====================================================
        // CLIENTE SIN IDENTIFICACIÓN
        // =====================================================
        //
        // Para nuestro API, una boleta sin identificación queda
        // normalizada internamente así:
        //
        // tipoDocumento   = "0"
        // numeroDocumento = "-"
        // nombre          = "CONSUMIDOR FINAL"
        //
        // IMPORTANTE:
        // No enviamos PartyIdentification a SUNAT en este caso.
        // Conservamos los valores internos únicamente para poder
        // guardar el comprobante sin volver nullable la BD.
        // =====================================================

        boolean sinIdentificacion =
                "0".equals(tipoDocumento)
                        && "-".equals(numeroDocumento);


        // =====================================================
        // DOCUMENTO CLIENTE IDENTIFICADO
        // =====================================================
        //
        // 1 = DNI
        // 4 = Carné de Extranjería
        // 6 = RUC
        // 7 = Pasaporte
        // =====================================================

        if (!sinIdentificacion) {

            w.writeStartElement(
                    "cac",
                    "PartyIdentification",
                    NS_CAC
            );


            w.writeStartElement(
                    "cbc",
                    "ID",
                    NS_CBC
            );


            w.writeAttribute(
                    "schemeID",
                    tipoDocumento
            );


            w.writeAttribute(
                    "schemeName",
                    "Documento de Identidad"
            );


            w.writeAttribute(
                    "schemeAgencyName",
                    "PE:SUNAT"
            );


            w.writeAttribute(
                    "schemeURI",
                    "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06"
            );


            w.writeCharacters(
                    numeroDocumento
            );


            w.writeEndElement();


            w.writeEndElement();
        }


        // =====================================================
        // NOMBRE / RAZÓN SOCIAL
        // =====================================================
        //
        // Para el caso sin identificación guardamos y mostramos
        // "CONSUMIDOR FINAL".
        // =====================================================

        if (
                nombre != null
                        && !nombre.isBlank()
        ) {

            w.writeStartElement(
                    "cac",
                    "PartyLegalEntity",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "RegistrationName",
                    NS_CBC,
                    nombre
            );


            w.writeEndElement();
        }


        w.writeEndElement();
        // Party


        w.writeEndElement();
        // AccountingCustomerParty
    }


    // =========================================================
    // FIRMA
    // =========================================================

    private void escribirBloqueFirma(

            XMLStreamWriter w,

            Empresa empresa

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "Signature",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                "IDSignSP"
        );


        // =====================================================
        // FIRMANTE
        // =====================================================

        w.writeStartElement(
                "cac",
                "SignatoryParty",
                NS_CAC
        );


        w.writeStartElement(
                "cac",
                "PartyIdentification",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                empresa.getRuc()
        );


        w.writeEndElement();


        w.writeStartElement(
                "cac",
                "PartyName",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "Name",
                NS_CBC,
                empresa.getRazonSocial()
        );


        w.writeEndElement();


        w.writeEndElement();
        // SignatoryParty


        // =====================================================
        // REFERENCIA A FIRMA XML
        // =====================================================

        w.writeStartElement(
                "cac",
                "DigitalSignatureAttachment",
                NS_CAC
        );


        w.writeStartElement(
                "cac",
                "ExternalReference",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "URI",
                NS_CBC,
                "#SignatureSP"
        );


        w.writeEndElement();


        w.writeEndElement();


        w.writeEndElement();
        // Signature
    }


    // =========================================================
    // FORMA DE PAGO
    // =========================================================

    private void escribirFormaPago(

            XMLStreamWriter w,

            ComprobanteRequest req,
            BigDecimal total

    ) throws Exception {


        // =====================================================
        // CRÉDITO
        // =====================================================

        if (
                "Credito".equalsIgnoreCase(
                        req.getFormaPago()
                )
        ) {

            ImportesXml.validarCredito(total, req.getMontoPendientePago(),
                    req.getCuotas() == null ? null : req.getCuotas().stream()
                            .map(cuota -> cuota == null ? null : cuota.getMonto()).toList());

            w.writeStartElement(
                    "cac",
                    "PaymentTerms",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "ID",
                    NS_CBC,
                    "FormaPago"
            );


            elemento(
                    w,
                    "cbc",
                    "PaymentMeansID",
                    NS_CBC,
                    "Credito"
            );


            importe(
                    w,
                    "Amount",
                    req.getMontoPendientePago()
            );


            w.writeEndElement();


            // =================================================
            // CUOTAS
            // =================================================

            int numeroCuota =
                    1;


            if (
                    req.getCuotas() != null
            ) {

                for (
                        ComprobanteRequest.CuotaRequest cuota :
                        req.getCuotas()
                ) {


                    w.writeStartElement(
                            "cac",
                            "PaymentTerms",
                            NS_CAC
                    );


                    elemento(
                            w,
                            "cbc",
                            "ID",
                            NS_CBC,
                            "FormaPago"
                    );


                    elemento(
                            w,
                            "cbc",
                            "PaymentMeansID",
                            NS_CBC,
                            String.format(
                                    "Cuota%03d",
                                    numeroCuota++
                            )
                    );


                    importe(
                            w,
                            "Amount",
                            cuota.getMonto()
                    );


                    if (
                            cuota.getFechaVencimiento() != null
                    ) {

                        elemento(
                                w,
                                "cbc",
                                "PaymentDueDate",
                                NS_CBC,
                                cuota
                                        .getFechaVencimiento()
                                        .toString()
                        );
                    }


                    w.writeEndElement();
                }
            }


        } else {


            // =================================================
            // CONTADO
            // =================================================

            w.writeStartElement(
                    "cac",
                    "PaymentTerms",
                    NS_CAC
            );


            elemento(
                    w,
                    "cbc",
                    "ID",
                    NS_CBC,
                    "FormaPago"
            );


            elemento(
                    w,
                    "cbc",
                    "PaymentMeansID",
                    NS_CBC,
                    "Contado"
            );


            w.writeEndElement();
        }
    }


    // =========================================================
    // IMPUESTOS DOCUMENTO
    // =========================================================

    private void escribirTaxTotalDocumento(

            XMLStreamWriter w,

            CalculoTributarioService.Totales t

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "TaxTotal",
                NS_CAC
        );


        // =====================================================
        // IGV TOTAL
        // =====================================================

        importe(
                w,
                "TaxAmount",
                t.igv()
        );


        // =====================================================
        // GRAVADA
        // =====================================================

        if (
                t.opGravada() != null
                        &&
                        t.opGravada().signum() > 0
        ) {

            escribirTaxSubtotal(
                    w,
                    t.opGravada(),
                    t.igv(),
                    "10",
                    null
            );
        }


        // =====================================================
        // EXONERADA
        // =====================================================

        if (
                t.opExonerada() != null
                        &&
                        t.opExonerada().signum() > 0
        ) {

            escribirTaxSubtotal(
                    w,
                    t.opExonerada(),
                    BigDecimal.ZERO,
                    "20",
                    null
            );
        }


        // =====================================================
        // INAFECTA
        // =====================================================

        if (
                t.opInafecta() != null
                        &&
                        t.opInafecta().signum() > 0
        ) {

            escribirTaxSubtotal(
                    w,
                    t.opInafecta(),
                    BigDecimal.ZERO,
                    "30",
                    null
            );
        }


        w.writeEndElement();
    }


    // =========================================================
    // TAX SUBTOTAL
    // =========================================================

    private void escribirTaxSubtotal(

            XMLStreamWriter w,

            BigDecimal base,

            BigDecimal impuesto,

            String afectacion,

            BigDecimal porcentaje

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "TaxSubtotal",
                NS_CAC
        );


        importe(
                w,
                "TaxableAmount",
                base
        );


        importe(
                w,
                "TaxAmount",
                impuesto
        );


        escribirTaxCategory(
                w,
                afectacion,
                porcentaje
        );


        w.writeEndElement();
    }


    // =========================================================
    // TOTALES MONETARIOS
    // =========================================================

    private void escribirTotalesMonetarios(

            XMLStreamWriter w,

            CalculoTributarioService.Totales t

    ) throws Exception {


        BigDecimal valorVenta =
                t.opGravada()
                        .add(
                                t.opExonerada()
                        )
                        .add(
                                t.opInafecta()
                        );


        w.writeStartElement(
                "cac",
                "LegalMonetaryTotal",
                NS_CAC
        );


        importe(
                w,
                "LineExtensionAmount",
                valorVenta
        );


        importe(
                w,
                "TaxInclusiveAmount",
                t.total()
        );


        importe(
                w,
                "PayableAmount",
                t.total()
        );


        w.writeEndElement();
    }


    // =========================================================
    // LÍNEA FACTURA / BOLETA
    // =========================================================

    private void escribirInvoiceLine(

            XMLStreamWriter w,

            CalculoTributarioService.ItemCalculado item

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "InvoiceLine",
                NS_CAC
        );


        // =====================================================
        // ID
        // =====================================================

        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                String.valueOf(
                        item.orden()
                )
        );


        // =====================================================
        // CANTIDAD
        // =====================================================

        w.writeStartElement(
                "cbc",
                "InvoicedQuantity",
                NS_CBC
        );


        w.writeAttribute(
                "unitCode",
                item.unidadMedida()
        );


        w.writeAttribute(
                "unitCodeListID",
                "UN/ECE rec 20"
        );


        w.writeAttribute(
                "unitCodeListAgencyName",
                "United Nations Economic Commission for Europe"
        );


        w.writeCharacters(
                decimal(
                        item.cantidad()
                )
        );


        w.writeEndElement();


        // =====================================================
        // VALOR VENTA ITEM
        // =====================================================

        importe(
                w,
                "LineExtensionAmount",
                item.baseImponible()
        );


        // =====================================================
        // PRECIO UNITARIO
        // =====================================================

        escribirPricingReference(
                w,
                item.precioUnitario()
        );


        // =====================================================
        // IMPUESTOS ITEM
        // =====================================================

        w.writeStartElement(
                "cac",
                "TaxTotal",
                NS_CAC
        );


        importe(
                w,
                "TaxAmount",
                item.igv()
        );


        escribirTaxSubtotal(
                w,
                item.baseImponible(),
                item.igv(),
                item.tipoAfectacionIgv(),
                item.porcentajeIgv()
        );


        w.writeEndElement();


        // =====================================================
        // PRODUCTO
        // =====================================================

        w.writeStartElement(
                "cac",
                "Item",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "Description",
                NS_CBC,
                item.descripcion()
        );


        w.writeEndElement();


        // =====================================================
        // PRECIO SIN IGV
        // =====================================================

        w.writeStartElement(
                "cac",
                "Price",
                NS_CAC
        );


        precioUnitario(
                w,
                item.valorUnitario()
        );


        w.writeEndElement();


        w.writeEndElement();
        // InvoiceLine
    }


    // =========================================================
    // LÍNEA NOTA DE CRÉDITO
    // =========================================================

    private void escribirCreditLine(

            XMLStreamWriter w,

            CalculoTributarioService.ItemCalculado item

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "CreditNoteLine",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                String.valueOf(
                        item.orden()
                )
        );


        // =====================================================
        // CANTIDAD
        // =====================================================

        w.writeStartElement(
                "cbc",
                "CreditedQuantity",
                NS_CBC
        );


        w.writeAttribute(
                "unitCode",
                item.unidadMedida()
        );


        w.writeAttribute(
                "unitCodeListID",
                "UN/ECE rec 20"
        );


        w.writeAttribute(
                "unitCodeListAgencyName",
                "United Nations Economic Commission for Europe"
        );


        w.writeCharacters(
                decimal(
                        item.cantidad()
                )
        );


        w.writeEndElement();


        // =====================================================
        // VALOR VENTA
        // =====================================================

        importe(
                w,
                "LineExtensionAmount",
                item.baseImponible()
        );


        // =====================================================
        // PRECIO
        // =====================================================

        escribirPricingReference(
                w,
                item.precioUnitario()
        );


        // =====================================================
        // IMPUESTOS
        // =====================================================

        w.writeStartElement(
                "cac",
                "TaxTotal",
                NS_CAC
        );


        importe(
                w,
                "TaxAmount",
                item.igv()
        );


        escribirTaxSubtotal(
                w,
                item.baseImponible(),
                item.igv(),
                item.tipoAfectacionIgv(),
                item.porcentajeIgv()
        );


        w.writeEndElement();


        // =====================================================
        // PRODUCTO
        // =====================================================

        w.writeStartElement(
                "cac",
                "Item",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "Description",
                NS_CBC,
                item.descripcion()
        );


        w.writeEndElement();


        // =====================================================
        // PRECIO
        // =====================================================

        w.writeStartElement(
                "cac",
                "Price",
                NS_CAC
        );


        precioUnitario(
                w,
                item.valorUnitario()
        );


        w.writeEndElement();


        w.writeEndElement();
        // CreditNoteLine
    }


    // =========================================================
    // PRECIO REFERENCIAL
    // =========================================================

    private void escribirPricingReference(

            XMLStreamWriter w,

            BigDecimal precioUnitario

    ) throws Exception {


        w.writeStartElement(
                "cac",
                "PricingReference",
                NS_CAC
        );


        w.writeStartElement(
                "cac",
                "AlternativeConditionPrice",
                NS_CAC
        );


        precioUnitario(
                w,
                precioUnitario
        );


        // =====================================================
        // TIPO DE PRECIO
        // =====================================================
        //
        // 01 = Precio unitario incluye IGV.
        //
        // IMPORTANTE:
        //
        // SUNAT ya aceptó nuestra factura, pero devolvió:
        //
        // OBSERVACIÓN 4252
        //
        // "El dato ingresado como atributo @listName
        // es incorrecto"
        //
        // Nodo:
        //
        // cbc:PriceTypeCode/@listName
        //
        // ANTES:
        //
        // listName="SUNAT:Indicador de Tipo de Precio"
        //
        // CORRECCIÓN:
        //
        // listName="Tipo de Precio"
        //
        // =====================================================

        w.writeStartElement(
                "cbc",
                "PriceTypeCode",
                NS_CBC
        );


        // =====================================================
        // CORRECCIÓN SUNAT 4252
        // =====================================================

        w.writeAttribute(
                "listName",
                "Tipo de Precio"
        );


        w.writeAttribute(
                "listAgencyName",
                "PE:SUNAT"
        );


        w.writeAttribute(
                "listURI",
                "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16"
        );


        w.writeCharacters(
                "01"
        );


        w.writeEndElement();


        w.writeEndElement();
        // AlternativeConditionPrice


        w.writeEndElement();
        // PricingReference
    }


    // =========================================================
    // CATEGORÍA TRIBUTARIA
    // =========================================================

    private void escribirTaxCategory(

            XMLStreamWriter w,

            String afectacion,

            BigDecimal porcentaje

    ) throws Exception {


        String idCategoria;

        String taxSchemeId;

        String taxName;

        String taxType;


        // =====================================================
        // AFECTACIÓN IGV
        // =====================================================

        switch (afectacion) {


            // =================================================
            // GRAVADO
            // =================================================

            case "10" -> {

                idCategoria =
                        "S";

                taxSchemeId =
                        "1000";

                taxName =
                        "IGV";

                taxType =
                        "VAT";
            }


            // =================================================
            // EXONERADO
            // =================================================

            case "20" -> {

                idCategoria =
                        "E";

                taxSchemeId =
                        "9997";

                taxName =
                        "EXO";

                taxType =
                        "VAT";
            }


            // =================================================
            // INAFECTO
            // =================================================

            case "30" -> {

                idCategoria =
                        "O";

                taxSchemeId =
                        "9998";

                taxName =
                        "INA";

                taxType =
                        "FRE";
            }


            default ->
                    throw new IllegalArgumentException(
                            "Afectación IGV no soportada: "
                                    + afectacion
                    );
        }


        // =====================================================
        // TAX CATEGORY
        // =====================================================

        w.writeStartElement(
                "cac",
                "TaxCategory",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                idCategoria
        );


        // =====================================================
        // PORCENTAJE
        // =====================================================

        if (
                porcentaje != null
                        &&
                        porcentaje.signum() > 0
        ) {

            elemento(
                    w,
                    "cbc",
                    "Percent",
                    NS_CBC,
                    decimal(
                            porcentaje
                    )
            );
        }


        // =====================================================
        // TIPO AFECTACIÓN IGV
        // =====================================================

        elemento(
                w,
                "cbc",
                "TaxExemptionReasonCode",
                NS_CBC,
                afectacion
        );


        // =====================================================
        // TAX SCHEME
        // =====================================================

        w.writeStartElement(
                "cac",
                "TaxScheme",
                NS_CAC
        );


        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                taxSchemeId
        );


        elemento(
                w,
                "cbc",
                "Name",
                NS_CBC,
                taxName
        );


        elemento(
                w,
                "cbc",
                "TaxTypeCode",
                NS_CBC,
                taxType
        );


        w.writeEndElement();
        // TaxScheme


        w.writeEndElement();
        // TaxCategory
    }


    // =========================================================
    // MONEDA
    // =========================================================

    private void escribirMoneda(

            XMLStreamWriter w

    ) throws Exception {


        w.writeStartElement(
                "cbc",
                "DocumentCurrencyCode",
                NS_CBC
        );


        w.writeAttribute(
                "listID",
                "ISO 4217 Alpha"
        );


        w.writeAttribute(
                "listName",
                "Currency"
        );


        w.writeAttribute(
                "listAgencyName",
                "United Nations Economic Commission for Europe"
        );


        w.writeCharacters(
                "PEN"
        );


        w.writeEndElement();
    }


    // =========================================================
    // IMPORTE MONETARIO
    // =========================================================

    private void precioUnitario(XMLStreamWriter w, BigDecimal valor) throws Exception {
        w.writeStartElement("cbc", "PriceAmount", NS_CBC);
        w.writeAttribute("currencyID", "PEN");
        w.writeCharacters(ImportesXml.unitario(valor).toPlainString());
        w.writeEndElement();
    }

    private void importe(

            XMLStreamWriter w,

            String nombre,

            BigDecimal valor

    ) throws Exception {


        if (
                valor == null
        ) {

            valor =
                    BigDecimal.ZERO;
        }


        w.writeStartElement(
                "cbc",
                nombre,
                NS_CBC
        );


        w.writeAttribute(
                "currencyID",
                "PEN"
        );


        w.writeCharacters(
                money(
                        valor
                )
        );


        w.writeEndElement();
    }


    // =========================================================
    // ELEMENTO SIMPLE
    // =========================================================

    private void elemento(

            XMLStreamWriter w,

            String prefix,

            String nombre,

            String namespace,

            String valor

    ) throws Exception {


        w.writeStartElement(
                prefix,
                nombre,
                namespace
        );


        if (
                valor != null
        ) {

            w.writeCharacters(
                    valor
            );
        }


        w.writeEndElement();
    }


    // =========================================================
    // MONEY
    // =========================================================

    private String money(

            BigDecimal value

    ) {


        if (
                value == null
        ) {

            return "0.00";
        }


        return ImportesXml.monetario(value).toPlainString();
    }


    // =========================================================
    // DECIMAL
    // =========================================================

    private String decimal(

            BigDecimal value

    ) {


        if (
                value == null
        ) {

            return "0";
        }


        return value
                .stripTrailingZeros()
                .toPlainString();
    }
}
