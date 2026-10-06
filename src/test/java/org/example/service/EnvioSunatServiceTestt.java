package org.example.service;

import org.example.model.Comprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;
import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@ExtendWith(MockitoExtension.class)
public class EnvioSunatServiceTestt {

    @Mock
    private ComprobanteRepository comprobanteRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private SunatDirectSoapClient sunatClient;
    @Mock private CdrParserService cdrParserService;
    @Mock private CryptoService cryptoService;
    @Mock private SunatPlazoService sunatPlazoService;
    @Mock private RespuestaSunatRecibidaService respuestaSunatRecibidaService;
    @Mock private SaldoNotaCreditoService saldoNotaCreditoService;

    private EnvioSunatService service;

    @BeforeEach
    void setUp() {
        service = new EnvioSunatService(
                comprobanteRepository, empresaRepository, sunatClient, cdrParserService, cryptoService, sunatPlazoService, respuestaSunatRecibidaService, saldoNotaCreditoService
        );
        ReflectionTestUtils.setField(service, "debugXml", false);
    }

    @Test
    void noDebeProcesarSiOtroWorkerYaTomoElComprobante() {
        when(comprobanteRepository.marcarEnviando(1L)).thenReturn(0);
        service.enviar(1L);
        verify(comprobanteRepository, never()).findById(anyLong());
        verifyNoInteractions(sunatClient);
    }

    @Test
    void cdrAceptadoDebeMarcarAceptado() {
        Comprobante c = prepararFactura();
        byte[] cdrZip = new byte[]{9,8,7};
        byte[] cdrXml = "<cdr/>".getBytes(StandardCharsets.UTF_8);
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(eq("20100130204"), eq("MODDATOS"), eq("moddatos"), anyString(), any()))
                .thenReturn(cdrZip);
        when(cdrParserService.procesar(cdrZip))
                .thenReturn(new CdrParserService.ResultadoCdr("0", "ACEPTADO", List.of(), true, cdrXml));

        service.enviar(1L);

        assertEquals(EstadoComprobante.ACEPTADO, c.getEstadoSfs());
        assertEquals("0", c.getCodigoRespuestaSunat());
        assertArrayEquals(cdrZip, c.getCdrZipBytes());
        assertArrayEquals(cdrXml, c.getCdrXmlBytes());
        verify(comprobanteRepository).save(c);
    }

    @Test
    void cdrAceptadoConObservacionesDebeMarcarAceptadoConObservacion() {
        Comprobante c = prepararFactura();
        byte[] zip = new byte[]{1};
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(cdrParserService.procesar(zip))
                .thenReturn(new CdrParserService.ResultadoCdr("0", "OK", List.of("OBS"), true, new byte[]{2}));

        service.enviar(1L);

        assertEquals(EstadoComprobante.ACEPTADO_CON_OBSERVACION, c.getEstadoSfs());
        assertTrue(c.getMensajeSunat().contains("OBS"));
    }

    @Test
    void cdrConCodigoNoCeroDebeMarcarRechazado() {
        Comprobante c = prepararFactura();
        byte[] zip = new byte[]{1};
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(cdrParserService.procesar(zip))
                .thenReturn(new CdrParserService.ResultadoCdr("2335", "RECHAZADO", List.of(), false, new byte[]{2}));

        service.enviar(1L);
        assertEquals(EstadoComprobante.RECHAZADO, c.getEstadoSfs());
    }

    @Test
    void timeoutDebeQuedarComoErrorEnvioSinReintentoCiego() {
        Comprobante c = prepararFactura();
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new SunatDirectSoapClient.SunatTransportException("timeout"));

        service.enviar(1L);

        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, c.getEstadoSfs());
        assertTrue(c.getMensajeSunat().toLowerCase().contains("timeout"));
        verify(sunatClient, times(1)).enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void soapFaultDebeQuedarInciertoSinRechazoFiscal() {
        Comprobante c = prepararFactura();
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new SunatDirectSoapClient.SunatSoapFaultException("soap-env:Client.3205", "XML INVALIDO", 500));

        service.enviar(1L);

        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, c.getEstadoSfs());
        assertTrue(c.getMensajeSunat().contains("SOAP Fault"));
        assertFalse(c.getMensajeSunat().contains("XML INVALIDO"));
    }

    @Test
    void respuestaVaciaDebeQuedarInciertaDespuesDelEnvio() {
        Comprobante c = prepararFactura();
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new byte[0]);

        service.enviar(1L);
        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, c.getEstadoSfs());
    }

    @Test
    void boletaNoDebeIrPorSendBill() {
        Comprobante c = prepararFactura();
        c.setTipoComprobante("03");
        prepararBase(c);

        service.enviar(1L);

        assertEquals(EstadoComprobante.ERROR_VALIDACION, c.getEstadoSfs());
        verifyNoInteractions(sunatClient);
    }

    @Test
    void ncDeFacturaSiPuedeIrPorSendBill() {
        Comprobante c = prepararFactura();
        c.setTipoComprobante("07");
        c.setTipoComprobanteAfectado("01");
        byte[] zip = new byte[]{1};
        prepararBase(c);
        when(sunatClient.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(cdrParserService.procesar(zip))
                .thenReturn(new CdrParserService.ResultadoCdr("0", "OK", List.of(), true, new byte[]{2}));

        service.enviar(1L);
        assertEquals(EstadoComprobante.ACEPTADO, c.getEstadoSfs());
    }

    private void prepararBase(Comprobante c) {
        if ("07".equals(c.getTipoComprobante())) {
            when(saldoNotaCreditoService.guardarResultadoIndividual(c)).thenReturn(c);
        }
        when(comprobanteRepository.marcarEnviando(1L)).thenReturn(1);
        when(comprobanteRepository.findById(1L)).thenReturn(Optional.of(c));
        Empresa e = empresaLista();
        when(empresaRepository.findByRuc("20100130204")).thenReturn(Optional.of(e));
        lenient().when(cryptoService.descifrarUsuarioSol("20100130204", "USR_CIF")).thenReturn("MODDATOS");
        lenient().when(cryptoService.descifrarClaveSol("20100130204", "PWD_CIF")).thenReturn("moddatos");
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
