package org.example.admin.repository;

import org.example.admin.model.SuscripcionEmpresa;

import org.example.model.EstadoEmpresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface SuscripcionEmpresaRepository
        extends JpaRepository<
        SuscripcionEmpresa,
        Long
        > {

    @Query("SELECT s.empresa.id FROM SuscripcionEmpresa s WHERE s.empresa.estadoPlataforma=:estado AND s.venceEn<=:hasta ORDER BY s.empresa.id")
    List<Long> buscarEmpresasParaRevision(@Param("estado") EstadoEmpresa estado,@Param("hasta") LocalDateTime hasta);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SuscripcionEmpresa s WHERE s.empresa.id=:empresaId")
    Optional<SuscripcionEmpresa> bloquearPorEmpresa(@Param("empresaId") Long empresaId);

    Optional<SuscripcionEmpresa>
    findByEmpresaId(
            Long empresaId
    );


    List<SuscripcionEmpresa>
    findTop50ByVenceEnAfterOrderByVenceEnAsc(
            LocalDateTime desde
    );


    List<SuscripcionEmpresa>
    findAllByVenceEnBetweenOrderByVenceEnAsc(

            LocalDateTime desde,

            LocalDateTime hasta
    );


    // =========================================================
    // EMPRESAS ACTIVAS PRÓXIMAS A VENCER O VENCIDAS
    // =========================================================

    @Query("""
            SELECT s
            FROM SuscripcionEmpresa s

            JOIN FETCH s.empresa e

            WHERE e.estadoPlataforma = :estado

              AND s.venceEn <= :hasta

            ORDER BY s.venceEn ASC
            """)
    List<SuscripcionEmpresa>
    buscarParaRevision(

            @Param("estado")
            EstadoEmpresa estado,

            @Param("hasta")
            LocalDateTime hasta
    );
}
