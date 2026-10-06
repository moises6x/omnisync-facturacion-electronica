
package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.model.SecuenciaResumenDiario;
import org.example.repository.SecuenciaResumenDiarioRepository;
import org.example.repository.ResumenDiarioRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * =============================================================
 * SECUENCIA RESUMEN DIARIO SERVICE
 * =============================================================
 *
 * Genera el correlativo de los archivos RC por empresa y fecha.
 *
 * SUNAT admite correlativos de 1 a 5 dígitos para el nombre del archivo.
 * Por seguridad detenemos la generación si se supera 99999.
 */
@Service
@RequiredArgsConstructor
public class SecuenciaResumenDiarioService {

    private final SecuenciaResumenDiarioRepository repository;
    private final ResumenDiarioRepository resumenRepository;

    @Transactional
    public int siguiente(
            String ruc,
            LocalDate fechaReferencia
    ) {
        return siguiente(ruc, fechaReferencia, fechaReferencia);
    }

    /**
     * Nuevos RC: ID por fecha de generación. Se conservan ambas restricciones existentes:
     * RUC/ID y RUC/fecha_documentos/correlativo, incluso entre generaciones de distintos días.
     * Las filas de secuencia se bloquean en orden de fecha para evitar inversión de locks.
     * Son marcas de máximo usado, no requieren numeración contigua ni migración de cabeceras.
     */
    @Transactional
    public int siguiente(String ruc, LocalDate fechaGeneracion, LocalDate fechaDocumentos) {

        if (ruc == null || !ruc.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException(
                    "RUC inválido para generar secuencia de Resumen Diario."
            );
        }

        if (fechaGeneracion == null || fechaDocumentos == null) {
            throw new IllegalArgumentException(
                    "La fecha de referencia del Resumen Diario es obligatoria."
            );
        }

        List<LocalDate> fechas = java.util.stream.Stream.of(fechaGeneracion, fechaDocumentos).distinct().sorted().toList();
        java.util.ArrayList<SecuenciaResumenDiario> secuencias = new java.util.ArrayList<>();
        for (LocalDate fecha : fechas) {
            repository.inicializarSiNoExiste(ruc, fecha);
            secuencias.add(repository.obtenerBloqueada(ruc, fecha).orElseThrow());
        }
        int piso = resumenRepository.buscarPisoCorrelativo(ruc, fechaDocumentos,
                "RC-" + fechaGeneracion.format(DateTimeFormatter.BASIC_ISO_DATE) + "-%");
        for (SecuenciaResumenDiario secuencia : secuencias) {
            if (secuencia.getUltimoCorrelativo() == null || secuencia.getUltimoCorrelativo() < 0)
                throw new IllegalStateException("La secuencia RC requiere conciliación.");
            piso = Math.max(piso, secuencia.getUltimoCorrelativo());
        }
        int correlativo = piso + 1;

        if (correlativo < 1 || correlativo > 99999) {
            throw new IllegalStateException(
                    "El correlativo del Resumen Diario salió del rango 1..99999."
            );
        }

        for (SecuenciaResumenDiario secuencia : secuencias) {
            secuencia.setUltimoCorrelativo(correlativo);
            repository.save(secuencia);
        }

        return correlativo;
    }
}


