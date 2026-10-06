package org.example.repository;

import org.example.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface EmpresaRepository
        extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findByRuc(
            String ruc
    );

    /** Serializa la asociación RC/ticket de una empresa, sólo después de SOAP. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Empresa e WHERE e.ruc = :ruc")
    Optional<Empresa> bloquearPorRuc(@Param("ruc") String ruc);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Empresa e WHERE e.id=:id")
    Optional<Empresa> bloquearPorId(@Param("id") Long id);

    boolean existsByRuc(
            String ruc
    );

    List<Empresa>
    findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT e.ruc AS ruc,
                   e.razonSocial AS razonSocial,
                   e.certificadoVigenteHasta AS certificadoVigenteHasta
            FROM Empresa e
            WHERE e.certificadoVigenteHasta IS NOT NULL
            """)
    List<EmpresaCertificadoProjection> listarCertificadosParaMonitoreo();
}
