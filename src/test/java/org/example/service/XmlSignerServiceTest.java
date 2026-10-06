package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cobertura defensiva del firmador XML. Las pruebas de firma positiva con
 * certificado real deben usar un PFX de fixture controlado; aquí verificamos
 * que entradas inseguras/incompletas sean rechazadas antes de producción.
 */
class XmlSignerServiceTest {

    private XmlSignerService service;

    @BeforeEach
    void setUp() {
        service = new XmlSignerService();
        ReflectionTestUtils.setField(service, "environment", "BETA");
        ReflectionTestUtils.setField(service, "minRsaBits", 1024);
        ReflectionTestUtils.setField(service, "trustStorePath", "");
        ReflectionTestUtils.setField(service, "trustStorePassword", "");
        ReflectionTestUtils.setField(service, "trustStoreType", "PKCS12");
        service.inicializar();
    }

    @Test
    void xmlVacioDebeRechazarse() {
        assertThrows(IllegalArgumentException.class,
                () -> service.firmarXml(new byte[0], new byte[]{1}, "123"));
    }

    @Test
    void pfxVacioDebeRechazarse() {
        assertThrows(IllegalArgumentException.class,
                () -> service.firmarXml(xmlMinimo(), new byte[0], "123"));
    }

    @Test
    void passwordPfxVacioDebeRechazarse() {
        assertThrows(IllegalArgumentException.class,
                () -> service.firmarXml(xmlMinimo(), new byte[]{1}, " "));
    }

    @Test
    void xmlConDoctypeDebeBloquearseAntesDeUsarCertificado() {

        byte[] xml =
                """
                <!DOCTYPE a [
                    <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Invoice>&xxe;</Invoice>
                """
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.firmarXml(
                                        xml,
                                        new byte[]{1},
                                        "123"
                                )
                );

        /*
         * Verificamos que el motivo real sea el bloqueo
         * del DOCTYPE y no un problema posterior del PFX.
         */
        assertTrue(
                ex.getMessage()
                        .toUpperCase()
                        .contains("DOCTYPE")
        );
    }

    @Test
    void pfxSinClavePrivadaDebeRechazarse() throws Exception {
        char[] password = "123".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, password);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, password);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.firmarXml(xmlMinimo(), out.toByteArray(), "123"));

        assertTrue(ex.getMessage().toLowerCase().contains("certificado")
                || ex.getMessage().toLowerCase().contains("privada"));
    }

    @Test
    void produccionDebeExigirTruststoreAlInicializar() {
        ReflectionTestUtils.setField(service, "environment", "PRODUCCION");
        ReflectionTestUtils.setField(service, "trustStorePath", "");
        ReflectionTestUtils.setField(service, "trustStorePassword", "");

        assertThrows(IllegalStateException.class, service::inicializar);
    }

    private byte[] xmlMinimo() {
        return ("<Invoice xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">"
                + "<ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/>"
                + "</ext:UBLExtension></ext:UBLExtensions></Invoice>")
                .getBytes(StandardCharsets.UTF_8);
    }
}
