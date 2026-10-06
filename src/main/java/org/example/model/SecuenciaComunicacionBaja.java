package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="secuencia_comunicacion_baja",
       uniqueConstraints=@UniqueConstraint(name="uk_secuencia_baja_empresa_fecha", columnNames={"ruc_emisor","fecha_referencia"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecuenciaComunicacionBaja {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="ruc_emisor",length=11,nullable=false) private String rucEmisor;
    @Column(name="fecha_referencia",nullable=false) private LocalDate fechaReferencia;
    @Column(name="ultimo_correlativo",nullable=false) private Integer ultimoCorrelativo;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @PrePersist void prePersist(){ var n=LocalDateTime.now(); createdAt=n; updatedAt=n; if(ultimoCorrelativo==null) ultimoCorrelativo=0; }
    @PreUpdate void preUpdate(){ updatedAt=LocalDateTime.now(); }
}
