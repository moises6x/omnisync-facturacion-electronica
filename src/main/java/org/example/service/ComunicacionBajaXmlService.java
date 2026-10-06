package org.example.service;

import org.example.model.*;
import org.springframework.stereotype.Service;
import javax.xml.stream.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/** Construye el XML VoidedDocuments UBL 2.0 para Comunicación de Baja (RA). */
@Service
public class ComunicacionBajaXmlService {
    private static final String NS_VOIDED="urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
    private static final String NS_SAC="urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    private static final String NS_CAC="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_EXT="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final DateTimeFormatter F=DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] construir(ComunicacionBaja baja, Empresa empresa){
        if(baja==null || empresa==null) throw new IllegalArgumentException("Comunicación de Baja y empresa son requeridas.");
        if(baja.getDetalles()==null || baja.getDetalles().isEmpty()) throw new IllegalArgumentException("La Comunicación de Baja no tiene documentos.");
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            XMLStreamWriter w=XMLOutputFactory.newFactory().createXMLStreamWriter(out,"UTF-8");
            w.writeStartDocument("UTF-8","1.0");
            w.writeStartElement("", "VoidedDocuments", NS_VOIDED);
            w.writeDefaultNamespace(NS_VOIDED); w.writeNamespace("sac",NS_SAC); w.writeNamespace("cac",NS_CAC); w.writeNamespace("cbc",NS_CBC); w.writeNamespace("ext",NS_EXT);
            w.writeStartElement("ext","UBLExtensions",NS_EXT); w.writeStartElement("ext","UBLExtension",NS_EXT); w.writeStartElement("ext","ExtensionContent",NS_EXT); w.writeEndElement(); w.writeEndElement(); w.writeEndElement();
            elemento(w,"cbc","UBLVersionID",NS_CBC,"2.0");
            elemento(w,"cbc","CustomizationID",NS_CBC,"1.0");
            elemento(w,"cbc","ID",NS_CBC,baja.getIdentificador());
            elemento(w,"cbc","ReferenceDate",NS_CBC,F.format(baja.getFechaDocumentos()));
            elemento(w,"cbc","IssueDate",NS_CBC,F.format(baja.getFechaGeneracion()));
            escribirReferenciaFirma(w,empresa);
            escribirEmisor(w,empresa);
            for(ComunicacionBajaDetalle d:baja.getDetalles()) escribirLinea(w,d);
            w.writeEndElement(); w.writeEndDocument(); w.flush(); w.close();
            return out.toByteArray();
        }catch(Exception e){ throw new RuntimeException("No se pudo construir VoidedDocuments: "+e.getMessage(),e); }
    }

    private void escribirReferenciaFirma(XMLStreamWriter w,Empresa e)throws Exception{
        w.writeStartElement("cac","Signature",NS_CAC); elemento(w,"cbc","ID",NS_CBC,"SignatureSP");
        w.writeStartElement("cac","SignatoryParty",NS_CAC);
        w.writeStartElement("cac","PartyIdentification",NS_CAC); elemento(w,"cbc","ID",NS_CBC,e.getRuc()); w.writeEndElement();
        w.writeStartElement("cac","PartyName",NS_CAC); elemento(w,"cbc","Name",NS_CBC,e.getRazonSocial()); w.writeEndElement(); w.writeEndElement();
        w.writeStartElement("cac","DigitalSignatureAttachment",NS_CAC); w.writeStartElement("cac","ExternalReference",NS_CAC); elemento(w,"cbc","URI",NS_CBC,"#SignatureSP"); w.writeEndElement(); w.writeEndElement(); w.writeEndElement();
    }
    private void escribirEmisor(XMLStreamWriter w,Empresa e)throws Exception{
        w.writeStartElement("cac","AccountingSupplierParty",NS_CAC);
        elemento(w,"cbc","CustomerAssignedAccountID",NS_CBC,e.getRuc()); elemento(w,"cbc","AdditionalAccountID",NS_CBC,"6");
        w.writeStartElement("cac","Party",NS_CAC); w.writeStartElement("cac","PartyLegalEntity",NS_CAC); elemento(w,"cbc","RegistrationName",NS_CBC,e.getRazonSocial()); w.writeEndElement(); w.writeEndElement(); w.writeEndElement();
    }
    private void escribirLinea(XMLStreamWriter w,ComunicacionBajaDetalle d)throws Exception{
        w.writeStartElement("sac","VoidedDocumentsLine",NS_SAC);
        elemento(w,"cbc","LineID",NS_CBC,String.valueOf(d.getNumeroLinea()));
        elemento(w,"cbc","DocumentTypeCode",NS_CBC,d.getTipoComprobante());
        elemento(w,"sac","DocumentSerialID",NS_SAC,d.getSerie());
        elemento(w,"sac","DocumentNumberID",NS_SAC,String.valueOf(d.getNumero()));
        elemento(w,"sac","VoidReasonDescription",NS_SAC,d.getMotivo());
        w.writeEndElement();
    }
    private void elemento(XMLStreamWriter w,String p,String n,String ns,String v)throws Exception{ w.writeStartElement(p,n,ns); w.writeCharacters(v==null?"":v); w.writeEndElement(); }
}
