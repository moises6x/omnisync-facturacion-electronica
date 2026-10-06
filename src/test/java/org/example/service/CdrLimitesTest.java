package org.example.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class CdrLimitesTest {
    private final CdrParserService parser = new CdrParserService();

    @Test
    void debeRechazarZipComprimidoSobreLimite() throws Exception {
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", xml());
        ReflectionTestUtils.setField(parser, "maxZipBytes", zip.length - 1);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    @Test
    void debeDetenerXmlDeGranExpansionSinConfiarEnTamanioDeclarado() throws Exception {
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", xml().replace("ACEPTADO", "A".repeat(20000)));
        ReflectionTestUtils.setField(parser, "maxXmlBytes", 2000);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    @Test
    void debeContarExpansionDeEntradaIgnoradaAnteriorAlXml() throws Exception {
        byte[] zip = zipConIgnorada("datos.bin", 4000, true);
        ReflectionTestUtils.setField(parser, "maxExpandedBytes", 2000);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    @Test
    void debeContarExpansionDeDirectorioAunqueNoSeUseComoXml() throws Exception {
        byte[] zip = zipConIgnorada("directorio/", 4000, true);
        ReflectionTestUtils.setField(parser, "maxExpandedBytes", 2000);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    @Test
    void debeContarEntradaIgnoradaPosteriorAlXml() throws Exception {
        byte[] zip = zipConIgnorada("datos.bin", 4000, false);
        ReflectionTestUtils.setField(parser, "maxExpandedBytes", xml().getBytes(StandardCharsets.UTF_8).length + 3999);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    @Test
    void debeLimitarNumeroDeEntradasIncluidosDirectorios() throws Exception {
        byte[] zip = zipConIgnorada("directorio/", 0, true);
        ReflectionTestUtils.setField(parser, "maxEntries", 1);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    @Test
    void debeAceptarLimitesExactosSinAlterarCdr() throws Exception {
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", xml());
        ReflectionTestUtils.setField(parser, "maxZipBytes", zip.length);
        ReflectionTestUtils.setField(parser, "maxXmlBytes", xml().getBytes(StandardCharsets.UTF_8).length);
        ReflectionTestUtils.setField(parser, "maxExpandedBytes", xml().getBytes(StandardCharsets.UTF_8).length);
        ReflectionTestUtils.setField(parser, "maxEntries", 1);
        assertTrue(parser.procesar(zip).aceptado());
    }

    @Test
    void configuracionInvalidaNoDebeDesactivarLimites() throws Exception {
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", xml());
        ReflectionTestUtils.setField(parser, "maxEntries", 0);
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class, () -> parser.procesar(zip));
    }

    private String xml() { return CdrParserServiceTest.applicationResponse("0", "ACEPTADO", ""); }
    private byte[] zipConIgnorada(String nombre, int bytes, boolean primero) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(out)) {
            if (primero) entrada(z, nombre, new byte[bytes]);
            entrada(z, "R.xml", xml().getBytes(StandardCharsets.UTF_8));
            if (!primero) entrada(z, nombre, new byte[bytes]);
        }
        return out.toByteArray();
    }
    private void entrada(ZipOutputStream z, String nombre, byte[] bytes) throws Exception {
        z.putNextEntry(new ZipEntry(nombre)); z.write(bytes); z.closeEntry();
    }
}
