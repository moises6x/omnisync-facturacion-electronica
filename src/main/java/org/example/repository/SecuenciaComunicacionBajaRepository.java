package org.example.repository;

import org.example.model.SecuenciaComunicacionBaja;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface SecuenciaComunicacionBajaRepository extends JpaRepository<SecuenciaComunicacionBaja,Long> {
    Optional<SecuenciaComunicacionBaja> findByRucEmisorAndFechaReferencia(String rucEmisor, LocalDate fechaReferencia);
    @Modifying(flushAutomatically=true,clearAutomatically=true)
    @Query(value="""
        INSERT INTO secuencia_comunicacion_baja(ruc_emisor,fecha_referencia,ultimo_correlativo,created_at,updated_at)
        VALUES(:ruc,:fecha,1,NOW(),NOW())
        ON DUPLICATE KEY UPDATE ultimo_correlativo=ultimo_correlativo+1,updated_at=NOW()
        """,nativeQuery=true)
    int incrementar(@Param("ruc") String ruc,@Param("fecha") LocalDate fecha);
}
