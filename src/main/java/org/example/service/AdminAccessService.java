package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminAccessService {

    private final byte[] expected;

    public AdminAccessService(
            @Value("${security.admin-key}")
            String adminKey
    ) {

        if (adminKey == null || adminKey.length() < 32) {
            throw new IllegalStateException(
                    "APP_ADMIN_KEY debe tener al menos 32 caracteres."
            );
        }

        this.expected = adminKey.getBytes(StandardCharsets.UTF_8);
    }

    public void validar(String supplied) {

        if (supplied == null || supplied.isBlank()) {
            throw new SecurityException("Credencial administrativa requerida.");
        }

        boolean ok = MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected
        );

        if (!ok) {
            throw new SecurityException("Credencial administrativa inválida.");
        }
    }
}
