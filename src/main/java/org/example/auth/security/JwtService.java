package org.example.auth.security;


import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.example.auth.model.Usuario;
import org.example.model.Empresa;


import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import org.springframework.security.oauth2.jwt.*;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.time.Duration;
import java.time.Instant;

import java.util.Base64;

@Service
public class JwtService {

    private final JwtEncoder encoder;

    private final JwtDecoder decoder;

    private final String issuer;

    private final Duration accessDuration;

    public JwtService(

            @Value("${security.jwt.secret-base64}")
            String secretBase64,

            @Value("${security.jwt.issuer:omnisync-facturacion}")
            String issuer,

            @Value("${security.jwt.access-minutes:10}")
            long accessMinutes
    ) {

        byte[] secret;

        try {

            secret =
                    Base64
                            .getDecoder()
                            .decode(secretBase64);

        } catch (Exception e) {

            throw new IllegalStateException(
                    "APP_JWT_SECRET_BASE64 no es Base64 válido."
            );
        }

        if (secret.length < 32) {

            throw new IllegalStateException(
                    "APP_JWT_SECRET_BASE64 debe tener al menos 32 bytes."
            );
        }

        SecretKey key =
                new SecretKeySpec(
                        secret,
                        "HmacSHA256"
                );

        this.encoder =
                new NimbusJwtEncoder(
                        new ImmutableSecret<>(key)
                );

        NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder
                        .withSecretKey(key)
                        .macAlgorithm(
                                MacAlgorithm.HS256
                        )
                        .build();

        jwtDecoder.setJwtValidator(
                JwtValidators
                        .createDefaultWithIssuer(
                                issuer
                        )
        );

        this.decoder =
                jwtDecoder;

        this.issuer =
                issuer;

        this.accessDuration =
                Duration.ofMinutes(
                        accessMinutes
                );
    }

    public String generar(
            Usuario usuario
    ) {

        Instant ahora =
                Instant.now();

        Empresa empresa =
                usuario.getEmpresa();

        JwtClaimsSet.Builder claims =
                JwtClaimsSet
                        .builder()
                        .issuer(issuer)
                        .issuedAt(ahora)
                        .expiresAt(
                                ahora.plus(
                                        accessDuration
                                )
                        )
                        .subject(
                                usuario
                                        .getId()
                                        .toString()
                        )
                        .claim(
                                "email",
                                usuario.getEmail()
                        )
                        .claim(
                                "role",
                                usuario
                                        .getRol()
                                        .name()
                        )
                        .claim(
                                "ver",
                                usuario
                                        .getTokenVersion()
                        );

        if (empresa != null) {

            claims.claim(
                    "tenantId",
                    empresa.getTenantId()
            );

            claims.claim(
                    "ruc",
                    empresa.getRuc()
            );
        }

        JwsHeader header =
                JwsHeader
                        .with(
                                MacAlgorithm.HS256
                        )
                        .type("JWT")
                        .build();

        return encoder
                .encode(
                        JwtEncoderParameters.from(
                                header,
                                claims.build()
                        )
                )
                .getTokenValue();
    }

    public Jwt validar(
            String token
    ) {

        return decoder.decode(token);
    }

    public long accessExpiresSeconds() {

        return accessDuration.toSeconds();
    }
}
