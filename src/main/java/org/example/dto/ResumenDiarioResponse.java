
package org.example.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Respuesta segura para API.
 *
 * No expone XML, CDR, credenciales ni certificado; sólo el estado operativo
 * que necesita el frontend para informar al usuario.
 */
public record ResumenDiarioResponse(

        Long id,

        String identificador,

        String nombreArchivoXml,

        LocalDate fechaDocumentos,

        LocalDate fechaGeneracion,

        Integer correlativo,

        Integer cantidadDocumentos,

        String estado,

        String ticketSunat,

        String codigoRespuestaSunat,

        String mensajeSunat,

        Integer intentosEnvio,

        Integer intentosConsulta,

        LocalDateTime enviadoEn,

        LocalDateTime ultimaConsultaEn,

        LocalDateTime finalizadoEn,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}
