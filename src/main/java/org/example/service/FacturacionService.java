package org.example.service;

import org.example.dto.*;

import java.time.LocalDate;

public interface FacturacionService {


    // =========================================================
    // EMITIR FACTURA / BOLETA
    // =========================================================

    ResultadoEmision procesarComprobante(
            String rucEmisor,
            String idempotencyKey,
            ComprobanteRequest request
    );


    // =========================================================
    // NOTA DE CRÉDITO
    // =========================================================

    ResultadoEmision procesarNotaCredito(
            String rucEmisor,
            String idempotencyKey,
            NotaCreditoRequest request
    );


    // =========================================================
    // CONSULTAR ESTADO POR ID INTERNO
    // =========================================================

    ResultadoEmision consultarEstado(
            String rucEmisor,
            Long comprobanteId
    );


    // =========================================================
    // HISTORIAL
    // =========================================================

    PaginaResponse<ComprobanteListadoResponse> listarComprobantes(

            String rucEmisor,

            String tipo,

            String estado,

            LocalDate desde,

            LocalDate hasta,

            int pagina,

            int tamanio
    );


    // =========================================================
    // RESUMEN / DASHBOARD
    // =========================================================

    ResumenFacturacionResponse obtenerResumen(

            String rucEmisor,

            String periodo,

            LocalDate desde,

            LocalDate hasta
    );
}