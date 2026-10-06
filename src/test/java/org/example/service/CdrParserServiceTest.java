package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CdrParserServiceTest {

    private final CdrParserService service = new CdrParserService();

    @Test
    void debeInterpretarCdrAceptado() throws Exception {
        byte[] zip = zipXml("R.xml", applicationResponse("0", "ACEPTADO", ""));
        var r = service.procesar(zip);
        assertTrue(r.aceptado());
        assertEquals("0", r.responseCode());
        assertEquals("ACEPTADO", r.description());
        assertEquals("F001-00000001", r.referenceId());
        assertEquals("F001-00000001", r.documentReferenceId());
        assertArrayEquals(applicationResponse("0", "ACEPTADO", "").getBytes(StandardCharsets.UTF_8), r.xml());
    }

    @Test
    void debeConservarObservaciones() throws Exception {
        byte[] zip = zipXml("R.xml", applicationResponse("0", "OK", "<cbc:Note>OBS 1</cbc:Note><cbc:Note>OBS 2</cbc:Note>"));
        var r = service.procesar(zip);
        assertEquals(2, r.observaciones().size());
        assertEquals("OBS 1", r.observaciones().get(0));
    }

    @Test
    void debeExponerReferenciasDistintasSinNormalizarlas() throws Exception {
        String xml = applicationResponse("0", "OK", "")
                .replace("<cbc:ReferenceID>F001-00000001</cbc:ReferenceID>", "<cbc:ReferenceID>RB-20261001-1</cbc:ReferenceID>")
                .replace("<cbc:ID>F001-00000001</cbc:ID>", "<cbc:ID>DOCUMENTO-DISTINTO</cbc:ID>");
        var r = service.procesar(zipXml("R.xml", xml));
        assertTrue(r.aceptado());
        assertEquals("RB-20261001-1", r.referenceId());
        assertEquals("DOCUMENTO-DISTINTO", r.documentReferenceId());
        assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), r.xml());
    }

    @Test
    void codigoDistintoDeCeroDebeSerRechazo() throws Exception {
        byte[] zip = zipXml("R.xml", applicationResponse("2335", "RECHAZADO", ""));
        var r = service.procesar(zip);
        assertFalse(r.aceptado());
        assertEquals("2335", r.responseCode());
    }

    @Test
    void debeRechazarZipSinXml() throws Exception {
        byte[] zip = zipXml("readme.txt", "hola");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.procesar(zip));
        assertTrue(ex.getMessage().contains("No se pudo procesar el CDR"));
    }

    @Test
    void debeRechazarXmlMalFormado() throws Exception {
        byte[] zip = zipXml("R.xml", "<root><ResponseCode>0</root>");
        assertThrows(RuntimeException.class, () -> service.procesar(zip));
    }

    @Test
    void debeBloquearDoctypeYXxe() throws Exception {
        String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root><ResponseCode>0</ResponseCode><Description>&xxe;</Description></root>";
        byte[] zip = zipXml("R.xml", xml);
        assertThrows(RuntimeException.class, () -> service.procesar(zip));
    }

    @Test
    void debeRechazarRaizIncorrectaAunqueContengaCodigoCero() throws Exception {
        assertCdrInvalida("<root><ResponseCode>0</ResponseCode><Description>ACEPTADO</Description></root>", "raíz");
    }

    @Test
    void debeRechazarNamespaceDeRaizIncorrecto() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("ApplicationResponse-2", "Invoice-2"), "raíz");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ResponseCode", "ReferenceID", "Description"})
    void debeRechazarCampoAusente(String campo) throws Exception {
        String xml = applicationResponse("0", "OK", "")
                .replaceAll("<cbc:" + campo + ">[^<]*</cbc:" + campo + ">", "");
        assertCdrInvalida(xml, campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ResponseCode", "ReferenceID", "Description"})
    void debeRechazarCampoVacio(String campo) throws Exception {
        String xml = applicationResponse("0", "OK", "")
                .replaceAll("<cbc:" + campo + ">[^<]*</cbc:" + campo + ">", "<cbc:" + campo + "> </cbc:" + campo + ">");
        assertCdrInvalida(xml, campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2335"})
    void debeRechazarResponseCodeDuplicadoInclusoSiCoincide(String segundoCodigo) throws Exception {
        String xml = applicationResponse("0", "OK", "").replace("</cac:Response>",
                "<cbc:ResponseCode>" + segundoCodigo + "</cbc:ResponseCode></cac:Response>");
        assertCdrInvalida(xml, "ResponseCode");
    }

    @Test
    void debeRechazarReferenceIdDuplicado() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("</cac:Response>",
                "<cbc:ReferenceID>F001-2</cbc:ReferenceID></cac:Response>"), "ReferenceID");
    }

    @Test
    void debeRechazarDescriptionDuplicada() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("</cac:Response>",
                "<cbc:Description>RECHAZADO</cbc:Description></cac:Response>"), "Description");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2335"})
    void debeRechazarMultiplesDocumentResponse(String segundoCodigo) throws Exception {
        String xml = applicationResponse("0", "OK", "");
        String segundo = applicationResponse(segundoCodigo, "RESULTADO", "");
        segundo = segundo.substring(segundo.indexOf("<cac:DocumentResponse>"), segundo.indexOf("</ApplicationResponse>"));
        assertCdrInvalida(xml.replace("</ApplicationResponse>", segundo + "</ApplicationResponse>"), "DocumentResponse");
    }

    @Test
    void debeRechazarMultiplesResponse() throws Exception {
        String xml = applicationResponse("0", "OK", "");
        String respuesta = xml.substring(xml.indexOf("<cac:Response>"), xml.indexOf("</cac:Response>") + "</cac:Response>".length());
        assertCdrInvalida(xml.replace("</cac:DocumentResponse>", respuesta + "</cac:DocumentResponse>"), "Response");
    }

    @Test
    void debeRechazarDocumentResponseAusente() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("cac:DocumentResponse", "cac:Otro"), "DocumentResponse");
    }

    @Test
    void debeRechazarResponseAusente() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("cac:Response>", "cac:Otro>"), "Response");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CommonBasicComponents-2", "CommonAggregateComponents-2"})
    void debeRechazarNamespaceDeComponentesIncorrecto(String namespace) throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace(namespace, "NamespaceFalso-2"), "exactamente un");
    }

    @Test
    void elementosFalsosFueraDeRutaNoDebenAlterarResultado() throws Exception {
        String falsos = """
                <cbc:ResponseCode>0</cbc:ResponseCode><cbc:ReferenceID>FALSA</cbc:ReferenceID>
                <cbc:Description>ACEPTADO FALSO</cbc:Description>
                <cac:Otro><cbc:Note>OBS FALSA</cbc:Note></cac:Otro>
                <f:ResponseCode xmlns:f="urn:falso">0</f:ResponseCode>
                """;
        var r = service.procesar(zipXml("R.xml", applicationResponse("2335", "RECHAZADO", falsos)));
        assertFalse(r.aceptado());
        assertEquals("2335", r.responseCode());
        assertEquals("RECHAZADO", r.description());
        assertEquals("F001-00000001", r.referenceId());
        assertTrue(r.observaciones().isEmpty());
    }

    @Test
    void codigoFueraDeRutaNoDebeSuplirCampoAusente() throws Exception {
        String xml = applicationResponse("0", "OK", "<cbc:ResponseCode>0</cbc:ResponseCode>")
                .replace("<cbc:ResponseCode>0</cbc:ResponseCode>", "")
                .replace("<cac:DocumentResponse>", "<cbc:ResponseCode>0</cbc:ResponseCode><cac:DocumentResponse>");
        assertCdrInvalida(xml, "ResponseCode");
    }

    @Test
    void referenciaFueraDeRutaNoDebeSuplirCampoAusente() throws Exception {
        String xml = applicationResponse("0", "OK", "<cbc:ReferenceID>FALSA</cbc:ReferenceID>")
                .replace("<cbc:ReferenceID>F001-00000001</cbc:ReferenceID>", "");
        assertCdrInvalida(xml, "ReferenceID");
    }

    @Test
    void respuestaAnidadaFueraDeRutaNoDebeSuplirRespuestaDirecta() throws Exception {
        String xml = applicationResponse("0", "OK", "").replace("<cac:Response>", "<cac:Otro><cac:Response>")
                .replace("</cac:Response>", "</cac:Response></cac:Otro>");
        assertCdrInvalida(xml, "Response");
    }

    @Test
    void noDebeConcatenarElementosEnUnCodigo() throws Exception {
        assertCdrInvalida(applicationResponse("<cbc:ID>0</cbc:ID>", "OK", ""), "texto simple");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "-1", "0.0", "12345"})
    void debeRechazarCodigoFueraDelFormatoDocumentado(String codigo) throws Exception {
        assertCdrInvalida(applicationResponse(codigo, "OK", ""), "numérico");
    }

    @ParameterizedTest
    @ValueSource(strings = {"F001-1", "RC-20260930-1", "RB-20260930-1", "RA-20260930-1"})
    void debeExtraerReferenciaSinImponerFormatoNiIgualdad(String referencia) throws Exception {
        String xml = applicationResponse("0", "OK", "").replace("F001-00000001", referencia);
        assertEquals(referencia, service.procesar(zipXml("R.xml", xml)).referenceId());
    }

    @Test
    void debeRechazarDocumentReferenceAusente() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("cac:DocumentReference", "cac:Otro"), "DocumentReference");
    }

    @Test
    void debeRechazarDocumentReferenceDuplicado() throws Exception {
        String xml = applicationResponse("0", "OK", "").replace("</cac:DocumentResponse>",
                "<cac:DocumentReference><cbc:ID>OTRA</cbc:ID></cac:DocumentReference></cac:DocumentResponse>");
        assertCdrInvalida(xml, "DocumentReference");
    }

    @Test
    void debeRechazarIdDocumentoAusente() throws Exception {
        assertCdrInvalida(applicationResponse("0", "OK", "").replace("<cbc:ID>F001-00000001</cbc:ID>", ""), "ID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2335"})
    void debeRechazarZipConVariosXmlAunqueCoincidan(String segundoCodigo) throws Exception {
        byte[] zip = zipXml("R1.xml", applicationResponse("0", "OK", ""),
                "R2.XML", applicationResponse(segundoCodigo, "RESULTADO", ""));
        var ex = assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> service.procesar(zip));
        assertTrue(ex.getMessage().contains("múltiples XML"));
    }

    @Test
    void debeAceptarUnSoloXmlAunqueNoSeaPrimeraEntrada() throws Exception {
        byte[] zip = zipXml("readme.txt", "auxiliar", "dummy/R.XML", applicationResponse("0", "OK", ""));
        assertTrue(service.procesar(zip).aceptado());
    }

    @Test
    void debeAceptarPrefijosDistintosConLosMismosNamespaces() throws Exception {
        String xml = applicationResponse("0", "OK", "").replace("cbc:", "bas:").replace("xmlns:cbc", "xmlns:bas")
                .replace("cac:", "agg:").replace("xmlns:cac", "xmlns:agg");
        assertTrue(service.procesar(zipXml("R.xml", xml)).aceptado());
    }

    @Test
    void debeRechazarZipVacioONulo() {
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> service.procesar(null));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> service.procesar(new byte[0]));
    }

    private void assertCdrInvalida(String xml, String mensaje) throws Exception {
        byte[] zip = zipXml("R.xml", xml);
        var ex = assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> service.procesar(zip));
        assertTrue(ex.getMessage().contains(mensaje), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    static String applicationResponse(String codigo, String descripcion, String notas) {
        // Perfil de lectura SUNAT v2.1, pp. 38/44. No es un fixture de firma/XSD.
        return """
                <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                  xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                  xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  %s
                  <cac:DocumentResponse>
                    <cac:Response>
                      <cbc:ReferenceID>F001-00000001</cbc:ReferenceID>
                      <cbc:ResponseCode>%s</cbc:ResponseCode>
                      <cbc:Description>%s</cbc:Description>
                    </cac:Response>
                    <cac:DocumentReference><cbc:ID>F001-00000001</cbc:ID></cac:DocumentReference>
                  </cac:DocumentResponse>
                </ApplicationResponse>
                """.formatted(notas, codigo, descripcion);
    }

    static byte[] zipXml(String... entradas) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (int i = 0; i < entradas.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entradas[i]));
                zos.write(entradas[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
