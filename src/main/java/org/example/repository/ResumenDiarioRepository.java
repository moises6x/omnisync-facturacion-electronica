
package org.example.repository;

import org.example.model.EstadoResumenDiario;
import org.example.model.ResumenDiario;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * =============================================================
 * RESUMEN DIARIO REPOSITORY
 * =============================================================
 *
 * Todas las operaciones expuestas al usuario se filtran por RUC.
 * Esto evita que una empresa del SaaS pueda consultar o modificar
 * resúmenes pertenecientes a otra empresa.
 */
public interface ResumenDiarioRepository
        extends JpaRepository<ResumenDiario, Long> {

    /** Piso histórico bajo bloqueo de las secuencias de ambas fechas; no cambia cabeceras existentes. */
    @Query("""
            SELECT COALESCE(MAX(r.correlativo),0) FROM ResumenDiario r
            WHERE r.rucEmisor=:ruc AND (r.fechaDocumentos=:fechaDocumentos OR r.identificador LIKE :prefijo)
            """)
    int buscarPisoCorrelativo(@Param("ruc") String ruc, @Param("fechaDocumentos") LocalDate fechaDocumentos,
            @Param("prefijo") String prefijo);

    Optional<ResumenDiario> findByIdAndRucEmisor(
            Long id,
            String rucEmisor
    );

    boolean existsByRucEmisorAndTicketSunatAndIdNot(String rucEmisor, String ticketSunat, Long id);

    /** Lectura actual, no snapshot previo, tras adquirir el lock de empresa. */
    @Query(value = "SELECT id FROM resumen_diario WHERE ruc_emisor = :ruc AND ticket_sunat = :ticket AND id <> :id FOR UPDATE", nativeQuery = true)
    List<Long> buscarOtrosConTicketActual(@Param("ruc") String ruc, @Param("ticket") String ticket, @Param("id") Long id);

    /** Bloquea sólo la cabecera, sin joins de detalles; requiere transacción activa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResumenDiario r WHERE r.id = :id AND r.rucEmisor = :ruc")
    Optional<ResumenDiario> bloquearPorIdYRuc(@Param("id") Long id, @Param("ruc") String ruc);

    /**
     * Recupera la cabecera junto con sus líneas en una sola operación.
     * Se utiliza para construir/reconstruir el XML SummaryDocuments.
     */
    @EntityGraph(attributePaths = "detalles")
    @Query("""
            SELECT r
            FROM ResumenDiario r
            WHERE r.id = :id
              AND r.rucEmisor = :ruc
            """)
    Optional<ResumenDiario> buscarCompleto(
            @Param("id") Long id,
            @Param("ruc") String ruc
    );

    List<ResumenDiario> findTop100ByRucEmisorOrderByCreatedAtDesc(
            String rucEmisor
    );

    List<ResumenDiario> findByRucEmisorAndFechaDocumentosOrderByCorrelativoDesc(
            String rucEmisor,
            LocalDate fechaDocumentos
    );

    /**
     * Obtiene resúmenes que ya tienen ticket y cuya consulta puede repetirse
     * de forma segura mediante getStatus.
     */
    @Query("""
            SELECT r
            FROM ResumenDiario r
            WHERE r.ticketSunat IS NOT NULL
               AND r.estado IN :estados
              AND NOT EXISTS (
                  SELECT otro.id FROM ResumenDiario otro
                  WHERE otro.rucEmisor = r.rucEmisor
                    AND otro.ticketSunat = r.ticketSunat
                    AND otro.id <> r.id
              )
            ORDER BY r.updatedAt ASC, r.id ASC
            """)
    List<ResumenDiario> buscarPendientesConsulta(
            @Param("estados") Collection<EstadoResumenDiario> estados,
            Pageable pageable
    );

    /**
     * Un resumen CREADO todavía no fue enviado. Si el servidor se reinició
     * después de guardar la BD y antes de ejecutar sendSummary, este query
     * permite recuperarlo sin volver a tomar las boletas.
     */
    @Query("""
            SELECT r
            FROM ResumenDiario r
            WHERE r.estado = org.example.model.EstadoResumenDiario.CREADO
            ORDER BY r.createdAt ASC, r.id ASC
            """)
    List<ResumenDiario> buscarCreadosPendientes(
            Pageable pageable
    );

    /**
     * Toma atómicamente un resumen para ejecutar sendSummary.
     *
     * Sólo CREADO y ERROR_ENVIO pueden reenviarse automáticamente/manual.
     * ERROR_ENVIO_INCIERTO queda bloqueado por seguridad porque SUNAT pudo
     * haber recibido el archivo aunque nuestro servidor no leyera el ticket.
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE resumen_diario

                    SET estado = 'ENVIANDO',
                        intentos_envio = intentos_envio + 1,
                        updated_at = NOW()

                    WHERE id = :id
                      AND ruc_emisor = :ruc
                      AND estado IN ('CREADO', 'ERROR_ENVIO')
                    """,
            nativeQuery = true
    )
    int marcarEnviando(
            @Param("id") Long id,
            @Param("ruc") String ruc
    );

    /**
     * Si el proceso murió después de marcar ENVIANDO y antes de guardar un
     * ticket, ya no podemos saber con certeza si SUNAT recibió el ZIP.
     * Por ello lo convertimos a ERROR_ENVIO_INCIERTO y NO lo reenviamos solo.
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE resumen_diario

                    SET estado = 'ERROR_ENVIO_INCIERTO',
                        mensaje_sunat = CONCAT(
                            COALESCE(mensaje_sunat, ''),
                            CASE
                                WHEN mensaje_sunat IS NULL OR mensaje_sunat = '' THEN ''
                                ELSE ' | '
                            END,
                            'El servidor quedó ENVIANDO demasiado tiempo. No se reintenta automáticamente por riesgo de duplicar el envío.'
                        ),
                        updated_at = NOW()

                    WHERE estado = 'ENVIANDO'
                      AND ticket_sunat IS NULL
                      AND updated_at < :corte
                    """,
            nativeQuery = true
    )
    int marcarEnviosVencidosComoInciertos(
            @Param("corte") LocalDateTime corte
    );
}
