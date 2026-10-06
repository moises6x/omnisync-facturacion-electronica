package org.example.repository;


import org.example.model.SecuenciaResumenDiario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * =============================================================
 * SECUENCIA RESUMEN DIARIO REPOSITORY
 * =============================================================
 *
 * Usa un UPSERT de MySQL para incrementar el correlativo de manera
 * atómica incluso si dos procesos intentan crear un RC al mismo tiempo.
 */
public interface SecuenciaResumenDiarioRepository
        extends JpaRepository<SecuenciaResumenDiario, Long> {

    Optional<SecuenciaResumenDiario>
    findByRucEmisorAndFechaReferencia(
            String rucEmisor,
            LocalDate fechaReferencia
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO secuencia_resumen_diario
                (ruc_emisor, fecha_referencia, ultimo_correlativo, created_at, updated_at)
            VALUES (:ruc, :fecha, 0, NOW(), NOW())
            """, nativeQuery = true)
    int inicializarSiNoExiste(@Param("ruc") String ruc, @Param("fecha") LocalDate fecha);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SecuenciaResumenDiario s WHERE s.rucEmisor=:ruc AND s.fechaReferencia=:fecha")
    Optional<SecuenciaResumenDiario> obtenerBloqueada(@Param("ruc") String ruc, @Param("fecha") LocalDate fecha);

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(
            value = """
                    INSERT INTO secuencia_resumen_diario (
                        ruc_emisor,
                        fecha_referencia,
                        ultimo_correlativo,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :ruc,
                        :fecha,
                        1,
                        NOW(),
                        NOW()
                    )

                    ON DUPLICATE KEY UPDATE
                        ultimo_correlativo = ultimo_correlativo + 1,
                        updated_at = NOW()
                    """,
            nativeQuery = true
    )
    int incrementar(
            @Param("ruc") String ruc,
            @Param("fecha") LocalDate fecha
    );
}
