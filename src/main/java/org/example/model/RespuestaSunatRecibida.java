package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.Size;
import org.example.util.TicketSunat;
import java.time.LocalDateTime;

/** Evidencia bruta append-only. Su existencia NO acredita CDR válido ni aceptación fiscal. */
@Entity
@Table(name = "respuesta_sunat_recibida", indexes = @Index(name = "idx_respuesta_sunat_documento",
        columnList = "ruc_emisor,flujo,documento_id,recibido_en"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class RespuestaSunatRecibida {
    public enum Flujo { CPE, RC, RA }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ruc_emisor", nullable = false, length = 11) private String rucEmisor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3) private Flujo flujo;
    @Column(name = "documento_id", nullable = false) private Long documentoId;
    @Size(max = TicketSunat.MAX_LENGTH)
    @Column(name = "ticket_sunat", length = TicketSunat.MAX_LENGTH) private String ticketSunat;
    @Column(name = "recibido_en", nullable = false) private LocalDateTime recibidoEn;
    @Column(nullable = false, length = 64) private String sha256;
    @Lob @Column(name = "respuesta_zip_bytes", nullable = false, columnDefinition = "LONGBLOB") private byte[] respuestaZipBytes;
}
