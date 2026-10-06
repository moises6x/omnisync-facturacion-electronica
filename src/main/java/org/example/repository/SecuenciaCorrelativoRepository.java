package org.example.repository;

import jakarta.persistence.LockModeType;
import org.example.model.SecuenciaCorrelativo;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SecuenciaCorrelativoRepository
        extends JpaRepository<SecuenciaCorrelativo, Long> {

    /*
     * Esto elimina la carrera que ocurría
     * cuando todavía NO existía la secuencia.
     */
    @Modifying
    @Query(
            value = """
                    INSERT IGNORE INTO secuencia_correlativo
                    (
                        ruc_emisor,
                        tipo_comprobante,
                        serie,
                        ultimo_numero,
                        created_at,
                        updated_at
                    )
                    VALUES
                    (
                        :ruc,
                        :tipo,
                        :serie,
                        0,
                        NOW(),
                        NOW()
                    )
                    """,
            nativeQuery = true
    )
    int inicializarSiNoExiste(

            @Param("ruc")
            String ruc,

            @Param("tipo")
            String tipo,

            @Param("serie")
            String serie
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT s
           FROM SecuenciaCorrelativo s
           WHERE s.rucEmisor = :ruc
             AND s.tipoComprobante = :tipo
             AND s.serie = :serie
           """)
    Optional<SecuenciaCorrelativo>
    obtenerSecuenciaBloqueada(

            @Param("ruc")
            String ruc,

            @Param("tipo")
            String tipo,

            @Param("serie")
            String serie
    );
}