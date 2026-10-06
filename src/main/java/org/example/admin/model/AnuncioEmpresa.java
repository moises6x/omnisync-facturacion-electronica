package org.example.admin.model;

import jakarta.persistence.*;
import lombok.*;

import org.example.model.Empresa;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "anuncio_empresa",

        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_anuncio_codigo_sistema",
                        columnNames = "codigo_sistema"
                )
        },

        indexes = {

                @Index(
                        name = "idx_anuncio_empresa",
                        columnList = "empresa_id"
                ),

                @Index(
                        name = "idx_anuncio_inicio",
                        columnList = "inicio_en"
                ),

                @Index(
                        name = "idx_anuncio_fin",
                        columnList = "fin_en"
                ),

                @Index(
                        name = "idx_anuncio_activo",
                        columnList = "activo"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnuncioEmpresa {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    /*
     * NULL = anuncio global.
     *
     * Con empresa = anuncio solamente para
     * esa empresa.
     */
    @ManyToOne(
            fetch = FetchType.LAZY
    )
    @JoinColumn(
            name = "empresa_id",
            foreignKey = @ForeignKey(
                    name = "fk_anuncio_empresa"
            )
    )
    private Empresa empresa;


    /*
     * Se utiliza para avisos creados automáticamente
     * por el backend.
     *
     * Los anuncios manuales pueden tenerlo NULL.
     */
    @Column(
            name = "codigo_sistema",
            length = 150
    )
    private String codigoSistema;


    @Column(
            nullable = false,
            length = 150
    )
    private String titulo;


    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String mensaje;


    @Enumerated(
            EnumType.STRING
    )
    @Column(
            nullable = false,
            length = 30
    )
    private TipoAnuncio tipo;


    @Column(
            name = "inicio_en",
            nullable = false
    )
    private LocalDateTime inicioEn;


    @Column(
            name = "fin_en"
    )
    private LocalDateTime finEn;


    @Column(
            name = "pantalla_completa",
            nullable = false
    )
    @Builder.Default
    private boolean pantallaCompleta =
            false;


    @Column(
            nullable = false
    )
    @Builder.Default
    private boolean activo =
            true;


    @Column(
            name = "creado_por_usuario_id"
    )
    private Long creadoPorUsuarioId;


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


        if (
                tipo == null
        ) {

            tipo =
                    TipoAnuncio.INFO;
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
}