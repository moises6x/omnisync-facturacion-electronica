package org.example.model;

/** Estado operativo de una Comunicación de Baja (RA) enviada por sendSummary. */
public enum EstadoComunicacionBaja {
    CREADO,
    ENVIANDO,
    TICKET_GENERADO,
    EN_PROCESO,
    ACEPTADO,
    RECHAZADO,
    ERROR_ENVIO,
    ERROR_ENVIO_INCIERTO,
    ERROR_CONSULTA,
    FUERA_DE_PLAZO
}
