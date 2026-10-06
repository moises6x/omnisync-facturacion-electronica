package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "secuencia_correlativo",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_serie_emisor", columnNames = {"ruc_emisor", "tipo_comprobante", "serie"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecuenciaCorrelativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ruc_emisor", length = 11, nullable = false)
    private String rucEmisor;

    @Column(name = "tipo_comprobante", length = 2, nullable = false)
    private String tipoComprobante;

    @Column(length = 4, nullable = false)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    private Integer ultimoNumero;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.ultimoNumero == null) this.ultimoNumero = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}