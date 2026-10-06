package org.example.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Guards de soporte aislado; no BD, secretos reales, Main ni SUNAT. */
class Fase5BetaPrecheckIntellijTest {
    @TempDir Path dir;
    private MockEnvironment env() throws Exception {
        var e = new MockEnvironment(); var props = new Properties();
        for (String name : List.of("application.properties", "application-beta.properties", "fase5-beta-precheck.properties"))
            try (var in = new ClassPathResource(name).getInputStream()) { props.load(in); }
        props.forEach((k,v) -> e.setProperty(k.toString(), v.toString()));
        e.setActiveProfiles("beta");
        String synthetic = Base64.getEncoder().encodeToString(new byte[32]);
        return e.withProperty("SPRING_PROFILES_ACTIVE", "beta").withProperty("APP_DEPLOYMENT_ENVIRONMENT", "BETA")
                .withProperty("SPRING_DATASOURCE_URL", Fase5BetaPrecheckIntellij.URL).withProperty("APP_BETA_DATASOURCE_URL", Fase5BetaPrecheckIntellij.URL)
                .withProperty("SPRING_DATASOURCE_USERNAME", "CUENTA_EXISTENTE_FICTICIA").withProperty("SPRING_DATASOURCE_PASSWORD", "SOLO_FIXTURE")
                .withProperty("APP_ADMIN_KEY", "ADMIN_FICTICIO").withProperty("APP_MASTER_KEY_BASE64", synthetic).withProperty("APP_JWT_SECRET_BASE64", synthetic);
    }
    @Test void cuentaExistentePermitidaSinExigirUsuarioExclusivo() throws Exception {
        assertDoesNotThrow(() -> Fase5BetaPrecheckIntellij.validarConfiguracion(env()));
    }
    @ParameterizedTest @ValueSource(strings={"bd_facturacion", "bd_facturacion_test", "bd_facturacion_restore_test"})
    void destinoDistintoSeRechazaAunqueCoincidaConAprobacion(String base) throws Exception {
        var e = env().withProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:3306/" + base)
                .withProperty("APP_BETA_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:3306/" + base);
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
    }
    @ParameterizedTest @ValueSource(strings={"prod", "test"})
    void mezclaDePerfilesSigueBloqueada(String otro) throws Exception {
        var e = env(); e.setActiveProfiles("beta", otro);
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
    }
    @Test void passwordNoVisibleNoSeSustituyePorCuentaODefault() throws Exception {
        var e = env().withProperty("SPRING_DATASOURCE_PASSWORD", "");
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
        // Regresion del acceso omitido silenciosamente: la continuacion exige el password de ensayo.
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.passwordEnsayoObligatoria(env()));
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.passwordEnsayoObligatoria(env().withProperty("FASE5_USUARIO_PASSWORD", " ")));
        var fixture = env().withProperty("FASE5_USUARIO_PASSWORD", "PASSWORD_EXCLUSIVO_FIXTURE");
        assertEquals("PASSWORD_EXCLUSIVO_FIXTURE", Fase5BetaPrecheckIntellij.passwordEnsayoObligatoria(fixture));
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.passwordNuevaEnsayoObligatoria(fixture));
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.passwordNuevaEnsayoObligatoria(fixture.withProperty("FASE5_USUARIO_PASSWORD_NUEVA", " ")));
        assertEquals("NUEVA_EXCLUSIVA_FIXTURE", Fase5BetaPrecheckIntellij.passwordNuevaEnsayoObligatoria(fixture.withProperty("FASE5_USUARIO_PASSWORD_NUEVA", "NUEVA_EXCLUSIVA_FIXTURE")));
    }
    @Test void automatizacionesReactivadasSeRechazan() throws Exception {
        for (String key : List.of("security.bootstrap.enabled", "security.bootstrap.auto-create", "sunat.padron-ruc.actualizacion.enabled",
                "sunat.resumen-diario.auto.enabled", "sunat.comunicacion-baja.auto.enabled", "sunat.certificate.monitor.enabled", "security.refresh.cleanup.enabled", "spring.flyway.enabled")) {
            var e = env().withProperty(key, "true");
            assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
        }
    }
    @Test void configuracionAprobadaNoPuedeDivergir() throws Exception {
        var e = env().withProperty("APP_BETA_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:3306/otra");
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
    }
    @Test void overrideNoPuedeOcultarVariablesDeDestinoIncorrectas() throws Exception {
        var e = env().withProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:3306/bd_facturacion")
                .withProperty("spring.datasource.url", Fase5BetaPrecheckIntellij.URL)
                .withProperty("app.beta.datasource-url", Fase5BetaPrecheckIntellij.URL);
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
    }
    @Test void jsonOConfigDataExtraNoSeIgnoranSilenciosamente() throws Exception {
        for (String key : List.of("SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION", "SPRING_CONFIG_IMPORT", "SPRING_CONFIG_ADDITIONAL_LOCATION", "SPRING_PROFILES_INCLUDE")) {
            var e = env().withProperty(key, "CONFIGURACION_FICTICIA_NO_COTEJADA");
            assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConfiguracion(e));
        }
    }
    @Test void ddlActualSeAceptaSinEjecutarlo() throws Exception {
        String sql = "-- FIXTURE LOCAL\ncreate table ejemplo (id bigint); create index idx on ejemplo (id); alter table ejemplo add constraint uk unique (id);";
        assertEquals(3, Fase5BetaPrecheckIntellij.sentencias(sql).size());
    }
    @ParameterizedTest @ValueSource(strings={"DROP TABLE x", "GRANT ALL ON x.* TO y", "USE otra", "CREATE TABLE otra.x (id bigint)", "CREATE INDEX idx ON otra.x (id)"})
    void ddlFueraDeAlcanceSeRechaza(String sql) {
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.sentencias(sql + ";"));
    }
    @Test void catalogoInesperadoSeRechazaAntesDeEjecutarSql() throws Exception {
        var c = mock(Connection.class); when(c.getCatalog()).thenReturn("bd_facturacion_test");
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConexion(c));
        verify(c, never()).createStatement();
    }
    @Test void puertoInesperadoSeRechaza() throws Exception {
        var c = mock(Connection.class); var st = mock(Statement.class); var rs = mock(ResultSet.class);
        when(c.getCatalog()).thenReturn(Fase5BetaPrecheckIntellij.BASE); when(c.createStatement()).thenReturn(st);
        when(st.executeQuery("SELECT DATABASE(),@@port")).thenReturn(rs); when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn(Fase5BetaPrecheckIntellij.BASE); when(rs.getInt(2)).thenReturn(33307);
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.validarConexion(c));
    }
    @Test void rutaDeMetadataDebeSerExternaYSinArchivoComoPadre() throws Exception {
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.rutaExterna("relativa"));
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.rutaExterna(Path.of("").toAbsolutePath().resolve("no-crear").toString()));
        Path file = Files.writeString(dir.resolve("archivo"), "FIXTURE");
        assertThrows(IllegalStateException.class, () -> Fase5BetaPrecheckIntellij.rutaExterna(file.resolve("hijo").toString()));
        assertEquals(dir.normalize(), Fase5BetaPrecheckIntellij.rutaExterna(dir.toString()));
        assertEquals(dir.normalize(), Fase5BetaPrecheckIntellij.rutaExterna(dir.toString().replace('\\', '/')));
    }
    @Test void tiposNoSeRelajanParaOcultarPerdidaDePrecision() {
        assertEquals("int", Fase5BetaPrecheckIntellij.normalizar("INTEGER"));
        assertEquals("bit", Fase5BetaPrecheckIntellij.normalizar("bit(1)"));
        assertNotEquals(Fase5BetaPrecheckIntellij.normalizar("decimal(22,10)"), Fase5BetaPrecheckIntellij.normalizar("decimal(12,2)"));
    }
}
