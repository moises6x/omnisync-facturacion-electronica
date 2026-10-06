package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.Size;
import org.example.util.TicketSunat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="comunicacion_baja",
       uniqueConstraints=@UniqueConstraint(name="uk_baja_empresa_identificador", columnNames={"ruc_emisor","identificador"}),
       indexes={@Index(name="idx_baja_empresa_estado",columnList="ruc_emisor,estado"),
                @Index(name="idx_baja_ticket",columnList="ticket_sunat")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComunicacionBaja {
    public void setMensajeSunat(String mensaje) { this.mensajeSunat=org.example.util.DiagnosticoSunat.acotar(mensaje); }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="ruc_emisor",length=11,nullable=false) private String rucEmisor;
    @Column(name="fecha_documentos",nullable=false) private LocalDate fechaDocumentos;
    @Column(name="fecha_generacion",nullable=false) private LocalDate fechaGeneracion;
    @Column(nullable=false) private Integer correlativo;
    @Column(length=40,nullable=false) private String identificador;
    @Column(name="nombre_archivo_xml",length=100,nullable=false) private String nombreArchivoXml;
    @Lob @Column(name="xml_firmado_bytes",columnDefinition="LONGBLOB") private byte[] xmlFirmadoBytes;
    @Column(name="hash_cpe",length=150) private String hashCpe;
    @Lob @Column(name="cdr_zip_bytes",columnDefinition="LONGBLOB") private byte[] cdrZipBytes;
    @Lob @Column(name="cdr_xml_bytes",columnDefinition="LONGBLOB") private byte[] cdrXmlBytes;
    @Enumerated(EnumType.STRING) @Column(length=40,nullable=false) private EstadoComunicacionBaja estado;
    @Size(max=TicketSunat.MAX_LENGTH)
    @Column(name="ticket_sunat",length=TicketSunat.MAX_LENGTH) private String ticketSunat;
    @Column(name="codigo_respuesta_sunat",length=20) private String codigoRespuestaSunat;
    @Column(name="mensaje_sunat",columnDefinition="TEXT") private String mensajeSunat;
    @Column(name="intentos_envio",nullable=false) @Builder.Default private Integer intentosEnvio=0;
    @Column(name="intentos_consulta",nullable=false) @Builder.Default private Integer intentosConsulta=0;
    @Column(name="enviado_en") private LocalDateTime enviadoEn;
    @Column(name="ultima_consulta_en") private LocalDateTime ultimaConsultaEn;
    @Column(name="finalizado_en") private LocalDateTime finalizadoEn;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy="comunicacion",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("numeroLinea ASC") @Builder.Default private List<ComunicacionBajaDetalle> detalles=new ArrayList<>();
    public void addDetalle(ComunicacionBajaDetalle d){ detalles.add(d); d.setComunicacion(this); }
    @PrePersist void prePersist(){ var n=LocalDateTime.now(); createdAt=n; updatedAt=n; if(estado==null) estado=EstadoComunicacionBaja.CREADO; }
    @PreUpdate void preUpdate(){ updatedAt=LocalDateTime.now(); }
}
