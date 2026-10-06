package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="comunicacion_baja_detalle",
       uniqueConstraints=@UniqueConstraint(name="uk_baja_detalle_comprobante",columnNames={"comprobante_id"}),
       indexes=@Index(name="idx_baja_detalle_comunicacion",columnList="comunicacion_baja_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComunicacionBajaDetalle {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="comunicacion_baja_id",nullable=false) private ComunicacionBaja comunicacion;
    @Column(name="comprobante_id",nullable=false) private Long comprobanteId;
    @Column(name="numero_linea",nullable=false) private Integer numeroLinea;
    @Column(name="tipo_comprobante",length=2,nullable=false) private String tipoComprobante;
    @Column(length=4,nullable=false) private String serie;
    @Column(nullable=false) private Integer numero;
    @Column(length=500,nullable=false) private String motivo;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @PrePersist void prePersist(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
