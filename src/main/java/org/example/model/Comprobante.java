package org.example.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * =============================================================
 * COMPROBANTE
 * =============================================================
 *
 * Representa los comprobantes electrónicos emitidos
 * por una empresa dentro del SaaS.
 *
 * Actualmente puede almacenar:
 *
 * 01 = Factura
 * 03 = Boleta
 * 07 = Nota de Crédito
 *
 *
 * IMPORTANTE PARA RESUMEN DIARIO SUNAT
 * -------------------------------------------------------------
 *
 * Las Facturas y las Notas de Crédito de Facturas continúan
 * siendo enviadas individualmente a SUNAT.
 *
 * Las Boletas y las Notas de Crédito asociadas a Boletas
 * podrán permanecer en:
 *
 * PENDIENTE_RESUMEN
 *
 * hasta ser incluidas dentro de un Resumen Diario SUNAT.
 *
 *
 * También almacenamos la referencia al comprobante afectado
 * por una Nota de Crédito.
 *
 * Ejemplo:
 *
 * Nota:
 * BC01-00000001
 *
 * afecta a:
 * B001-00000010
 *
 * Esto será necesario posteriormente para construir
 * correctamente el XML SummaryDocuments.
 */
@Entity
@Table(
        name = "comprobante",

        uniqueConstraints = {

                /*
                 * Evita que una misma empresa pueda tener
                 * dos comprobantes con exactamente:
                 *
                 * tipo + serie + número
                 */
                @UniqueConstraint(
                        name = "uk_comprobante_emisor",
                        columnNames = {
                                "ruc_emisor",
                                "tipo_comprobante",
                                "serie",
                                "numero"
                        }
                )
        },

        indexes = {

                /*
                 * Optimiza búsquedas por documento del cliente.
                 */
                @Index(
                        name = "idx_busqueda_cliente",
                        columnList = "tipo_doc_cliente,num_doc_cliente"
                ),

                /*
                 * Optimiza búsquedas históricas por fecha.
                 */
                @Index(
                        name = "idx_fecha_emision",
                        columnList = "fecha_emision"
                ),

                /*
                 * Optimiza consultas por estado SUNAT.
                 */
                @Index(
                        name = "idx_estado_sfs",
                        columnList = "estado_sfs"
                ),

                /*
                 * =================================================
                 * ÍNDICE PARA RESUMEN DIARIO
                 * =================================================
                 *
                 * Será utilizado para localizar rápidamente:
                 *
                 * - empresa
                 * - fecha de emisión
                 * - estado
                 * - tipo de comprobante
                 *
                 * Ejemplo:
                 *
                 * todas las boletas del 19/09/2026
                 * que están PENDIENTE_RESUMEN.
                 */
                @Index(
                        name = "idx_comprobante_resumen_diario",
                        columnList =
                                "ruc_emisor,fecha_emision,estado_sfs,tipo_comprobante"
                ),

                /*
                 * Optimiza búsqueda de Notas de Crédito
                 * según el tipo de documento que modifican.
                 */
                @Index(
                        name = "idx_comprobante_afectado",
                        columnList =
                                "ruc_emisor,tipo_comprobante_afectado,serie_comprobante_afectado,numero_comprobante_afectado"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comprobante {
    public void setMensajeSunat(String mensaje) { this.mensajeSunat=org.example.util.DiagnosticoSunat.acotar(mensaje); }


    // =========================================================
    // IDENTIFICADOR INTERNO
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================
    // EMPRESA / TIENDA
    // =========================================================

    /**
     * Tienda o establecimiento desde donde
     * se realizó la operación.
     */
    @Column(
            name = "tienda_id",
            nullable = false
    )
    private Long tiendaId;


    /**
     * RUC de la empresa emisora.
     *
     * Este campo es fundamental para el aislamiento
     * multiempresa del SaaS.
     */
    @Column(
            name = "ruc_emisor",
            length = 11,
            nullable = false
    )
    private String rucEmisor;


    // =========================================================
    // DATOS DEL COMPROBANTE
    // =========================================================

    /**
     * Catálogo SUNAT 51.
     *
     * Ejemplo:
     *
     * 0101 = Venta interna.
     */
    @Column(
            name = "tipo_operacion",
            length = 4
    )
    private String tipoOperacion;


    /**
     * Tipo SUNAT:
     *
     * 01 = Factura
     * 03 = Boleta
     * 07 = Nota de Crédito
     */
    @Column(
            name = "tipo_comprobante",
            length = 2,
            nullable = false
    )
    private String tipoComprobante;


    /**
     * Serie del documento.
     *
     * Ejemplos:
     *
     * F001
     * B001
     * FC01
     * BC01
     */
    @Column(
            length = 4,
            nullable = false
    )
    private String serie;


    /**
     * Correlativo generado exclusivamente
     * por el backend.
     */
    @Column(nullable = false)
    private Integer numero;


    /**
     * Fecha real de emisión del comprobante.
     *
     * Esta fecha también servirá posteriormente
     * para agrupar boletas dentro del Resumen Diario.
     */
    @Column(
            name = "fecha_emision",
            nullable = false
    )
    private LocalDateTime fechaEmision;


    /**
     * Moneda.
     *
     * Normalmente:
     *
     * PEN = Soles
     * USD = Dólares
     */
    @Column(
            length = 3,
            nullable = false
    )
    private String moneda;


    /**
     * Forma tributaria de pago.
     *
     * NO confundir con medio de pago de caja.
     *
     * Aquí:
     *
     * Contado
     * Credito
     *
     * Más adelante, para cierre de caja, tendremos otro campo
     * separado para:
     *
     * EFECTIVO
     * YAPE
     * PLIN
     * TARJETA
     * TRANSFERENCIA
     *
     * No mezclamos ambos conceptos.
     */
    @Column(
            name = "forma_pago",
            length = 10,
            nullable = false
    )
    private String formaPago;


    // =========================================================
    // CLIENTE
    // =========================================================

    /**
     * Tipo de documento del cliente.
     *
     * Ejemplos:
     *
     * 1 = DNI
     * 6 = RUC
     * 7 = Pasaporte
     */
    @Column(
            name = "tipo_doc_cliente",
            length = 1,
            nullable = false
    )
    private String tipoDocCliente;


    /**
     * Número del documento del cliente.
     */
    @Column(
            name = "num_doc_cliente",
            length = 15,
            nullable = false
    )
    private String numDocCliente;


    /**
     * Nombre completo o razón social.
     */
    @Column(
            name = "rzn_social_cliente",
            nullable = false
    )
    private String rznSocialCliente;


    /**
     * Dirección del cliente.
     */
    @Column(name = "direccion_cliente")
    private String direccionCliente;


    // =========================================================
    // TOTALES TRIBUTARIOS
    // =========================================================

    /**
     * Total de operaciones gravadas sin IGV.
     */
    @Column(
            name = "op_gravada",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal opGravada;


    /**
     * Total de operaciones exoneradas.
     */
    @Column(
            name = "op_exonerada",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal opExonerada;


    /**
     * Total de operaciones inafectas.
     */
    @Column(
            name = "op_inafecta",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal opInafecta;


    /**
     * Tasa IGV efectiva usada en la emisión.
     *
     * Se conserva como snapshot histórico para que un cambio
     * posterior en la configuración de la empresa no altere
     * el tratamiento de este comprobante ni de sus notas.
     */
    @Column(
            name = "igv_porcentaje",
            precision = 5,
            scale = 2
    )
    private BigDecimal igvPorcentaje;


    /**
     * IGV total del comprobante.
     */
    @Column(
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal igv;


    /**
     * Importe total final del documento.
     */
    @Column(
            name = "monto_total",
            precision = 14,
            scale = 2,
            nullable = false
    )
    private BigDecimal montoTotal;


    // =========================================================
    // COMPROBANTE AFECTADO
    // =========================================================
    //
    // Estos campos son importantes para Notas de Crédito.
    //
    // Antes el DTO tenía esta información, pero después de
    // generar la Nota de Crédito se perdía porque no se
    // persistía en la tabla.
    //
    // Ahora se conservará permanentemente.
    // =========================================================

    /**
     * Tipo del comprobante original.
     *
     * Ejemplo:
     *
     * 01 = la NC modifica una Factura
     * 03 = la NC modifica una Boleta
     */
    @Column(
            name = "tipo_comprobante_afectado",
            length = 2
    )
    private String tipoComprobanteAfectado;


    /**
     * Serie del comprobante original.
     *
     * Ejemplo:
     *
     * B001
     */
    @Column(
            name = "serie_comprobante_afectado",
            length = 4
    )
    private String serieComprobanteAfectado;


    /**
     * Número del comprobante original.
     */
    @Column(name = "numero_comprobante_afectado")
    private Integer numeroComprobanteAfectado;


    /**
     * Código SUNAT del motivo de la Nota de Crédito.
     */
    @Column(
            name = "codigo_motivo_nota",
            length = 2
    )
    private String codigoMotivoNota;


    /**
     * Explicación del motivo de emisión de la Nota.
     */
    @Column(
            name = "descripcion_motivo_nota",
            length = 500
    )
    private String descripcionMotivoNota;


    // =========================================================
    // ARCHIVOS SUNAT
    // =========================================================

    /**
     * Nombre del XML individual generado.
     *
     * Ejemplo:
     *
     * 20100130204-03-B001-00000001.xml
     */
    @Column(name = "nombre_archivo_xml")
    private String nombreArchivoXml;


    /**
     * XML UBL firmado digitalmente.
     */
    @Lob
    @Column(
            name = "xml_firmado_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] xmlFirmadoBytes;


    /**
     * Digest/hash generado al firmar el CPE.
     */
    @Column(
            name = "hash_cpe",
            length = 150
    )
    private String hashCpe;


    /**
     * CDR comprimido que SUNAT devuelve.
     *
     * Para boletas informadas mediante Resumen Diario,
     * posteriormente el CDR principal pertenecerá al Resumen.
     */
    @Lob
    @Column(
            name = "cdr_zip_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] cdrZipBytes;


    /**
     * XML extraído del CDR.
     */
    @Lob
    @Column(
            name = "cdr_xml_bytes",
            columnDefinition = "LONGBLOB"
    )
    private byte[] cdrXmlBytes;


    // =========================================================
    // ESTADO
    // =========================================================

    /**
     * Estado interno de procesamiento.
     *
     * Ahora también permite:
     *
     * PENDIENTE_RESUMEN
     * EN_RESUMEN
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "estado_sfs",
            length = 40,
            nullable = false
    )
    private EstadoComprobante estadoSfs;




    /**
     * Código de estado que se utilizará la próxima vez que este
     * documento participe en un Resumen Diario SUNAT.
     *
     * Catálogo 19:
     * 1 = Adicionar
     * 2 = Modificar
     * 3 = Anulado
     *
     * Esta versión utiliza 1 para la emisión normal y 3 únicamente
     * para una boleta previamente informada que no fue otorgada al
     * adquirente/usuario.
     */
    @Column(
            name = "codigo_estado_resumen",
            length = 1,
            nullable = false
    )
    @Builder.Default
    private String codigoEstadoResumen = "1";


    /**
     * Código devuelto por SUNAT.
     */
    @Column(
            name = "codigo_respuesta_sunat",
            length = 20
    )
    private String codigoRespuestaSunat;


    /**
     * Mensaje de aceptación, observación,
     * rechazo o error.
     */
    @Column(
            name = "mensaje_sunat",
            columnDefinition = "TEXT"
    )
    private String mensajeSunat;


    // =========================================================
    // AUDITORÍA
    // =========================================================

    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;


    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // =========================================================
    // DETALLES / ITEMS
    // =========================================================

    @OneToMany(
            mappedBy = "comprobante",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<DetalleComprobante> detalles =
            new ArrayList<>();


    // =========================================================
    // PRE PERSIST
    // =========================================================

    /**
     * Completa valores por defecto antes de insertar
     * un comprobante nuevo en MySQL.
     */
    @PrePersist
    public void prePersist() {

        LocalDateTime ahora =
                LocalDateTime.now();


        createdAt =
                ahora;


        updatedAt =
                ahora;


        if (fechaEmision == null) {

            fechaEmision =
                    ahora;
        }


        if (moneda == null) {

            moneda =
                    "PEN";
        }


        if (formaPago == null) {

            formaPago =
                    "Contado";
        }


        if (tipoOperacion == null) {

            tipoOperacion =
                    "0101";
        }


        /*
         * Estado por defecto genérico.
         *
         * FacturacionServiceImpl decidirá explícitamente:
         *
         * Factura -> PENDIENTE_ENVIO
         * Boleta  -> PENDIENTE_RESUMEN
         */
        if (estadoSfs == null) {

            estadoSfs =
                    EstadoComprobante.PENDIENTE_ENVIO;
        }

        if (codigoEstadoResumen == null || codigoEstadoResumen.isBlank()) {
            codigoEstadoResumen = "1";
        }


        if (opGravada == null) {

            opGravada =
                    BigDecimal.ZERO;
        }


        if (opExonerada == null) {

            opExonerada =
                    BigDecimal.ZERO;
        }


        if (opInafecta == null) {

            opInafecta =
                    BigDecimal.ZERO;
        }


        if (igv == null) {

            igv =
                    BigDecimal.ZERO;
        }
    }


    // =========================================================
    // PRE UPDATE
    // =========================================================

    /**
     * Actualiza automáticamente la fecha
     * de modificación.
     */
    @PreUpdate
    public void preUpdate() {

        updatedAt =
                LocalDateTime.now();
    }


    // =========================================================
    // AGREGAR ITEM
    // =========================================================

    /**
     * Mantiene sincronizada la relación bidireccional:
     *
     * Comprobante -> Detalle
     * Detalle -> Comprobante
     */
    public void addDetalle(
            DetalleComprobante detalle
    ) {

        detalles.add(
                detalle
        );


        detalle.setComprobante(
                this
        );
    }
}
