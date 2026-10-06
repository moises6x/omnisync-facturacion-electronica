
package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.Size;
import org.example.util.TicketSunat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================
 * RESUMEN DIARIO SUNAT
 * =============================================================
 *
 * Cabecera persistente de un archivo RC enviado mediante sendSummary.
 *
 * Un Resumen Diario agrupa exclusivamente documentos de:
 *
 * - una misma empresa
 * - una misma fecha de emisión
 *
 * En esta versión incluimos:
 *
 * - Boleta 03
 * - Nota de Crédito 07 cuando modifica una Boleta 03
 *
 * El XML, ticket y CDR quedan almacenados para auditoría y para poder
 * recuperar el procesamiento después de reinicios del servidor.
 */
@Entity
@Table(
        name = "resumen_diario",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resumen_diario_identificador",
                        columnNames = {
                                "ruc_emisor",
                                "identificador"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_resumen_diario_empresa_fecha_correlativo",
                        columnNames = {
                                "ruc_emisor",
                                "fecha_documentos",
                                "correlativo"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_resumen_diario_empresa_fecha",
                        columnList = "ruc_emisor,fecha_documentos"
                ),
                @Index(
                        name = "idx_resumen_diario_estado",
                        columnList = "estado,updated_at"
                ),
                @Index(
                        name = "idx_resumen_diario_ticket",
                        columnList = "ticket_sunat"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenDiario {
    public void setMensajeSunat(String mensaje) { this.mensajeSunat=org.example.util.DiagnosticoSunat.acotar(mensaje); }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================
    // EMPRESA / IDENTIFICACIÓN DEL RC
    // =========================================================

    @Column(
            name = "ruc_emisor",
            length = 11,
            nullable = false
    )
    private String rucEmisor;

    /** Fecha de emisión de todas las boletas/notas incluidas. */
    @Column(
            name = "fecha_documentos",
            nullable = false
    )
    private LocalDate fechaDocumentos;

    /** Fecha en que nuestro backend genera el resumen. */
    @Column(
            name = "fecha_generacion",
            nullable = false
    )
    private LocalDate fechaGeneracion;

    /** Correlativo del archivo RC para esa empresa y fecha. */
    @Column(nullable = false)
    private Integer correlativo;

    /** Ejemplo: RC-20260919-1 */
    @Column(
            length = 40,
            nullable = false
    )
    private String identificador;

    /** Ejemplo: 20123456789-RC-20260919-1.xml */
    @Column(
            name = "nombre_archivo_xml",
            length = 100,
            nullable = false
    )
    private String nombreArchivoXml;

    @Column(
            name = "cantidad_documentos",
            nullable = false
    )
    private Integer cantidadDocumentos;

    // =========================================================
    // XML / FIRMA / CDR
    // =========================================================

    @Lob
    @Column(
            name = "xml_firmado_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] xmlFirmadoBytes;

    @Column(
            name = "hash_cpe",
            length = 150
    )
    private String hashCpe;

    @Lob
    @Column(
            name = "cdr_zip_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] cdrZipBytes;

    @Lob
    @Column(
            name = "cdr_xml_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] cdrXmlBytes;

    // =========================================================
    // SUNAT / TICKET
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(
            length = 40,
            nullable = false
    )
    private EstadoResumenDiario estado;

    @Size(max = TicketSunat.MAX_LENGTH)
    @Column(
            name = "ticket_sunat",
            length = TicketSunat.MAX_LENGTH
    )
    private String ticketSunat;

    @Column(
            name = "codigo_respuesta_sunat",
            length = 30
    )
    private String codigoRespuestaSunat;

    @Column(
            name = "mensaje_sunat",
            columnDefinition = "TEXT"
    )
    private String mensajeSunat;

    @Column(
            name = "intentos_envio",
            nullable = false
    )
    @Builder.Default
    private Integer intentosEnvio = 0;

    @Column(
            name = "intentos_consulta",
            nullable = false
    )
    @Builder.Default
    private Integer intentosConsulta = 0;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(name = "ultima_consulta_en")
    private LocalDateTime ultimaConsultaEn;

    @Column(name = "finalizado_en")
    private LocalDateTime finalizadoEn;

    // =========================================================
    // AUDITORÍA
    // =========================================================

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

    // =========================================================
    // DETALLE
    // =========================================================

    @OneToMany(
            mappedBy = "resumen",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("numeroLinea ASC")
    @Builder.Default
    private List<ResumenDiarioDetalle> detalles = new ArrayList<>();

    public void addDetalle(
            ResumenDiarioDetalle detalle
    ) {
        detalles.add(detalle);
        detalle.setResumen(this);
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        createdAt = ahora;
        updatedAt = ahora;

        if (estado == null) {
            estado = EstadoResumenDiario.CREADO;
        }

        if (intentosEnvio == null) {
            intentosEnvio = 0;
        }

        if (intentosConsulta == null) {
            intentosConsulta = 0;
        }

        if (cantidadDocumentos == null) {
            cantidadDocumentos = detalles.size();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
