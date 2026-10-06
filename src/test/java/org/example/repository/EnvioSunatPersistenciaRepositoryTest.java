package org.example.repository;

import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.aop.framework.ProxyFactory;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Sólo MySQL desechable: rollback real posterior a recepción y recuperación sin red. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EnvioSunatPersistenciaRepositoryTest {
    @Autowired ComprobanteRepository docs;
    @Autowired RespuestaSunatRecibidaRepository respuestas;
    @Autowired PlatformTransactionManager tm;

    @Test
    void rollbackDeResultadoConservaZipYEnviandoSeRecuperaComoInciertoSinReenvio() {
        var tx = new TransactionTemplate(tm);
        Clock clock = Clock.system(ZoneId.of("America/Lima"));
        Comprobante doc = tx.execute(s -> docs.saveAndFlush(Comprobante.builder().tiendaId(1L).rucEmisor("20100130204")
                .tipoOperacion("0101").tipoComprobante("01").serie("F990").numero(1).fechaEmision(LocalDateTime.now(clock))
                .moneda("PEN").tipoDocCliente("1").numDocCliente("48411579").rznSocialCliente("CLIENTE FICTICIO")
                .opGravada(BigDecimal.ZERO).opExonerada(BigDecimal.TEN).opInafecta(BigDecimal.ZERO)
                .igv(BigDecimal.ZERO).montoTotal(BigDecimal.TEN).estadoSfs(EstadoComprobante.PENDIENTE_ENVIO)
                .nombreArchivoXml("f.xml").xmlFirmadoBytes(new byte[]{1}).build()));
        Long id = doc.getId();
        var proxy = new ProxyFactory(new RespuestaSunatRecibidaService(respuestas, clock)); proxy.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(tm);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); proxy.addAdvice(advice);
        var archivo = (RespuestaSunatRecibidaService) proxy.getProxy();
        var fallando = mock(ComprobanteRepository.class, org.mockito.AdditionalAnswers.delegatesTo(docs));
        doAnswer(inv -> tx.execute(s -> {
            docs.saveAndFlush(inv.getArgument(0));
            throw new IllegalStateException("Fallo simulado después de escribir y antes de commit");
        })).when(fallando).save(any(Comprobante.class));
        var empresas = mock(EmpresaRepository.class); var empresa = mock(Empresa.class);
        when(empresas.findByRuc(doc.getRucEmisor())).thenReturn(Optional.of(empresa));
        when(empresa.getRuc()).thenReturn(doc.getRucEmisor()); when(empresa.tieneConfiguracionSunat()).thenReturn(true);
        var crypto = mock(CryptoService.class);
        when(crypto.descifrarUsuarioSol(any(), any())).thenReturn("USUARIO_FICTICIO");
        when(crypto.descifrarClaveSol(any(), any())).thenReturn("CLAVE_FICTICIA");
        var client = mock(SunatDirectSoapClient.class); var parser = mock(CdrParserService.class);
        byte[] zip = {42, 43};
        when(client.enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(zip);
        when(parser.procesar(zip)).thenReturn(new CdrParserService.ResultadoCdr("0", "ACEPTADO", List.of(), true, new byte[]{44}));
        var service = new EnvioSunatService(fallando, empresas, client, parser, crypto,
                mock(SunatPlazoService.class), archivo, mock(SaldoNotaCreditoService.class));
        try {
            assertThrows(IllegalStateException.class, () -> service.enviar(id));
            var persistido = docs.findById(id).orElseThrow();
            assertEquals(EstadoComprobante.ENVIANDO, persistido.getEstadoSfs());
            assertNull(persistido.getCdrZipBytes());
            assertArrayEquals(zip, respuestas.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(doc.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).getFirst().getRespuestaZipBytes());
            var executor = mock(Executor.class);
            var recovery = new EnvioSunatRecoveryScheduler(docs, service, executor, Clock.offset(clock, Duration.ofHours(1)));
            recovery.recuperarPendientes();
            assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, docs.findById(id).orElseThrow().getEstadoSfs());
            verifyNoInteractions(executor);
            service.enviar(id);
            verify(client, times(1)).enviarZipDirecto(anyString(), anyString(), anyString(), anyString(), any());
        } finally {
            tx.executeWithoutResult(s -> {
                respuestas.findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(doc.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).forEach(respuestas::delete);
                docs.deleteById(id);
            });
        }
    }
}
