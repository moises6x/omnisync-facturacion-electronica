package org.example.admin.model;

import jakarta.persistence.*;
import lombok.*;

import org.example.model.Empresa;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "suscripcion_empresa",
        uniqueConstraints = {

                @UniqueConstraint(
                        name = "uk_suscripcion_empresa",
                        columnNames = "empresa_id"
                )
        },
        indexes = {

                @Index(
                        name = "idx_suscripcion_vence",
                        columnList = "vence_en"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionEmpresa {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "empresa_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(
                    name = "fk_suscripcion_empresa"
            )
    )
    private Empresa empresa;


    @Column(
            name = "inicio_en",
            nullable = false
    )
    private LocalDateTime inicioEn;


    @Column(
            name = "vence_en",
            nullable = false
    )
    private LocalDateTime venceEn;


    /*
     * Si está activo, el backend puede suspender
     * automáticamente la empresa al vencer.
     */
    @Column(
            name = "suspension_automatica",
            nullable = false
    )
    @Builder.Default
    private boolean suspensionAutomatica =
            true;


    /*
     * Días que puede seguir funcionando después
     * del vencimiento.
     */
    @Column(
            name = "dias_gracia",
            nullable = false
    )
    @Builder.Default
    private int diasGracia =
            0;


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

        LocalDateTime ahora =
                LocalDateTime.now();


        if (
                inicioEn == null
        ) {

            inicioEn =
                    ahora;
        }


        createdAt =
                ahora;


        updatedAt =
                ahora;
    }


    @PreUpdate
    public void preUpdate() {

        updatedAt =
                LocalDateTime.now();
    }


    public LocalDateTime limiteConGracia() {

        return venceEn
                .plusDays(
                        diasGracia
                );
    }


    public boolean vencida() {

        return LocalDateTime
                .now()
                .isAfter(
                        limiteConGracia()
                );
    }
}