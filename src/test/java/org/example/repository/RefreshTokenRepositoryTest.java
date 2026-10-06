package org.example.repository;

import org.example.auth.dto.AuthResponse;
import org.example.auth.exception.AuthException;
import org.example.auth.model.*;
import org.example.auth.repository.*;
import org.example.auth.security.JwtService;
import org.example.auth.service.*;
import org.example.model.Empresa;
import org.example.service.AdminAccessService;
import org.junit.jupiter.api.*;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenRepositoryTest {
    private static final String PLANO = "REFRESH_FICTICIO_SOLO_TEST_SIN_CREDENCIALES_REALES";
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EmpresaRepository empresas;
    @Autowired private PlatformTransactionManager transactionManager;
    private Long usuarioId, empresaId;
    private TransactionTemplate tx;
    private AuthService auth;
    private JwtService jwt;

    @BeforeEach
    void setUp() throws Exception {
        tx = new TransactionTemplate(transactionManager); jwt = mock(JwtService.class);
        AuthService target = new AuthService(usuarios, tokens, mock(PasswordEncoder.class),
                mock(PasswordPolicyService.class), jwt, mock(AdminAccessService.class));
        ReflectionTestUtils.setField(target, "refreshDays", 30);
        // Ejecutar las anotaciones reales, incluido noRollbackFor=AuthException, sin arrancar la app.
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(transactionManager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(advice); auth = (AuthService) factory.getProxy();
        tx.executeWithoutResult(s -> {
            Empresa e = empresas.saveAndFlush(Empresa.builder().ruc("20999999999").razonSocial("EMPRESA TEST REFRESH").build());
            empresaId = e.getId();
            Usuario u = usuarios.saveAndFlush(Usuario.builder().empresa(e).nombre("TRABAJADOR TEST")
                    .email("refresh-test@example.invalid").passwordHash("NO_LOGIN_TEST")
                    .rol(RolUsuario.TRABAJADOR).estado(EstadoUsuario.ACTIVO).build());
            usuarioId = u.getId();
            tokens.saveAndFlush(RefreshToken.builder().usuario(u).tokenHash(hash(PLANO)).familyId(UUID.randomUUID().toString())
                    .creadoEn(LocalDateTime.now()).expiraEn(LocalDateTime.now().plusDays(1)).build());
        });
    }

    @AfterEach
    void limpiarFixturesPropios() {
        tx.executeWithoutResult(s -> {
            tokens.findAll().stream().filter(r -> r.getUsuario().getId().equals(usuarioId)).forEach(tokens::delete);
            tokens.flush(); usuarios.deleteById(usuarioId); usuarios.flush(); empresas.deleteById(empresaId); empresas.flush();
        });
    }

    @Test
    void rotacionDebeCrearUnSucesorConMismaFamiliaYRevocarOriginal() {
        when(jwt.generar(any())).thenReturn("ACCESS_FICTICIO_TEST");
        AuthResponse resultado = auth.refresh(PLANO);
        tx.executeWithoutResult(s -> {
            RefreshToken anterior = tokens.findByTokenHash(hash(PLANO)).orElseThrow();
            assertNotNull(anterior.getRevocadoEn()); assertNotNull(anterior.getReemplazadoPorHash());
            RefreshToken nuevo = tokens.findByTokenHash(anterior.getReemplazadoPorHash()).orElseThrow();
            assertEquals(anterior.getFamilyId(), nuevo.getFamilyId()); assertNull(nuevo.getRevocadoEn());
            assertEquals(usuarioId, nuevo.getUsuario().getId()); assertEquals(2, tokens.count());
            assertEquals(hash(resultado.refreshToken()), nuevo.getTokenHash());
        });
    }

    @Test
    void dosRefreshConcurrentesSoloDebenRotarUnaVezYElReplayDebeInvalidarSesiones() throws Exception {
        CountDownLatch generando = new CountDownLatch(1), liberar = new CountDownLatch(1), segundoIniciado = new CountDownLatch(1);
        when(jwt.generar(any())).thenAnswer(inv -> {
            generando.countDown(); assertTrue(liberar.await(10, TimeUnit.SECONDS)); return "ACCESS_FICTICIO_TEST";
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<AuthResponse> primero = executor.submit(() -> auth.refresh(PLANO));
                assertTrue(generando.await(10, TimeUnit.SECONDS));
                Future<?> segundo = executor.submit(() -> {
                    segundoIniciado.countDown(); assertThrows(AuthException.class, () -> auth.refresh(PLANO));
                });
                assertTrue(segundoIniciado.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> segundo.get(200, TimeUnit.MILLISECONDS));
                liberar.countDown(); assertNotNull(primero.get(10, TimeUnit.SECONDS)); segundo.get(10, TimeUnit.SECONDS);
            } finally { liberar.countDown(); }
        }
        verify(jwt, times(1)).generar(any());
        tx.executeWithoutResult(s -> {
            assertEquals(2, tokens.count(), "no debe existir un segundo sucesor");
            assertTrue(tokens.findAll().stream().allMatch(RefreshToken::estaRevocado));
            assertEquals(1, usuarios.findById(usuarioId).orElseThrow().getTokenVersion());
        });
    }

    @Test
    void tokenExpiradoDebeRevocarseSinCrearSucesor() {
        tx.executeWithoutResult(s -> tokens.findByTokenHash(hash(PLANO)).orElseThrow().setExpiraEn(LocalDateTime.now().minusDays(1)));
        assertThrows(AuthException.class, () -> auth.refresh(PLANO));
        tx.executeWithoutResult(s -> { assertTrue(tokens.findByTokenHash(hash(PLANO)).orElseThrow().estaRevocado()); assertEquals(1, tokens.count()); });
        verifyNoInteractions(jwt);
    }

    @Test
    void logoutDebeRevocarTokenYNoPermitirRotacion() {
        auth.logout(PLANO); assertThrows(AuthException.class, () -> auth.refresh(PLANO));
        assertEquals(1, tokens.count()); verifyNoInteractions(jwt);
    }

    @Test
    void falloAlGenerarAccessDebeRevertirConsumoYSucesor() {
        when(jwt.generar(any())).thenThrow(new IllegalStateException("fallo de firma simulado"));
        assertThrows(IllegalStateException.class, () -> auth.refresh(PLANO));
        tx.executeWithoutResult(s -> {
            RefreshToken r = tokens.findByTokenHash(hash(PLANO)).orElseThrow();
            assertNull(r.getRevocadoEn()); assertNull(r.getReemplazadoPorHash()); assertEquals(1, tokens.count());
        });
    }

    private static String hash(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
