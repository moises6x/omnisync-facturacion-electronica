package org.example.auth.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refresh_token_hash",
                        columnNames = "token_hash"
                )
        },
        indexes = {
                @Index(
                        name = "idx_refresh_usuario",
                        columnList = "usuario_id"
                ),
                @Index(
                        name = "idx_refresh_expiracion",
                        columnList = "expira_en"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "usuario_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_refresh_usuario"
            )
    )
    private Usuario usuario;

    /*
     * El refresh token real JAMÁS se guarda.
     *
     * Solamente SHA-256(token).
     */
    @Column(
            name = "token_hash",
            nullable = false,
            length = 64
    )
    private String tokenHash;

    @Column(
            name = "family_id",
            nullable = false,
            length = 36
    )
    private String familyId;

    /*
     * Permite detectar reutilización de refresh tokens.
     */
    @Column(
            name = "reemplazado_por_hash",
            length = 64
    )
    private String reemplazadoPorHash;

    @Column(
            name = "creado_en",
            nullable = false
    )
    private LocalDateTime creadoEn;

    @Column(
            name = "expira_en",
            nullable = false
    )
    private LocalDateTime expiraEn;

    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;

    @Column(length = 64)
    private String ip;

    @Column(
            name = "user_agent",
            length = 500
    )
    private String userAgent;

    public boolean estaExpirado() {

        return LocalDateTime.now()
                .isAfter(expiraEn);
    }

    public boolean estaRevocado() {

        return revocadoEn != null;
    }
}