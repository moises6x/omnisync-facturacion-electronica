package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte VERSION = 1;
    private static final String STRING_PREFIX = "ENCv1:";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(
            @Value("${security.secrets.master-key-base64}")
            String masterKeyBase64
    ) {

        try {
            byte[] raw = Base64.getDecoder().decode(masterKeyBase64);

            if (raw.length != 32) {
                throw new IllegalStateException(
                        "APP_MASTER_KEY_BASE64 debe contener exactamente 32 bytes (AES-256)."
                );
            }

            this.key = new SecretKeySpec(raw, "AES");

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY_BASE64 no es Base64 válido.",
                    e
            );
        }
    }

    public String cifrarUsuarioSol(String ruc, String valor) {
        return cifrarTexto(valor, contexto(ruc, "usuarioSol"));
    }

    public String descifrarUsuarioSol(String ruc, String valor) {
        return descifrarTexto(valor, contexto(ruc, "usuarioSol"));
    }

    public String cifrarClaveSol(String ruc, String valor) {
        return cifrarTexto(valor, contexto(ruc, "claveSol"));
    }

    public String descifrarClaveSol(String ruc, String valor) {
        return descifrarTexto(valor, contexto(ruc, "claveSol"));
    }

    public String cifrarPfxPassword(String ruc, String valor) {
        return cifrarTexto(valor, contexto(ruc, "pfxPassword"));
    }

    public String descifrarPfxPassword(String ruc, String valor) {
        return descifrarTexto(valor, contexto(ruc, "pfxPassword"));
    }

    public byte[] cifrarPfx(String ruc, byte[] valor) {
        return cifrarBytes(valor, contexto(ruc, "pfxBytes"));
    }

    public byte[] descifrarPfx(String ruc, byte[] valor) {
        return descifrarBytes(valor, contexto(ruc, "pfxBytes"));
    }

    private String cifrarTexto(String plain, String aad) {

        if (plain == null) return null;

        byte[] encrypted = cifrarBytes(
                plain.getBytes(StandardCharsets.UTF_8),
                aad
        );

        return STRING_PREFIX + Base64.getEncoder().encodeToString(encrypted);
    }

    private String descifrarTexto(String encrypted, String aad) {

        if (encrypted == null) return null;

        if (!encrypted.startsWith(STRING_PREFIX)) {
            throw new IllegalStateException(
                    "Se encontró un secreto sin cifrar. Reconfigure la empresa antes de emitir."
            );
        }

        byte[] envelope = Base64.getDecoder().decode(
                encrypted.substring(STRING_PREFIX.length())
        );

        byte[] plain = descifrarBytes(envelope, aad);

        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] cifrarBytes(byte[] plain, String aad) {

        if (plain == null) return null;

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            byte[] ciphertext = cipher.doFinal(plain);

            return ByteBuffer
                    .allocate(1 + IV_LENGTH + ciphertext.length)
                    .put(VERSION)
                    .put(iv)
                    .put(ciphertext)
                    .array();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo cifrar información sensible.",
                    e
            );
        }
    }

    private byte[] descifrarBytes(byte[] envelope, String aad) {

        if (envelope == null) return null;

        try {
            if (envelope.length <= 1 + IV_LENGTH) {
                throw new IllegalStateException("Secreto cifrado inválido.");
            }

            ByteBuffer buffer = ByteBuffer.wrap(envelope);

            byte version = buffer.get();

            if (version != VERSION) {
                throw new IllegalStateException("Versión de cifrado no soportada.");
            }

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo descifrar información sensible. Verifique la clave maestra.",
                    e
            );
        }
    }

    private String contexto(String ruc, String campo) {
        return "empresa:" + ruc + ":" + campo;
    }
}
