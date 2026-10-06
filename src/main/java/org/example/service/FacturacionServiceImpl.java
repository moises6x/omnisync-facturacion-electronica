
package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.dto.*;
import org.example.event.ComprobanteCreadoEvent;

import org.example.model.Comprobante;
import org.example.model.DetalleComprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;

import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;


/**
 * =============================================================
 * FACTURACIÓN SERVICE
 * =============================================================
 *
 * Gestiona:
 *
 * - Factura 01
 * - Boleta 03
 * - Nota de Crédito 07
 *
 *
 * CAMBIO IMPORTANTE PARA RESUMEN DIARIO
 * -------------------------------------------------------------
 *
 * FACTURA:
 *
 *      PENDIENTE_ENVIO
 *             ↓
 *       ComprobanteCreadoEvent
 *             ↓
 *       EnvioSunatService
 *             ↓
 *          sendBill
 *
 *
 * BOLETA:
 *
 *      PENDIENTE_RESUMEN
 *             ↓
 *       Resumen Diario
 *             ↓
 *         sendSummary
 *
 *
 * NC DE FACTURA:
 *
 *      sendBill
 *
 *
 * NC DE BOLETA:
 *
 *      Resumen Diario
 */
@Service
@RequiredArgsConstructor
public class FacturacionServiceImpl
        implements FacturacionService {


    // =========================================================
    // REPOSITORIES
    // =========================================================

    private final ComprobanteRepository comprobanteRepository;

    private final EmpresaRepository empresaRepository;


    // =========================================================
    // SERVICIOS
    // =========================================================

    private final CorrelativoService correlativoService;

    private final IdempotencyService idempotencyService;

    private final CalculoTributarioService calculoTributarioService;

    private final ValidacionTributariaService validacionTributariaService;
    private final SaldoNotaCreditoService saldoNotaCreditoService;

    private final UblXmlBuilderService ublXmlBuilderService;

    private final UblValidatorService ublValidatorService;

    private final XmlSignerService xmlSignerService;

    private final CryptoService cryptoService;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Clock central configurado en America/Lima.
     * La misma fecha se persiste y se escribe en el XML.
     */
    private final Clock clock;


    // =========================================================
    // FACTURA / BOLETA
    // =========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultadoEmision procesarComprobante(

            String rucEmisor,

            String idempotencyKey,

            ComprobanteRequest request
    ) {

        // =====================================================
        // 1. EMPRESA EMISORA
        // =====================================================

        Empresa empresa =
                buscarEmpresaLista(
                        rucEmisor
                );


        // =====================================================
        // 2. IDEMPOTENCIA
        // =====================================================
        //
        // Evita crear dos comprobantes por doble clic,
        // reintentos HTTP o problemas de red.
        // =====================================================

        IdempotencyService.ContextoIdempotencia contexto =
                idempotencyService.iniciar(

                        rucEmisor,

                        idempotencyKey,

                        request
                );


        if (contexto.yaProcesada()) {

            Comprobante existente =
                    comprobanteRepository
                            .findByIdAndRucEmisor(

                                    contexto
                                            .solicitud()
                                            .getComprobanteId(),

                                    rucEmisor
                            )
                            .orElseThrow();


            return convertirResultado(

                    existente,

                    "La solicitud ya había sido procesada. "
                            +
                            "Se devuelve el mismo comprobante."
            );
        }


        // =====================================================
        // 3. CÁLCULOS TRIBUTARIOS
        // =====================================================

        BigDecimal porcentajeIgvEmpresa =
                empresa.igvPorcentajeEfectivo();


        CalculoTributarioService.Totales totales =
                calculoTributarioService.calcular(
                        request,
                        porcentajeIgvEmpresa
                );


        // =====================================================
        // 4. VALIDACIÓN TRIBUTARIA
        // =====================================================

        validacionTributariaService.validar(
                request,
                totales
        );


        // =====================================================
        // 5. CORRELATIVO
        // =====================================================

        int numero =
                correlativoService.siguiente(

                        rucEmisor,

                        request.getTipoComprobante(),

                        request.getSerie()
                );


        // =====================================================
        // 6. FECHA TRIBUTARIA ÚNICA
        // =====================================================
        //
        // Se calcula una sola vez para que XML y BD utilicen
        // exactamente el mismo día/hora en America/Lima.
        // =====================================================

        LocalDateTime fechaEmision =
                LocalDateTime.now(
                        clock
                );


        // =====================================================
        // 7. CONSTRUIR XML UBL 2.1
        // =====================================================

        byte[] xmlRaw =
                ublXmlBuilderService
                        .construirXmlUbl21(

                                empresa,

                                request,

                                numero,

                                totales,

                                fechaEmision
                        );


        // =====================================================
        // 7. FIRMAR XML
        // =====================================================

        XmlSignerService.ResultadoFirma firma =
                firmarSeguro(

                        empresa,

                        xmlRaw
                );


        // =====================================================
        // 8. VALIDAR XML FIRMADO
        // =====================================================

        ublValidatorService.validarFactura(
                firma.xmlFirmado()
        );


        // =====================================================
        // 9. NOMBRE ARCHIVO
        // =====================================================

        String nombreBase =
                String.format(

                        "%s-%s-%s-%08d",

                        rucEmisor,

                        request.getTipoComprobante(),

                        request.getSerie(),

                        numero
                );


        // =====================================================
        // 10. DETERMINAR FLUJO SUNAT
        // =====================================================
        //
        // 01 FACTURA:
        // PENDIENTE_ENVIO -> sendBill
        //
        // 03 BOLETA:
        // PENDIENTE_RESUMEN -> Resumen Diario
        //
        // =====================================================

        boolean esBoleta =
                "03".equals(
                        request.getTipoComprobante()
                );


        EstadoComprobante estadoInicial =
                esBoleta

                        ?

                        EstadoComprobante.PENDIENTE_RESUMEN

                        :

                        EstadoComprobante.PENDIENTE_ENVIO;


        // =====================================================
        // 11. CREAR ENTIDAD
        // =====================================================

        Comprobante comprobante =
                Comprobante.builder()

                        .tiendaId(
                                request.getTiendaId()
                        )

                        .rucEmisor(
                                rucEmisor
                        )

                        .tipoOperacion(
                                request.getTipoOperacion()
                        )

                        .tipoComprobante(
                                request.getTipoComprobante()
                        )

                        .serie(
                                request.getSerie()
                        )

                        .numero(
                                numero
                        )

                        .fechaEmision(
                                fechaEmision
                        )

                        .formaPago(
                                request.getFormaPago()
                        )

                        .tipoDocCliente(
                                request.getTipoDocCliente()
                        )

                        .numDocCliente(
                                request.getNumDocCliente()
                        )

                        .rznSocialCliente(
                                request.getRznSocialCliente()
                        )

                        .direccionCliente(
                                request.getDireccionCliente()
                        )

                        .opGravada(
                                totales.opGravada()
                        )

                        .opExonerada(
                                totales.opExonerada()
                        )

                        .opInafecta(
                                totales.opInafecta()
                        )

                        .igvPorcentaje(
                                porcentajeIgvEmpresa
                        )

                        .igv(
                                totales.igv()
                        )

                        .montoTotal(
                                totales.total()
                        )

                        .nombreArchivoXml(
                                nombreBase + ".xml"
                        )

                        .xmlFirmadoBytes(
                                firma.xmlFirmado()
                        )

                        .hashCpe(
                                firma.hashCpe()
                        )

                        .estadoSfs(
                                estadoInicial
                        )

                        .build();


        // =====================================================
        // 12. ITEMS
        // =====================================================

        for (
                CalculoTributarioService.ItemCalculado item :
                totales.items()
        ) {

            DetalleComprobante detalle =
                    DetalleComprobante.builder()

                            .itemOrden(
                                    item.orden()
                            )

                            .codigoProducto(
                                    "-"
                            )

                            .descripcion(
                                    item.descripcion()
                            )

                            .unidadMedida(
                                    item.unidadMedida()
                            )

                            .cantidad(
                                    item.cantidad()
                            )

                            .valorUnitario(
                                    item.valorUnitario()
                            )

                            .precioUnitario(
                                    item.precioUnitario()
                            )

                            .igvItem(
                                    item.igv()
                            )

                            .porcentajeIgv(
                                    item.porcentajeIgv()
                            )

                            .tipoAfectacionIgv(
                                    item.tipoAfectacionIgv()
                            )

                            .montoTotalItem(
                                    item.total()
                            )

                            .build();


            comprobante.addDetalle(
                    detalle
            );
        }


        // =====================================================
        // 13. GUARDAR
        // =====================================================

        Comprobante guardado =
                comprobanteRepository.saveAndFlush(
                        comprobante
                );


        // =====================================================
        // 14. VINCULAR IDEMPOTENCIA
        // =====================================================

        idempotencyService.vincularComprobante(

                contexto.solicitud(),

                guardado.getId()
        );


        // =====================================================
        // 15. ENVÍO INDIVIDUAL
        // =====================================================
        //
        // Solamente las FACTURAS disparan el worker sendBill.
        //
        // Las BOLETAS quedan esperando al Resumen Diario.
        // =====================================================

        if (!esBoleta) {

            eventPublisher.publishEvent(

                    new ComprobanteCreadoEvent(
                            guardado.getId()
                    )
            );
        }


        // =====================================================
        // 16. RESPUESTA
        // =====================================================

        String mensaje =
                esBoleta

                        ?

                        "Boleta generada, firmada y pendiente de inclusión en Resumen Diario SUNAT."

                        :

                        "Factura generada, firmada, validada y pendiente de respuesta SUNAT.";


        return convertirResultado(

                guardado,

                mensaje
        );
    }


    // =========================================================
    // NOTA DE CRÉDITO
    // =========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultadoEmision procesarNotaCredito(

            String rucEmisor,

            String idempotencyKey,

            NotaCreditoRequest request
    ) {

        // =====================================================
        // 1. EMPRESA
        // =====================================================

        Empresa empresa =
                buscarEmpresaLista(
                        rucEmisor
                );


        // =====================================================
        // 2. IDEMPOTENCIA
        // =====================================================

        IdempotencyService.ContextoIdempotencia contexto =
                idempotencyService.iniciar(

                        rucEmisor,

                        idempotencyKey,

                        request
                );


        if (contexto.yaProcesada()) {

            Comprobante existente =
                    comprobanteRepository
                            .findByIdAndRucEmisor(

                                    contexto
                                            .solicitud()
                                            .getComprobanteId(),

                                    rucEmisor
                            )
                            .orElseThrow();


            return convertirResultado(

                    existente,

                    "Solicitud repetida. "
                            +
                            "Se devuelve la misma Nota de Crédito."
            );
        }


        // =====================================================
        // 3. VALIDAR REQUEST
        // =====================================================

        validacionTributariaService.validar(
                request
        );


        // =====================================================
        // 4. SEPARAR SERIE / NÚMERO ORIGINAL
        // =====================================================

        String[] partes =
                request
                        .getNumComprobanteAfectado()
                        .split("-");


        if (partes.length != 2) {

            throw new IllegalArgumentException(
                    "Formato de comprobante afectado inválido."
            );
        }


        String serieOrigen =
                partes[0]
                        .trim();


        Integer numeroOrigen;


        try {

            numeroOrigen =
                    Integer.valueOf(
                            partes[1]
                    );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Número del comprobante afectado inválido."
            );
        }


        // =====================================================
        // 5. BUSCAR COMPROBANTE ORIGINAL
        // =====================================================

        Comprobante origen =
                comprobanteRepository
                        .bloquearOriginalNotaCredito(

                                rucEmisor,

                                request.getTipoComprobanteAfectado(),

                                serieOrigen,

                                numeroOrigen
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "El comprobante afectado no existe."
                                        )
                        );


        // =====================================================
        // 6. IDENTIFICAR SI LA NC ES DE BOLETA
        // =====================================================

        boolean notaDeBoleta =
                "03".equals(
                        origen.getTipoComprobante()
                );


        boolean notaDeFactura =
                "01".equals(
                        origen.getTipoComprobante()
                );


        if (
                !notaDeBoleta
                        &&
                        !notaDeFactura
        ) {

            throw new IllegalArgumentException(
                    "El comprobante afectado debe ser una Factura o Boleta."
            );
        }


        // =====================================================
        // 7. VALIDAR ESTADO DEL ORIGINAL
        // =====================================================

        boolean aceptado =

                origen.getEstadoSfs()
                        ==
                        EstadoComprobante.ACEPTADO

                        ||

                        origen.getEstadoSfs()
                                ==
                                EstadoComprobante.ACEPTADO_CON_OBSERVACION;


        /*
         * FACTURA:
         *
         * debe haber sido aceptada previamente porque
         * la Nota de Crédito se enviará individualmente.
         */
        if (
                notaDeFactura
                        &&
                        !aceptado
        ) {

            throw new IllegalArgumentException(
                    "La factura afectada aún no está aceptada por SUNAT."
            );
        }


        /*
         * BOLETA:
         *
         * puede encontrarse:
         *
         * PENDIENTE_RESUMEN
         * EN_RESUMEN
         * ACEPTADO
         * ACEPTADO_CON_OBSERVACION
         */
        if (notaDeBoleta) {

            boolean estadoPermitidoBoleta =

                    origen.getEstadoSfs()
                            ==
                            EstadoComprobante.PENDIENTE_RESUMEN

                            ||

                            origen.getEstadoSfs()
                                    ==
                                    EstadoComprobante.EN_RESUMEN

                            ||

                            aceptado;


            if (!estadoPermitidoBoleta) {

                throw new IllegalArgumentException(
                        "La boleta afectada no se encuentra en un estado válido para emitir la Nota de Crédito."
                );
            }
        }


        // =====================================================
        // 8. VALIDAR CLIENTE
        // =====================================================

        if (
                !Objects.equals(

                        origen.getNumDocCliente(),

                        request.getNumDocCliente()
                )
        ) {

            throw new IllegalArgumentException(
                    "El cliente de la Nota de Crédito no coincide con el comprobante original."
            );
        }


        // =====================================================
        // 9. CÁLCULOS
        // =====================================================

        BigDecimal porcentajeIgvOrigen =
                resolverPorcentajeIgvHistorico(
                        origen,
                        empresa
                );


        CalculoTributarioService.Totales totales =
                calculoTributarioService.calcular(
                        request,
                        porcentajeIgvOrigen
                );


        // =====================================================
        // 9.1 VALIDAR NC CONTRA EL COMPROBANTE ORIGINAL
        // =====================================================
        //
        // Aquí ya tenemos:
        //
        // - el documento original recuperado de NUESTRA empresa
        // - los importes recalculados por el backend
        //
        // Por eso podemos impedir, entre otras cosas:
        //
        // - cambiar arbitrariamente de cliente
        // - una NC mayor que el comprobante original
        // - anulación total con montos parciales
        // - corrección de descripción alterando importes
        // =====================================================

        validacionTributariaService
                .validarContraComprobanteOriginal(

                        request,

                        origen,

                        totales
                );


        // =====================================================
        // El original sigue bloqueado hasta persistir la NC y vincular idempotencia.
        // Las NC existentes se leen FOR UPDATE, incluso si idempotencia ya estableció un snapshot.
        saldoNotaCreditoService.validar(origen, request.getCodMotivoSunat(), totales);

        // 10. CORRELATIVO DE NC
        // =====================================================

        int numero =
                correlativoService.siguiente(

                        rucEmisor,

                        "07",

                        request.getSerie()
                );


        // =====================================================
        // 11. FECHA TRIBUTARIA ÚNICA DE LA NOTA
        // =====================================================

        LocalDateTime fechaEmisionNota =
                LocalDateTime.now(
                        clock
                );


        // =====================================================
        // 12. XML NOTA DE CRÉDITO UBL 2.1
        // =====================================================

        byte[] xmlRaw =
                ublXmlBuilderService
                        .construirXmlNotaCredito21(

                                empresa,

                                request,

                                numero,

                                totales,

                                fechaEmisionNota
                        );


        // =====================================================
        // 12. FIRMA
        // =====================================================

        XmlSignerService.ResultadoFirma firma =
                firmarSeguro(

                        empresa,

                        xmlRaw
                );


        // =====================================================
        // 13. VALIDAR XML FIRMADO
        // =====================================================

        ublValidatorService.validarNotaCredito(
                firma.xmlFirmado()
        );


        // =====================================================
        // 14. NOMBRE ARCHIVO
        // =====================================================

        String nombreBase =
                String.format(

                        "%s-07-%s-%08d",

                        rucEmisor,

                        request.getSerie(),

                        numero
                );


        // =====================================================
        // 15. ESTADO INICIAL
        // =====================================================
        //
        // NC de Factura:
        // PENDIENTE_ENVIO -> sendBill
        //
        // NC de Boleta:
        // PENDIENTE_RESUMEN -> Resumen Diario
        //
        // =====================================================

        EstadoComprobante estadoInicial =
                notaDeBoleta

                        ?

                        EstadoComprobante.PENDIENTE_RESUMEN

                        :

                        EstadoComprobante.PENDIENTE_ENVIO;


        // =====================================================
        // 16. CREAR NOTA
        // =====================================================

        Comprobante nota =
                Comprobante.builder()

                        .tiendaId(
                                request.getTiendaId()
                        )

                        .rucEmisor(
                                rucEmisor
                        )

                        .tipoOperacion(
                                origen.getTipoOperacion()
                        )

                        .tipoComprobante(
                                "07"
                        )

                        .serie(
                                request.getSerie()
                        )

                        .numero(
                                numero
                        )

                        .fechaEmision(
                                fechaEmisionNota
                        )

                        .formaPago(
                                "Contado"
                        )

                        .tipoDocCliente(
                                request.getTipoDocCliente()
                        )

                        .numDocCliente(
                                request.getNumDocCliente()
                        )

                        .rznSocialCliente(
                                request.getRznSocialCliente()
                        )

                        /*
                         * =================================================
                         * REFERENCIA AL DOCUMENTO ORIGINAL
                         * =================================================
                         *
                         * Antes esta información existía solamente en
                         * NotaCreditoRequest.
                         *
                         * Ahora queda persistida.
                         */
                        .tipoComprobanteAfectado(
                                origen.getTipoComprobante()
                        )

                        .serieComprobanteAfectado(
                                origen.getSerie()
                        )

                        .numeroComprobanteAfectado(
                                origen.getNumero()
                        )

                        .codigoMotivoNota(
                                request.getCodMotivoSunat()
                        )

                        .descripcionMotivoNota(
                                request.getDescripcionMotivo()
                        )

                        .opGravada(
                                totales.opGravada()
                        )

                        .opExonerada(
                                totales.opExonerada()
                        )

                        .opInafecta(
                                totales.opInafecta()
                        )

                        .igvPorcentaje(
                                porcentajeIgvOrigen
                        )

                        .igv(
                                totales.igv()
                        )

                        .montoTotal(
                                totales.total()
                        )

                        .nombreArchivoXml(
                                nombreBase + ".xml"
                        )

                        .xmlFirmadoBytes(
                                firma.xmlFirmado()
                        )

                        .hashCpe(
                                firma.hashCpe()
                        )

                        .estadoSfs(
                                estadoInicial
                        )

                        .build();


        // =====================================================
        // 17. ITEMS
        // =====================================================

        for (
                CalculoTributarioService.ItemCalculado item :
                totales.items()
        ) {

            DetalleComprobante detalle =
                    DetalleComprobante.builder()

                            .itemOrden(
                                    item.orden()
                            )

                            .codigoProducto(
                                    "-"
                            )

                            .descripcion(
                                    item.descripcion()
                            )

                            .unidadMedida(
                                    item.unidadMedida()
                            )

                            .cantidad(
                                    item.cantidad()
                            )

                            .valorUnitario(
                                    item.valorUnitario()
                            )

                            .precioUnitario(
                                    item.precioUnitario()
                            )

                            .igvItem(
                                    item.igv()
                            )

                            .porcentajeIgv(
                                    item.porcentajeIgv()
                            )

                            .tipoAfectacionIgv(
                                    item.tipoAfectacionIgv()
                            )

                            .montoTotalItem(
                                    item.total()
                            )

                            .build();


            nota.addDetalle(
                    detalle
            );
        }


        // =====================================================
        // 18. GUARDAR
        // =====================================================

        Comprobante guardada =
                comprobanteRepository.saveAndFlush(
                        nota
                );


        // =====================================================
        // 19. VINCULAR IDEMPOTENCIA
        // =====================================================

        idempotencyService.vincularComprobante(

                contexto.solicitud(),

                guardada.getId()
        );


        // =====================================================
        // 20. ENVÍO
        // =====================================================
        //
        // NC factura:
        // evento -> sendBill
        //
        // NC boleta:
        // NO evento -> Resumen Diario
        //
        // =====================================================

        if (!notaDeBoleta) {

            eventPublisher.publishEvent(

                    new ComprobanteCreadoEvent(
                            guardada.getId()
                    )
            );
        }


        // =====================================================
        // 21. RESPUESTA
        // =====================================================

        String mensaje =
                notaDeBoleta

                        ?

                        "Nota de Crédito de Boleta generada y pendiente de inclusión en Resumen Diario SUNAT."

                        :

                        "Nota de Crédito generada, firmada, validada y pendiente de respuesta SUNAT.";


        return convertirResultado(

                guardada,

                mensaje
        );
    }


    // =========================================================
    // CONSULTAR ESTADO
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ResultadoEmision consultarEstado(

            String rucEmisor,

            Long comprobanteId
    ) {

        Comprobante comprobante =
                comprobanteRepository
                        .findByIdAndRucEmisor(

                                comprobanteId,

                                rucEmisor
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Comprobante no encontrado."
                                        )
                        );


        return convertirResultado(

                comprobante,

                comprobante.getMensajeSunat() != null

                        ?

                        comprobante.getMensajeSunat()

                        :

                        mensajeSegunEstado(
                                comprobante.getEstadoSfs()
                        )
        );
    }


    // =========================================================
    // LISTAR
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ComprobanteListadoResponse>
    listarComprobantes(

            String rucEmisor,

            String tipo,

            String estado,

            LocalDate desde,

            LocalDate hasta,

            int pagina,

            int tamanio
    ) {

        // =====================================================
        // PAGINACIÓN
        // =====================================================

        if (pagina < 0) {

            throw new IllegalArgumentException(
                    "La página no puede ser negativa."
            );
        }


        if (
                tamanio < 1
                        ||
                        tamanio > 200
        ) {

            throw new IllegalArgumentException(
                    "El tamaño de página debe estar entre 1 y 200."
            );
        }


        // =====================================================
        // TIPO
        // =====================================================

        String tipoNormalizado =
                null;


        if (
                tipo != null
                        &&
                        !tipo.isBlank()
        ) {

            tipoNormalizado =
                    tipo.trim();


            if (
                    !"01".equals(tipoNormalizado)

                            &&

                            !"03".equals(tipoNormalizado)

                            &&

                            !"07".equals(tipoNormalizado)
            ) {

                throw new IllegalArgumentException(
                        "Tipo de comprobante inválido. Use 01, 03 o 07."
                );
            }
        }


        // =====================================================
        // ESTADO
        // =====================================================

        EstadoComprobante estadoEnum =
                null;


        if (
                estado != null
                        &&
                        !estado.isBlank()
        ) {

            try {

                estadoEnum =
                        EstadoComprobante.valueOf(

                                estado
                                        .trim()
                                        .toUpperCase(
                                                Locale.ROOT
                                        )
                        );

            } catch (IllegalArgumentException e) {

                throw new IllegalArgumentException(
                        "Estado de comprobante inválido."
                );
            }
        }


        // =====================================================
        // FECHAS
        // =====================================================

        if (
                desde != null
                        &&
                        hasta != null
                        &&
                        desde.isAfter(
                                hasta
                        )
        ) {

            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a la fecha hasta."
            );
        }


        LocalDateTime fechaDesde =
                desde != null

                        ?

                        desde.atStartOfDay()

                        :

                        LocalDateTime.of(
                                1900,
                                1,
                                1,
                                0,
                                0
                        );


        LocalDateTime fechaHastaExclusiva =
                hasta != null

                        ?

                        hasta
                                .plusDays(1)
                                .atStartOfDay()

                        :

                        LocalDateTime.of(
                                9999,
                                12,
                                31,
                                23,
                                59,
                                59
                        );


        PageRequest pageable =
                PageRequest.of(
                        pagina,
                        tamanio
                );


        Page<Comprobante> resultado =
                comprobanteRepository.buscarHistorial(

                        rucEmisor,

                        tipoNormalizado,

                        estadoEnum,

                        fechaDesde,

                        fechaHastaExclusiva,

                        pageable
                );


        return new PaginaResponse<>(

                resultado
                        .getContent()
                        .stream()
                        .map(
                                this::convertirListado
                        )
                        .toList(),

                resultado.getNumber(),

                resultado.getSize(),

                resultado.getTotalElements(),

                resultado.getTotalPages(),

                resultado.isFirst(),

                resultado.isLast()
        );
    }


    // =========================================================
    // DASHBOARD
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ResumenFacturacionResponse obtenerResumen(

            String rucEmisor,

            String periodo,

            LocalDate desde,

            LocalDate hasta
    ) {

        RangoFechas rango =
                resolverRango(

                        periodo,

                        desde,

                        hasta
                );


        LocalDateTime fechaDesde =
                rango
                        .desde()
                        .atStartOfDay();


        LocalDateTime fechaHastaExclusiva =
                rango
                        .hasta()
                        .plusDays(1)
                        .atStartOfDay();


        ComprobanteRepository.ResumenProjection resumen =
                comprobanteRepository.obtenerResumen(

                        rucEmisor,

                        fechaDesde,

                        fechaHastaExclusiva
                );


        BigDecimal montoFacturas =
                ceroSiNull(
                        resumen.getMontoFacturas()
                );


        BigDecimal montoBoletas =
                ceroSiNull(
                        resumen.getMontoBoletas()
                );


        BigDecimal montoNotasCredito =
                ceroSiNull(
                        resumen.getMontoNotasCredito()
                );


        // =====================================================
        // VENTA BRUTA
        // =====================================================

        BigDecimal ventasBrutas =
                montoFacturas.add(
                        montoBoletas
                );


        // =====================================================
        // VENTA NETA
        // =====================================================

        BigDecimal ventasNetas =
                ventasBrutas.subtract(
                        montoNotasCredito
                );


        return new ResumenFacturacionResponse(

                rango.periodo(),

                rango.desde(),

                rango.hasta(),

                longSeguro(
                        resumen.getTotalComprobantes()
                ),

                longSeguro(
                        resumen.getFacturas()
                ),

                longSeguro(
                        resumen.getBoletas()
                ),

                longSeguro(
                        resumen.getNotasCredito()
                ),

                longSeguro(
                        resumen.getAceptados()
                ),

                longSeguro(
                        resumen.getPendientes()
                ),

                longSeguro(
                        resumen.getRechazados()
                ),

                longSeguro(
                        resumen.getErrores()
                ),

                montoFacturas,

                montoBoletas,

                montoNotasCredito,

                ventasBrutas,

                ventasNetas
        );
    }


    // =========================================================
    // CONVERTIR LISTADO
    // =========================================================

    private ComprobanteListadoResponse convertirListado(

            Comprobante comprobante
    ) {

        String numeroCompleto =
                String.format(

                        "%s-%08d",

                        comprobante.getSerie(),

                        comprobante.getNumero()
                );


        String descripcionTipo =
                switch (
                        comprobante.getTipoComprobante()
                        ) {

                    case "01" ->
                            "FACTURA";

                    case "03" ->
                            "BOLETA";

                    case "07" ->
                            "NOTA DE CREDITO";

                    default ->
                            "COMPROBANTE";
                };


        return new ComprobanteListadoResponse(

                comprobante.getId(),

                comprobante.getTipoComprobante(),

                descripcionTipo,

                comprobante.getSerie(),

                comprobante.getNumero(),

                numeroCompleto,

                comprobante.getFechaEmision(),

                comprobante.getTipoDocCliente(),

                comprobante.getNumDocCliente(),

                comprobante.getRznSocialCliente(),

                comprobante.getMontoTotal(),

                comprobante
                        .getEstadoSfs()
                        .name(),

                comprobante.getMensajeSunat()
        );
    }


    // =========================================================
    // RANGOS DASHBOARD
    // =========================================================

    private RangoFechas resolverRango(

            String periodo,

            LocalDate desde,

            LocalDate hasta
    ) {

        LocalDate hoy =
                LocalDate.now(
                        clock
                );


        String periodoNormalizado =
                periodo == null

                        ?

                        "MES"

                        :

                        periodo
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );


        return switch (
                periodoNormalizado
                ) {

            case "HOY", "DIA" ->

                    new RangoFechas(
                            "HOY",
                            hoy,
                            hoy
                    );


            case "MES" ->

                    new RangoFechas(

                            "MES",

                            hoy.withDayOfMonth(1),

                            hoy
                    );


            case "ANIO", "AÑO" ->

                    new RangoFechas(

                            "ANIO",

                            hoy.withDayOfYear(1),

                            hoy
                    );


            case "RANGO" -> {

                if (
                        desde == null
                                ||
                                hasta == null
                ) {

                    throw new IllegalArgumentException(
                            "Para periodo RANGO debe enviar desde y hasta."
                    );
                }


                if (
                        desde.isAfter(
                                hasta
                        )
                ) {

                    throw new IllegalArgumentException(
                            "La fecha desde no puede ser posterior a la fecha hasta."
                    );
                }


                yield new RangoFechas(

                        "RANGO",

                        desde,

                        hasta
                );
            }


            default ->

                    throw new IllegalArgumentException(
                            "Periodo inválido. Use HOY, MES, ANIO o RANGO."
                    );
        };
    }


    private record RangoFechas(

            String periodo,

            LocalDate desde,

            LocalDate hasta
    ) {
    }


    // =========================================================
    // EMPRESA
    // =========================================================

    /**
     * Devuelve la tasa IGV con la que se emitió el comprobante
     * original. Los documentos nuevos la guardan explícitamente.
     *
     * Para documentos BETA previos a esta mejora existe un fallback
     * calculado desde IGV / operación gravada.
     */
    private BigDecimal resolverPorcentajeIgvHistorico(
            Comprobante origen,
            Empresa empresa
    ) {

        if (origen.getIgvPorcentaje() != null) {

            return origen.getIgvPorcentaje()
                    .setScale(
                            2,
                            java.math.RoundingMode.HALF_UP
                    );
        }

        if (
                origen.getOpGravada() != null
                        && origen.getOpGravada().compareTo(BigDecimal.ZERO) > 0
                        && origen.getIgv() != null
        ) {

            return origen.getIgv()
                    .multiply(
                            new BigDecimal("100")
                    )
                    .divide(
                            origen.getOpGravada(),
                            2,
                            java.math.RoundingMode.HALF_UP
                    );
        }

        return empresa.igvPorcentajeEfectivo();
    }


    private Empresa buscarEmpresaLista(
            String ruc
    ) {

        Empresa empresa =
                empresaRepository
                        .findByRuc(
                                ruc
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Empresa no configurada."
                                        )
                        );


        if (
                empresa.getPfxBytes() == null
                        ||
                        empresa.getPfxBytes().length == 0
        ) {

            throw new IllegalArgumentException(
                    "La empresa no tiene certificado PFX configurado."
            );
        }


        if (
                empresa.getPfxPassword() == null
                        ||
                        empresa.getPfxPassword().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "La empresa no tiene contraseña PFX."
            );
        }


        if (
                empresa.getUsuarioSol() == null
                        ||
                        empresa.getUsuarioSol().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "No existe usuario SOL."
            );
        }


        if (
                empresa.getClaveSol() == null
                        ||
                        empresa.getClaveSol().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "No existe clave SOL."
            );
        }


        if (
                empresa.getCertificadoVigenteHasta() != null

                        &&

                        empresa
                                .getCertificadoVigenteHasta()
                                .isBefore(
                                        LocalDate.now()
                                )
        ) {

            throw new IllegalArgumentException(
                    "El certificado digital de la empresa está vencido."
            );
        }


        return empresa;
    }


    // =========================================================
    // FIRMA SEGURA
    // =========================================================

    private XmlSignerService.ResultadoFirma firmarSeguro(

            Empresa empresa,

            byte[] xmlRaw
    ) {

        byte[] pfxPlano =
                cryptoService.descifrarPfx(

                        empresa.getRuc(),

                        empresa.getPfxBytes()
                );


        String pfxPasswordPlano =
                cryptoService.descifrarPfxPassword(

                        empresa.getRuc(),

                        empresa.getPfxPassword()
                );


        try {

            return xmlSignerService.firmarXml(

                    xmlRaw,

                    pfxPlano,

                    pfxPasswordPlano
            );

        } finally {

            /*
             * Sobrescribimos los bytes del certificado
             * desencriptado una vez utilizados.
             */
            Arrays.fill(
                    pfxPlano,
                    (byte) 0
            );
        }
    }


    // =========================================================
    // RESULTADO EMISIÓN
    // =========================================================

    private ResultadoEmision convertirResultado(

            Comprobante comprobante,

            String mensaje
    ) {

        return new ResultadoEmision(

                comprobante.getId(),

                comprobante
                        .getEstadoSfs()
                        .name(),

                comprobante.getTipoComprobante(),

                comprobante.getSerie(),

                comprobante.getNumero(),

                mensaje
        );
    }


    // =========================================================
    // MENSAJE SEGÚN ESTADO
    // =========================================================

    private String mensajeSegunEstado(
            EstadoComprobante estado
    ) {

        if (estado == null) {

            return "Estado no disponible.";
        }


        return switch (estado) {

            case PENDIENTE_ENVIO ->

                    "Pendiente de envío individual a SUNAT.";

            case ENVIANDO ->

                    "El comprobante está siendo enviado a SUNAT.";

            case PENDIENTE_RESUMEN ->

                    "Pendiente de inclusión en Resumen Diario SUNAT.";

            case EN_RESUMEN ->

                    "El comprobante forma parte de un Resumen Diario en procesamiento.";

            case ACEPTADO ->

                    "Comprobante aceptado.";

            case ACEPTADO_CON_OBSERVACION ->

                    "Comprobante aceptado con observaciones.";

            case RECHAZADO ->

                    "Comprobante rechazado.";

            case ERROR_VALIDACION ->

                    "Error de validación.";

            case ERROR_ENVIO ->

                    "Error durante el envío.";

            case ERROR_ENVIO_INCIERTO ->

                    "El envío quedó en estado incierto y requiere verificación antes de reintentar.";

            case FUERA_DE_PLAZO ->

                    "El comprobante quedó fuera del plazo configurado para envío automático a SUNAT.";

            case ANULADO ->

                    "Numeración anulada/dada de baja correctamente.";
        };
    }


    // =========================================================
    // UTILIDADES
    // =========================================================

    private BigDecimal ceroSiNull(
            BigDecimal valor
    ) {

        return valor != null

                ?

                valor

                :

                BigDecimal.ZERO;
    }


    private long longSeguro(
            Long valor
    ) {

        return valor != null

                ?

                valor

                :

                0L;
    }
}
