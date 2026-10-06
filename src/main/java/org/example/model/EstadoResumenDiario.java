
package org.example.model;

/**
 * =============================================================
 * ESTADO RESUMEN DIARIO
 * =============================================================
 *
 * Representa el ciclo de vida técnico de un Resumen Diario SUNAT.
 *
 * La separación entre estados es importante porque sendSummary es
 * asíncrono: primero se envía el ZIP, SUNAT devuelve un ticket y luego
 * debemos consultar ese ticket mediante getStatus.
 */
public enum EstadoResumenDiario {

    /** El resumen fue creado en BD, pero todavía no se envió. */
    CREADO,

    /** Un proceso tomó el resumen para ejecutar sendSummary. */
    ENVIANDO,

    /** SUNAT recibió sendSummary y devolvió un ticket. */
    TICKET_GENERADO,

    /** getStatus indicó que SUNAT todavía está procesando el ticket. */
    PROCESANDO,

    /** El CDR del Resumen Diario fue aceptado sin observaciones. */
    ACEPTADO,

    /** El CDR fue aceptado, pero SUNAT devolvió observaciones. */
    ACEPTADO_CON_OBSERVACION,

    /** SUNAT devolvió una CDR de rechazo. */
    RECHAZADO,

    /** El XML no pudo construirse, firmarse o validarse localmente. */
    ERROR_VALIDACION,

    /** SUNAT respondió con un error explícito antes de entregar ticket. */
    ERROR_ENVIO,

    /**
     * Hubo timeout, caída de red o respuesta ambigua durante sendSummary.
     *
     * NO se reintenta automáticamente porque SUNAT podría haber recibido
     * el archivo aunque nuestro servidor no haya alcanzado a leer el ticket.
     */
    ERROR_ENVIO_INCIERTO,

    /** Existe ticket, pero temporalmente no pudimos consultar getStatus. */
    ERROR_CONSULTA,

    /**
     * Los comprobantes de un resumen fallido/rechazado fueron devueltos
     * conscientemente a PENDIENTE_RESUMEN para poder generar otro envío.
     */
    LIBERADO
}