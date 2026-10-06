
package org.example.service;

import org.example.model.Empresa;
import org.example.model.ResumenDiario;
import org.example.model.ResumenDiarioDetalle;

import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * =============================================================
 * RESUMEN DIARIO XML SERVICE
 * =============================================================
 *
 * Construye el XML SummaryDocuments utilizado por SUNAT para informar:
 *
 * - Boleta 03
 * - Nota de Crédito 07 asociada a Boleta 03
 *
 * Este documento utiliza la estructura peruana UBL 2.0.
 *
 * IMPORTANTE:
 *
 * Este servicio NO firma. Sólo construye el XML base.
 * XmlSignerService inserta posteriormente la firma XMLDSIG dentro de
 * ext:ExtensionContent.
 */
@Service
public class ResumenDiarioXmlService {

    private static final String NS_SUMMARY =
            "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";

    private static final String NS_SAC =
            "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";

    private static final String NS_CAC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    private static final String NS_CBC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private static final String NS_EXT =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ISO_LOCAL_DATE;

    // =========================================================
    // CONSTRUIR
    // =========================================================

    public byte[] construir(
            ResumenDiario resumen,
            Empresa empresa
    ) {

        validarCabecera(
                resumen,
                empresa
        );

        List<ResumenDiarioDetalle> detalles =
                resumen
                        .getDetalles()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        ResumenDiarioDetalle::getNumeroLinea
                                )
                        )
                        .toList();

        if (detalles.isEmpty()) {
            throw new IllegalArgumentException(
                    "El Resumen Diario no contiene documentos."
            );
        }

        try {

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

            // =================================================
            // ROOT
            // =================================================

            w.writeStartElement(
                    "p",
                    "SummaryDocuments",
                    NS_SUMMARY
            );

            w.writeNamespace(
                    "p",
                    NS_SUMMARY
            );

            w.writeNamespace(
                    "sac",
                    NS_SAC
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
            // EXTENSIÓN DE FIRMA
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

            // XmlSignerService insertará aquí ds:Signature.

            w.writeEndElement(); // ExtensionContent
            w.writeEndElement(); // UBLExtension
            w.writeEndElement(); // UBLExtensions

            // =================================================
            // DATOS GENERALES
            // =================================================

            elemento(
                    w,
                    "cbc",
                    "UBLVersionID",
                    NS_CBC,
                    "2.0"
            );

            elemento(
                    w,
                    "cbc",
                    "CustomizationID",
                    NS_CBC,
                    "1.1"
            );

            elemento(
                    w,
                    "cbc",
                    "ID",
                    NS_CBC,
                    resumen.getIdentificador()
            );

            /*
             * El XSD SUNAT exige este orden:
             *
             * ReferenceDate -> fecha de emisión de los documentos.
             * IssueDate     -> fecha de generación del resumen.
             */
            elemento(
                    w,
                    "cbc",
                    "ReferenceDate",
                    NS_CBC,
                    resumen
                            .getFechaDocumentos()
                            .format(FECHA)
            );

            elemento(
                    w,
                    "cbc",
                    "IssueDate",
                    NS_CBC,
                    resumen
                            .getFechaGeneracion()
                            .format(FECHA)
            );

            elemento(
                    w,
                    "cbc",
                    "Note",
                    NS_CBC,
                    "CONSOLIDADO DE BOLETAS DE VENTA"
            );

            // =================================================
            // REFERENCIA DE LA FIRMA
            // =================================================

            escribirReferenciaFirma(
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
            // LÍNEAS
            // =================================================

            for (ResumenDiarioDetalle detalle : detalles) {
                escribirLinea(
                        w,
                        detalle
                );
            }

            w.writeEndElement(); // SummaryDocuments
            w.writeEndDocument();

            w.flush();
            w.close();

            return baos.toByteArray();

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error construyendo SummaryDocuments: "
                            + mensajeSeguro(e),
                    e
            );
        }
    }

    // =========================================================
    // FIRMA REFERENCIADA
    // =========================================================

    private void escribirReferenciaFirma(
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
                "SignatureSP"
        );

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

        w.writeEndElement(); // PartyIdentification

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

        w.writeEndElement(); // PartyName
        w.writeEndElement(); // SignatoryParty

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

        w.writeEndElement(); // ExternalReference
        w.writeEndElement(); // DigitalSignatureAttachment
        w.writeEndElement(); // Signature
    }

    // =========================================================
    // EMISOR
    // =========================================================

    private void escribirEmisor(
            XMLStreamWriter w,
            Empresa empresa
    ) throws Exception {

        w.writeStartElement(
                "cac",
                "AccountingSupplierParty",
                NS_CAC
        );

        elemento(
                w,
                "cbc",
                "CustomerAssignedAccountID",
                NS_CBC,
                empresa.getRuc()
        );

        elemento(
                w,
                "cbc",
                "AdditionalAccountID",
                NS_CBC,
                "6"
        );

        w.writeStartElement(
                "cac",
                "Party",
                NS_CAC
        );

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
                empresa.getRazonSocial()
        );

        w.writeEndElement(); // PartyLegalEntity
        w.writeEndElement(); // Party
        w.writeEndElement(); // AccountingSupplierParty
    }

    // =========================================================
    // LÍNEA
    // =========================================================

    private void escribirLinea(
            XMLStreamWriter w,
            ResumenDiarioDetalle d
    ) throws Exception {

        validarDetalle(d);

        w.writeStartElement(
                "sac",
                "SummaryDocumentsLine",
                NS_SAC
        );

        elemento(
                w,
                "cbc",
                "LineID",
                NS_CBC,
                Integer.toString(
                        d.getNumeroLinea()
                )
        );

        elemento(
                w,
                "cbc",
                "DocumentTypeCode",
                NS_CBC,
                d.getTipoComprobante()
        );

        elemento(
                w,
                "cbc",
                "ID",
                NS_CBC,
                numeroDocumento(
                        d.getSerie(),
                        d.getNumero()
                )
        );

        // =====================================================
        // CLIENTE
        // =====================================================
        //
        // Para boleta sin identificación nuestro modelo interno usa:
        //
        // tipo = 0
        // número = -
        //
        // SummaryDocuments necesita informar ambos valores.
        // =====================================================

        w.writeStartElement(
                "cac",
                "AccountingCustomerParty",
                NS_CAC
        );

        /*
         * SUNAT muestra para una boleta sin identificación:
         *
         * CustomerAssignedAccountID = "-"
         * AdditionalAccountID       = "-"
         *
         * Internamente nuestro Comprobante utiliza tipo "0" y número "-"
         * para mantener columnas NOT NULL. Esa representación interna NO debe
         * salir literalmente en el SummaryDocuments.
         */
        boolean clienteSinIdentificacion =
                "0".equals(d.getTipoDocCliente())
                        &&
                        "-".equals(d.getNumDocCliente());

        elemento(
                w,
                "cbc",
                "CustomerAssignedAccountID",
                NS_CBC,
                clienteSinIdentificacion
                        ? "-"
                        : d.getNumDocCliente()
        );

        elemento(
                w,
                "cbc",
                "AdditionalAccountID",
                NS_CBC,
                clienteSinIdentificacion
                        ? "-"
                        : d.getTipoDocCliente()
        );

        w.writeEndElement(); // AccountingCustomerParty

        // =====================================================
        // REFERENCIA DE NC A BOLETA
        // =====================================================

        if ("07".equals(d.getTipoComprobante())) {

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
                    numeroDocumento(
                            d.getSerieComprobanteAfectado(),
                            d.getNumeroComprobanteAfectado()
                    )
            );

            elemento(
                    w,
                    "cbc",
                    "DocumentTypeCode",
                    NS_CBC,
                    d.getTipoComprobanteAfectado()
            );

            w.writeEndElement(); // InvoiceDocumentReference
            w.writeEndElement(); // BillingReference
        }

        // =====================================================
        // ESTADO DE LA LÍNEA
        // =====================================================

        w.writeStartElement(
                "cac",
                "Status",
                NS_CAC
        );

        elemento(
                w,
                "cbc",
                "ConditionCode",
                NS_CBC,
                d.getCodigoEstado()
        );

        w.writeEndElement(); // Status

        // =====================================================
        // TOTAL DEL DOCUMENTO
        // =====================================================

        importeSac(
                w,
                "TotalAmount",
                d.getMontoTotal()
        );

        // =====================================================
        // VALORES DE VENTA
        // =====================================================

        billingPayment(
                w,
                d.getOpGravada(),
                "01"
        );

        billingPayment(
                w,
                d.getOpExonerada(),
                "02"
        );

        billingPayment(
                w,
                d.getOpInafecta(),
                "03"
        );

        // =====================================================
        // IGV
        // =====================================================

        w.writeStartElement(
                "cac",
                "TaxTotal",
                NS_CAC
        );

        importeCbc(
                w,
                "TaxAmount",
                d.getIgv()
        );

        w.writeStartElement(
                "cac",
                "TaxSubtotal",
                NS_CAC
        );

        importeCbc(
                w,
                "TaxAmount",
                d.getIgv()
        );

        w.writeStartElement(
                "cac",
                "TaxCategory",
                NS_CAC
        );

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
                "1000"
        );

        elemento(
                w,
                "cbc",
                "Name",
                NS_CBC,
                "IGV"
        );

        elemento(
                w,
                "cbc",
                "TaxTypeCode",
                NS_CBC,
                "VAT"
        );

        w.writeEndElement(); // TaxScheme
        w.writeEndElement(); // TaxCategory
        w.writeEndElement(); // TaxSubtotal
        w.writeEndElement(); // TaxTotal

        w.writeEndElement(); // SummaryDocumentsLine
    }

    // =========================================================
    // BILLING PAYMENT
    // =========================================================

    private void billingPayment(
            XMLStreamWriter w,
            BigDecimal importe,
            String instructionId
    ) throws Exception {

        w.writeStartElement(
                "sac",
                "BillingPayment",
                NS_SAC
        );

        importeCbc(
                w,
                "PaidAmount",
                importe
        );

        elemento(
                w,
                "cbc",
                "InstructionID",
                NS_CBC,
                instructionId
        );

        w.writeEndElement();
    }

    // =========================================================
    // VALIDACIONES LOCALES
    // =========================================================

    private void validarCabecera(
            ResumenDiario resumen,
            Empresa empresa
    ) {

        if (resumen == null) {
            throw new IllegalArgumentException(
                    "Resumen Diario requerido."
            );
        }

        if (empresa == null) {
            throw new IllegalArgumentException(
                    "Empresa requerida para construir el Resumen Diario."
            );
        }

        if (
                empresa.getRuc() == null
                        ||
                        !empresa.getRuc().matches("^[0-9]{11}$")
        ) {
            throw new IllegalArgumentException(
                    "RUC del emisor inválido."
            );
        }

        if (
                resumen.getRucEmisor() == null
                        ||
                        !resumen.getRucEmisor().equals(
                                empresa.getRuc()
                        )
        ) {
            throw new IllegalArgumentException(
                    "El Resumen Diario no pertenece a la empresa indicada."
            );
        }

        if (
                empresa.getRazonSocial() == null
                        ||
                        empresa.getRazonSocial().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La empresa no tiene razón social configurada."
            );
        }

        if (resumen.getFechaDocumentos() == null) {
            throw new IllegalArgumentException(
                    "El Resumen Diario no tiene fecha de documentos."
            );
        }

        if (resumen.getFechaGeneracion() == null) {
            throw new IllegalArgumentException(
                    "El Resumen Diario no tiene fecha de generación."
            );
        }

        if (
                resumen
                        .getFechaGeneracion()
                        .isBefore(
                                resumen.getFechaDocumentos()
                        )
        ) {
            throw new IllegalArgumentException(
                    "La fecha de generación del Resumen Diario no puede ser anterior a la fecha de los documentos."
            );
        }

        if (
                resumen.getIdentificador() == null
                        ||
                        resumen.getIdentificador().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "El Resumen Diario no tiene identificador RC."
            );
        }
    }

    private void validarDetalle(
            ResumenDiarioDetalle d
    ) {

        if (d == null) {
            throw new IllegalArgumentException(
                    "Existe una línea nula en el Resumen Diario."
            );
        }

        if (
                !"03".equals(d.getTipoComprobante())
                        &&
                        !"07".equals(d.getTipoComprobante())
        ) {
            throw new IllegalArgumentException(
                    "El Resumen Diario sólo soporta Boleta 03 y NC 07 de Boleta."
            );
        }

        if (
                d.getSerie() == null
                        ||
                        d.getSerie().length() != 4
                        ||
                        !d.getSerie().startsWith("B")
        ) {
            throw new IllegalArgumentException(
                    "Serie inválida en línea de Resumen Diario: "
                            + d.getSerie()
            );
        }

        if (d.getNumero() == null || d.getNumero() < 1) {
            throw new IllegalArgumentException(
                    "Correlativo de comprobante inválido en Resumen Diario."
            );
        }

        if (
                d.getTipoDocCliente() == null
                        ||
                        d.getTipoDocCliente().isBlank()
                        ||
                        d.getNumDocCliente() == null
                        ||
                        d.getNumDocCliente().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La línea del Resumen Diario no tiene identificación normalizada del cliente."
            );
        }

        if (!"PEN".equalsIgnoreCase(d.getMoneda())) {
            throw new IllegalArgumentException(
                    "Esta versión del Resumen Diario sólo admite documentos expresados en PEN."
            );
        }

        exigirImporte(d.getOpGravada(), "opGravada");
        exigirImporte(d.getOpExonerada(), "opExonerada");
        exigirImporte(d.getOpInafecta(), "opInafecta");
        exigirImporte(d.getIgv(), "igv");
        exigirImporte(d.getMontoTotal(), "montoTotal");

        if ("07".equals(d.getTipoComprobante())) {

            if (
                    !"03".equals(
                            d.getTipoComprobanteAfectado()
                    )
            ) {
                throw new IllegalArgumentException(
                        "Una NC incluida en Resumen Diario debe afectar una Boleta 03."
                );
            }

            if (
                    d.getSerieComprobanteAfectado() == null
                            ||
                            !d.getSerieComprobanteAfectado().startsWith("B")
                            ||
                            d.getNumeroComprobanteAfectado() == null
            ) {
                throw new IllegalArgumentException(
                        "La NC no tiene una referencia válida a la boleta afectada."
                );
            }
        }

        if (
                !"1".equals(d.getCodigoEstado())
                        &&
                        !"2".equals(d.getCodigoEstado())
                        &&
                        !"3".equals(d.getCodigoEstado())
        ) {
            throw new IllegalArgumentException(
                    "Código de estado de línea inválido para Resumen Diario."
            );
        }
    }

    private void exigirImporte(
            BigDecimal valor,
            String campo
    ) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException(
                    "Importe inválido en " + campo + "."
            );
        }
    }

    // =========================================================
    // HELPERS XML
    // =========================================================

    private void elemento(
            XMLStreamWriter w,
            String prefijo,
            String nombre,
            String namespace,
            String valor
    ) throws Exception {

        w.writeStartElement(
                prefijo,
                nombre,
                namespace
        );

        w.writeCharacters(
                valor == null ? "" : valor
        );

        w.writeEndElement();
    }

    private void importeCbc(
            XMLStreamWriter w,
            String nombre,
            BigDecimal importe
    ) throws Exception {

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
                moneda(importe)
        );

        w.writeEndElement();
    }

    private void importeSac(
            XMLStreamWriter w,
            String nombre,
            BigDecimal importe
    ) throws Exception {

        w.writeStartElement(
                "sac",
                nombre,
                NS_SAC
        );

        w.writeAttribute(
                "currencyID",
                "PEN"
        );

        w.writeCharacters(
                moneda(importe)
        );

        w.writeEndElement();
    }

    private String moneda(
            BigDecimal valor
    ) {
        return valor
                .setScale(
                        2,
                        java.math.RoundingMode.HALF_UP
                )
                .toPlainString();
    }

    private String numeroDocumento(
            String serie,
            Integer numero
    ) {

        if (
                serie == null
                        ||
                        numero == null
        ) {
            throw new IllegalArgumentException(
                    "Serie/número incompletos para Resumen Diario."
            );
        }

        return String.format(
                "%s-%08d",
                serie,
                numero
        );
    }

    private String mensajeSeguro(
            Exception e
    ) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.getClass().getSimpleName();
    }
}
