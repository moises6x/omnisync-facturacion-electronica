package org.example.admin.repository;

import org.example.admin.model.AnuncioEmpresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnuncioEmpresaRepository
        extends JpaRepository<AnuncioEmpresa, Long> {


    List<AnuncioEmpresa>
    findAllByOrderByCreatedAtDesc();


    boolean existsByCodigoSistema(
            String codigoSistema
    );

    @Query(value="SELECT id FROM anuncio_empresa WHERE codigo_sistema=:codigo FOR UPDATE",nativeQuery=true)
    List<Long> buscarCodigoActual(@Param("codigo") String codigo);
    default boolean existeCodigoActual(String codigo){return !buscarCodigoActual(codigo).isEmpty();}


    // =========================================================
    // ANUNCIOS ACTIVOS DE UNA EMPRESA
    // =========================================================

    @Query("""
            SELECT a
            FROM AnuncioEmpresa a
            WHERE a.activo = true
              AND a.inicioEn <= :ahora
              AND (
                    a.finEn IS NULL
                    OR
                    a.finEn >= :ahora
              )
              AND (
                    a.empresa IS NULL
                    OR
                    a.empresa.id = :empresaId
              )
            ORDER BY
                a.pantallaCompleta DESC,
                a.inicioEn DESC,
                a.id DESC
            """)
    List<AnuncioEmpresa>
    buscarActivos(

            @Param("empresaId")
            Long empresaId,

            @Param("ahora")
            LocalDateTime ahora
    );


    // =========================================================
    // DESACTIVAR AVISOS PREVIOS AL SUSPENDER
    //
    // Conservamos el aviso SUSCRIPCION_SUSPENDIDA si existiera.
    // =========================================================

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE AnuncioEmpresa a

            SET
                a.activo = false,
                a.updatedAt = :ahora

            WHERE a.empresa.id = :empresaId

              AND a.activo = true

              AND a.codigoSistema IS NOT NULL

              AND a.codigoSistema LIKE 'SUSCRIPCION_%'

              AND a.codigoSistema NOT LIKE 'SUSCRIPCION_SUSPENDIDA%'
            """)
    int desactivarAvisosPreviosSuscripcion(

            @Param("empresaId")
            Long empresaId,

            @Param("ahora")
            LocalDateTime ahora
    );


    // =========================================================
    // DESACTIVAR TODOS LOS AVISOS DE SUSCRIPCIÓN
    //
    // Se usa cuando la empresa paga / renueva.
    // =========================================================

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE AnuncioEmpresa a

            SET
                a.activo = false,
                a.updatedAt = :ahora

            WHERE a.empresa.id = :empresaId

              AND a.activo = true

              AND a.codigoSistema IS NOT NULL

              AND a.codigoSistema LIKE 'SUSCRIPCION_%'
            """)
    int desactivarTodosAvisosSuscripcion(

            @Param("empresaId")
            Long empresaId,

            @Param("ahora")
            LocalDateTime ahora
    );
}
