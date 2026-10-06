package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nota_venta",
        indexes = {
                @Index(name = "idx_nv_tienda_fecha", columnList = "tienda_id, fecha_emision")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tienda_id", nullable = false)
    private Long tiendaId;

    @Column(name = "ruc_emisor", length = 11, nullable = false)
    private String rucEmisor;

    @Column(name = "codigo_ticket", length = 20, nullable = false) // Ej: NV01-00000001
    private String codigoTicket;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "monto_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @Column(name = "observacion")
    private String observacion; // Ej: "Venta rápida de S/ 10 sin comprobante"

    @PrePersist
    public void prePersist() {
        this.fechaEmision = LocalDateTime.now();
    }
}