package org.example.repository;

import org.example.model.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

public interface ComunicacionBajaRepository extends JpaRepository<ComunicacionBaja,Long> {
    Optional<ComunicacionBaja> findByIdAndRucEmisor(Long id,String rucEmisor);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ComunicacionBaja c where c.id=:id and c.rucEmisor=:ruc")
    Optional<ComunicacionBaja> bloquearPorIdYRuc(@Param("id") Long id,@Param("ruc") String ruc);
    @EntityGraph(attributePaths="detalles")
    @Query("select c from ComunicacionBaja c where c.id=:id and c.rucEmisor=:ruc")
    Optional<ComunicacionBaja> buscarCompleto(@Param("id") Long id,@Param("ruc") String ruc);
    List<ComunicacionBaja> findByRucEmisorOrderByIdDesc(String rucEmisor);
    boolean existsByDetalles_ComprobanteId(Long comprobanteId);
    boolean existsByRucEmisorAndTicketSunatAndIdNot(String rucEmisor, String ticketSunat, Long id);
    @Query(value="SELECT id FROM comunicacion_baja WHERE ruc_emisor=:ruc AND ticket_sunat=:ticket AND id<>:id FOR UPDATE",nativeQuery=true)
    List<Long> buscarOtrosConTicketActual(@Param("ruc") String ruc,@Param("ticket") String ticket,@Param("id") Long id);
    @Query("select c from ComunicacionBaja c where c.estado in :estados order by c.id")
    List<ComunicacionBaja> buscarPendientes(@Param("estados") Collection<EstadoComunicacionBaja> estados, Pageable pageable);

    @Query("select c from ComunicacionBaja c where c.estado=org.example.model.EstadoComunicacionBaja.CREADO and c.ticketSunat is null order by c.id")
    List<ComunicacionBaja> buscarCreadasSinTicket(Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="""
            UPDATE comunicacion_baja
            SET estado='ERROR_ENVIO_INCIERTO',
                mensaje_sunat='Envío RA interrumpido o vencido. Requiere conciliación; no se reenvía automáticamente.',
                updated_at=NOW()
            WHERE estado='ENVIANDO' AND ticket_sunat IS NULL AND updated_at < :corte
            """,nativeQuery=true)
    int marcarEnviosVencidosComoInciertos(@Param("corte") LocalDateTime corte);
}
