package org.example.service;

import org.example.model.Comprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;
import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para EnvioSunatService.
 *
 * No se conecta realmente a SUNAT ni a MySQL. Todos los colaboradores
 * externos se reemplazan por mocks de Mockito.
 */
@ExtendWith(MockitoExtension.class)
class EnvioSunatServiceTest {

    @Mock
    private ComprobanteRepository comprobanteRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private SunatDirectSoapClient sunatClient;

    @Mock
    private CdrParserService cdrParserService;

    @Mock
    private CryptoService cryptoService;

    /**
     * Dependencia nueva de EnvioSunatService.
     * Valida si el comprobante todavía está dentro del plazo permitido.
     */
    @Mock
    private SunatPlazoService sunatPlazoService;
    @Mock private RespuestaSunatRecibidaService respuestaSunatRecibidaService;
    @Mock private SaldoNotaCreditoService saldoNotaCreditoService;

    private EnvioSunatService service;

    @BeforeEach
    void setUp() {
        service = new EnvioSunatService(
                comprobanteRepository,
                empresaRepository,
                sunatClient,
                cdrParserService,
                cryptoService,
                sunatPlazoService,
                respuestaSunatRecibidaService,
                saldoNotaCreditoService
        );

        /*
         * @Value no se inyecta porque en este test construimos el servicio
         * manualmente y no levantamos el contexto completo de Spring.
         */
        ReflectionTestUtils.setField(service, "debugXml", false);

        /*
         * Los mocks de ResultadoCdr usados por estas pruebas no necesitan
         * validar el ReferenceID real de una CDR. Dejamos esta validación
         * desactivada expresamente para mantener el test unitario aislado.
         */
        ReflectionTestUtils.setField(service, "cdrRequireReferenceId", false);
    }

    @Test
    void noDebeProcesarSiOtroWorkerYaTomoElComprobante() {
        when(comprobanteRepository.marcarEnviando(1L)).thenReturn(0);

        service.enviar(1L);

        verify(comprobanteRepository, never()).findById(anyLong());
        verifyNoInteractions(sunatClient);
        verifyNoInteractions(sunatPlazoService);
    }

