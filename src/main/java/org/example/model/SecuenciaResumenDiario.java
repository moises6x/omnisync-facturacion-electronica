
package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * =============================================================
 * SECUENCIA DE RESUMEN DIARIO
 * =============================================================
 *
 * Conserva el último correlativo utilizado por:
 *
 * - empresa
 * - fecha de generación del identificador; también conserva una marca máxima
 *   por fecha de documentos para respetar la unicidad histórica sin cambiar RC previos.
 *
 * Ejemplo:
 *
 * 20123456789 + 2026-09-19
 *
 * puede generar:
 *
 * RC-20260919-1
 * RC-20260919-2
 * RC-20260919-3
 *
 * Esto es necesario porque SUNAT admite como máximo 500 líneas por
 * archivo y una empresa puede necesitar varios resúmenes del mismo día.
 */
@Entity
@Table(
        name = "secuencia_resumen_diario",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_secuencia_resumen_empresa_fecha",
                        columnNames = {
                                "ruc_emisor",
                                "fecha_referencia"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_secuencia_resumen_empresa_fecha",
                        columnList = "ruc_emisor,fecha_referencia"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecuenciaResumenDiario {

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
            name = "fecha_referencia",
            nullable = false
    )
    private LocalDate fechaReferencia;

    @Column(
            name = "ultimo_correlativo",
            nullable = false
    )
    private Integer ultimoCorrelativo;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        createdAt = ahora;
        updatedAt = ahora;

        if (ultimoCorrelativo == null) {
            ultimoCorrelativo = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
