package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "solicitud_emision",

        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_empresa",
                        columnNames = {
                                "ruc_emisor",
                                "idempotency_key"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudEmision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "ruc_emisor",
            length = 11,
            nullable = false
    )
    private String rucEmisor;

    @Column(
            name = "idempotency_key",
            length = 100,
            nullable = false
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            length = 64,
            nullable = false
    )
    private String requestHash;

    @Column(name = "comprobante_id")
    private Long comprobanteId;

    @Column(
            length = 30,
            nullable = false
    )
    private String estado;

    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        LocalDateTime ahora =
                LocalDateTime.now();

        createdAt = ahora;
        updatedAt = ahora;

        if (estado == null)
            estado = "PROCESANDO";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt =
                LocalDateTime.now();
    }
}