    @Test
    void cdrAceptadoDebeMarcarAceptado() {
        Comprobante c = prepararFactura();
        byte[] cdrZip = new byte[]{9, 8, 7};
        byte[] cdrXml = "<cdr/>".getBytes(StandardCharsets.UTF_8);

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                eq("20100130204"),
                eq("MODDATOS"),
                eq("moddatos"),
                anyString(),
                any()
        )).thenReturn(cdrZip);

        when(cdrParserService.procesar(cdrZip))
                .thenReturn(new CdrParserService.ResultadoCdr(
                        "0",
                        "ACEPTADO",
                        List.of(),
                        true,
                        cdrXml
                ));

        service.enviar(1L);

        assertEquals(EstadoComprobante.ACEPTADO, c.getEstadoSfs());
        assertEquals("0", c.getCodigoRespuestaSunat());
        assertArrayEquals(cdrZip, c.getCdrZipBytes());
        assertArrayEquals(cdrXml, c.getCdrXmlBytes());
        verify(comprobanteRepository).save(c);
        verify(sunatPlazoService).validarEnvioIndividual(c.getFechaEmision());
    }

    @Test
    void cdrAceptadoConObservacionesDebeMarcarAceptadoConObservacion() {
        Comprobante c = prepararFactura();
        byte[] zip = new byte[]{1};

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(zip);

        when(cdrParserService.procesar(zip))
                .thenReturn(new CdrParserService.ResultadoCdr(
                        "0",
                        "OK",
                        List.of("OBS"),
                        true,
                        new byte[]{2}
                ));

        service.enviar(1L);

        assertEquals(
                EstadoComprobante.ACEPTADO_CON_OBSERVACION,
                c.getEstadoSfs()
        );
        assertTrue(c.getMensajeSunat().contains("OBS"));
    }

    @Test
    void cdrConCodigoNoCeroDebeMarcarRechazado() {
        Comprobante c = prepararFactura();
        byte[] zip = new byte[]{1};

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(zip);

        when(cdrParserService.procesar(zip))
                .thenReturn(new CdrParserService.ResultadoCdr(
                        "2335",
                        "RECHAZADO",
                        List.of(),
                        false,
                        new byte[]{2}
                ));

        service.enviar(1L);

        assertEquals(EstadoComprobante.RECHAZADO, c.getEstadoSfs());
    }

    @Test
    void timeoutDebeQuedarComoErrorEnvioInciertoSinReintentoCiego() {
        Comprobante c = prepararFactura();

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenThrow(
                new SunatDirectSoapClient.SunatTransportException("timeout")
        );

        service.enviar(1L);

        /*
         * El código productivo actual considera un timeout como un resultado
         * incierto: SUNAT pudo haber recibido el comprobante antes de que se
         * cortara la conexión. Por eso NO debe quedar ERROR_ENVIO normal.
         */
        assertEquals(
                EstadoComprobante.ERROR_ENVIO_INCIERTO,
                c.getEstadoSfs()
        );

        assertTrue(
                c.getMensajeSunat()
                        .toLowerCase()
                        .contains("timeout")
        );

        verify(sunatClient, times(1)).enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void soapFaultDebeQuedarInciertoSinCdrDeRechazo() {
        Comprobante c = prepararFactura();

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenThrow(
                new SunatDirectSoapClient.SunatSoapFaultException(
                        "soap-env:Client.3205",
                        "XML INVALIDO",
                        500
                )
        );

        service.enviar(1L);

        assertEquals(
                EstadoComprobante.ERROR_ENVIO_INCIERTO,
                c.getEstadoSfs()
        );
        assertTrue(c.getMensajeSunat().contains("SOAP Fault"));
        assertFalse(c.getMensajeSunat().contains("XML INVALIDO"));
    }

    @Test
    void respuestaVaciaDebeQuedarInciertaDespuesDelEnvio() {
        Comprobante c = prepararFactura();

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(new byte[0]);

        service.enviar(1L);

        assertEquals(
                EstadoComprobante.ERROR_ENVIO_INCIERTO,
                c.getEstadoSfs()
        );
    }

    @Test
    void boletaNoDebeIrPorSendBill() {
        Comprobante c = prepararFactura();
        c.setTipoComprobante("03");

        prepararBase(c);

        service.enviar(1L);

        assertEquals(
                EstadoComprobante.ERROR_VALIDACION,
                c.getEstadoSfs()
        );
        verifyNoInteractions(sunatClient);
    }

    @Test
    void ncDeFacturaSiPuedeIrPorSendBill() {
        Comprobante c = prepararFactura();
        c.setTipoComprobante("07");
        c.setTipoComprobanteAfectado("01");

        byte[] zip = new byte[]{1};

        prepararBase(c);

        when(sunatClient.enviarZipDirecto(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(zip);

        when(cdrParserService.procesar(zip))
                .thenReturn(new CdrParserService.ResultadoCdr(
                        "0",
                        "OK",
                        List.of(),
                        true,
                        new byte[]{2}
                ));

        service.enviar(1L);

        assertEquals(EstadoComprobante.ACEPTADO, c.getEstadoSfs());
    }

    @Test
    void parserRealNoDebeAceptarXmlAjenoAlContratoSunat() throws Exception {
        usarParserReal();
        Comprobante c = prepararFactura();
        prepararBase(c);
        byte[] zip = CdrParserServiceTest.zipXml("R.xml",
                "<root><ResponseCode>0</ResponseCode><Description>ACEPTADO</Description></root>");
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);

        service.enviar(1L);

        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, c.getEstadoSfs());
        assertNull(c.getCodigoRespuestaSunat());
        assertNull(c.getCdrXmlBytes());
        verify(comprobanteRepository).save(c);
    }

    @Test
    void parserRealDebeProcesarApplicationResponseConReferenciaCorrecta() throws Exception {
        usarParserReal();
        Comprobante c = prepararFactura();
        prepararBase(c);
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", CdrParserServiceTest.applicationResponse("0", "ACEPTADO", ""));
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);

        service.enviar(1L);

        assertEquals(EstadoComprobante.ACEPTADO, c.getEstadoSfs());
        assertEquals("0", c.getCodigoRespuestaSunat());
        assertArrayEquals(zip, c.getCdrZipBytes());
        verify(comprobanteRepository).save(c);
    }

    private void usarParserReal() {
        service = new EnvioSunatService(comprobanteRepository, empresaRepository, sunatClient,
                new CdrParserService(), cryptoService, sunatPlazoService, respuestaSunatRecibidaService, saldoNotaCreditoService);
        ReflectionTestUtils.setField(service, "debugXml", false);
        ReflectionTestUtils.setField(service, "cdrRequireReferenceId", true);
    }

    @Test
    void debeConservarRespuestaAntesDeInterpretarUnZipInvalido() {
        Comprobante c = prepararFactura(); prepararBase(c);
        byte[] zip = {42};
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(cdrParserService.procesar(zip)).thenThrow(new SunatDirectSoapClient.SunatProtocolException("ZIP inválido simulado"));
        service.enviar(1L);
        var orden = inOrder(respuestaSunatRecibidaService, cdrParserService);
        orden.verify(respuestaSunatRecibidaService).conservar("20100130204", org.example.model.RespuestaSunatRecibida.Flujo.CPE, 1L, null, zip);
        orden.verify(cdrParserService).procesar(zip);
        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, c.getEstadoSfs()); assertNull(c.getCdrXmlBytes());
    }

    @Test
    void falloAlConservarRespuestaNoDebeDeclararAceptacion() {
        Comprobante c = prepararFactura(); prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(new byte[]{42});
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("persistencia no disponible simulada"))
                .when(respuestaSunatRecibidaService).conservar(anyString(), any(), anyLong(), isNull(), any());
        service.enviar(1L);
        verifyNoInteractions(cdrParserService); assertNull(c.getCdrXmlBytes());
        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, c.getEstadoSfs());
    }

    /**
     * Configura los mocks comunes para un comprobante que sí fue tomado
     * por el worker y puede continuar con el flujo de envío.
     */
    private void prepararBase(Comprobante c) {
        if ("07".equals(c.getTipoComprobante())) {
            when(saldoNotaCreditoService.guardarResultadoIndividual(c)).thenReturn(c);
        }
        when(comprobanteRepository.marcarEnviando(1L)).thenReturn(1);
        when(comprobanteRepository.findById(1L)).thenReturn(Optional.of(c));

        Empresa e = empresaLista();

        when(empresaRepository.findByRuc("20100130204"))
                .thenReturn(Optional.of(e));

        lenient()
                .when(cryptoService.descifrarUsuarioSol(
                        "20100130204",
                        "USR_CIF"
                ))
                .thenReturn("MODDATOS");

        lenient()
                .when(cryptoService.descifrarClaveSol(
                        "20100130204",
                        "PWD_CIF"
                ))
                .thenReturn("moddatos");
    }

    @Test
    void timeoutDeNcConservaReservaYDelegaCommitFueraDeSoap() {
        Comprobante nc = prepararFactura(); nc.setTipoComprobante("07"); nc.setTipoComprobanteAfectado("01");
        nc.setSerieComprobanteAfectado("F001"); nc.setNumeroComprobanteAfectado(1);
        prepararBase(nc);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new SunatDirectSoapClient.SunatTransportException("timeout controlado", new java.io.IOException()));
        service.enviar(1L);
        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, nc.getEstadoSfs());
        var orden = inOrder(sunatClient, saldoNotaCreditoService);
        orden.verify(sunatClient).enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any());
        orden.verify(saldoNotaCreditoService).guardarResultadoIndividual(nc);
        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    void ncAceptadaDelegaConfirmacionBajoOriginalDespuesDeRecibirCdr() {
        Comprobante nc = prepararFactura(); nc.setTipoComprobante("07"); nc.setTipoComprobanteAfectado("01");
        nc.setSerieComprobanteAfectado("F001"); nc.setNumeroComprobanteAfectado(1);
        prepararBase(nc);
        byte[] zip = {42};
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(cdrParserService.procesar(zip)).thenReturn(new CdrParserService.ResultadoCdr("0", "ACEPTADO", List.of(), true, new byte[]{1}));
        service.enviar(1L);
        assertEquals(EstadoComprobante.ACEPTADO, nc.getEstadoSfs());
        var orden = inOrder(respuestaSunatRecibidaService, cdrParserService, saldoNotaCreditoService);
        orden.verify(respuestaSunatRecibidaService).conservar(anyString(), any(), anyLong(), isNull(), same(zip));
        orden.verify(cdrParserService).procesar(zip);
        orden.verify(saldoNotaCreditoService).guardarResultadoIndividual(nc);
    }

    /**
     * Crea una factura mínima preparada para el flujo sendBill.
     */
    @Test
    void falloDeCredencialesAntesDeRedDebeSerLocalSinConsultarSunat() {
        Comprobante c = prepararFactura(); prepararBase(c);
        when(cryptoService.descifrarUsuarioSol("20100130204", "USR_CIF"))
                .thenThrow(new IllegalStateException("Configuración local no disponible"));
        service.enviar(1L);
        assertEquals(EstadoComprobante.ERROR_VALIDACION, c.getEstadoSfs());
        verifyNoInteractions(sunatClient, respuestaSunatRecibidaService, cdrParserService);
        verify(comprobanteRepository).save(c);
    }

    @ParameterizedTest
    @ValueSource(strings = {"conexion", "timeout", "fault", "protocolo", "parser", "archivo"})
    void fallosPosterioresAlIntentoReservanNcYNoReenvian(String caso) {
        Comprobante nc = prepararFactura(); nc.setTipoComprobante("07"); nc.setTipoComprobanteAfectado("01");
        prepararBase(nc);
        byte[] zip = {42};
        RuntimeException error = switch (caso) {
            case "conexion" -> new SunatDirectSoapClient.SunatTransportException("Conexión abierta; respuesta no recibida");
            case "timeout" -> new SunatDirectSoapClient.SunatTransportException("Timeout", new java.net.SocketTimeoutException());
            case "fault" -> new SunatDirectSoapClient.SunatSoapFaultException("Client.3205", "FICTICIO", 500);
            default -> new IllegalStateException("Fallo controlado");
        };
        if (caso.equals("parser") || caso.equals("archivo")) {
            when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
            if (caso.equals("parser")) when(cdrParserService.procesar(zip)).thenThrow(error);
            else doThrow(error).when(respuestaSunatRecibidaService).conservar(anyString(), any(), anyLong(), isNull(), same(zip));
        } else {
            when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenThrow(error);
        }
        service.enviar(1L);
        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, nc.getEstadoSfs());
        assertNull(nc.getCdrXmlBytes());
        verify(saldoNotaCreditoService).guardarResultadoIndividual(nc);
        verify(comprobanteRepository, never()).save(any());
        // El update atómico no permite retomar un incierto; no se vuelve a llamar sendBill.
        when(comprobanteRepository.marcarEnviando(1L)).thenReturn(0);
        service.enviar(1L);
        verify(sunatClient, times(1)).enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any());
        if (caso.equals("archivo")) verifyNoInteractions(cdrParserService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "07"})
    void falloDePersistenciaDeResultadoNoDebeConfirmarNiReenviar(String tipo) {
        Comprobante c = prepararFactura(); c.setTipoComprobante(tipo); c.setTipoComprobanteAfectado("01");
        prepararBase(c);
        byte[] zip = {42};
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(cdrParserService.procesar(zip)).thenReturn(new CdrParserService.ResultadoCdr("0", "ACEPTADO", List.of(), true, new byte[]{1}));
        RuntimeException fallo = new org.springframework.dao.DataAccessResourceFailureException("Fallo simulado");
        if (tipo.equals("07")) when(saldoNotaCreditoService.guardarResultadoIndividual(c)).thenThrow(fallo);
        else when(comprobanteRepository.save(c)).thenThrow(fallo);
        var auditoria = mock(org.example.auth.service.AuditoriaService.class);
        ReflectionTestUtils.setField(service, "auditoriaService", auditoria);
        var ex = assertThrows(IllegalStateException.class, () -> service.enviar(1L));
        assertTrue(ex.getMessage().contains("no reenviar")); assertNull(ex.getCause());
        verifyNoInteractions(auditoria);
        verify(respuestaSunatRecibidaService).conservar(anyString(), any(), anyLong(), isNull(), same(zip));
        when(comprobanteRepository.marcarEnviando(1L)).thenReturn(0);
        service.enviar(1L);
        verify(sunatClient, times(1)).enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any());
        if (tipo.equals("07")) verify(saldoNotaCreditoService, times(1)).guardarResultadoIndividual(c);
        else verify(comprobanteRepository, times(1)).save(c);
    }

    private Comprobante prepararFactura() {
        Comprobante c = new Comprobante();
        c.setId(1L);
        c.setRucEmisor("20100130204");
        c.setTipoComprobante("01");
        c.setSerie("F001");
        c.setNumero(1);
        c.setEstadoSfs(EstadoComprobante.ENVIANDO);
        c.setNombreArchivoXml("20100130204-01-F001-00000001.xml");
        c.setXmlFirmadoBytes("<xml/>".getBytes(StandardCharsets.UTF_8));
        return c;
    }

    /**
     * Crea una empresa ficticia con la configuración mínima que el servicio
     * exige antes de intentar comunicarse con SUNAT.
     */
    private Empresa empresaLista() {
        Empresa e = new Empresa();
        e.setRuc("20100130204");
        e.setUsuarioSol("USR_CIF");
        e.setClaveSol("PWD_CIF");
        e.setPfxBytes(new byte[]{1});
        e.setPfxPassword("PFX_CIF");
        return e;
    }
}
