package org.example.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.example.model.Empresa;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
        name = "usuario",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_usuario_email",
                        columnNames = "email"
                )
        },
        indexes = {
                @Index(
                        name = "idx_usuario_empresa",
                        columnList = "empresa_id"
                ),
                @Index(
                        name = "idx_usuario_estado",
                        columnList = "estado"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * NULL solamente para SUPER_ADMIN.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "empresa_id",
            foreignKey = @ForeignKey(
                    name = "fk_usuario_empresa"
            )
    )
    private Empresa empresa;

    @Column(
            nullable = false,
            length = 150
    )
    private String nombre;

    @Column(
            nullable = false,
            length = 200
    )
    private String email;

    /*
     * NUNCA almacenar contraseña en texto plano.
     */
    @JsonIgnore
    @Column(
            name = "password_hash",
            nullable = false,
            length = 100
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private RolUsuario rol;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private EstadoUsuario estado;

    /*
     * Principalmente utilizado para TRABAJADOR.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "usuario_permiso",
            joinColumns = @JoinColumn(
                    name = "usuario_id"
            ),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_usuario_permiso",
                    columnNames = {
                            "usuario_id",
                            "permiso"
                    }
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(
            name = "permiso",
            length = 50,
            nullable = false
    )
    @Builder.Default
    private Set<PermisoUsuario> permisos =
            new HashSet<>();

    /*
     * Protección contra fuerza bruta.
     */
    @Column(
            name = "intentos_fallidos",
            nullable = false
    )
    @Builder.Default
    private int intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    /*
     * Permite invalidar TODOS los JWT existentes
     * de un usuario sin tener que almacenarlos.
     */
    @Column(
            name = "token_version",
            nullable = false
    )
    @Builder.Default
    private long tokenVersion = 0L;

    /*
     * Para contraseñas temporales creadas por un administrador.
     */
    @Column(
            name = "debe_cambiar_password",
            nullable = false
    )
    @Builder.Default
    private boolean debeCambiarPassword = false;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

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

        createdAt = ahora;
        updatedAt = ahora;

        normalizar();

        if (estado == null) {
            estado = EstadoUsuario.ACTIVO;
        }
    }

    @PreUpdate
    public void preUpdate() {

        updatedAt =
                LocalDateTime.now();

        normalizar();
    }

    private void normalizar() {

        if (email != null) {

            email = email
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }

        if (nombre != null) {
            nombre = nombre.trim();
        }
    }
}