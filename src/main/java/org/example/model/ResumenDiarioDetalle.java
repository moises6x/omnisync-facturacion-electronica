
package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * =============================================================
 * DETALLE DEL RESUMEN DIARIO
 * =============================================================
 *
 * Guarda una fotografía de cada documento exactamente como fue tomado
 * para construir el SummaryDocuments.
 *
 * No dependemos solamente de leer nuevamente Comprobante porque un
 * documento tributario enviado debe quedar auditable aun si posteriormente
 * se agregan campos o cambian datos operativos de la aplicación.
 */
@Entity
@Table(
        name = "resumen_diario_detalle",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resumen_detalle_linea",
                        columnNames = {
                                "resumen_id",
                                "numero_linea"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_resumen_detalle_comprobante",
                        columnNames = {
                                "resumen_id",
                                "comprobante_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_resumen_detalle_resumen",
                        columnList = "resumen_id"
                ),
                @Index(
                        name = "idx_resumen_detalle_comprobante",
                        columnList = "comprobante_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenDiarioDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "resumen_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_resumen_detalle_resumen"
            )
    )
    private ResumenDiario resumen;

    /** ID interno del Comprobante que originó esta línea. */
    @Column(
            name = "comprobante_id",
            nullable = false
    )
    private Long comprobanteId;

    @Column(
            name = "numero_linea",
            nullable = false
    )
    private Integer numeroLinea;

    // =========================================================
    // DOCUMENTO
    // =========================================================

    @Column(
            name = "tipo_comprobante",
            length = 2,
            nullable = false
    )
    private String tipoComprobante;

    @Column(
            length = 4,
            nullable = false
    )
    private String serie;

    @Column(nullable = false)
    private Integer numero;

    // =========================================================
    // CLIENTE
    // =========================================================

    @Column(
            name = "tipo_doc_cliente",
            length = 1,
            nullable = false
    )
    private String tipoDocCliente;

    @Column(
            name = "num_doc_cliente",
            length = 15,
            nullable = false
    )
    private String numDocCliente;

    // =========================================================
    // IMPORTES
    // =========================================================

    @Column(
            name = "op_gravada",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal opGravada;

    @Column(
            name = "op_exonerada",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal opExonerada;

    @Column(
            name = "op_inafecta",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal opInafecta;

    @Column(
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal igv;

    @Column(
            name = "monto_total",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal montoTotal;

    @Column(
            length = 3,
            nullable = false
    )
    @Builder.Default
    private String moneda = "PEN";

    // =========================================================
    // DOCUMENTO AFECTADO - NOTA DE CRÉDITO
    // =========================================================

    @Column(
            name = "tipo_comprobante_afectado",
            length = 2
    )
    private String tipoComprobanteAfectado;

    @Column(
            name = "serie_comprobante_afectado",
            length = 4
    )
    private String serieComprobanteAfectado;

    @Column(name = "numero_comprobante_afectado")
    private Integer numeroComprobanteAfectado;

    // =========================================================
    // ESTADO DE LA LÍNEA EN SUMMARYDOCUMENTS
    // =========================================================
    //
    // 1 = Adicionar
    // 2 = Modificar
    // 3 = Anulado
    //
    // Esta primera implementación crea nuevas líneas con código 1.
    // Las modificaciones/bajas se implementarán expresamente y no se
    // inferirán automáticamente.
    // =========================================================

    @Column(
            name = "codigo_estado",
            length = 1,
            nullable = false
    )
    @Builder.Default
    private String codigoEstado = "1";

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

        if (moneda == null || moneda.isBlank()) {
            moneda = "PEN";
        }

        if (codigoEstado == null || codigoEstado.isBlank()) {
            codigoEstado = "1";
        }
    }
}