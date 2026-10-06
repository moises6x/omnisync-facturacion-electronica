package org.example.service;


import org.example.model.*;
import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;
import org.example.repository.ResumenDiarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)


public class ResumenDiarioServiceTestt  {


    // =========================================================
    // CONSTANTES
    // =========================================================

    private static final String RUC =
            "20100130204";

    private static final String RUC_EMPRESA_B =
            "20600000001";

    private static final Long RESUMEN_ID =
            10L;

    private static final String TICKET =
            "TICKET-TEST-001";

    private static final LocalDate FECHA_DOCUMENTOS =
            LocalDate.of(
                    2026,
                    9,
                    19
            );


    // =========================================================
    // REPOSITORIES MOCK
    // =========================================================

    @Mock
    private ResumenDiarioRepository resumenRepository;

    @Mock
    private ComprobanteRepository comprobanteRepository;

    @Mock
    private EmpresaRepository empresaRepository;


    // =========================================================
    // SERVICIOS MOCK
    // =========================================================

    @Mock
    private SecuenciaResumenDiarioService secuenciaService;

    @Mock
    private ResumenDiarioXmlService xmlService;

    @Mock
    private XmlSignerService xmlSignerService;

    @Mock
    private UblValidatorService ublValidatorService;

    @Mock
    private SunatDirectSoapClient sunatClient;

    @Mock
    private CdrParserService cdrParserService;
    @Mock private RespuestaSunatRecibidaService respuestaSunatRecibidaService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private SunatPlazoService sunatPlazoService;


    // =========================================================
    // TRANSACCIONES MOCK
    // =========================================================

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;


    // =========================================================
    // SERVICIO BAJO PRUEBA
    // =========================================================

    private ResumenDiarioService service;


    // =========================================================
    // RELOJ FIJO
    // =========================================================

    private final Clock clock =
            Clock.fixed(
                    Instant.parse(
                            "2026-09-19T19:00:00Z"
                    ),
                    ZoneId.of(
                            "America/Lima"
                    )
            );


    // =========================================================
    // SETUP
    // =========================================================

    @BeforeEach
    void setUp() {
        lenient().when(comprobanteRepository.actualizarEstadoDesdeResumen(anyString(),anyList(),any(),anyString(),anyString()))
                .thenAnswer(i->((java.util.List<?>)i.getArgument(1)).size());

        lenient().when(resumenRepository.bloquearPorIdYRuc(anyLong(), anyString())).thenAnswer(inv -> {
            Optional<ResumenDiario> r = resumenRepository.findByIdAndRucEmisor(inv.getArgument(0), inv.getArgument(1));
            return r.isPresent() ? r : resumenRepository.buscarCompleto(inv.getArgument(0), inv.getArgument(1));
        });
        lenient().when(empresaRepository.bloquearPorRuc(anyString())).thenAnswer(inv -> empresaRepository.findByRuc(inv.getArgument(0)));

        service =
                new ResumenDiarioService(

                        resumenRepository,

                        comprobanteRepository,

                        empresaRepository,

                        secuenciaService,

                        xmlService,

                        xmlSignerService,

                        ublValidatorService,

                        sunatClient,

                        cdrParserService,

                        cryptoService,

                        sunatPlazoService,

                        clock,

                        transactionManager,
                        respuestaSunatRecibidaService
                );


        /*
         * Como estamos construyendo manualmente el servicio
         * y no mediante Spring, @Value no inyecta:
         *
         * sunat.resumen-diario.max-lineas
         *
         * Por eso fijamos 500 manualmente para las pruebas.
         */
        ReflectionTestUtils.setField(
                service,
                "maxLineasConfigurado",
                500
        );
    }


    // =========================================================
    // TEST 1
    // GETSTATUS = 98
    // =========================================================

