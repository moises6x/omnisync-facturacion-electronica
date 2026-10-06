
package org.example.repository;

import org.example.model.Comprobante;
import org.example.model.EstadoComprobante;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;


/**
 * =============================================================
 * COMPROBANTE REPOSITORY
 * =============================================================
 *
 * Acceso a los comprobantes almacenados en MySQL.
 *
 * Todas las búsquedas funcionales importantes utilizan
 * el RUC del emisor para mantener el aislamiento
 * multiempresa.
 */
public interface ComprobanteRepository
        extends JpaRepository<Comprobante, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comprobante c WHERE c.rucEmisor=:ruc AND c.tipoComprobante=:tipo AND c.serie=:serie AND c.numero=:numero")
    Optional<Comprobante> bloquearOriginalNotaCredito(@Param("ruc") String ruc, @Param("tipo") String tipo,
            @Param("serie") String serie, @Param("numero") Integer numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comprobante c WHERE c.id=:id AND c.rucEmisor=:ruc")
    Optional<Comprobante> bloquearPorIdYRuc(@Param("id") Long id, @Param("ruc") String ruc);

    /** Lectura actual InnoDB, no SUM ni entidades potencialmente antiguas del persistence context. */
    @Query(value = """
            SELECT id AS id, moneda AS moneda, codigo_motivo_nota AS motivo, estado_sfs AS estado,
                   monto_total AS total, op_gravada AS gravada, op_exonerada AS exonerada,
                   op_inafecta AS inafecta, igv AS igv, serie AS serie, numero AS numero,
                   codigo_respuesta_sunat AS codigo, cdr_zip_bytes AS cdrZip, cdr_xml_bytes AS cdrXml
            FROM comprobante
            WHERE ruc_emisor=:ruc AND tipo_comprobante='07'
              AND tipo_comprobante_afectado=:tipo AND serie_comprobante_afectado=:serie
              AND numero_comprobante_afectado=:numero
            ORDER BY id FOR UPDATE
            """, nativeQuery = true)
    List<NotaCreditoSaldoProjection> bloquearNotasCreditoDelOriginal(@Param("ruc") String ruc,
            @Param("tipo") String tipo, @Param("serie") String serie, @Param("numero") Integer numero);

    @Query("""
            SELECT COUNT(c) FROM Comprobante c WHERE c.rucEmisor=:ruc AND c.tipoComprobante='07'
              AND (c.tipoComprobanteAfectado IS NULL OR c.tipoComprobanteAfectado NOT IN ('01','03')
                   OR c.serieComprobanteAfectado IS NULL OR TRIM(c.serieComprobanteAfectado)=''
                   OR c.numeroComprobanteAfectado IS NULL OR c.numeroComprobanteAfectado < 1)
            """)
    long contarNotasCreditoSinReferencia(@Param("ruc") String ruc);

    /** Cabecera RC/RA -> originales en orden -> NC; nunca se bloquea una NC antes de su original. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM Comprobante o WHERE o.rucEmisor=:ruc AND EXISTS
              (SELECT n.id FROM Comprobante n WHERE n.rucEmisor=:ruc AND n.id IN :ids
               AND n.tipoComprobante='07' AND n.tipoComprobanteAfectado=o.tipoComprobante
               AND n.serieComprobanteAfectado=o.serie AND n.numeroComprobanteAfectado=o.numero)
            ORDER BY o.id
            """)
    List<Comprobante> bloquearOriginalesDeNotas(@Param("ruc") String ruc, @Param("ids") List<Long> ids);

    interface NotaCreditoSaldoProjection {
        Long getId();
        String getMoneda();
        String getMotivo();
        String getEstado();
        BigDecimal getTotal();
        BigDecimal getGravada();
        BigDecimal getExonerada();
        BigDecimal getInafecta();
        BigDecimal getIgv();
        String getSerie();
        Integer getNumero();
        String getCodigo();
        byte[] getCdrZip();
        byte[] getCdrXml();
    }

    @Query("""
            SELECT MIN(c.fechaEmision) FROM Comprobante c
            WHERE c.rucEmisor = :ruc
              AND c.estadoSfs = org.example.model.EstadoComprobante.PENDIENTE_RESUMEN
              AND c.fechaEmision >= :desde
              AND (c.tipoComprobante = '03' OR (c.tipoComprobante = '07' AND c.tipoComprobanteAfectado = '03'))
            """)
    LocalDateTime buscarFechaMasAntiguaPendienteResumenDesde(@Param("ruc") String ruc, @Param("desde") LocalDateTime desde);

    /** Recuperación interna: una caída en ENVIANDO no demuestra que SUNAT no recibió el CPE. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE comprobante
            SET estado_sfs = 'ERROR_ENVIO_INCIERTO',
                mensaje_sunat = 'Envío interrumpido o vencido. Requiere conciliación; no se reenvía automáticamente.',
                updated_at = NOW()
            WHERE estado_sfs = 'ENVIANDO' AND updated_at < :corte
              AND (tipo_comprobante = '01' OR (tipo_comprobante = '07' AND tipo_comprobante_afectado = '01'))
            """, nativeQuery = true)
    int marcarEnviosVencidosComoInciertos(@Param("corte") LocalDateTime corte);


    // =========================================================
    // BUSCAR POR ID + EMPRESA
    // =========================================================

    /**
     * Recupera un comprobante solamente si pertenece
     * al RUC autenticado.
     *
     * También trae sus items mediante EntityGraph.
     */
    @EntityGraph(attributePaths = "detalles")
    Optional<Comprobante> findByIdAndRucEmisor(
            Long id,
            String rucEmisor
    );


    // =========================================================
    // BUSCAR POR SERIE / NÚMERO
    // =========================================================

    /**
     * Utilizado, entre otras cosas, al emitir una
     * Nota de Crédito para verificar que el documento
     * original exista.
     */
    Optional<Comprobante>
    findByRucEmisorAndTipoComprobanteAndSerieAndNumero(

            String rucEmisor,

            String tipoComprobante,

            String serie,

            Integer numero
    );


    // =========================================================
    // CONSULTA PÚBLICA CPE - PROYECCIÓN MÍNIMA
    // =========================================================

    @Query("""
            SELECT
                c.fechaEmision AS fechaEmision,
                c.montoTotal AS montoTotal,
                c.tipoDocCliente AS tipoDocCliente,
                c.numDocCliente AS numDocCliente,
                c.estadoSfs AS estadoSfs
            FROM Comprobante c
            WHERE c.rucEmisor = :ruc
              AND c.tipoComprobante = :tipo
              AND c.serie = :serie
              AND c.numero = :numero
            """)
    Optional<ConsultaPublicaCpeProjection> buscarParaConsultaPublica(
            @Param("ruc") String ruc,
            @Param("tipo") String tipo,
            @Param("serie") String serie,
            @Param("numero") Integer numero
    );


    // =========================================================
    // HISTORIAL
    // =========================================================

    /**
     * Historial paginado de comprobantes.
     *
     * Permite filtrar por:
     *
     * - empresa
     * - tipo
     * - estado
     * - rango de fechas
     */
    @Query("""
            SELECT c
            FROM Comprobante c

            WHERE c.rucEmisor = :ruc

              AND (
                    :tipo IS NULL
                    OR c.tipoComprobante = :tipo
              )

              AND (
                    :estado IS NULL
                    OR c.estadoSfs = :estado
              )

              AND c.fechaEmision >= :desde

              AND c.fechaEmision < :hastaExclusivo

            ORDER BY
                c.fechaEmision DESC,
                c.id DESC
            """)
    Page<Comprobante> buscarHistorial(

            @Param("ruc")
            String ruc,

            @Param("tipo")
            String tipo,

            @Param("estado")
            EstadoComprobante estado,

            @Param("desde")
            LocalDateTime desde,

            @Param("hastaExclusivo")
            LocalDateTime hastaExclusivo,

            Pageable pageable
    );


    // =========================================================
    // RESUMEN DIARIO SUNAT
    // =========================================================

    /**
     * =========================================================
     * BUSCAR DOCUMENTOS PENDIENTES PARA RESUMEN DIARIO
     * =========================================================
     *
     * Obtiene únicamente documentos de UNA empresa
     * y UNA fecha de emisión.
     *
     * Incluye:
     *
     * 03 = Boleta
     *
     * y:
     *
     * 07 = Nota de Crédito,
     * solamente cuando afecta a una Boleta 03.
     *
     *
     * IMPORTANTE:
     *
     * No toma:
     *
     * - Facturas
     * - NC asociadas a Facturas
     *
     * porque esas continúan por sendBill.
     */
    @Query("""
            SELECT c
            FROM Comprobante c

            WHERE c.rucEmisor = :ruc

              AND c.estadoSfs =
                    org.example.model.EstadoComprobante.PENDIENTE_RESUMEN

              AND c.fechaEmision >= :desde

              AND c.fechaEmision < :hastaExclusivo

              AND (
                    c.tipoComprobante = '03'

                    OR

                    (
                        c.tipoComprobante = '07'
                        AND c.tipoComprobanteAfectado = '03'
                    )
              )

            ORDER BY
                c.fechaEmision ASC,
                c.id ASC
            """)
    List<Comprobante> buscarPendientesResumenDiario(

            @Param("ruc")
            String ruc,

            @Param("desde")
            LocalDateTime desde,

            @Param("hastaExclusivo")
            LocalDateTime hastaExclusivo
    );


    // =========================================================
    // MARCAR DOCUMENTO COMO PARTE DE RESUMEN
    // =========================================================

    /**
     * Realiza la transición:
     *
     * PENDIENTE_RESUMEN
     *       ↓
     * EN_RESUMEN
     *
     * utilizando un UPDATE atómico.
     *
     * Posteriormente ResumenDiarioService utilizará este método
     * para impedir que dos resúmenes tomen el mismo documento.
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE comprobante

                    SET estado_sfs = 'EN_RESUMEN',
                        updated_at = NOW()

                    WHERE id = :id

                      AND ruc_emisor = :ruc

                      AND estado_sfs = 'PENDIENTE_RESUMEN'
                    """,
            nativeQuery = true
    )
    int marcarEnResumen(

            @Param("id")
            Long id,

            @Param("ruc")
            String ruc
    );


    // =========================================================
    // DEVOLVER A PENDIENTE RESUMEN
    // =========================================================

    /**
     * Se usará cuando la creación del Resumen Diario falle
     * ANTES de conseguir enviarlo correctamente.
     *
     * Permite volver:
     *
     * EN_RESUMEN
     *      ↓
     * PENDIENTE_RESUMEN
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE comprobante

                    SET estado_sfs = 'PENDIENTE_RESUMEN',
                        updated_at = NOW()

                    WHERE id = :id

                      AND ruc_emisor = :ruc

                      AND estado_sfs = 'EN_RESUMEN'
                    """,
            nativeQuery = true
    )
    int devolverAPendienteResumen(

            @Param("id")
            Long id,

            @Param("ruc")
            String ruc
    );



    // =========================================================
    // RESUMEN DIARIO - BAJA DE BOLETA YA INFORMADA
    // =========================================================

    /**
     * Catálogo 19 / SummaryDocuments:
     * código 3 = Anulado.
     *
     * Sólo se permite preparar la baja de una boleta que ya fue
     * informada y aceptada. Si fue otorgada al cliente, el flujo
     * correcto es una Nota de Crédito y este método NO debe usarse.
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE comprobante

                    SET codigo_estado_resumen = '3',
                        estado_sfs = 'PENDIENTE_RESUMEN',
                        updated_at = NOW()

                    WHERE id = :id
                      AND ruc_emisor = :ruc
                      AND tipo_comprobante = '03'
                      AND estado_sfs IN ('ACEPTADO', 'ACEPTADO_CON_OBSERVACION')
                    """,
            nativeQuery = true
    )
    int prepararBajaBoletaEnResumen(
            @Param("id") Long id,
            @Param("ruc") String ruc
    );


    // =========================================================
    // RESUMEN DIARIO - CONSULTAS LIMITADAS / SCHEDULER
    // =========================================================

    /**
     * Igual que buscarPendientesResumenDiario, pero permite aplicar un
     * Pageable para respetar el máximo de 500 líneas por archivo RC sin
     * cargar en memoria todos los pendientes de un día.
     */
    @Query("""
            SELECT c
            FROM Comprobante c

            WHERE c.rucEmisor = :ruc

              AND c.estadoSfs =
                    org.example.model.EstadoComprobante.PENDIENTE_RESUMEN

              AND c.fechaEmision >= :desde

              AND c.fechaEmision < :hastaExclusivo

              AND (
                    c.tipoComprobante = '03'

                    OR

                    (
                        c.tipoComprobante = '07'
                        AND c.tipoComprobanteAfectado = '03'
                    )
              )

            ORDER BY
                c.fechaEmision ASC,
                c.id ASC
            """)
    List<Comprobante> buscarPendientesResumenDiarioLimitado(

            @Param("ruc")
            String ruc,

            @Param("desde")
            LocalDateTime desde,

            @Param("hastaExclusivo")
            LocalDateTime hastaExclusivo,

            Pageable pageable
    );


    /**
     * Empresas que todavía tienen boletas/NC de boleta sin informar.
     * Es utilizado únicamente por el scheduler interno.
     */
    @Query("""
            SELECT DISTINCT c.rucEmisor
            FROM Comprobante c
            WHERE c.estadoSfs =
                    org.example.model.EstadoComprobante.PENDIENTE_RESUMEN
              AND (
                    c.tipoComprobante = '03'
                    OR
                    (
                        c.tipoComprobante = '07'
                        AND c.tipoComprobanteAfectado = '03'
                    )
              )
            ORDER BY c.rucEmisor ASC
            """)
    List<String> buscarRucsConPendientesResumen();


    /**
     * Devuelve la fecha/hora del pendiente más antiguo de una empresa.
     * Permite al scheduler procesar primero aquello que está más cerca
     * del límite tributario.
     */
    @Query("""
            SELECT MIN(c.fechaEmision)
            FROM Comprobante c
            WHERE c.rucEmisor = :ruc
              AND c.estadoSfs =
                    org.example.model.EstadoComprobante.PENDIENTE_RESUMEN
              AND (
                    c.tipoComprobante = '03'
                    OR
                    (
                        c.tipoComprobante = '07'
                        AND c.tipoComprobanteAfectado = '03'
                    )
              )
            """)
    LocalDateTime buscarFechaMasAntiguaPendienteResumen(
            @Param("ruc")
            String ruc
    );


    /**
     * Cuando SUNAT acepta el CDR del Resumen Diario, todas las líneas que
     * todavía están EN_RESUMEN se actualizan con el resultado del RC.
     *
     * No copiamos el ZIP/XML del CDR a cada comprobante porque eso
     * duplicaría hasta 500 veces el mismo LONGBLOB. El CDR real queda
     * almacenado una sola vez en resumen_diario.
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE Comprobante c

            SET c.estadoSfs = :estado,
                c.codigoRespuestaSunat = :codigo,
                c.mensajeSunat = :mensaje,
                c.updatedAt = CURRENT_TIMESTAMP

            WHERE c.rucEmisor = :ruc
              AND c.id IN :ids
              AND c.estadoSfs =
                    org.example.model.EstadoComprobante.EN_RESUMEN
            """)
    int actualizarEstadoDesdeResumen(

            @Param("ruc")
            String ruc,

            @Param("ids")
            List<Long> ids,

            @Param("estado")
            EstadoComprobante estado,

            @Param("codigo")
            String codigo,

            @Param("mensaje")
            String mensaje
    );


    /**
     * Operación manual y controlada para volver documentos de un resumen
     * fallido/rechazado a PENDIENTE_RESUMEN.
     *
     * ERROR_ENVIO_INCIERTO nunca utiliza este método automáticamente.
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE Comprobante c

            SET c.estadoSfs =
                    org.example.model.EstadoComprobante.PENDIENTE_RESUMEN,
                c.updatedAt = CURRENT_TIMESTAMP

            WHERE c.rucEmisor = :ruc
              AND c.id IN :ids
              AND c.estadoSfs =
                    org.example.model.EstadoComprobante.EN_RESUMEN
            """)
    int devolverLoteAPendienteResumen(

            @Param("ruc")
            String ruc,

            @Param("ids")
            List<Long> ids
    );

    /** Sólo libera documentos de esta cabecera sin otro propietario activo.
     * El llamador mantiene el lock de cabecera hasta confirmar LIBERADO. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE comprobante c
            SET c.estado_sfs='PENDIENTE_RESUMEN', c.updated_at=NOW()
            WHERE c.ruc_emisor=:ruc AND c.id IN :ids AND c.estado_sfs='EN_RESUMEN'
              AND EXISTS (SELECT 1 FROM resumen_diario_detalle d
                          JOIN resumen_diario r ON r.id=d.resumen_id
                          WHERE d.comprobante_id=c.id AND r.id=:resumenId AND r.ruc_emisor=:ruc)
              AND NOT EXISTS (SELECT 1 FROM resumen_diario_detalle d2
                              JOIN resumen_diario r2 ON r2.id=d2.resumen_id
                              WHERE d2.comprobante_id=c.id AND r2.id<>:resumenId
                                AND r2.ruc_emisor=:ruc AND r2.estado NOT IN
                                    ('LIBERADO','RECHAZADO','ACEPTADO','ACEPTADO_CON_OBSERVACION'))
            """, nativeQuery = true)
    int liberarDocumentosDelResumen(@Param("ruc") String ruc, @Param("resumenId") Long resumenId,
                                   @Param("ids") List<Long> ids);


    // =========================================================
    // DASHBOARD
    // =========================================================

    /**
     * Obtiene estadísticas de facturación directamente
     * desde MySQL sin cargar todos los comprobantes en Java.
     */
    @Query(
            value = """
                    SELECT

                        COUNT(*) AS totalComprobantes,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN tipo_comprobante = '01'
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS facturas,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN tipo_comprobante = '03'
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS boletas,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN tipo_comprobante = '07'
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS notasCredito,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN estado_sfs IN (
                                        'ACEPTADO',
                                        'ACEPTADO_CON_OBSERVACION'
                                    )
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS aceptados,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN estado_sfs IN (
                                        'PENDIENTE_ENVIO',
                                        'ENVIANDO',
                                        'PENDIENTE_RESUMEN',
                                        'EN_RESUMEN'
                                    )
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS pendientes,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN estado_sfs = 'RECHAZADO'
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS rechazados,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN estado_sfs IN (
                                        'ERROR_VALIDACION',
                                        'ERROR_ENVIO',
                                        'ERROR_ENVIO_INCIERTO',
                                        'FUERA_DE_PLAZO'
                                    )
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS errores,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN tipo_comprobante = '01'
                                     AND estado_sfs IN (
                                        'ACEPTADO',
                                        'ACEPTADO_CON_OBSERVACION'
                                     )
                                    THEN monto_total
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS montoFacturas,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN tipo_comprobante = '03'
                                     AND estado_sfs IN (
                                        'ACEPTADO',
                                        'ACEPTADO_CON_OBSERVACION'
                                     )
                                    THEN monto_total
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS montoBoletas,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN tipo_comprobante = '07'
                                     AND estado_sfs IN (
                                        'ACEPTADO',
                                        'ACEPTADO_CON_OBSERVACION'
                                     )
                                    THEN monto_total
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS montoNotasCredito

                    FROM comprobante

                    WHERE ruc_emisor = :ruc

                      AND fecha_emision >= :desde

                      AND fecha_emision < :hastaExclusivo
                    """,
            nativeQuery = true
    )
    ResumenProjection obtenerResumen(

            @Param("ruc")
            String ruc,

            @Param("desde")
            LocalDateTime desde,

            @Param("hastaExclusivo")
            LocalDateTime hastaExclusivo
    );


    // =========================================================
    // PROYECCIÓN DASHBOARD
    // =========================================================

    interface ResumenProjection {

        Long getTotalComprobantes();

        Long getFacturas();

        Long getBoletas();

        Long getNotasCredito();

        Long getAceptados();

        Long getPendientes();

        Long getRechazados();

        Long getErrores();

        BigDecimal getMontoFacturas();

        BigDecimal getMontoBoletas();

        BigDecimal getMontoNotasCredito();
    }


    // =========================================================
    // WORKER SUNAT - SENDBILL
    // =========================================================

    /**
     * Impide que dos workers envíen simultáneamente
     * el mismo comprobante individual.
     *
     * Solamente realiza:
     *
     * PENDIENTE_ENVIO
     *       ↓
     * ENVIANDO
     */
    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE comprobante

                    SET estado_sfs = 'ENVIANDO',
                        updated_at = NOW()

                    WHERE id = :id

                      AND estado_sfs = 'PENDIENTE_ENVIO'
                    """,
            nativeQuery = true
    )
    int marcarEnviando(

            @Param("id")
            Long id
    );

    // =========================================================
    // RECUPERACIÓN DE EVENTOS AFTER_COMMIT
    // =========================================================

    /**
     * Busca únicamente IDs que todavía permanecen en
     * PENDIENTE_ENVIO.
     *
     * Este query permite recuperar facturas que fueron grabadas
     * correctamente en MySQL, pero cuyo evento asíncrono no llegó
     * a ejecutarse porque el proceso se reinició o cayó justo
     * después del COMMIT.
     *
     * IMPORTANTE:
     * no toma ERROR_ENVIO. Un timeout de red puede haber ocurrido
     * después de que SUNAT recibió el documento y requiere un flujo
     * de recuperación más cuidadoso que un reenvío ciego.
     */
    @Query("""
            SELECT c.id
            FROM Comprobante c
            WHERE c.estadoSfs =
                  org.example.model.EstadoComprobante.PENDIENTE_ENVIO
            ORDER BY c.fechaEmision ASC, c.id ASC
            """)
    List<Long> buscarIdsPendientesEnvio(
            Pageable pageable
    );

}
