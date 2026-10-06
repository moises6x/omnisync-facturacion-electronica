package org.example.service;

import org.example.model.ComunicacionBaja;
import org.example.model.ComunicacionBajaDetalle;
import org.example.model.Empresa;
import org.junit.jupiter.api.Test;


import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComunicacionBajaXmlServiceTestt {
    @Test
    void debeConstruirVoidedDocumentsConCamposPrincipales(){
        Empresa e=Empresa.builder().ruc("20100130204").razonSocial("EMPRESA TEST SAC").build();
        ComunicacionBaja b=ComunicacionBaja.builder().rucEmisor(e.getRuc()).fechaDocumentos(LocalDate.of(2026,9,19)).fechaGeneracion(LocalDate.of(2026,9,19)).identificador("RA-20260919-1").build();
        b.addDetalle(ComunicacionBajaDetalle.builder().comprobanteId(1L).numeroLinea(1).tipoComprobante("01").serie("F001").numero(15).motivo("ERROR DE EMISION - NO OTORGADO").build());
        String xml=new String(new ComunicacionBajaXmlService().construir(b,e), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<cbc:UBLVersionID>2.0</cbc:UBLVersionID>"));
        assertTrue(xml.contains("<cbc:CustomizationID>1.0</cbc:CustomizationID>"));
        assertTrue(xml.contains("RA-20260919-1"));
        assertTrue(xml.contains("<sac:DocumentSerialID>F001</sac:DocumentSerialID>"));
        assertTrue(xml.contains("<sac:DocumentNumberID>15</sac:DocumentNumberID>"));
    }
}
