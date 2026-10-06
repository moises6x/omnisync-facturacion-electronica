package org.example.admin.model;

import jakarta.persistence.*;
import lombok.*;

import org.example.model.Empresa;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pago_empresa",
        uniqueConstraints = @UniqueConstraint(name="uk_pago_empresa_operacion",columnNames={"empresa_id","idempotency_hash"}),
        indexes = {

                @Index(
                        name = "idx_pago_empresa",
                        columnList = "empresa_id"
                ),

                @Index(
                        name = "idx_pago_fecha",
                        columnList = "pagado_en"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoEmpresa {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(name="idempotency_hash",length=64)
    private String idempotencyHash;
    @Column(name="request_hash",length=64)
    private String requestHash;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "empresa_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_pago_empresa"
            )
    )
    private Empresa empresa;


    /*
     * Periodo que está pagando.
     */
    @Column(
            name = "periodo_inicio",
            nullable = false
    )
    private LocalDateTime periodoInicio;


    @Column(
            name = "periodo_fin",
            nullable = false
    )
    private LocalDateTime periodoFin;


    /*
     * Fecha en la que debía renovar.
     *
     * Nos permitirá calcular:
     * - pagó puntual
     * - pagó tarde
     */
    @Column(
            name = "fecha_vencimiento",
            nullable = false
    )
    private LocalDateTime fechaVencimiento;


    @Column(
            name = "pagado_en",
            nullable = false
    )
    private LocalDateTime pagadoEn;


    @Column(
            precision = 12,
            scale = 2,
            nullable = false
    )
    private BigDecimal monto;


    @Column(
            length = 500
    )
    private String observacion;


    @Column(
            name = "registrado_por_usuario_id"
    )
    private Long registradoPorUsuarioId;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;


    @PrePersist
    public void prePersist() {

        if (
                pagadoEn == null
        ) {

            pagadoEn =
                    LocalDateTime.now();
        }


        if (
                createdAt == null
        ) {

            createdAt =
                    LocalDateTime.now();
        }


        if (
                monto == null
        ) {

            monto =
                    BigDecimal.ZERO;
        }
    }


    public boolean pagoPuntual() {

        return !pagadoEn
                .isAfter(
                        fechaVencimiento
                );
    }
}
