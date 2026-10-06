package org.example.repository;

import org.example.admin.dto.RenovarSuscripcionRequest;
import org.example.admin.model.*;
import org.example.admin.repository.*;
import org.example.admin.service.*;
import org.example.auth.repository.UsuarioRepository;
import org.example.auth.security.SecurityContextService;
import org.example.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest @ActiveProfiles("test") @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class Fase4SuscripcionRepositoryTest {
    @Autowired EmpresaRepository empresas;@Autowired SuscripcionEmpresaRepository suscripciones;
    @Autowired PagoEmpresaRepository pagos;@Autowired AnuncioEmpresaRepository anuncios;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;
    private TransactionTemplate tx;private Long eid;private LocalDateTime vence;
    private final RenovarSuscripcionRequest request=new RenovarSuscripcionRequest(1,new BigDecimal("25.00"),"PAGO FICTICIO");
    @BeforeEach void fixture(){
        tx=new TransactionTemplate(tm);vence=LocalDateTime.now().plusMonths(1).withNano(0);
        eid=tx.execute(s->{var e=empresas.saveAndFlush(Empresa.builder().ruc("20100130204").razonSocial("FICTICIO").build());
            suscripciones.saveAndFlush(SuscripcionEmpresa.builder().empresa(e).inicioEn(LocalDateTime.now()).venceEn(vence).build());return e.getId();});
    }
    @AfterEach void limpiar(){tx.executeWithoutResult(s->{
        jdbc.update("DELETE FROM anuncio_empresa WHERE empresa_id=?",eid);jdbc.update("DELETE FROM pago_empresa WHERE empresa_id=?",eid);
        suscripciones.delete(suscripciones.findByEmpresaId(eid).orElseThrow());suscripciones.flush();empresas.deleteById(eid);empresas.flush();
    });}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void dosInstanciasRenovandoAcreditanCadaOperacionUnaVez(boolean misma)throws Exception{
        var dentro=new CountDownLatch(1);var continuar=new CountDownLatch(1);var segunda=new CountDownLatch(1);
        var repo=mock(PagoEmpresaRepository.class,org.mockito.AdditionalAnswers.delegatesTo(pagos));
        doAnswer(i->{dentro.countDown();esperar(continuar);return pagos.saveAndFlush(i.getArgument(0));}).when(repo).saveAndFlush(any());
        var uno=servicio(repo);var dos=servicio(pagos);
        try(var pool=Executors.newFixedThreadPool(2)){
            try{
                var f1=pool.submit(()->uno.renovar(eid,request,"OPERACION-1"));esperar(dentro);
                var f2=pool.submit(()->{segunda.countDown();return dos.renovar(eid,request,misma?"OPERACION-1":"OPERACION-2");});
                esperar(segunda);assertThrows(TimeoutException.class,()->f2.get(200,TimeUnit.MILLISECONDS));
                continuar.countDown();f1.get(10,TimeUnit.SECONDS);f2.get(10,TimeUnit.SECONDS);
            }finally{continuar.countDown();}
        }
        assertEquals(misma?1:2,pagos.findAllByEmpresaIdOrderByPagadoEnDesc(eid).size());
        assertEquals(vence.plusMonths(misma?1:2),suscripciones.findByEmpresaId(eid).orElseThrow().getVenceEn());
        assertEquals(64,pagos.findAllByEmpresaIdOrderByPagadoEnDesc(eid).getFirst().getIdempotencyHash().length());
    }
    @Test void repetirPayloadDistintoSeRechazaSinOtroPago(){
        var s=servicio(pagos);s.renovar(eid,request,"MISMA");
        assertThrows(IllegalArgumentException.class,()->s.renovar(eid,new RenovarSuscripcionRequest(2,request.monto(),request.observacion()),"MISMA"));
        assertEquals(1,pagos.findAllByEmpresaIdOrderByPagadoEnDesc(eid).size());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void renovacionYSchedulerNuncaPierdenPeriodoNiSuspendenRenovacionConfirmada(boolean renovarPrimero)throws Exception{
        tx.executeWithoutResult(s->suscripciones.findByEmpresaId(eid).orElseThrow().setVenceEn(LocalDateTime.now().minusDays(1)));
        var dentro=new CountDownLatch(1);var continuar=new CountDownLatch(1);var segunda=new CountDownLatch(1);
        var repo=mock(EmpresaRepository.class,org.mockito.AdditionalAnswers.delegatesTo(empresas));
        doAnswer(i->{var r=empresas.bloquearPorId(eid);dentro.countDown();esperar(continuar);return r;}).when(repo).bloquearPorId(eid);
        var ctx=mock(SecurityContextService.class);when(ctx.usuarioId()).thenReturn(44L);
        var renovarBloqueado=proxy(new SuperAdminEmpresaService(repo,mock(UsuarioRepository.class),suscripciones,pagos,anuncios,ctx));
        var schedulerBloqueado=proxy(new SuscripcionAutomaticaService(suscripciones,anuncios,repo));
        var renovar=servicio(pagos);var scheduler=proxy(new SuscripcionAutomaticaService(suscripciones,anuncios,empresas));
        try(var pool=Executors.newFixedThreadPool(2)){
            try{
                var f1=pool.submit(()->{if(renovarPrimero)renovarBloqueado.renovar(eid,request,"RENOVAR");else schedulerBloqueado.procesar();});esperar(dentro);
                var f2=pool.submit(()->{segunda.countDown();if(renovarPrimero)scheduler.procesar();else renovar.renovar(eid,request,"RENOVAR");});
                esperar(segunda);assertThrows(TimeoutException.class,()->f2.get(200,TimeUnit.MILLISECONDS));continuar.countDown();f1.get(10,TimeUnit.SECONDS);f2.get(10,TimeUnit.SECONDS);
            }finally{continuar.countDown();}
        }
        assertEquals(EstadoEmpresa.ACTIVA,empresas.findById(eid).orElseThrow().getEstadoPlataforma());
        assertTrue(suscripciones.findByEmpresaId(eid).orElseThrow().getVenceEn().isAfter(LocalDateTime.now()));
        assertEquals(1,pagos.findAllByEmpresaIdOrderByPagadoEnDesc(eid).size());
    }
    @Test void metadataIdempotenciaEsAditivaYNoGlobal(){
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pago_empresa' AND COLUMN_NAME IN ('idempotency_hash','request_hash') AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64",Integer.class));
        assertEquals("empresa_id,idempotency_hash",jdbc.queryForObject("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pago_empresa' AND INDEX_NAME='uk_pago_empresa_operacion' AND NON_UNIQUE=0",String.class));
    }
    @Test void dosSchedulersNoDuplicanAvisoConSnapshotAnteriorAlLock()throws Exception{
        tx.executeWithoutResult(s->suscripciones.findByEmpresaId(eid).orElseThrow().setVenceEn(LocalDateTime.now().plusHours(10)));
        var dentro=new CountDownLatch(1);var continuar=new CountDownLatch(1);var segunda=new CountDownLatch(1);
        var repo=mock(EmpresaRepository.class,org.mockito.AdditionalAnswers.delegatesTo(empresas));
        doAnswer(i->{var r=empresas.bloquearPorId(eid);dentro.countDown();esperar(continuar);return r;}).when(repo).bloquearPorId(eid);
        var uno=proxy(new SuscripcionAutomaticaService(suscripciones,anuncios,repo));
        var dos=proxy(new SuscripcionAutomaticaService(suscripciones,anuncios,empresas));
        try(var pool=Executors.newFixedThreadPool(2)){
            try{
                var f1=pool.submit(uno::procesar);esperar(dentro);
                var f2=pool.submit(()->{segunda.countDown();return dos.procesar();});esperar(segunda);
                assertThrows(TimeoutException.class,()->f2.get(200,TimeUnit.MILLISECONDS));continuar.countDown();
                f1.get(10,TimeUnit.SECONDS);f2.get(10,TimeUnit.SECONDS);
            }finally{continuar.countDown();}
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM anuncio_empresa WHERE empresa_id=?",Integer.class,eid));
        assertEquals(EstadoEmpresa.ACTIVA,empresas.findById(eid).orElseThrow().getEstadoPlataforma());
    }
    private SuperAdminEmpresaService servicio(PagoEmpresaRepository p){var ctx=mock(SecurityContextService.class);when(ctx.usuarioId()).thenReturn(44L);return proxy(new SuperAdminEmpresaService(empresas,mock(UsuarioRepository.class),suscripciones,p,anuncios,ctx));}
    @SuppressWarnings("unchecked") private <T>T proxy(T target){var f=new ProxyFactory(target);f.setProxyTargetClass(true);var a=new TransactionInterceptor();a.setTransactionManager(tm);a.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());f.addAdvice(a);return (T)f.getProxy();}
    private static void esperar(CountDownLatch l){try{assertTrue(l.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
