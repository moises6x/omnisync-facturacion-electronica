package org.example.auth.repository;

import org.example.auth.model.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    // =========================================================
    // BUSCAR REFRESH TOKEN POR HASH
    // =========================================================

    Optional<RefreshToken> findByTokenHash(
            String tokenHash
    );

    /** Serializa consumo/logout del mismo token; el llamador mantiene la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :hash")
    Optional<RefreshToken> bloquearPorTokenHash(@Param("hash") String hash);

    @Query("SELECT r.usuario.id FROM RefreshToken r WHERE r.tokenHash=:hash")
    Optional<Long> buscarUsuarioIdPorHash(@Param("hash") String hash);


    // =========================================================
    // REVOCAR TODAS LAS SESIONES ACTIVAS DE UN USUARIO
    // =========================================================

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE RefreshToken r
            SET r.revocadoEn = :ahora
            WHERE r.usuario.id = :usuarioId
              AND r.revocadoEn IS NULL
            """)
    int revocarTodosActivos(

            @Param("usuarioId")
            Long usuarioId,

            @Param("ahora")
            LocalDateTime ahora
    );


    // =========================================================
    // ELIMINAR REFRESH TOKENS EXPIRADOS
    // =========================================================
    //
    // IMPORTANTE:
    //
    // NO elimina simplemente por estar revocado.
    //
    // Un token revocado pero todavía no expirado se conserva
    // temporalmente para poder detectar una posible
    // reutilización de un token robado.
    //
    // Solamente desaparece cuando ya terminó su vida útil.
    // =========================================================

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            DELETE FROM RefreshToken r
            WHERE r.expiraEn <= :ahora
            """)
    int eliminarExpirados(

            @Param("ahora")
            LocalDateTime ahora
    );
}
