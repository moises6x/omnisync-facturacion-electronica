package org.example.admin.repository;

import org.example.admin.model.PagoEmpresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PagoEmpresaRepository
        extends JpaRepository<
        PagoEmpresa,
        Long
        > {

    @Query(value="SELECT request_hash FROM pago_empresa WHERE empresa_id=:empresaId AND idempotency_hash=:hash FOR UPDATE",nativeQuery=true)
    java.util.Optional<String> buscarOperacionActual(@Param("empresaId") Long empresaId,@Param("hash") String hash);

    List<PagoEmpresa>
    findAllByEmpresaIdOrderByPagadoEnDesc(
            Long empresaId
    );


    long countByPagadoEnBetween(
            LocalDateTime desde,
            LocalDateTime hasta
    );


    @Query("""
            SELECT COALESCE(
                SUM(p.monto),
                0
            )
            FROM PagoEmpresa p
            WHERE p.pagadoEn >= :desde
              AND p.pagadoEn < :hasta
            """)
    BigDecimal sumarIngresos(

            @Param("desde")
            LocalDateTime desde,

            @Param("hasta")
            LocalDateTime hasta
    );


    @Query("""
            SELECT COUNT(p)
            FROM PagoEmpresa p
            WHERE p.pagadoEn >= :desde
              AND p.pagadoEn < :hasta
              AND p.pagadoEn <= p.fechaVencimiento
            """)
    long contarPagosPuntuales(

            @Param("desde")
            LocalDateTime desde,

            @Param("hasta")
            LocalDateTime hasta
    );
}
