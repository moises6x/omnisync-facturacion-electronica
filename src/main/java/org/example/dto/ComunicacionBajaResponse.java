package org.example.dto;

import java.time.*;

public record ComunicacionBajaResponse(
        Long id, String identificador, String nombreArchivoXml,
        LocalDate fechaDocumentos, LocalDate fechaGeneracion,
        String estado, String ticketSunat, String codigoRespuestaSunat,
        String mensajeSunat, LocalDateTime enviadoEn, LocalDateTime ultimaConsultaEn,
        LocalDateTime finalizadoEn, LocalDateTime createdAt, LocalDateTime updatedAt
) {}
