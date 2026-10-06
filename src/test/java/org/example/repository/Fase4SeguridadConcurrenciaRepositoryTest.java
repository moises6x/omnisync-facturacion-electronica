package org.example.repository;

import org.example.auth.dto.*;
import org.example.auth.exception.AuthException;
import org.example.auth.model.*;
import org.example.auth.repository.*;
import org.example.auth.security.*;
import org.example.auth.service.*;
import org.example.admin.dto.SuspenderEmpresaRequest;
import org.example.admin.repository.*;
import org.example.admin.service.*;
import org.example.dto.EmpresaConfigRequest;
import org.example.model.*;
import org.example.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest @ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class Fase4SeguridadConcurrenciaRepositoryTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired RefreshTokenRepository tokens;
    @Autowired EmpresaRepository empresas;
    @Autowired PlatformTransactionManager tm;
    private TransactionTemplate tx;
    private Long uid,eid;
    private PasswordEncoder encoder;
    private AuthService auth;
    private SuperAdminUsuarioService admin;

    @BeforeEach void fixture() {
        tx=new TransactionTemplate(tm); encoder=mock(PasswordEncoder.class);
        eid=tx.execute(s -> empresas.saveAndFlush(Empresa.builder().ruc("20100130204").razonSocial("EMISOR FICTICIO")
                .pfxBytes(new byte[]{1}).pfxPassword("FICTICIO").usuarioSol("SOL_ANTERIOR").claveSol("CIF_ANTERIOR").apiKeyHash("API_ANTERIOR").build()).getId());
        uid=tx.execute(s -> usuarios.saveAndFlush(Usuario.builder().empresa(empresas.findById(eid).orElseThrow()).nombre("TRABAJADOR")
                .email("fase4@example.invalid").rol(RolUsuario.TRABAJADOR).estado(EstadoUsuario.ACTIVO).passwordHash("HASH_ANTERIOR").build()).getId());
        var jwt=mock(JwtService.class); when(jwt.generar(any())).thenReturn("JWT_FICTICIO");
        var target=new AuthService(usuarios,tokens,encoder,mock(PasswordPolicyService.class),jwt,mock(AdminAccessService.class));
        ReflectionTestUtils.setField(target,"maxIntentos",5); ReflectionTestUtils.setField(target,"minutosBloqueo",15);
        ReflectionTestUtils.setField(target,"refreshDays",30); target.init(); auth=proxy(target);
        admin=proxy(new SuperAdminUsuarioService(empresas,usuarios,tokens,encoder,mock(PasswordPolicyService.class)));
    }
    @AfterEach void limpiar() {
        tx.executeWithoutResult(s -> {
            tokens.findAll().stream().filter(t -> t.getUsuario().getId().equals(uid)).forEach(tokens::delete); tokens.flush();
            usuarios.deleteById(uid); usuarios.flush(); empresas.deleteById(eid); empresas.flush();
        });
    }

    @ParameterizedTest @ValueSource(strings={"desactivar","temporal","fallo-reset","password-logout"})
    void escriturasDeUsuarioDeDosServiciosNoRestauranDatosAntiguos(String caso) throws Exception {
        var dentro=new CountDownLatch(1); var salir=new CountDownLatch(1); var segunda=new CountDownLatch(1); var comparaciones=new AtomicInteger();
        when(encoder.matches(anyString(),anyString())).thenAnswer(i -> {
            if(comparaciones.incrementAndGet()==1){dentro.countDown(); esperar(salir);}
            return !caso.equals("fallo-reset") && i.getArgument(0).equals("ACTUAL_FICTICIO");
        });
        when(encoder.encode("NUEVA_FICTICIA")).thenReturn("HASH_NUEVO");
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                var f1=pool.submit(() -> {
                    if(caso.equals("password-logout")) auth.cambiarPassword(uid,new ChangePasswordRequest("ACTUAL_FICTICIO","NUEVA_FICTICIA"));
                    else if(caso.equals("fallo-reset")) assertThrows(AuthException.class,() -> auth.login(new LoginRequest("fase4@example.invalid","ACTUAL_FICTICIO")));
                    else auth.login(new LoginRequest("fase4@example.invalid","ACTUAL_FICTICIO"));
                });
                esperar(dentro);
                var f2=pool.submit(() -> {
                    segunda.countDown();
                    if(caso.equals("desactivar")) admin.actualizarEstado(eid,uid,false);
                    else if(caso.equals("password-logout")) auth.logoutAll(uid);
                    else admin.passwordTemporal(eid,uid,"NUEVA_FICTICIA");
                });
                esperar(segunda); assertThrows(TimeoutException.class,() -> f2.get(200,TimeUnit.MILLISECONDS));
                salir.countDown(); f1.get(10,TimeUnit.SECONDS); f2.get(10,TimeUnit.SECONDS);
            } finally { salir.countDown(); }
        }
        var u=usuarios.findById(uid).orElseThrow();
        assertEquals(caso.equals("password-logout")?2:1,u.getTokenVersion());
        if(caso.equals("desactivar")) assertEquals(EstadoUsuario.DESACTIVADO,u.getEstado());
        else assertEquals("HASH_NUEVO",u.getPasswordHash());
        if(caso.equals("temporal")||caso.equals("fallo-reset")) assertTrue(u.isDebeCambiarPassword());
        if(caso.equals("fallo-reset")) assertEquals(1,u.getIntentosFallidos());
    }

    @ParameterizedTest @ValueSource(strings={"suspension","rotacion","configuracion"})
    void configuracionEmpresaNoSobrescribeCamposConcurrentes(String caso) throws Exception {
        var contexto=mock(SecurityContextService.class); when(contexto.empresaIdRequerido()).thenReturn(eid);
        when(contexto.rucEmpresaRequerido()).thenReturn("20100130204");
        var crypto=mock(CryptoService.class); var dentro=new CountDownLatch(1); var salir=new CountDownLatch(1); var segunda=new CountDownLatch(1);
        when(crypto.cifrarUsuarioSol(anyString(),anyString())).thenAnswer(i -> { dentro.countDown(); esperar(salir); return "SOL_NUEVO"; });
        when(crypto.cifrarClaveSol(anyString(),anyString())).thenReturn("CIF_NUEVO");
        var keys=mock(ApiKeyService.class); when(keys.generarApiKey()).thenReturn("API_FICTICIA_NUEVA"); when(keys.hash(any())).thenReturn("API_NUEVA");
        var uno=proxy(new EmpresaConfiguracionService(empresas,contexto,mock(EmpresaAccessService.class),crypto,mock(XmlSignerService.class),keys));
        var dos=proxy(new EmpresaConfiguracionService(empresas,contexto,mock(EmpresaAccessService.class),crypto,mock(XmlSignerService.class),keys));
        var superAdmin=proxy(new SuperAdminEmpresaService(empresas,mock(UsuarioRepository.class),mock(SuscripcionEmpresaRepository.class),
                mock(PagoEmpresaRepository.class),mock(AnuncioEmpresaRepository.class),contexto));
        // convertir requiere suscripción: proveer fixture de lectura, no crear pagos ni bootstrap.
        var sus=(SuscripcionEmpresaRepository)ReflectionTestUtils.getField(superAdmin,"suscripcionRepository");
        var periodo=new org.example.admin.model.SuscripcionEmpresa(); periodo.setVenceEn(java.time.LocalDateTime.now().plusDays(1));
        when(sus.findByEmpresaId(eid)).thenReturn(java.util.Optional.of(periodo));
        var r=new EmpresaConfigRequest(); r.setUsuarioSol("FICTICIO"); r.setClaveSol("FICTICIO");
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                var f1=pool.submit(() -> uno.configurarSunat(r,null)); esperar(dentro);
                var f2=pool.submit(() -> { segunda.countDown();
                    if(caso.equals("suspension")) superAdmin.suspender(eid,new SuspenderEmpresaRequest("FICTICIO"));
                    else if(caso.equals("rotacion")) dos.rotarApiKey(); else dos.configurarSunat(r,null);
                });
                esperar(segunda); assertThrows(TimeoutException.class,() -> f2.get(200,TimeUnit.MILLISECONDS));
                salir.countDown(); f1.get(10,TimeUnit.SECONDS); f2.get(10,TimeUnit.SECONDS);
            } finally { salir.countDown(); }
        }
        var e=empresas.findById(eid).orElseThrow(); assertEquals("SOL_NUEVO",e.getUsuarioSol()); assertEquals("CIF_NUEVO",e.getClaveSol());
        if(caso.equals("suspension")) assertEquals(EstadoEmpresa.SUSPENDIDA,e.getEstadoPlataforma());
        if(caso.equals("rotacion")) assertEquals("API_NUEVA",e.getApiKeyHash());
    }
    @SuppressWarnings("unchecked") private <T>T proxy(T target){
        var f=new ProxyFactory(target); f.setProxyTargetClass(true); var a=new TransactionInterceptor(); a.setTransactionManager(tm);
        a.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); f.addAdvice(a); return (T)f.getProxy();
    }
    private static void esperar(CountDownLatch l){try{assertTrue(l.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
