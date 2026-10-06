package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "empresa",
        uniqueConstraints = {

                @UniqueConstraint(
                        name = "uk_empresa_ruc",
                        columnNames = "ruc"
                ),

                @UniqueConstraint(
                        name = "uk_empresa_tenant",
                        columnNames = "tenant_id"
                )
        },
        indexes = {

                @Index(
                        name = "idx_empresa_estado",
                        columnList = "estado_plataforma"
                ),

                @Index(
                        name = "idx_empresa_created",
                        columnList = "created_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empresa {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    // =========================================================
    // TENANT
    // =========================================================

    @Column(
            name = "tenant_id",
            length = 36
    )
    private String tenantId;


    @Column(
            length = 11,
            nullable = false
    )
    private String ruc;


    @Column(
            name = "razon_social",
            nullable = false,
            length = 250
    )
    private String razonSocial;


    // =========================================================
    // ESTADO DE LA PLATAFORMA
    // =========================================================

    @Enumerated(
            EnumType.STRING
    )
    @Column(
            name = "estado_plataforma",
            length = 20
    )
    @Builder.Default
    private EstadoEmpresa estadoPlataforma =
            EstadoEmpresa.ACTIVA;


    @Column(
            name = "suspendida_en"
    )
    private LocalDateTime suspendidaEn;


    @Column(
            name = "motivo_suspension",
            length = 500
    )
    private String motivoSuspension;


    // =========================================================
    // CONFIGURACIÓN TRIBUTARIA DEL TENANT
    // =========================================================
    //
    // La tasa IGV NO la decide el frontend. Cada empresa conserva
    // su propia configuración tributaria dentro del SaaS.
    //
    // Los comprobantes almacenarán además una copia histórica de
    // la tasa usada al emitirlos, para que una modificación futura
    // no cambie el tratamiento de documentos anteriores.
    // =========================================================

    @Column(
            name = "igv_porcentaje",
            precision = 5,
            scale = 2
    )
    @Builder.Default
    private BigDecimal igvPorcentaje =
            new BigDecimal("18.00");

    @Column(name = "igv_configurado_en")
    private LocalDateTime igvConfiguradoEn;

    @Column(
            name = "igv_configuracion_motivo",
            length = 500
    )
    private String igvConfiguracionMotivo;


    // =========================================================
    // CREDENCIALES SUNAT
    // =========================================================
    //
    // Pueden ser NULL mientras la empresa todavía
    // no haya terminado su configuración SUNAT.
    //
    // Cuando existan siempre estarán cifradas.
    // =========================================================

    /*
     * Puede ser NULL mientras la empresa todavía
     * no haya configurado sus credenciales SUNAT.
     *
     * Cuando se configura SUNAT, estos valores
     * se almacenan cifrados mediante CryptoService.
     */
    @Column(
            name = "usuario_sol",
            length = 2048
    )
    private String usuarioSol;

    @Column(
            name = "clave_sol",
            length = 2048
    )
    private String claveSol;


    @Lob
    @Column(
            name = "pfx_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] pfxBytes;


    @Column(
            name = "pfx_password",
            length = 2048
    )
    private String pfxPassword;


    // =========================================================
    // API KEY
    // =========================================================

    @Column(
            name = "api_key_hash",
            length = 64
    )
    private String apiKeyHash;


    // =========================================================
    // CERTIFICADO
    // =========================================================

    @Column(
            name = "certificado_titular",
            length = 500
    )
    private String certificadoTitular;


    @Column(
            name = "certificado_emisor",
            length = 500
    )
    private String certificadoEmisor;


    @Column(
            name = "certificado_vigente_desde"
    )
    private LocalDate certificadoVigenteDesde;


    @Column(
            name = "certificado_vigente_hasta"
    )
    private LocalDate certificadoVigenteHasta;


    // =========================================================
    // DIRECCIÓN FISCAL
    // =========================================================

    @Column(
            name = "direccion_fiscal"
    )
    private String direccionFiscal;


    @Column(
            length = 6
    )
    private String ubigeo;


    private String departamento;

    private String provincia;

    private String distrito;


    @Column(
            name = "codigo_establecimiento",
            length = 4
    )
    private String codigoEstablecimiento;


    // =========================================================
    // AUDITORÍA
    // =========================================================

    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;


    @Column(
            name = "updated_at"
    )
    private LocalDateTime updatedAt;


    // =========================================================
    // PRE PERSIST
    // =========================================================

    @PrePersist
    public void prePersist() {

        LocalDateTime ahora =
                LocalDateTime.now();
        createdAt =
                ahora;
        updatedAt =
                ahora;
        if (
                tenantId == null
                        ||
                        tenantId.isBlank()
        ) {

            tenantId =
                    UUID
                            .randomUUID()
                            .toString();
        }


        if (
                codigoEstablecimiento == null
                        ||
                        codigoEstablecimiento.isBlank()
        ) {

            codigoEstablecimiento =
                    "0000";
        }


        if (
                estadoPlataforma == null
        ) {

            estadoPlataforma =
                    EstadoEmpresa.ACTIVA;
        }

        if (igvPorcentaje == null) {

            igvPorcentaje =
                    new BigDecimal("18.00");
        }
    }


    // =========================================================
    // PRE UPDATE
    // =========================================================

    @PreUpdate
    public void preUpdate() {

        updatedAt =
                LocalDateTime.now();


        if (
                tenantId == null
                        ||
                        tenantId.isBlank()
        ) {

            tenantId =
                    UUID
                            .randomUUID()
                            .toString();
        }


        if (
                codigoEstablecimiento == null
                        ||
                        codigoEstablecimiento.isBlank()
        ) {

            codigoEstablecimiento =
                    "0000";
        }


        if (
                estadoPlataforma == null
        ) {

            estadoPlataforma =
                    EstadoEmpresa.ACTIVA;
        }

        if (igvPorcentaje == null) {

            igvPorcentaje =
                    new BigDecimal("18.00");
        }
    }


    // =========================================================
    // UTILIDADES
    // =========================================================

    /**
     * Fallback para empresas creadas antes de incorporar la
     * configuración tributaria por tenant.
     */
    public BigDecimal igvPorcentajeEfectivo() {

        return igvPorcentaje != null
                ? igvPorcentaje
                : new BigDecimal("18.00");
    }


    public boolean estaActiva() {

        return estadoPlataforma
                ==
                EstadoEmpresa.ACTIVA;
    }


    public boolean tieneConfiguracionSunat() {

        return usuarioSol != null
                &&
                !usuarioSol.isBlank()
                &&
                claveSol != null
                &&
                !claveSol.isBlank()
                &&
                pfxBytes != null
                &&
                pfxBytes.length > 0
                &&
                pfxPassword != null
                &&
                !pfxPassword.isBlank();
    }
}