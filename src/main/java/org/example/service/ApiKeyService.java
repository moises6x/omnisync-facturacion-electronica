package org.example.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ApiKeyService {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generarApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public String hash(String apiKey) {

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    md.digest(apiKey.getBytes(StandardCharsets.UTF_8))
            );

        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar hash de API key.", e);
        }
    }

    public boolean coincide(String apiKey, String hashEsperado) {

        if (apiKey == null || apiKey.isBlank() || hashEsperado == null) {
            return false;
        }

        byte[] actual = hash(apiKey).getBytes(StandardCharsets.UTF_8);
        byte[] esperado = hashEsperado.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(actual, esperado);
    }
}
