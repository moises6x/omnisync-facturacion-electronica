package org.example.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "padron_ruc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PadronRuc {

    @Id
    @Column(name = "ruc", length = 11, nullable = false)
    private String ruc;

    @Column(name = "razon_social", length = 250, nullable = false)
    private String razonSocial;

    @Column(name = "estado", length = 40)
    private String estado;

    @Column(name = "condicion", length = 40)
    private String condicion;

    @Column(name = "ubigeo", length = 10)
    private String ubigeo;

    @Column(name = "tipo_via", length = 40)
    private String tipoVia;

    @Column(name = "nombre_via", length = 200)
    private String nombreVia;

    @Column(name = "codigo_zona", length = 40)
    private String codigoZona;

    @Column(name = "tipo_zona", length = 40)
    private String tipoZona;

    @Column(name = "numero", length = 40)
    private String numero;

    @Column(name = "interior", length = 40)
    private String interior;

    @Column(name = "lote", length = 40)
    private String lote;

    @Column(name = "departamento", length = 40)
    private String departamento;

    @Column(name = "manzana", length = 40)
    private String manzana;

    @Column(name = "kilometro", length = 40)
    private String kilometro;
}
