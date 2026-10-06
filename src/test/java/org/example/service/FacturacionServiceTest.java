package org.example.service;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.event.ComprobanteCreadoEvent;
import org.example.model.Comprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;
import org.example.model.SolicitudEmision;
import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturacionServiceTest {

    private static final String RUC = "20100130204";

    /**
     * Tasa usada por la empresa de prueba.
     *
     * Ahora el sistema es multiempresa y cada empresa
     * puede tener su propia configuración tributaria.
     */
    private static final BigDecimal IGV_EMPRESA =
            new BigDecimal("18.00");


    // =========================================================
    // MOCKS
    // =========================================================

    @Mock
    private ComprobanteRepository comprobanteRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private CorrelativoService correlativoService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private CalculoTributarioService calculoTributarioService;

    @Mock
    private ValidacionTributariaService validacionTributariaService;

    @Mock
    private UblXmlBuilderService ublXmlBuilderService;

    @Mock
    private UblValidatorService ublValidatorService;

    @Mock
    private XmlSignerService xmlSignerService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private ApplicationEventPublisher eventPublisher;


    // =========================================================
    // SERVICIO BAJO PRUEBA
    // =========================================================

    private FacturacionServiceImpl service;

    private Clock clock;

    private Empresa empresaActual;


    // =========================================================
    // CONFIGURACIÓN DE CADA TEST
    // =========================================================

    @BeforeEach
    void setUp() {

        /*
         * Usamos una fecha fija para que los tests
         * sean siempre reproducibles.
         */
        clock = Clock.fixed(
                Instant.parse("2026-09-19T19:00:00Z"),
                ZoneId.of("America/Lima")
        );

        service = new FacturacionServiceImpl(
                comprobanteRepository,
                empresaRepository,
                correlativoService,
                idempotencyService,
                calculoTributarioService,
                validacionTributariaService,
                new SaldoNotaCreditoService(comprobanteRepository, new CdrParserService()),
                ublXmlBuilderService,
                ublValidatorService,
                xmlSignerService,
                cryptoService,
                eventPublisher,
                clock
        );
    }


    // =========================================================
    // FACTURA
    // =========================================================

    @Test
    void facturaDebeQuedarPendienteEnvioYPublicarEvento() {

        ComprobanteRequest request =
                comprobanteRequest(
                        "01",
                        "F001"
                );

        prepararEmisionComprobante(
                request,
                10
        );

        var resultado =
                service.procesarComprobante(
                        RUC,
                        "KEY-FAC",
                        request
                );

        ArgumentCaptor<Comprobante> captor =
                ArgumentCaptor.forClass(
                        Comprobante.class
                );

        verify(
                comprobanteRepository
        ).saveAndFlush(
                captor.capture()
        );

        Comprobante guardado =
                captor.getValue();

        assertEquals(
                EstadoComprobante.PENDIENTE_ENVIO,
                guardado.getEstadoSfs()
        );

        assertEquals(
                "F001",
                guardado.getSerie()
        );

        assertEquals(
                10,
                guardado.getNumero()
        );

        /*
         * El comprobante debe conservar
         * la tasa tributaria utilizada.
         */
        assertEquals(
                0,
                IGV_EMPRESA.compareTo(
                        guardado.getIgvPorcentaje()
                )
        );

        assertEquals(
                "PENDIENTE_ENVIO",
                resultado.estado()
        );

        /*
         * Las facturas se envían mediante
         * el flujo individual sendBill.
         */
        verify(
                eventPublisher
        ).publishEvent(
                any(
                        ComprobanteCreadoEvent.class
                )
        );
    }


    // =========================================================
    // BOLETA
    // =========================================================

    @Test
    void boletaDebeQuedarPendienteResumenYSinEventoSendBill() {

        ComprobanteRequest request =
                comprobanteRequest(
                        "03",
                        "B001"
                );

        prepararEmisionComprobante(
                request,
                11
        );

        var resultado =
                service.procesarComprobante(
                        RUC,
                        "KEY-BOL",
                        request
                );

        ArgumentCaptor<Comprobante> captor =
                ArgumentCaptor.forClass(
                        Comprobante.class
                );

        verify(
                comprobanteRepository
        ).saveAndFlush(
                captor.capture()
        );

        Comprobante guardado =
                captor.getValue();

        assertEquals(
                EstadoComprobante.PENDIENTE_RESUMEN,
                guardado.getEstadoSfs()
        );

        /*
         * También debe conservarse la tasa
         * usada para calcular la boleta.
         */
        assertEquals(
                0,
                IGV_EMPRESA.compareTo(
                        guardado.getIgvPorcentaje()
                )
        );

        assertEquals(
                "PENDIENTE_RESUMEN",
                resultado.estado()
        );

        /*
         * Las boletas no deben ir por sendBill.
         * Se procesan mediante Resumen Diario.
         */
        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any(
                        ComprobanteCreadoEvent.class
                )
        );
    }


    // =========================================================
    // IDEMPOTENCIA
    // =========================================================

    @Test
    void idempotenciaYaProcesadaDebeDevolverMismoComprobanteSinGenerarOtro() {

        prepararEmpresa();

        SolicitudEmision solicitud =
                new SolicitudEmision();

        solicitud.setComprobanteId(
                77L
        );

        when(
                idempotencyService.iniciar(
                        eq(RUC),
                        eq("KEY"),
                        any()
                )
        ).thenReturn(
                new IdempotencyService.ContextoIdempotencia(
                        solicitud,
                        true
                )
        );

        Comprobante existente =
                new Comprobante();

        existente.setId(
                77L
        );

        existente.setEstadoSfs(
                EstadoComprobante.ACEPTADO
        );

        existente.setTipoComprobante(
                "01"
        );

        existente.setSerie(
                "F001"
        );

        existente.setNumero(
                1
        );

        existente.setIgvPorcentaje(
                IGV_EMPRESA
        );

        when(
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                77L,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        existente
                )
        );

        var resultado =
                service.procesarComprobante(
                        RUC,
                        "KEY",
                        comprobanteRequest(
                                "01",
                                "F001"
                        )
                );

        assertEquals(
                77L,
                resultado.comprobanteId()
        );

        /*
         * Si la solicitud ya fue procesada,
         * no debe volver a calcular impuestos,
         * solicitar correlativo ni generar XML.
         */
        verifyNoInteractions(
                calculoTributarioService,
                correlativoService,
                ublXmlBuilderService
        );

        verify(
                comprobanteRepository,
                never()
        ).saveAndFlush(
                any()
        );
    }


    // =========================================================
    // EMPRESA SIN CERTIFICADO
    // =========================================================

    @Test
    void empresaSinPfxDebeBloquearEmisionAntesDeGenerarCorrelativo() {

        Empresa empresa =
                empresaLista();

        empresa.setPfxBytes(
                null
        );

        when(
                empresaRepository.findByRuc(
                        RUC
                )
        ).thenReturn(
                Optional.of(
                        empresa
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.procesarComprobante(
                                RUC,
                                "KEY",
                                comprobanteRequest(
                                        "01",
                                        "F001"
                                )
                        )
        );

        /*
         * Si la empresa no está correctamente
         * configurada, no debe consumirse
         * ningún correlativo.
         */
        verifyNoInteractions(
                correlativoService
        );
    }


    // =========================================================
    // NOTA DE CRÉDITO DE FACTURA
    // =========================================================

    @Test
    void ncDeFacturaAceptadaDebeQuedarPendienteEnvioYPublicarEvento() {

        NotaCreditoRequest request =
                ncRequest(
                        "01",
                        "FC01",
                        "F001-00000001"
                );

        Comprobante origen =
                origen(
                        "01",
                        "F001",
                        EstadoComprobante.ACEPTADO
                );

        prepararNc(
                request,
                origen,
                2
        );

        var resultado =
                service.procesarNotaCredito(
                        RUC,
                        "KEY-NCF",
                        request
                );

        ArgumentCaptor<Comprobante> captor =
                ArgumentCaptor.forClass(
                        Comprobante.class
                );

        verify(
                comprobanteRepository
        ).saveAndFlush(
                captor.capture()
        );

        Comprobante nc =
                captor.getValue();

        assertEquals(
                EstadoComprobante.PENDIENTE_ENVIO,
                nc.getEstadoSfs()
        );

        assertEquals(
                "01",
                nc.getTipoComprobanteAfectado()
        );

        assertEquals(
                "F001",
                nc.getSerieComprobanteAfectado()
        );

        /*
         * La Nota de Crédito debe usar la misma
         * tasa histórica del comprobante original.
         */
        assertEquals(
                0,
                IGV_EMPRESA.compareTo(
                        nc.getIgvPorcentaje()
                )
        );

        assertEquals(
                "PENDIENTE_ENVIO",
                resultado.estado()
        );

        verify(
                eventPublisher
        ).publishEvent(
                any(
                        ComprobanteCreadoEvent.class
                )
        );
    }


    // =========================================================
    // NOTA DE CRÉDITO DE BOLETA
    // =========================================================

    @Test
    void ncDeBoletaPuedeEmitirseEstandoPendienteResumenYNoPublicaSendBill() {

        NotaCreditoRequest request =
                ncRequest(
                        "03",
                        "BC01",
                        "B001-00000001"
                );

        Comprobante origen =
                origen(
                        "03",
                        "B001",
                        EstadoComprobante.PENDIENTE_RESUMEN
                );

        prepararNc(
                request,
                origen,
                3
        );

        var resultado =
                service.procesarNotaCredito(
                        RUC,
                        "KEY-NCB",
                        request
                );

        ArgumentCaptor<Comprobante> captor =
                ArgumentCaptor.forClass(
                        Comprobante.class
                );

        verify(
                comprobanteRepository
        ).saveAndFlush(
                captor.capture()
        );

        Comprobante nc =
                captor.getValue();

        assertEquals(
                EstadoComprobante.PENDIENTE_RESUMEN,
                nc.getEstadoSfs()
        );

        assertEquals(
                0,
                IGV_EMPRESA.compareTo(
                        nc.getIgvPorcentaje()
                )
        );

        assertEquals(
                "PENDIENTE_RESUMEN",
                resultado.estado()
        );

        /*
         * Una NC asociada a una boleta
         * también debe ir por Resumen Diario.
         */
        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any(
                        ComprobanteCreadoEvent.class
                )
        );
    }


    // =========================================================
    // NC DE FACTURA NO ACEPTADA
    // =========================================================

    @Test
    void ncDeFacturaNoAceptadaDebeRechazarse() {

        NotaCreditoRequest request =
                ncRequest(
                        "01",
                        "FC01",
                        "F001-00000001"
                );

        prepararEmpresa();

        SolicitudEmision solicitud =
                new SolicitudEmision();

        when(
                idempotencyService.iniciar(
                        eq(RUC),
                        anyString(),
                        any()
                )
        ).thenReturn(
                new IdempotencyService.ContextoIdempotencia(
                        solicitud,
                        false
                )
        );

        Comprobante origen =
                origen(
                        "01",
                        "F001",
                        EstadoComprobante.PENDIENTE_ENVIO
                );

        when(
                comprobanteRepository
                        .bloquearOriginalNotaCredito(
                                RUC,
                                "01",
                                "F001",
                                1
                        )
        ).thenReturn(
                Optional.of(
                        origen
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.procesarNotaCredito(
                                RUC,
                                "KEY",
                                request
                        )
        );

        verify(
                comprobanteRepository,
                never()
        ).saveAndFlush(
                any()
        );
    }


    // =========================================================
    // CONSULTA DE ESTADO MULTIEMPRESA
    // =========================================================

    @Test
    void consultarEstadoDebeRespetarRuc() {

        Comprobante comprobante =
                new Comprobante();

        comprobante.setId(
                5L
        );

        comprobante.setTipoComprobante(
                "03"
        );

        comprobante.setSerie(
                "B001"
        );

        comprobante.setNumero(
                1
        );

        comprobante.setEstadoSfs(
                EstadoComprobante.PENDIENTE_RESUMEN
        );

        comprobante.setIgvPorcentaje(
                IGV_EMPRESA
        );

        when(
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                5L,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        comprobante
                )
        );

        var resultado =
                service.consultarEstado(
                        RUC,
                        5L
                );

        assertEquals(
                5L,
                resultado.comprobanteId()
        );

        verify(
                comprobanteRepository
        ).findByIdAndRucEmisor(
                5L,
                RUC
        );
    }


    // =========================================================
    // PREPARAR EMISIÓN FACTURA / BOLETA
    // =========================================================

    private void prepararEmisionComprobante(
            ComprobanteRequest request,
            int numero
    ) {

        prepararEmpresa();

        SolicitudEmision solicitud =
                new SolicitudEmision();

        when(
                idempotencyService.iniciar(
                        eq(RUC),
                        anyString(),
                        same(request)
                )
        ).thenReturn(
                new IdempotencyService.ContextoIdempotencia(
                        solicitud,
                        false
                )
        );

        CalculoTributarioService.Totales totales =
                totales();

        /*
         * IMPORTANTE:
         *
         * Antes el servicio utilizaba:
         *
         * calcular(request)
         *
         * Ahora, al ser multiempresa, utiliza:
         *
         * calcular(request, porcentajeIgv)
         *
         * Por eso el mock también debe recibir
         * el IGV configurado en la empresa.
         */
        when(
                calculoTributarioService.calcular(
                        same(request),
                        eq(IGV_EMPRESA)
                )
        ).thenReturn(
                totales
        );

        when(
                correlativoService.siguiente(
                        RUC,
                        request.getTipoComprobante(),
                        request.getSerie()
                )
        ).thenReturn(
                numero
        );

        when(
                ublXmlBuilderService.construirXmlUbl21(
                        eq(empresaActual),
                        same(request),
                        eq(numero),
                        same(totales),
                        any()
                )
        ).thenReturn(
                "<xml/>".getBytes()
        );

        prepararFirma();

        when(
                comprobanteRepository.saveAndFlush(
                        any(
                                Comprobante.class
                        )
                )
        ).thenAnswer(
                invocation -> {

                    Comprobante comprobante =
                            invocation.getArgument(
                                    0
                            );

                    comprobante.setId(
                            100L + numero
                    );

                    return comprobante;
                }
        );
    }


    // =========================================================
    // PREPARAR EMPRESA
    // =========================================================

    private void prepararEmpresa() {

        empresaActual =
                empresaLista();

        when(
                empresaRepository.findByRuc(
                        RUC
                )
        ).thenReturn(
                Optional.of(
                        empresaActual
                )
        );
    }


    // =========================================================
    // PREPARAR FIRMA XML
    // =========================================================

    private void prepararFirma() {

        when(
                cryptoService.descifrarPfx(
                        eq(RUC),
                        any()
                )
        ).thenReturn(
                new byte[]{
                        1,
                        2,
                        3
                }
        );

        when(
                cryptoService.descifrarPfxPassword(
                        eq(RUC),
                        anyString()
                )
        ).thenReturn(
                "plain-pfx"
        );

        when(
                xmlSignerService.firmarXml(
                        any(),
                        any(),
                        eq("plain-pfx")
                )
        ).thenReturn(
                new XmlSignerService.ResultadoFirma(
                        "<signed/>".getBytes(),
                        "HASH"
                )
        );
    }


    // =========================================================
    // PREPARAR NOTA DE CRÉDITO
    // =========================================================

    private void prepararNc(
            NotaCreditoRequest request,
            Comprobante origen,
            int numero
    ) {

        prepararEmpresa();

        SolicitudEmision solicitud =
                new SolicitudEmision();

        when(
                idempotencyService.iniciar(
                        eq(RUC),
                        anyString(),
                        same(request)
                )
        ).thenReturn(
                new IdempotencyService.ContextoIdempotencia(
                        solicitud,
                        false
                )
        );

        when(
                comprobanteRepository
                        .bloquearOriginalNotaCredito(
                                RUC,
                                request.getTipoComprobanteAfectado(),
                                origen.getSerie(),
                                origen.getNumero()
                        )
        ).thenReturn(
                Optional.of(
                        origen
                )
        );

        CalculoTributarioService.Totales totales =
                totales();

        /*
         * La Nota de Crédito debe usar la tasa
         * histórica del comprobante original.
         *
         * En este test es 18.00.
         */
        when(
                calculoTributarioService.calcular(
                        same(request),
                        eq(IGV_EMPRESA)
                )
        ).thenReturn(
                totales
        );

        when(
                correlativoService.siguiente(
                        RUC,
                        "07",
                        request.getSerie()
                )
        ).thenReturn(
                numero
        );

        when(
                ublXmlBuilderService
                        .construirXmlNotaCredito21(
                                eq(empresaActual),
                                same(request),
                                eq(numero),
                                same(totales),
                                any()
                        )
        ).thenReturn(
                "<credit/>".getBytes()
        );

        prepararFirma();

        when(
                comprobanteRepository.saveAndFlush(
                        any(
                                Comprobante.class
                        )
                )
        ).thenAnswer(
                invocation -> {

                    Comprobante comprobante =
                            invocation.getArgument(
                                    0
                            );

                    comprobante.setId(
                            200L + numero
                    );

                    return comprobante;
                }
        );
    }


    // =========================================================
    // EMPRESA DE PRUEBA
    // =========================================================

    private Empresa empresaLista() {

        Empresa empresa =
                new Empresa();

        empresa.setRuc(
                RUC
        );

        empresa.setPfxBytes(
                new byte[]{
                        9
                }
        );

        empresa.setPfxPassword(
                "PFX_CIF"
        );

        empresa.setUsuarioSol(
                "USR_CIF"
        );

        empresa.setClaveSol(
                "PWD_CIF"
        );

        /*
         * Configuración tributaria propia
         * de esta empresa.
         */
        empresa.setIgvPorcentaje(
                IGV_EMPRESA
        );

        return empresa;
    }


    // =========================================================
    // TOTALES DE PRUEBA
    // =========================================================

    private CalculoTributarioService.Totales totales() {

        return new CalculoTributarioService.Totales(

                new BigDecimal(
                        "100.00"
                ),

                BigDecimal.ZERO,

                BigDecimal.ZERO,

                new BigDecimal(
                        "18.00"
                ),

                new BigDecimal(
                        "118.00"
                ),

                List.of()
        );
    }


    // =========================================================
    // REQUEST FACTURA / BOLETA
    // =========================================================

    private ComprobanteRequest comprobanteRequest(
            String tipo,
            String serie
    ) {

        ComprobanteRequest request =
                new ComprobanteRequest();

        request.setTiendaId(
                1L
        );

        request.setTipoOperacion(
                "0101"
        );

        request.setTipoComprobante(
                tipo
        );

        request.setSerie(
                serie
        );

        request.setFormaPago(
                "Contado"
        );

        request.setTipoDocCliente(
                "1"
        );

        request.setNumDocCliente(
                "48411579"
        );

        request.setRznSocialCliente(
                "CLIENTE"
        );

        return request;
    }


    // =========================================================
    // REQUEST NOTA DE CRÉDITO
    // =========================================================

    private NotaCreditoRequest ncRequest(
            String tipoAfectado,
            String serie,
            String afectado
    ) {

        NotaCreditoRequest request =
                new NotaCreditoRequest();

        request.setTiendaId(
                1L
        );

        request.setSerie(
                serie
        );

        request.setTipoComprobanteAfectado(
                tipoAfectado
        );

        request.setNumComprobanteAfectado(
                afectado
        );

        request.setCodMotivoSunat(
                "01"
        );

        request.setDescripcionMotivo(
                "ANULACION"
        );

        request.setTipoDocCliente(
                "1"
        );

        request.setNumDocCliente(
                "48411579"
        );

        request.setRznSocialCliente(
                "CLIENTE"
        );

        return request;
    }


    // =========================================================
    // COMPROBANTE ORIGINAL PARA NOTA DE CRÉDITO
    // =========================================================

    private Comprobante origen(
            String tipo,
            String serie,
            EstadoComprobante estado
    ) {

        Comprobante comprobante =
                new Comprobante();

        comprobante.setRucEmisor(
                RUC
        );
        comprobante.setMoneda("PEN");
        comprobante.setOpExonerada(BigDecimal.ZERO);
        comprobante.setOpInafecta(BigDecimal.ZERO);

        comprobante.setTipoOperacion(
                "0101"
        );

        comprobante.setTipoComprobante(
                tipo
        );

        comprobante.setSerie(
                serie
        );

        comprobante.setNumero(
                1
        );

        comprobante.setEstadoSfs(
                estado
        );

        comprobante.setNumDocCliente(
                "48411579"
        );

        comprobante.setMontoTotal(
                new BigDecimal(
                        "118.00"
                )
        );

        comprobante.setOpGravada(
                new BigDecimal(
                        "100.00"
                )
        );

        comprobante.setIgv(
                new BigDecimal(
                        "18.00"
                )
        );

        /*
         * Muy importante para una Nota de Crédito:
         * almacenamos la tasa histórica utilizada
         * en el comprobante original.
         */
        comprobante.setIgvPorcentaje(
                IGV_EMPRESA
        );

        return comprobante;
    }
}
