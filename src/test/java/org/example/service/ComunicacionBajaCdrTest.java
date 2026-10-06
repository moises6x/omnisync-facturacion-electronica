package org.example.service;

import org.example.auth.service.AuditoriaService;
import org.example.model.ComunicacionBaja;
import org.example.model.Empresa;
import org.example.model.EstadoComunicacionBaja;
import org.example.repository.ComprobanteRepository;
import org.example.repository.ComunicacionBajaRepository;
import org.example.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComunicacionBajaCdrTest {

    @Mock private ComunicacionBajaRepository repository;
    @Mock private ComprobanteRepository comprobanteRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private SecuenciaComunicacionBajaService secuenciaService;
    @Mock private ComunicacionBajaXmlService xmlService;
    @Mock private XmlSignerService xmlSignerService;
    @Mock private UblValidatorService ublValidatorService;
    @Mock private SunatDirectSoapClient sunatClient;
    @Mock private CryptoService cryptoService;
    @Mock private SunatPlazoService plazoService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock private org.springframework.transaction.TransactionStatus transactionStatus;
    @Mock private RespuestaSunatRecibidaService respuestaSunatRecibidaService;

    @Test
    void parserRealNoDebeAnularComprobanteConXmlAjenoAlContratoSunat() throws Exception {
        String ruc = "20100130204";
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Lima"));
        ComunicacionBajaService service = new ComunicacionBajaService(repository, comprobanteRepository,
                empresaRepository, secuenciaService, xmlService, xmlSignerService, ublValidatorService,
                sunatClient, new CdrParserService(), cryptoService, plazoService, auditoriaService, clock, transactionManager, respuestaSunatRecibidaService);
        ComunicacionBaja baja = ComunicacionBaja.builder().id(1L).rucEmisor(ruc)
                .identificador("RA-20260930-1").ticketSunat("TICKET-TEST")
                .estado(EstadoComunicacionBaja.TICKET_GENERADO).build();
        Empresa empresa = new Empresa();
        empresa.setRuc(ruc);
        empresa.setUsuarioSol("USUARIO_CIF_PRUEBA");
        empresa.setClaveSol("CLAVE_CIF_PRUEBA");
        empresa.setPfxBytes(new byte[]{1});
        empresa.setPfxPassword("PFX_CIF_PRUEBA");
        when(repository.buscarCompleto(1L, ruc)).thenReturn(Optional.of(baja));
        when(repository.bloquearPorIdYRuc(1L, ruc)).thenReturn(Optional.of(baja));
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(empresaRepository.findByRuc(ruc)).thenReturn(Optional.of(empresa));
        when(cryptoService.descifrarUsuarioSol(ruc, "USUARIO_CIF_PRUEBA")).thenReturn("USUARIO_PRUEBA");
        when(cryptoService.descifrarClaveSol(ruc, "CLAVE_CIF_PRUEBA")).thenReturn("CLAVE_PRUEBA");
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", "<root><ResponseCode>0</ResponseCode></root>");
        when(sunatClient.consultarTicket(ruc, "USUARIO_PRUEBA", "CLAVE_PRUEBA", "TICKET-TEST"))
                .thenReturn(new SunatDirectSoapClient.EstadoTicketSunat("0", zip));

        service.consultarTicket(ruc, 1L);

        assertEquals(EstadoComunicacionBaja.ERROR_CONSULTA, baja.getEstado());
        assertNull(baja.getCdrXmlBytes());
        assertNull(baja.getFinalizadoEn());
        verify(respuestaSunatRecibidaService).conservar(ruc, org.example.model.RespuestaSunatRecibida.Flujo.RA, 1L, "TICKET-TEST", zip);
        verify(repository).save(baja);
        verifyNoInteractions(comprobanteRepository, auditoriaService);
    }
}
