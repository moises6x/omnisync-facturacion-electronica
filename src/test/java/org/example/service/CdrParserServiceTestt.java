package org.example.service;



import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;


public class CdrParserServiceTestt{

    private final CdrParserService service = new CdrParserService();

    @Test
    void debeInterpretarCdrAceptado() throws Exception {
        byte[] zip = zipXml("R.xml", CdrParserServiceTest.applicationResponse("0", "ACEPTADO", ""));
        var r = service.procesar(zip);
        assertTrue(r.aceptado());
        assertEquals("0", r.responseCode());
        assertEquals("ACEPTADO", r.description());
    }

    @Test
    void debeConservarObservaciones() throws Exception {
        byte[] zip = zipXml("R.xml", CdrParserServiceTest.applicationResponse("0", "OK", "<cbc:Note>OBS 1</cbc:Note><cbc:Note>OBS 2</cbc:Note>"));
        var r = service.procesar(zip);
        assertEquals(2, r.observaciones().size());
        assertEquals("OBS 1", r.observaciones().get(0));
    }

    @Test
    void codigoDistintoDeCeroDebeSerRechazo() throws Exception {
        byte[] zip = zipXml("R.xml", CdrParserServiceTest.applicationResponse("2335", "RECHAZADO", ""));
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

    private byte[] zipXml(String nombre, String contenido) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(nombre));
            zos.write(contenido.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void debeExtraerReferenceId() throws Exception {
        byte[] zip = zipXml("R-test.xml", CdrParserServiceTest.applicationResponse("0", "ACEPTADO", ""));
        assertEquals("F001-00000001", service.procesar(zip).referenceId());
    }

}

