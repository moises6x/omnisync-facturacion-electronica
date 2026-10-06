package org.example.model;



import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "detalle_comprobante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleComprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detalle_comprobante"))
    private Comprobante comprobante;

    @Column(name = "item_orden", nullable = false)
    private Integer itemOrden;

    @Column(name = "codigo_producto", length = 50)
    private String codigoProducto;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(name = "unidad_medida", length = 5, nullable = false)
    private String unidadMedida;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal cantidad;

    @Column(name = "valor_unitario", precision = 22, scale = 10, nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "precio_unitario", precision = 22, scale = 10, nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "igv_item", precision = 14, scale = 2, nullable = false)
    private BigDecimal igvItem;

    @Column(name = "porcentaje_igv", precision = 5, scale = 2)
    private BigDecimal porcentajeIgv;

    @Column(name = "tipo_afectacion_igv", length = 2, nullable = false)
    private String tipoAfectacionIgv;

    @Column(name = "monto_total_item", precision = 14, scale = 2, nullable = false)
    private BigDecimal montoTotalItem;
}
