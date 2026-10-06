package org.example.repository;

import jakarta.persistence.LockModeType;
import org.example.model.SolicitudEmision;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SolicitudEmisionRepository
        extends JpaRepository<SolicitudEmision, Long> {

    /**
     * INSERT IGNORE evita que dos peticiones simultáneas
     * creen la misma clave.
     */
    @Modifying
    @Query(
            value = """
                    INSERT IGNORE INTO solicitud_emision
                    (
                        ruc_emisor,
                        idempotency_key,
                        request_hash,
                        estado,
                        created_at,
                        updated_at
                    )
                    VALUES
                    (
                        :ruc,
                        :idempotencyKey,
                        :requestHash,
                        'PROCESANDO',
                        NOW(),
                        NOW()
                    )
                    """,
            nativeQuery = true
    )
    int inicializarSiNoExiste(

            @Param("ruc")
            String ruc,

            @Param("idempotencyKey")
            String idempotencyKey,

            @Param("requestHash")
            String requestHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT s
           FROM SolicitudEmision s
           WHERE s.rucEmisor = :ruc
             AND s.idempotencyKey = :key
           """)
    Optional<SolicitudEmision> obtenerBloqueada(

            @Param("ruc")
            String ruc,

            @Param("key")
            String key
    );
}