package org.example.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private static final String MASTER = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void debeExigirClaveBase64ValidaDe32Bytes() {
        assertThrows(IllegalStateException.class, () -> new CryptoService("***"));
        String corta = Base64.getEncoder().encodeToString("corta".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> new CryptoService(corta));
    }

    @Test
    void usuarioSolDebeCifrarYDescifrar() {
        CryptoService service = new CryptoService(MASTER);
        String cifrado = service.cifrarUsuarioSol("20100130204", "MODDATOS");
        assertTrue(cifrado.startsWith("ENCv1:"));
        assertNotEquals("MODDATOS", cifrado);
        assertEquals("MODDATOS", service.descifrarUsuarioSol("20100130204", cifrado));
    }

    @Test
    void claveSolYPfxPasswordDebenTenerRoundTrip() {
        CryptoService service = new CryptoService(MASTER);
        String clave = service.cifrarClaveSol("20100130204", "moddatos");
        String pfx = service.cifrarPfxPassword("20100130204", "pfx-pass");
        assertEquals("moddatos", service.descifrarClaveSol("20100130204", clave));
        assertEquals("pfx-pass", service.descifrarPfxPassword("20100130204", pfx));
    }

    @Test
    void pfxBinarioDebeTenerRoundTrip() {
        CryptoService service = new CryptoService(MASTER);
        byte[] original = new byte[]{1,2,3,4,5,6};
        byte[] cifrado = service.cifrarPfx("20100130204", original);
        assertFalse(Arrays.equals(original, cifrado));
        assertArrayEquals(original, service.descifrarPfx("20100130204", cifrado));
    }

    @Test
    void aadDebeImpedirDescifrarConOtroRuc() {
        CryptoService service = new CryptoService(MASTER);
        String cifrado = service.cifrarClaveSol("20100130204", "secreto");
        assertThrows(IllegalStateException.class,
                () -> service.descifrarClaveSol("20600000001", cifrado));
    }

    @Test
    void noDebeAceptarSecretosTextoPlano() {
        CryptoService service = new CryptoService(MASTER);
        assertThrows(IllegalStateException.class,
                () -> service.descifrarUsuarioSol("20100130204", "MODDATOS"));
    }

    @Test
    void manipulacionDelCiphertextDebeDetectarsePorGcm() {
        CryptoService service = new CryptoService(MASTER);
        byte[] cifrado = service.cifrarPfx("20100130204", new byte[]{9,8,7,6});
        cifrado[cifrado.length - 1] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> service.descifrarPfx("20100130204", cifrado));
    }
}
