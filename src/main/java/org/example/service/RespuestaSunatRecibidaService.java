package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.RespuestaSunatRecibida;
import org.example.repository.RespuestaSunatRecibidaRepository;
import org.example.util.TicketSunat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RespuestaSunatRecibidaService {
    private final RespuestaSunatRecibidaRepository repository;
    private final Clock clock;

    /** Commit independiente antes del parser/finalización; jamás registra XML/ZIP en logs. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void conservar(String ruc, RespuestaSunatRecibida.Flujo flujo, Long documentoId, String ticket, byte[] zip) {
        TicketSunat.validarLongitud(ticket);
        if (zip == null || zip.length == 0) return;
        if (ruc == null || !ruc.matches("[0-9]{11}") || flujo == null || documentoId == null) {
            throw new IllegalArgumentException("Identificación requerida para conservar respuesta SUNAT.");
        }
        try {
            byte[] copia = zip.clone();
            repository.saveAndFlush(RespuestaSunatRecibida.builder().rucEmisor(ruc).flujo(flujo).documentoId(documentoId)
                    .ticketSunat(ticket).recibidoEn(LocalDateTime.now(clock))
                    .sha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(copia)))
                    .respuestaZipBytes(copia).build());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular SHA-256 de respuesta SUNAT.", e);
        }
    }
}
