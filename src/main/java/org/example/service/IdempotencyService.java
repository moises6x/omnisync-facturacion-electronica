package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.model.SolicitudEmision;
import org.example.repository.SolicitudEmisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final SolicitudEmisionRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(
            propagation = Propagation.MANDATORY
    )
    public ContextoIdempotencia iniciar(

            String ruc,

            String idempotencyKey,

            Object request

    ) {

        if (idempotencyKey == null ||
                idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Debe enviar X-Idempotency-Key"
            );
        }

        String hash =
                generarHash(request);

        repository.inicializarSiNoExiste(
                ruc,
                idempotencyKey,
                hash
        );

        SolicitudEmision solicitud =
                repository
                        .obtenerBloqueada(
                                ruc,
                                idempotencyKey
                        )
                        .orElseThrow();

        /*
         * La misma clave no puede utilizarse
         * para enviar dos facturas diferentes.
         */
        if (!hash.equals(
                solicitud.getRequestHash()
        )) {

            throw new IllegalArgumentException(
                    "La X-Idempotency-Key ya fue utilizada con una solicitud diferente."
            );
        }

        boolean yaProcesada =
                solicitud.getComprobanteId()
                        != null;

        return new ContextoIdempotencia(
                solicitud,
                yaProcesada
        );
    }

    @Transactional(
            propagation = Propagation.MANDATORY
    )
    public void vincularComprobante(

            SolicitudEmision solicitud,

            Long comprobanteId

    ) {

        solicitud.setComprobanteId(
                comprobanteId
        );

        solicitud.setEstado(
                "CREADO"
        );

        repository.save(solicitud);
    }

    private String generarHash(
            Object objeto
    ) {

        try {

            byte[] json =
                    objectMapper.writeValueAsBytes(
                            objeto
                    );

            MessageDigest md =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] digest =
                    md.digest(json);

            return HexFormat
                    .of()
                    .formatHex(digest);

        } catch (Exception e) {

            throw new RuntimeException(
                    "No se pudo generar hash idempotente",
                    e
            );
        }
    }

    public record ContextoIdempotencia(

            SolicitudEmision solicitud,

            boolean yaProcesada

    ) {
    }
}