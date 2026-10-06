package org.example.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "evento_auditoria",
        indexes = {
                @Index(
                        name = "idx_auditoria_fecha",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_auditoria_usuario",
                        columnList = "usuario_id"
                ),
                @Index(
                        name = "idx_auditoria_empresa",
                        columnList = "empresa_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(
            name = "ruc_empresa",
            length = 11
    )
    private String rucEmpresa;

    @Column(
            nullable = false,
            length = 100
    )
    private String accion;

    @Column(
            nullable = false,
            length = 20
    )
    private String resultado;

    @Column(length = 64)
    private String ip;

    @Column(
            name = "user_agent",
            length = 500
    )
    private String userAgent;

    @Column(
            length = 1000
    )
    private String detalle;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
