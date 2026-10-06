package org.example.model;

public enum EstadoComprobante {

    PENDIENTE_RESUMEN,

    EN_RESUMEN,

    PENDIENTE_ENVIO,

    ENVIANDO,

    ACEPTADO,

    ACEPTADO_CON_OBSERVACION,

    /** Documento aceptado/informado que posteriormente fue anulado por el flujo fiscal permitido. */
    ANULADO,

    RECHAZADO,

    ERROR_VALIDACION,

    ERROR_ENVIO,

    /**
     * Hubo un corte/timeout cuando no es posible saber con certeza
     * si SUNAT recibió el request. No debe reintentarse a ciegas.
     */
    ERROR_ENVIO_INCIERTO,

    /**
     * El documento quedó fuera del plazo configurado para el flujo
     * SUNAT y requiere intervención/revisión.
     */
    FUERA_DE_PLAZO
}