    @Test
    void debeMantenerResumenProcesandoCuandoSunatDevuelve98() {

        prepararTransaccion();

        ResumenDiario resumen =
                crearResumen(
                        EstadoResumenDiario.TICKET_GENERADO
                );

        prepararEmpresa();

        when(
                resumenRepository
                        .findByIdAndRucEmisor(
                                RESUMEN_ID,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        resumen
                )
        );


        when(
                sunatClient.consultarTicket(
                        eq(RUC),
                        eq("USUARIO_SOL_PLANO"),
                        eq("CLAVE_SOL_PLANA"),
                        eq(TICKET)
                )
        ).thenReturn(
                new SunatDirectSoapClient.EstadoTicketSunat(
                        "98",
                        null
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        service.consultarTicketInterno(
                RUC,
                RESUMEN_ID
        );


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                EstadoResumenDiario.PROCESANDO,
                resumen.getEstado()
        );

        assertEquals(
                "98",
                resumen.getCodigoRespuestaSunat()
        );

        assertEquals(
                1,
                resumen.getIntentosConsulta()
        );

        assertNotNull(
                resumen.getUltimaConsultaEn()
        );


        verifyNoInteractions(
                cdrParserService
        );


        verify(
                comprobanteRepository,
                never()
        ).actualizarEstadoDesdeResumen(
                anyString(),
                anyList(),
                any(),
                anyString(),
                anyString()
        );


        /*
         * Consultar un ticket jamás debe volver
         * a ejecutar sendSummary.
         */
        verify(
                sunatClient,
                never()
        ).enviarResumenDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }


    // =========================================================
    // TEST 2
    // CDR ACEPTADO
    // =========================================================

    @Test
    void debeAceptarResumenYComprobantesCuandoCdrEsAceptado() {

        prepararTransaccion();

        ResumenDiario resumen =
                crearResumen(
                        EstadoResumenDiario.PROCESANDO
                );

        prepararEmpresa();


        ResumenDiarioDetalle detalle1 =
                ResumenDiarioDetalle
                        .builder()

                        .comprobanteId(
                                101L
                        )

                        .numeroLinea(
                                1
                        )

                        .build();


        ResumenDiarioDetalle detalle2 =
                ResumenDiarioDetalle
                        .builder()

                        .comprobanteId(
                                102L
                        )

                        .numeroLinea(
                                2
                        )

                        .build();


        resumen.addDetalle(
                detalle1
        );

        resumen.addDetalle(
                detalle2
        );


        when(
                resumenRepository
                        .findByIdAndRucEmisor(
                                RESUMEN_ID,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        resumen
                )
        );


        when(
                resumenRepository
                        .buscarCompleto(
                                RESUMEN_ID,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        resumen
                )
        );


        byte[] cdrZip =
                new byte[]{
                        1,
                        2,
                        3
                };


        byte[] cdrXml =
                "<CDR>ACEPTADO</CDR>"
                        .getBytes();


        when(
                sunatClient.consultarTicket(
                        eq(RUC),
                        eq("USUARIO_SOL_PLANO"),
                        eq("CLAVE_SOL_PLANA"),
                        eq(TICKET)
                )
        ).thenReturn(
                new SunatDirectSoapClient.EstadoTicketSunat(
                        "0",
                        cdrZip
                )
        );


        when(
                cdrParserService.procesar(
                        cdrZip
                )
        ).thenReturn(
                new CdrParserService.ResultadoCdr(

                        "0",

                        "El Resumen Diario ha sido aceptado",

                        List.of(),

                        true,

                        cdrXml
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        service.consultarTicketInterno(
                RUC,
                RESUMEN_ID
        );


        // =====================================================
        // VALIDAR RESUMEN
        // =====================================================

        assertEquals(
                EstadoResumenDiario.ACEPTADO,
                resumen.getEstado()
        );

        assertEquals(
                "0",
                resumen.getCodigoRespuestaSunat()
        );

        assertEquals(
                "El Resumen Diario ha sido aceptado",
                resumen.getMensajeSunat()
        );

        assertArrayEquals(
                cdrZip,
                resumen.getCdrZipBytes()
        );

        assertArrayEquals(
                cdrXml,
                resumen.getCdrXmlBytes()
        );

        assertEquals(
                1,
                resumen.getIntentosConsulta()
        );

        assertNotNull(
                resumen.getFinalizadoEn()
        );


        // =====================================================
        // VALIDAR COMPROBANTES
        // =====================================================

        verify(
                comprobanteRepository,
                times(1)
        ).actualizarEstadoDesdeResumen(

                eq(RUC),

                eq(
                        List.of(
                                101L,
                                102L
                        )
                ),

                eq(
                        EstadoComprobante.ACEPTADO
                ),

                eq(
                        "0"
                ),

                contains(
                        "Resumen"
                )
        );
    }


    // =========================================================
    // TEST 3
    // GETSTATUS = 99 SIN CDR
    // =========================================================

    @Test
    void debeRechazarResumenCuandoSunatDevuelve99SinCdr() {

        prepararTransaccion();

        ResumenDiario resumen =
                crearResumen(
                        EstadoResumenDiario.PROCESANDO
                );

        prepararEmpresa();


        when(
                resumenRepository
                        .findByIdAndRucEmisor(
                                RESUMEN_ID,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        resumen
                )
        );


        when(
                sunatClient.consultarTicket(
                        eq(RUC),
                        eq("USUARIO_SOL_PLANO"),
                        eq("CLAVE_SOL_PLANA"),
                        eq(TICKET)
                )
        ).thenReturn(
                new SunatDirectSoapClient.EstadoTicketSunat(
                        "99",
                        null
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        service.consultarTicketInterno(
                RUC,
                RESUMEN_ID
        );


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                EstadoResumenDiario.RECHAZADO,
                resumen.getEstado()
        );

        assertEquals(
                "99",
                resumen.getCodigoRespuestaSunat()
        );

        assertEquals(
                1,
                resumen.getIntentosConsulta()
        );

        assertNotNull(
                resumen.getFinalizadoEn()
        );


        verifyNoInteractions(
                cdrParserService
        );


        verify(
                comprobanteRepository,
                never()
        ).actualizarEstadoDesdeResumen(
                anyString(),
                anyList(),
                any(),
                anyString(),
                anyString()
        );
    }


    // =========================================================
    // TEST 4
    // ERROR DE RED EN GETSTATUS
    // =========================================================

    @Test
    void debeMarcarErrorConsultaCuandoFallaLaRed() {

        prepararTransaccion();

        ResumenDiario resumen =
                crearResumen(
                        EstadoResumenDiario.PROCESANDO
                );

        prepararEmpresa();


        when(
                resumenRepository
                        .findByIdAndRucEmisor(
                                RESUMEN_ID,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        resumen
                )
        );


        when(
                sunatClient.consultarTicket(
                        eq(RUC),
                        eq("USUARIO_SOL_PLANO"),
                        eq("CLAVE_SOL_PLANA"),
                        eq(TICKET)
                )
        ).thenThrow(
                new SunatDirectSoapClient
                        .SunatTransportException(
                        "Timeout de conexión simulado"
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        service.consultarTicketInterno(
                RUC,
                RESUMEN_ID
        );


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                EstadoResumenDiario.ERROR_CONSULTA,
                resumen.getEstado()
        );

        assertEquals(
                1,
                resumen.getIntentosConsulta()
        );

        assertNotNull(
                resumen.getUltimaConsultaEn()
        );

        assertTrue(
                resumen
                        .getMensajeSunat()
                        .contains(
                                "Timeout de conexión simulado"
                        )
        );


        assertEquals(
                TICKET,
                resumen.getTicketSunat()
        );


        verify(
                sunatClient,
                never()
        ).enviarResumenDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );


        verify(
                comprobanteRepository,
                never()
        ).actualizarEstadoDesdeResumen(
                anyString(),
                anyList(),
                any(),
                anyString(),
                anyString()
        );
    }


    // =========================================================
    // TEST 5
    // RC YA ACEPTADO
    // =========================================================

    @Test
    void noDebeConsultarSunatSiResumenYaEstaAceptado() {

        ResumenDiario resumen =
                crearResumen(
                        EstadoResumenDiario.ACEPTADO
                );


        when(
                resumenRepository
                        .findByIdAndRucEmisor(
                                RESUMEN_ID,
                                RUC
                        )
        ).thenReturn(
                Optional.of(
                        resumen
                )
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        service.consultarTicketInterno(
                RUC,
                RESUMEN_ID
        );


        // =====================================================
        // VALIDAR
        // =====================================================

        verifyNoInteractions(
                sunatClient
        );

        verifyNoInteractions(
                cdrParserService
        );

        verifyNoInteractions(
                empresaRepository
        );

        verifyNoInteractions(
                cryptoService
        );
    }


    // =========================================================
    // TEST 6
    // AGRUPAR VARIAS BOLETAS
    // =========================================================
    //
    // Simulamos exactamente lo que probamos manualmente:
    //
    // B001-1
    // B001-2
    // B001-3
    //
    // Las tres están PENDIENTE_RESUMEN.
    //
    // El resultado debe ser:
    //
    // UN SOLO RC
    // cantidadDocumentos = 3
    // tres líneas
    //
    // =========================================================

    @Test
    void debeAgruparTresBoletasEnUnSoloResumen() {

        prepararTransaccion();


        List<Comprobante> boletas =
                List.of(

                        crearBoleta(
                                1L,
                                RUC,
                                1
                        ),

                        crearBoleta(
                                2L,
                                RUC,
                                2
                        ),

                        crearBoleta(
                                3L,
                                RUC,
                                3
                        )
                );


        when(
                comprobanteRepository
                        .buscarPendientesResumenDiarioLimitado(
                                eq(RUC),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .atStartOfDay()
                                ),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .plusDays(1)
                                                .atStartOfDay()
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                boletas
        );


        /*
         * Simulamos que los tres UPDATE atómicos:
         *
         * PENDIENTE_RESUMEN -> EN_RESUMEN
         *
         * fueron exitosos.
         */
        when(
                comprobanteRepository
                        .marcarEnResumen(
                                anyLong(),
                                eq(RUC)
                        )
        ).thenReturn(
                1
        );


        when(
                secuenciaService.siguiente(
                        RUC,
                        FECHA_DOCUMENTOS,
                        FECHA_DOCUMENTOS
                )
        ).thenReturn(
                1
        );


        configurarGuardadoConIds(
                100L
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        Long resumenId =
                crearResumenPrivado(
                        RUC,
                        FECHA_DOCUMENTOS
                );


        // =====================================================
        // CAPTURAR EL RC CREADO
        // =====================================================

        ArgumentCaptor<ResumenDiario> captor =
                ArgumentCaptor.forClass(
                        ResumenDiario.class
                );


        verify(
                resumenRepository
        ).saveAndFlush(
                captor.capture()
        );


        ResumenDiario resumen =
                captor.getValue();


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                100L,
                resumenId
        );

        assertEquals(
                RUC,
                resumen.getRucEmisor()
        );

        assertEquals(
                3,
                resumen.getCantidadDocumentos()
        );

        assertEquals(
                3,
                resumen.getDetalles().size()
        );

        assertEquals(
                "RC-20260919-1",
                resumen.getIdentificador()
        );


        assertEquals(
                1,
                resumen
                        .getDetalles()
                        .get(0)
                        .getNumeroLinea()
        );

        assertEquals(
                2,
                resumen
                        .getDetalles()
                        .get(1)
                        .getNumeroLinea()
        );

        assertEquals(
                3,
                resumen
                        .getDetalles()
                        .get(2)
                        .getNumeroLinea()
        );


        assertEquals(
                1L,
                resumen
                        .getDetalles()
                        .get(0)
                        .getComprobanteId()
        );

        assertEquals(
                2L,
                resumen
                        .getDetalles()
                        .get(1)
                        .getComprobanteId()
        );

        assertEquals(
                3L,
                resumen
                        .getDetalles()
                        .get(2)
                        .getComprobanteId()
        );


        verify(
                comprobanteRepository,
                times(3)
        ).marcarEnResumen(
                anyLong(),
                eq(RUC)
        );
    }


    // =========================================================
    // TEST 7
    // EXACTAMENTE 500 DOCUMENTOS
    // =========================================================
    //
    // SUNAT / nuestra configuración:
    //
    // máximo = 500 líneas por bloque RC.
    //
    // Si tenemos exactamente 500:
    //
    // se debe crear UN SOLO RC
    // con 500 detalles.
    //
    // =========================================================

    @Test
    void debeCrearUnSoloResumenCuandoExistenExactamente500Documentos() {

        prepararTransaccion();


        List<Comprobante> boletas =
                crearBoletas(
                        500,
                        RUC,
                        1L
                );


        when(
                comprobanteRepository
                        .buscarPendientesResumenDiarioLimitado(
                                eq(RUC),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .atStartOfDay()
                                ),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .plusDays(1)
                                                .atStartOfDay()
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                boletas
        );


        when(
                comprobanteRepository
                        .marcarEnResumen(
                                anyLong(),
                                eq(RUC)
                        )
        ).thenReturn(
                1
        );


        when(
                secuenciaService.siguiente(
                        RUC,
                        FECHA_DOCUMENTOS,
                        FECHA_DOCUMENTOS
                )
        ).thenReturn(
                20
        );


        configurarGuardadoConIds(
                200L
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        Long resumenId =
                crearResumenPrivado(
                        RUC,
                        FECHA_DOCUMENTOS
                );


        // =====================================================
        // CAPTURAR RESUMEN
        // =====================================================

        ArgumentCaptor<ResumenDiario> resumenCaptor =
                ArgumentCaptor.forClass(
                        ResumenDiario.class
                );


        verify(
                resumenRepository
        ).saveAndFlush(
                resumenCaptor.capture()
        );


        ResumenDiario resumen =
                resumenCaptor.getValue();


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                200L,
                resumenId
        );

        assertEquals(
                500,
                resumen.getCantidadDocumentos()
        );

        assertEquals(
                500,
                resumen.getDetalles().size()
        );

        assertEquals(
                20,
                resumen.getCorrelativo()
        );


        /*
         * La primera línea debe ser 1.
         */
        assertEquals(
                1,
                resumen
                        .getDetalles()
                        .get(0)
                        .getNumeroLinea()
        );


        /*
         * La última línea debe ser 500.
         */
        assertEquals(
                500,
                resumen
                        .getDetalles()
                        .get(499)
                        .getNumeroLinea()
        );


        verify(
                comprobanteRepository,
                times(500)
        ).marcarEnResumen(
                anyLong(),
                eq(RUC)
        );


        /*
         * Comprobamos también que el servicio haya solicitado
         * una página cuyo máximo sea exactamente 500.
         */
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );


        verify(
                comprobanteRepository
        ).buscarPendientesResumenDiarioLimitado(
                eq(RUC),
                eq(
                        FECHA_DOCUMENTOS
                                .atStartOfDay()
                ),
                eq(
                        FECHA_DOCUMENTOS
                                .plusDays(1)
                                .atStartOfDay()
                ),
                pageableCaptor.capture()
        );


        assertEquals(
                500,
                pageableCaptor
                        .getValue()
                        .getPageSize()
        );
    }


    // =========================================================
    // TEST 8
    // 501 DOCUMENTOS
    // =========================================================
    //
    // Este es uno de los tests más importantes.
    //
    // 501 documentos NO pueden producir
    // un RC de 501 líneas.
    //
    // Debe ocurrir:
    //
    // RC-1 -> 500 documentos
    // RC-2 ->   1 documento
    //
    // =========================================================

    @Test
    void debeDividir501DocumentosEnUnResumenDe500YOtroDe1() {

        prepararTransaccion();


        List<Comprobante> todos =
                crearBoletas(
                        501,
                        RUC,
                        1L
                );


        /*
         * Simula el comportamiento real de la base:
         *
         * primera llamada:
         * devuelve 500
         *
         * segunda llamada:
         * como los primeros 500 ya están EN_RESUMEN,
         * solamente queda 1 PENDIENTE_RESUMEN.
         */
        AtomicInteger cursor =
                new AtomicInteger(
                        0
                );


        when(
                comprobanteRepository
                        .buscarPendientesResumenDiarioLimitado(
                                eq(RUC),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .atStartOfDay()
                                ),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .plusDays(1)
                                                .atStartOfDay()
                                ),
                                any(Pageable.class)
                        )
        ).thenAnswer(
                invocation -> {

                    Pageable pageable =
                            invocation.getArgument(
                                    3
                            );


                    int inicio =
                            cursor.get();


                    if (
                            inicio
                                    >=
                                    todos.size()
                    ) {

                        return List.of();
                    }


                    int fin =
                            Math.min(
                                    inicio
                                            +
                                            pageable.getPageSize(),

                                    todos.size()
                            );


                    List<Comprobante> bloque =
                            new ArrayList<>(
                                    todos.subList(
                                            inicio,
                                            fin
                                    )
                            );


                    cursor.set(
                            fin
                    );


                    return bloque;
                }
        );


        when(
                comprobanteRepository
                        .marcarEnResumen(
                                anyLong(),
                                eq(RUC)
                        )
        ).thenReturn(
                1
        );


        /*
         * Cada nuevo RC recibe un correlativo nuevo.
         */
        when(
                secuenciaService.siguiente(
                        RUC,
                        FECHA_DOCUMENTOS,
                        FECHA_DOCUMENTOS
                )
        ).thenReturn(
                30,
                31
        );


        configurarGuardadoConIds(
                300L
        );


        // =====================================================
        // CREAR PRIMER RC
        // =====================================================

        Long primerId =
                crearResumenPrivado(
                        RUC,
                        FECHA_DOCUMENTOS
                );


        // =====================================================
        // CREAR SEGUNDO RC
        // =====================================================

        Long segundoId =
                crearResumenPrivado(
                        RUC,
                        FECHA_DOCUMENTOS
                );


        // =====================================================
        // CAPTURAR LOS DOS RC
        // =====================================================

        ArgumentCaptor<ResumenDiario> captor =
                ArgumentCaptor.forClass(
                        ResumenDiario.class
                );


        verify(
                resumenRepository,
                times(2)
        ).saveAndFlush(
                captor.capture()
        );


        List<ResumenDiario> resumenes =
                captor.getAllValues();


        ResumenDiario primero =
                resumenes.get(
                        0
                );


        ResumenDiario segundo =
                resumenes.get(
                        1
                );


        // =====================================================
        // VALIDAR IDS
        // =====================================================

        assertEquals(
                300L,
                primerId
        );

        assertEquals(
                301L,
                segundoId
        );


        // =====================================================
        // VALIDAR PRIMER RC
        // =====================================================

        assertEquals(
                500,
                primero.getCantidadDocumentos()
        );

        assertEquals(
                500,
                primero.getDetalles().size()
        );

        assertEquals(
                30,
                primero.getCorrelativo()
        );

        assertEquals(
                "RC-20260919-30",
                primero.getIdentificador()
        );


        // =====================================================
        // VALIDAR SEGUNDO RC
        // =====================================================

        assertEquals(
                1,
                segundo.getCantidadDocumentos()
        );

        assertEquals(
                1,
                segundo.getDetalles().size()
        );

        assertEquals(
                31,
                segundo.getCorrelativo()
        );

        assertEquals(
                "RC-20260919-31",
                segundo.getIdentificador()
        );


        /*
         * El último comprobante debe terminar
         * en el segundo RC.
         */
        assertEquals(
                501L,
                segundo
                        .getDetalles()
                        .get(0)
                        .getComprobanteId()
        );


        /*
         * Se intentaron tomar exactamente
         * los 501 documentos.
         */
        verify(
                comprobanteRepository,
                times(501)
        ).marcarEnResumen(
                anyLong(),
                eq(RUC)
        );


        verify(
                secuenciaService,
                times(2)
        ).siguiente(
                RUC,
                FECHA_DOCUMENTOS,
                FECHA_DOCUMENTOS
        );
    }


    // =========================================================
    // TEST 9
    // AISLAMIENTO ENTRE EMPRESAS
    // =========================================================
    //
    // Esta prueba simula deliberadamente algo que NO debería
    // ocurrir en el repository:
    //
    // el query de Empresa A devuelve accidentalmente:
    //
    // - una boleta de Empresa A
    // - una boleta de Empresa B
    //
    // marcarEnResumen utiliza:
    //
    // ID + RUC
    //
    // Por tanto, la boleta de Empresa B no puede ser tomada
    // utilizando el RUC de Empresa A.
    //
    // Resultado:
    //
    // RC Empresa A -> sólo documento Empresa A
    //
    // =========================================================

    @Test
    void noDebeMezclarDocumentosDeDosEmpresasEnElMismoResumen() {

        prepararTransaccion();


        Comprobante empresaA =
                crearBoleta(
                        1001L,
                        RUC,
                        1
                );


        Comprobante empresaB =
                crearBoleta(
                        2001L,
                        RUC_EMPRESA_B,
                        1
                );


        /*
         * Simulamos incluso una respuesta defectuosa
         * del query para verificar la segunda barrera.
         */
        when(
                comprobanteRepository
                        .buscarPendientesResumenDiarioLimitado(
                                eq(RUC),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .atStartOfDay()
                                ),
                                eq(
                                        FECHA_DOCUMENTOS
                                                .plusDays(1)
                                                .atStartOfDay()
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(
                        empresaA,
                        empresaB
                )
        );


        /*
         * Empresa A:
         *
         * UPDATE ... WHERE id = 1001
         * AND ruc_emisor = RUC_A
         *
         * funciona.
         */
        when(
                comprobanteRepository
                        .marcarEnResumen(
                                1001L,
                                RUC
                        )
        ).thenReturn(
                1
        );


        /*
         * Empresa B:
         *
         * intentamos marcarla usando RUC_A.
         *
         * En MySQL el UPDATE devuelve 0,
         * porque el RUC no coincide.
         */
        when(
                comprobanteRepository
                        .marcarEnResumen(
                                2001L,
                                RUC
                        )
        ).thenReturn(
                0
        );


        when(
                secuenciaService.siguiente(
                        RUC,
                        FECHA_DOCUMENTOS,
                        FECHA_DOCUMENTOS
                )
        ).thenReturn(
                40
        );


        configurarGuardadoConIds(
                400L
        );


        // =====================================================
        // EJECUTAR
        // =====================================================

        Long id =
                crearResumenPrivado(
                        RUC,
                        FECHA_DOCUMENTOS
                );


        // =====================================================
        // CAPTURAR
        // =====================================================

        ArgumentCaptor<ResumenDiario> captor =
                ArgumentCaptor.forClass(
                        ResumenDiario.class
                );


        verify(
                resumenRepository
        ).saveAndFlush(
                captor.capture()
        );


        ResumenDiario resumen =
                captor.getValue();


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                400L,
                id
        );


        assertEquals(
                RUC,
                resumen.getRucEmisor()
        );


        /*
         * Solamente la boleta de Empresa A
         * debe quedar dentro del RC.
         */
        assertEquals(
                1,
                resumen.getCantidadDocumentos()
        );


        assertEquals(
                1,
                resumen.getDetalles().size()
        );


        assertEquals(
                1001L,
                resumen
                        .getDetalles()
                        .get(0)
                        .getComprobanteId()
        );


        /*
         * El documento de Empresa B jamás entra.
         */
        assertTrue(
                resumen
                        .getDetalles()
                        .stream()
                        .noneMatch(
                                detalle ->
                                        Long.valueOf(
                                                        2001L
                                                )
                                                .equals(
                                                        detalle
                                                                .getComprobanteId()
                                                )
                        )
        );


        /*
         * Tampoco debe existir ninguna operación
         * utilizando el RUC de Empresa B.
         */
        verify(
                comprobanteRepository,
                never()
        ).marcarEnResumen(
                anyLong(),
                eq(RUC_EMPRESA_B)
        );
    }


    // =========================================================
    // HELPER
    // INVOCAR CREACIÓN TRANSACCIONAL
    // =========================================================
    //
    // crearResumenTransaccional es private.
    //
    // Para este test unitario queremos comprobar únicamente
    // la construcción del lote antes de:
    //
    // - XML
    // - firma
    // - sendSummary
    //
    // ReflectionTestUtils nos permite probar específicamente
    // esa parte sin llamar a SUNAT.
    //
    // =========================================================

    private Long crearResumenPrivado(
            String ruc,
            LocalDate fecha
    ) {

        return ReflectionTestUtils.invokeMethod(
                service,
                "crearResumenTransaccional",
                ruc,
                fecha
        );
    }


    // =========================================================
    // HELPER
    // CREAR UNA BOLETA
    // =========================================================

    private Comprobante crearBoleta(
            Long id,
            String ruc,
            int numero
    ) {

        return Comprobante
                .builder()

                .id(
                        id
                )

                .tiendaId(
                        1L
                )

                .rucEmisor(
                        ruc
                )

                .tipoOperacion(
                        "0101"
                )

                .tipoComprobante(
                        "03"
                )

                .serie(
                        "B001"
                )

                .numero(
                        numero
                )

                .fechaEmision(
                        FECHA_DOCUMENTOS
                                .atTime(
                                        10,
                                        0
                                )
                                .plusSeconds(
                                        numero
                                )
                )

                .moneda(
                        "PEN"
                )

                .formaPago(
                        "Contado"
                )

                .tipoDocCliente(
                        "1"
                )

                .numDocCliente(
                        "48411579"
                )

                .rznSocialCliente(
                        "CLIENTE TEST"
                )

                .opGravada(
                        new BigDecimal(
                                "100.00"
                        )
                )

                .opExonerada(
                        BigDecimal.ZERO
                )

                .opInafecta(
                        BigDecimal.ZERO
                )

                .igv(
                        new BigDecimal(
                                "18.00"
                        )
                )

                .montoTotal(
                        new BigDecimal(
                                "118.00"
                        )
                )

                .estadoSfs(
                        EstadoComprobante.PENDIENTE_RESUMEN
                )

                .build();
    }


    // =========================================================
    // HELPER
    // CREAR MUCHAS BOLETAS
    // =========================================================

    private List<Comprobante> crearBoletas(
            int cantidad,
            String ruc,
            long primerId
    ) {

        List<Comprobante> resultado =
                new ArrayList<>(
                        cantidad
                );


        for (
                int i = 0;
                i < cantidad;
                i++
        ) {

            resultado.add(
                    crearBoleta(

                            primerId + i,

                            ruc,

                            i + 1
                    )
            );
        }


        return resultado;
    }


    // =========================================================
    // HELPER
    // SIMULAR IDs DE MYSQL
    // =========================================================
    //
    // En producción:
    //
    // MySQL asigna el ID de resumen_diario.
    //
    // En un mock no existe MySQL,
    // por eso simulamos IDs incrementales.
    //
    // =========================================================

    private void configurarGuardadoConIds(
            long primerId
    ) {

        AtomicLong secuenciaIds =
                new AtomicLong(
                        primerId
                );


        when(
                resumenRepository
                        .saveAndFlush(
                                any(
                                        ResumenDiario.class
                                )
                        )
        ).thenAnswer(
                invocation -> {

                    ResumenDiario resumen =
                            invocation.getArgument(
                                    0
                            );


                    if (
                            resumen.getId()
                                    ==
                                    null
                    ) {

                        resumen.setId(
                                secuenciaIds
                                        .getAndIncrement()
                        );
                    }


                    return resumen;
                }
        );
    }


    // =========================================================
    // HELPER
    // TRANSACCIÓN SIMULADA
    // =========================================================

    private void prepararTransaccion() {

        when(
                transactionManager.getTransaction(
                        any(
                                TransactionDefinition.class
                        )
                )
        ).thenReturn(
                transactionStatus
        );
    }


    // =========================================================
    // HELPER
    // CREAR RESUMEN PARA TESTS GETSTATUS
    // =========================================================

    private ResumenDiario crearResumen(
            EstadoResumenDiario estado
    ) {

        return ResumenDiario
                .builder()

                .id(
                        RESUMEN_ID
                )

                .rucEmisor(
                        RUC
                )

                .fechaDocumentos(
                        FECHA_DOCUMENTOS
                )

                .fechaGeneracion(
                        FECHA_DOCUMENTOS
                )

                .correlativo(
                        99
                )

                .identificador(
                        "RC-20260919-99"
                )

                .nombreArchivoXml(
                        RUC
                                +
                                "-RC-20260919-99.xml"
                )

                .cantidadDocumentos(
                        1
                )

                .estado(
                        estado
                )

                .ticketSunat(
                        TICKET
                )

                .intentosEnvio(
                        1
                )

                .intentosConsulta(
                        0
                )

                .build();
    }


    // =========================================================
    // HELPER
    // EMPRESA SUNAT SIMULADA
    // =========================================================

    private void prepararEmpresa() {

        Empresa empresa =
                new Empresa();


        empresa.setRuc(
                RUC
        );


        empresa.setPfxBytes(
                new byte[]{
                        1,
                        2,
                        3
                }
        );


        empresa.setPfxPassword(
                "PFX_PASSWORD_CIFRADA"
        );


        empresa.setUsuarioSol(
                "USUARIO_SOL_CIFRADO"
        );


        empresa.setClaveSol(
                "CLAVE_SOL_CIFRADA"
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


        when(
                cryptoService.descifrarUsuarioSol(
                        RUC,
                        "USUARIO_SOL_CIFRADO"
                )
        ).thenReturn(
                "USUARIO_SOL_PLANO"
        );


        when(
                cryptoService.descifrarClaveSol(
                        RUC,
                        "CLAVE_SOL_CIFRADA"
                )
        ).thenReturn(
                "CLAVE_SOL_PLANA"
        );
    }
}
