package org.example.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.apache.xml.security.signature.XMLSignature;
import org.example.admin.dto.CrearEmpresaRequest;
import org.example.admin.repository.*;
import org.example.admin.service.SuperAdminEmpresaService;
import org.example.admin.service.SuperAdminUsuarioService;
import org.example.auth.dto.CrearUsuarioRequest;
import org.example.auth.dto.LoginRequest;
import org.example.auth.dto.ChangePasswordRequest;
import org.example.auth.model.RolUsuario;
import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.Usuario;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UsuarioRepository;
import org.example.auth.security.AppPrincipal;
import org.example.auth.security.SecurityContextService;
import org.example.auth.security.JwtService;
import org.example.auth.service.AuthService;
import org.example.auth.service.PasswordPolicyService;
import org.example.dto.EmpresaConfigRequest;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.dto.ComunicacionBajaRequest;
import org.example.model.ComunicacionBaja;
import org.example.model.EstadoComunicacionBaja;
import org.example.model.Comprobante;
import org.example.model.EstadoComprobante;
import org.example.model.RespuestaSunatRecibida;
import org.example.model.ResumenDiario;
import org.example.model.EstadoResumenDiario;
import org.example.repository.*;
import org.example.auth.repository.EventoAuditoriaRepository;
import org.example.auth.service.AuditoriaService;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.example.model.Empresa;
import org.example.model.EstadoEmpresa;
import org.example.repository.EmpresaRepository;
import org.example.service.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.w3c.dom.Element;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Herramienta de TEST operada en IntelliJ, NO otra aplicacion ni un endpoint.
 * Consume secretos del proceso, nunca los imprime/archiva. No SpringApplication,
 * component scan de beans, scheduler, bootstrap ni migrador.
 * Los modos de precheck nunca emiten. --primera-factura-beta requiere autorizacion
 * separada y permite SOLO una factura/sendBill con services productivos.
 * --diagnosticar-primera-factura-beta comprueba SOLO su configuracion inicial:
 * JDBC local beta/Hibernate sin DDL ni refresh de beans fiscales, y retorna.
 * --continuar-primera-factura-beta reutiliza SOLO ID=1, sin creacion ni correlativos.
 * --segunda-factura-beta crea SOLO otra factura, con idempotencia/evidencia independientes.
 * --nota-credito-factura-beta crea SOLO una NC del escenario local contra 01/F501-2.
 * --boleta-resumen-beta crea SOLO una boleta y un RC; maximo un sendSummary y tres getStatus.
 * --nota-credito-boleta-beta crea SOLO una NC de 03/B501-1 y un RC nuevo con los mismos limites.
 * --factura-para-baja-beta crea SOLO una factura dedicada; --baja-factura-beta solo comunica su baja.
 * Solo JDBC al catalogo beta aprobado y services/repositorios actuales en TX aisladas.
 */
public final class Fase5BetaPrecheckIntellij {
    static final String URL = "jdbc:mysql://127.0.0.1:3306/bd_facturacion_beta_ensayo";
    static final String BASE = "bd_facturacion_beta_ensayo";
    private static final String EMAIL = "ensayo.fase5@example.invalid";
    private static final String SEGUNDA_FACTURA_KEY = "FASE5-SEGUNDA-FACTURA-BETA-UNICA";
    private static final String NC_FACTURA_KEY = "FASE5-NC-FACTURA-F501-2-BETA-UNICA";
    private static final String NC_MOTIVO = "04";
    private static final String NC_DESCRIPCION = "DESCUENTO FICTICIO DE ENSAYO BETA";
    private static final String BOLETA_RC_KEY = "FASE5-BOLETA-RC-BETA-UNICA";
    private static final String NC_BOLETA_RC_KEY = "FASE5-NC-BOLETA-B501-1-RC-BETA-UNICA";
    private static final String FACTURA_RA_KEY = "FASE5-FACTURA-PARA-BAJA-BETA-UNICA";
    private static final String RA_MOTIVO = "BAJA DE FACTURA FICTICIA DE ENSAYO BETA NO OTORGADA";
    private static String etapa = "CONFIGURACION";
    private static ControlFacturaUnica controlFacturaActiva;

    public static void main(String[] args) {
        boolean soloDiagnostico = Arrays.equals(args, new String[]{"--diagnosticar-primera-factura-beta"});
        // No permitir que drivers/Hibernate impriman excepciones con datos del entorno.
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT").setLevel(Level.OFF);
        try {
            if (Arrays.equals(args, new String[]{"--preparar-acceso"})) {
                prepararAcceso(entorno(), false);
            } else if (Arrays.equals(args, new String[]{"--cambiar-password-acceso"})) {
                prepararAcceso(entorno(), true);
            } else if (Arrays.equals(args, new String[]{"--primera-factura-beta"})) {
                etapa = "PRIMERA_FACTURA_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), false, false, false, false, false);
            } else if (Arrays.equals(args, new String[]{"--continuar-primera-factura-beta"})) {
                etapa = "PRIMERA_FACTURA_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), false, true, false, false, false);
            } else if (Arrays.equals(args, new String[]{"--segunda-factura-beta"})) {
                etapa = "SEGUNDA_FACTURA_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), false, false, true, false, false);
            } else if (Arrays.equals(args, new String[]{"--nota-credito-factura-beta"})) {
                etapa = "NC_FACTURA_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), false, false, false, true, false);
            } else if (Arrays.equals(args, new String[]{"--boleta-resumen-beta"})) {
                etapa = "BOLETA_RC_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), false, false, false, false, true);
            } else if (Arrays.equals(args, new String[]{"--nota-credito-boleta-beta"})) {
                etapa = "NC_BOLETA_RC_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), false, false, false, false, false, true);
            } else if (Arrays.equals(args, new String[]{"--factura-para-baja-beta"})) {
                etapa = "FACTURA_RA_CONFIGURACION";
                primeraFacturaBeta(entorno(), false, false, false, false, false, false, true, false);
            } else if (Arrays.equals(args, new String[]{"--baja-factura-beta"})) {
                etapa = "BAJA_FACTURA_CONFIGURACION";
                primeraFacturaBeta(entorno(), false, false, false, false, false, false, false, true);
            } else if (soloDiagnostico) {
                etapa = "PRIMERA_FACTURA_CONFIG_CARGA_ENTORNO";
                primeraFacturaBeta(entorno(), true, false, false, false, false);
                // Solo despues de cerrar SessionFactory/registry/conexion sin excepcion.
                System.out.println("DIAGNOSTICO_CONFIGURACION=OK");
            } else {
                exigir(Arrays.equals(args, new String[]{"--preparar"}), "ARGUMENTO_REQUERIDO");
                ejecutar(entorno());
            }
        } catch (Throwable error) {
            System.out.println("PRECHECK_ERROR_ETAPA=" + etapa);
            System.out.println("PRECHECK_ERROR_TIPO=" + error.getClass().getSimpleName());
            // Ni mensaje/cause/stack: podrian contener SQL o datos de conexion.
            if (soloDiagnostico || etapa.startsWith("PRIMERA_FACTURA_CONFIG_")) {
                // El diagnostico informa SOLO subetapa/tipo; nunca mensajes ni datos.
            } else if (controlFacturaActiva != null) {
                System.out.println("SEND_BILL_INVOCACIONES=" + controlFacturaActiva.invocaciones.get());
                if (controlFacturaActiva.boletaRc || controlFacturaActiva.bajaFactura) System.out.println("SEND_SUMMARY_INVOCACIONES=" + controlFacturaActiva.resumenesEnviados.get()
                        + "\nGET_STATUS_INVOCACIONES=" + controlFacturaActiva.consultasTicket.get());
                boolean sinEnvio = controlFacturaActiva.boletaRc || controlFacturaActiva.bajaFactura
                        ? controlFacturaActiva.resumenesEnviados.get() == 0 : controlFacturaActiva.invocaciones.get() == 0;
                System.out.println("RESULTADO=" + (sinEnvio ? "BLOQUEADO_ANTES_DE_ENVIAR" : "ENVIO_INCIERTO"));
                System.out.println("NO_REEJECUTAR=SI");
            } else System.out.println("ESTADO=BLOQUEADO ANTES DE EJECUCION BETA");
            System.exit(1);
        } finally { SecurityContextHolder.clearContext(); }
    }

    static ConfigurableEnvironment entorno() throws IOException {
        var env = new StandardEnvironment(); var p = new Properties();
        for (String file : List.of("application.properties", "application-beta.properties", "fase5-beta-precheck.properties"))
            try (var in = new InputStreamReader(new ClassPathResource(file).getInputStream(), StandardCharsets.UTF_8)) { p.load(in); }
        env.getPropertySources().addLast(new PropertiesPropertySource("fase5-soporte-explicito", p));
        env.setActiveProfiles(env.getRequiredProperty("spring.profiles.active").split(","));
        return env;
    }

    static void validarConfiguracion(ConfigurableEnvironment env) {
        validarConfiguracion(env, false);
    }

    private static void subetapaValidacion(boolean detallada, String guarda) {
        if (detallada) etapa = "PRIMERA_FACTURA_CONFIG_VALIDACION_GENERAL_" + guarda;
    }

    private static void validarConfiguracion(ConfigurableEnvironment env, boolean detallada) {
        // Este soporte carga explicitamente tres recursos, no el ConfigData de Boot.
        // Rechazar mecanismos que de otro modo quedarian sin cotejar.
        for (String key : List.of("spring.application.json", "SPRING_APPLICATION_JSON", "spring.config.location", "SPRING_CONFIG_LOCATION",
                "spring.config.import", "SPRING_CONFIG_IMPORT", "spring.config.additional-location", "SPRING_CONFIG_ADDITIONAL_LOCATION",
                "spring.config.name", "SPRING_CONFIG_NAME", "spring.profiles.include", "SPRING_PROFILES_INCLUDE")) {
            subetapaValidacion(detallada, "CONFIG_EXTERNA_" + key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            exigir(env.getProperty(key, "").isBlank(), "CONFIGURACION_EXTERNA_NO_COTEJADA");
        }
        subetapaValidacion(detallada, "PERFIL_EXCLUSIVO");
        exigir(Arrays.equals(env.getActiveProfiles(), new String[]{"beta"}), "PERFIL_NO_EXCLUSIVO");
        subetapaValidacion(detallada, "PROCESSOR_FISCAL");
        ConfiguracionFiscalEnvironmentPostProcessor.validar(env);
        subetapaValidacion(detallada, "VARIABLES_DESTINO");
        exigir(URL.equals(env.getProperty("SPRING_DATASOURCE_URL")) && URL.equals(env.getProperty("APP_BETA_DATASOURCE_URL")), "VARIABLES_DESTINO_NO_APROBADAS");
        subetapaValidacion(detallada, "DATASOURCE_EFECTIVO");
        exigir(URL.equals(env.getProperty("spring.datasource.url")), "DESTINO_NO_APROBADO");
        subetapaValidacion(detallada, "APROBACION_DATASOURCE");
        exigir(URL.equals(env.getProperty("app.beta.datasource-url")), "APROBACION_NO_COINCIDE");
        for (String key : List.of("SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "APP_ADMIN_KEY",
                "APP_MASTER_KEY_BASE64", "APP_JWT_SECRET_BASE64")) {
            subetapaValidacion(detallada, "DISPONIBILIDAD_" + key); // Nombre fijo de guarda, nunca valor.
            exigir(!env.getProperty(key, "").isBlank(), "VARIABLE_NO_VISIBLE");
        }
        subetapaValidacion(detallada, "MASTER_FORMATO_LONGITUD");
        exigir(Base64.getDecoder().decode(env.getRequiredProperty("APP_MASTER_KEY_BASE64")).length == 32, "MASTER_INVALIDA");
        subetapaValidacion(detallada, "JWT_FORMATO_LONGITUD");
        exigir(Base64.getDecoder().decode(env.getRequiredProperty("APP_JWT_SECRET_BASE64")).length >= 32, "JWT_INVALIDO");
        subetapaValidacion(detallada, "CUENTA_COHERENTE");
        exigir(env.getRequiredProperty("SPRING_DATASOURCE_USERNAME").equals(env.getProperty("spring.datasource.username")), "CUENTA_NO_COINCIDE");
        subetapaValidacion(detallada, "PASSWORD_BD_COHERENTE");
        exigir(env.getRequiredProperty("SPRING_DATASOURCE_PASSWORD").equals(env.getProperty("spring.datasource.password")), "PASSWORD_NO_COINCIDE");
        subetapaValidacion(detallada, "MASTER_COHERENTE");
        exigir(env.getRequiredProperty("APP_MASTER_KEY_BASE64").equals(env.getProperty("security.secrets.master-key-base64")), "MASTER_NO_COINCIDE");
        subetapaValidacion(detallada, "JWT_COHERENTE");
        exigir(env.getRequiredProperty("APP_JWT_SECRET_BASE64").equals(env.getProperty("security.jwt.secret-base64")), "JWT_NO_COINCIDE");
        subetapaValidacion(detallada, "ADMIN_COHERENTE");
        exigir(env.getRequiredProperty("APP_ADMIN_KEY").equals(env.getProperty("security.admin-key")), "ADMIN_NO_COINCIDE");
        subetapaValidacion(detallada, "ZONA_FISCAL");
        exigir("America/Lima".equals(env.getProperty("app.time-zone")), "ZONA_FISCAL_INESPERADA");
        for (String key : List.of("security.bootstrap.enabled", "security.bootstrap.auto-create", "spring.flyway.enabled",
                "sunat.resumen-diario.auto.enabled", "sunat.comunicacion-baja.auto.enabled", "sunat.padron-ruc.actualizacion.enabled",
                "sunat.certificate.monitor.enabled", "security.refresh.cleanup.enabled", "sunat.debug.xml")) {
            subetapaValidacion(detallada, "AUTOMATIZACION_" + key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            exigir("false".equals(env.getProperty(key)), "AUTOMATIZACION_ACTIVA");
        }
        subetapaValidacion(detallada, "SUSCRIPCIONES_OFF");
        exigir("-".equals(env.getProperty("admin.suscripciones.cron")), "SUSCRIPCIONES_ACTIVAS");
        subetapaValidacion(detallada, "DDL_AUTO");
        exigir("validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")), "DDL_AUTO_INESPERADO");
        subetapaValidacion(detallada, "INGRESS_LOCAL");
        exigir("127.0.0.1".equals(env.getProperty("server.address")), "INGRESS_NO_LOCAL");
        subetapaValidacion(detallada, "PARALELISMO");
        exigir("1".equals(env.getProperty("sunat.async.max-pool-size")), "PARALELISMO_INESPERADO");
        subetapaValidacion(detallada, "RECOVERY_DIFERIDO");
        exigir(env.getProperty("sunat.envio-recovery.initial-delay-ms", Long.class, 0L) >= TimeUnit.HOURS.toMillis(1), "RECOVERY_NO_CONTROLADO");
        subetapaValidacion(detallada, "CORS_ORIGENES");
        for (String origin : env.getRequiredProperty("security.cors.allowed-origins").split(",")) {
            URI uri = URI.create(origin.trim());
            exigir(Set.of("http", "https").contains(uri.getScheme()) && Set.of("localhost", "127.0.0.1").contains(uri.getHost())
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty()), "CORS_NO_LOCAL");
        }
    }

    static Path rutaExterna(String text) throws IOException {
        Path path = Path.of(text);
        exigir(path.isAbsolute(), "RUTA_NO_ABSOLUTA");
        path = path.normalize();
        for (Path component : path) exigir(!component.toString().equalsIgnoreCase("padron_reducido_ruc"), "RUTA_RESTRINGIDA");
        exigir(!path.startsWith(Path.of("").toAbsolutePath().normalize()), "RUTA_DENTRO_PROYECTO");
        // Comprobar padres de arriba abajo, sin seguir enlaces hacia contenido ajeno.
        Path cursor = path.getRoot();
        for (Path component : path) {
            cursor = cursor.resolve(component);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                var attrs = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                exigir(attrs.isDirectory() && !attrs.isSymbolicLink() && !attrs.isOther(), "RUTA_CON_ENLACE_O_ARCHIVO");
            }
        }
        return path;
    }

    static void validarConexion(Connection c) throws Exception {
        exigir(BASE.equals(c.getCatalog()), "CATALOGO_INESPERADO");
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT DATABASE(),@@port")) {
            exigir(r.next() && BASE.equals(r.getString(1)) && r.getInt(2) == 3306, "IDENTIDAD_BD_INESPERADA");
        }
    }

    static List<String> sentencias(String ddl) {
        String body = ddl.replaceAll("(?m)^--[^\\r\\n]*", "").trim();
        var result = Arrays.stream(body.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        exigir(!result.isEmpty(), "DDL_VACIO");
        for (String sql : result) exigir(sql.matches("(?is)(create table |create index |alter table )[a-zA-Z0-9_]+ .*") && !sql.contains("."), "DDL_NO_PERMITIDO");
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransaccionesEnsayo { }

    /** Orquestacion de TEST autorizada, no una implementacion fiscal beta.
     * Sin Main, component scan de beans ni schedulers; beans fiscales originales,
     * proxies transaccionales reales; creacion con AFTER_COMMIT/@Async o continuacion
     * explicita de ID=1 con worker original, sin beans de creacion ni otro evento.
     * Directorios exclusivos y evidencia previa impiden una segunda continuacion.
     */
    private static void primeraFacturaBeta(ConfigurableEnvironment env, boolean soloDiagnostico, boolean continuar, boolean segunda, boolean nc, boolean rc) throws Exception {
        primeraFacturaBeta(env, soloDiagnostico, continuar, segunda, nc, rc, false);
    }

    private static void primeraFacturaBeta(ConfigurableEnvironment env, boolean soloDiagnostico, boolean continuar, boolean segunda, boolean nc, boolean rc, boolean ncBoleta) throws Exception {
        primeraFacturaBeta(env, soloDiagnostico, continuar, segunda, nc, rc, ncBoleta, false, false);
    }

    private static void primeraFacturaBeta(ConfigurableEnvironment env, boolean soloDiagnostico, boolean continuar, boolean segunda, boolean nc, boolean rc, boolean ncBoleta, boolean facturaRa, boolean bajaFactura) throws Exception {
        exigir((continuar ? 1 : 0) + (segunda ? 1 : 0) + (nc ? 1 : 0) + (rc ? 1 : 0) + (ncBoleta ? 1 : 0)
                + (facturaRa ? 1 : 0) + (bajaFactura ? 1 : 0) <= 1, "MODOS_INCOMPATIBLES");
        boolean flujoRc = rc || ncBoleta;
        etapa = "PRIMERA_FACTURA_CONFIG_VALIDACION_GENERAL";
        validarConfiguracion(env, true);
        etapa = "PRIMERA_FACTURA_CONFIG_AUTORIZACION_SEND_BILL";
        if (flujoRc || bajaFactura) exigir("true".equals(env.getProperty("FASE5_AUTORIZAR_UN_SEND_SUMMARY"))
                && "true".equals(env.getProperty("FASE5_AUTORIZAR_GET_STATUS")), "AUTORIZACION_RC_REQUERIDA");
        else exigir("true".equals(env.getProperty("FASE5_AUTORIZAR_UN_SEND_BILL")), "AUTORIZACION_UNICA_REQUERIDA");
        if (bajaFactura) exigir("true".equals(env.getProperty("FASE5_FACTURA_RA_NO_OTORGADA")), "CONFIRMAR_FACTURA_DEDICADA_NO_OTORGADA");
        etapa = "PRIMERA_FACTURA_CONFIG_MAIN_DETENIDO";
        exigir("true".equals(env.getProperty("FASE5_MAIN_DETENIDO")), "CONFIRMAR_OTROS_PROCESOS_DETENIDOS");
        etapa = "PRIMERA_FACTURA_CONFIG_FISCAL_ENTORNO";
        exigir("BETA".equals(env.getProperty("sunat.environment")), "CONFIGURACION_FISCAL_NO_SEGURA");
        etapa = "PRIMERA_FACTURA_CONFIG_FISCAL_ENDPOINT";
        exigir("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService".equals(env.getProperty("sunat.billservice.url")), "CONFIGURACION_FISCAL_NO_SEGURA");
        etapa = "PRIMERA_FACTURA_CONFIG_FISCAL_XSD";
        exigir("true".equals(env.getProperty("sunat.validation.xsd.enabled")), "CONFIGURACION_FISCAL_NO_SEGURA");
        etapa = "PRIMERA_FACTURA_CONFIG_FISCAL_REFERENCIA_CDR";
        exigir("true".equals(env.getProperty("sunat.cdr.require-reference-id", "true")), "CONFIGURACION_FISCAL_NO_SEGURA");
        etapa = "PRIMERA_FACTURA_CONFIG_EVIDENCIA_RUTA";
        Path raiz = rutaExterna(env.getRequiredProperty("FASE5_EVIDENCE_PATH"));
        etapa = "PRIMERA_FACTURA_CONFIG_EVIDENCIA_DIRECTORIO";
        exigir(Files.isDirectory(raiz, LinkOption.NOFOLLOW_LINKS), "EVIDENCIA_EXTERNA_REQUERIDA");
        etapa = "PRIMERA_FACTURA_CONFIG_PREVENCION_REEJECUCION";
        Path anterior = raiz.resolve("primera-factura-beta");
        Path evidencia = bajaFactura ? raiz.resolve("baja-factura-beta") : facturaRa ? raiz.resolve("factura-para-baja-beta")
                : ncBoleta ? raiz.resolve("nota-credito-boleta-beta") : rc ? raiz.resolve("boleta-resumen-beta") : nc ? raiz.resolve("nota-credito-factura-beta") : segunda ? raiz.resolve("segunda-factura-beta")
                : continuar ? anterior.resolve("continuacion-factura-1") : anterior;
        if (continuar) validarEvidenciaSinEnvio(anterior, evidencia);
        exigir(!Files.exists(evidencia, LinkOption.NOFOLLOW_LINKS), "EJECUCION_PREVIA_NO_REPETIR");
        etapa = "PRIMERA_FACTURA_CONFIG_DATASOURCE_PROVISION";
        var ds = new DriverManagerDataSource(URL, env.getRequiredProperty("SPRING_DATASOURCE_USERNAME"), env.getRequiredProperty("SPRING_DATASOURCE_PASSWORD"));
        etapa = "PRIMERA_FACTURA_CONFIG_DATASOURCE_CONEXION";
        try (var control = ds.getConnection()) {
            etapa = "PRIMERA_FACTURA_CONFIG_DATASOURCE_IDENTIDAD";
            validarConexion(control);
            etapa = "PRIMERA_FACTURA_CONFIG_PREVENCION_REEJECUCION_LOCK";
            try (var st = control.createStatement(); var rs = st.executeQuery("SELECT GET_LOCK('FASE5_PRIMERA_FACTURA_BETA_UNICA',0)")) {
                exigir(rs.next() && rs.getInt(1) == 1, "OTRA_EJECUCION_EN_CURSO");
            }
            // GET_LOCK se libera al cerrar esta conexion; no se consulta ni importa padron.
            etapa = "PRIMERA_FACTURA_CONFIG_HIBERNATE_OPCIONES";
            var opciones = new HashMap<String, Object>();
            opciones.put("hibernate.connection.datasource", ds);
            opciones.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            opciones.put("hibernate.boot.allow_jdbc_metadata_access", false);
            opciones.put("hibernate.hbm2ddl.auto", "none");
            opciones.put("jakarta.persistence.schema-generation.database.action", "none");
            opciones.put("jakarta.persistence.schema-generation.scripts.action", "none");
            opciones.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            etapa = "PRIMERA_FACTURA_CONFIG_HIBERNATE_REGISTRY";
            var registry = new StandardServiceRegistryBuilder().applySettings(opciones).build();
            try {
                etapa = "PRIMERA_FACTURA_CONFIG_HIBERNATE_ENTIDADES";
                var sources = new MetadataSources(registry);
                var scan = new ClassPathScanningCandidateComponentProvider(false);
                scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for (var e : scan.findCandidateComponents("org.example")) sources.addAnnotatedClass(Class.forName(e.getBeanClassName()));
                etapa = "PRIMERA_FACTURA_CONFIG_HIBERNATE_METADATA";
                var metadata = sources.buildMetadata();
                etapa = "PRIMERA_FACTURA_CONFIG_HIBERNATE_SESSION_FACTORY";
                try (var factory = metadata.buildSessionFactory(); var ctx = new AnnotationConfigApplicationContext()) {
                    etapa = "PRIMERA_FACTURA_CONFIG_CONTEXTO_REGISTRO";
                    ctx.setEnvironment(env);
                    ctx.getBeanFactory().registerSingleton("entityManagerFactory", factory);
                    ctx.registerBean("transactionManager", JpaTransactionManager.class, () -> new JpaTransactionManager(factory));
                    registrarRepositorioTransaccional(ctx, factory, EmpresaRepository.class);
                    registrarRepositorioTransaccional(ctx, factory, ComprobanteRepository.class);
                    registrarRepositorioTransaccional(ctx, factory, SecuenciaCorrelativoRepository.class);
                    registrarRepositorioTransaccional(ctx, factory, SolicitudEmisionRepository.class);
                    registrarRepositorioTransaccional(ctx, factory, RespuestaSunatRecibidaRepository.class);
                    registrarRepositorioTransaccional(ctx, factory, UsuarioRepository.class);
                    registrarRepositorioTransaccional(ctx, factory, EventoAuditoriaRepository.class);
                    if (flujoRc || facturaRa || bajaFactura) {
                        registrarRepositorioTransaccional(ctx, factory, ResumenDiarioRepository.class);
                        registrarRepositorioTransaccional(ctx, factory, SecuenciaResumenDiarioRepository.class);
                    }
                    if (facturaRa || bajaFactura) {
                        registrarRepositorioTransaccional(ctx, factory, ComunicacionBajaRepository.class);
                        registrarRepositorioTransaccional(ctx, factory, SecuenciaComunicacionBajaRepository.class);
                    }
                    ctx.registerBean(ObjectMapper.class, () -> org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build());
                    ctx.registerBean(ReglasTributariasProperties.class, () -> Binder.get(env).bind("tributacion", ReglasTributariasProperties.class).orElseGet(ReglasTributariasProperties::new));
                    // Corte estructural: SIN refresh, beans fiscales activos, executor/listener,
                    // escritura de evidencia, factura, correlativo, XML ni cliente SOAP.
                    // El diagnostico mantiene las guardas, incluso la autorizacion declarativa,
                    // pero ese flag JAMAS habilita envio en este modo.
                    if (soloDiagnostico) return;
                    var guard = new ControlFacturaUnica(env, evidencia, ctx, ds);
                    guard.segunda = segunda;
                    guard.notaCredito = nc;
                    guard.boletaRc = flujoRc;
                    guard.ncBoleta = ncBoleta;
                    guard.facturaRa = facturaRa; guard.bajaFactura = bajaFactura;
                    if (continuar) { guard.evidenciaAnterior = anterior; guard.id = 1L; }
                    controlFacturaActiva = guard;
                    ctx.getBeanFactory().addBeanPostProcessor(guard);
                    ctx.register(TransaccionesEnsayo.class, AsyncConfig.class, TimeZoneConfig.class,
                            SaldoNotaCreditoService.class,
                            UblValidatorService.class, XmlSignerService.class, CryptoService.class,
                            CdrParserService.class, SunatPlazoService.class, RespuestaSunatRecibidaService.class,
                            AuditoriaService.class, SunatDirectSoapClient.class, EnvioSunatService.class);
                    if (!continuar) ctx.register(CorrelativoService.class, IdempotencyService.class,
                            CalculoTributarioService.class, ValidacionTributariaService.class, UblXmlBuilderService.class,
                            ComprobanteEnvioListener.class, FacturacionServiceImpl.class);
                    if (flujoRc) ctx.register(ResumenDiarioService.class, ResumenDiarioXmlService.class, SecuenciaResumenDiarioService.class);
                    if (bajaFactura) ctx.register(ComunicacionBajaService.class, ComunicacionBajaXmlService.class, SecuenciaComunicacionBajaService.class);
                    etapa = "PRIMERA_FACTURA_CONTEXTO_SELECTIVO";
                    ctx.refresh();
                    var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class));
                    if (continuar || segunda || nc || flujoRc || facturaRa || bajaFactura) tx.setReadOnly(true);
                    var comprobantes = ctx.getBean(ComprobanteRepository.class);
                    var empresas = ctx.getBean(EmpresaRepository.class);
                    etapa = "PRIMERA_FACTURA_RELOAD_EMPRESA_ACCESO";
                    tx.executeWithoutResult(status -> {
                        exigir(empresas.count() == 1, "EMPRESA_BETA_UNICA_REQUERIDA");
                        if (!facturaRa && !bajaFactura) exigir(comprobantes.count() == (ncBoleta ? 4 : rc ? 3 : nc ? 2 : continuar || segunda ? 1 : 0)
                                && ctx.getBean(SolicitudEmisionRepository.class).count() == (ncBoleta ? 4 : rc ? 3 : nc ? 2 : continuar || segunda ? 1 : 0), "DATOS_PREVIOS_NO_REPETIR_EMISION");
                        Empresa empresa = empresas.findByRuc("20100066603").orElseThrow();
                        guard.validarEmpresa(empresa);
                        var usuario = ctx.getBean(UsuarioRepository.class).findByEmailIgnoreCase(EMAIL).orElseThrow();
                        exigir(usuario.getEmpresa() != null && usuario.getEmpresa().getId().equals(empresa.getId())
                                && usuario.getRol() == RolUsuario.EMPRESA_ADMIN && usuario.getEstado() == EstadoUsuario.ACTIVO
                                && !usuario.isDebeCambiarPassword(), "ACCESO_NO_LISTO");
                        if (continuar) guard.validarContinuacion(EstadoComprobante.PENDIENTE_ENVIO);
                        if (segunda) guard.validarPreparacionSegunda();
                        if (nc) guard.validarPreparacionNc();
                        if (rc) guard.validarPreparacionRc();
                        if (ncBoleta) guard.validarPreparacionNcBoleta();
                        if (bajaFactura) {
                            etapa = "RA_PRECHECK_FACTURA_DEDICADA";
                            guard.validarPreparacionDedicada();
                        } else if (facturaRa) guard.validarPreparacionDedicada();
                    });
                    Files.createDirectory(evidencia); // Exclusivo: nunca borrar/reutilizar este directorio para repetir.
                    Files.writeString(evidencia.resolve("inicio.txt"), (bajaFactura ? "MODO=BAJA_FACTURA\nFACTURAS_NUEVAS=0\nRA_MAX=1\nSEND_BILL_MAX=0\nSEND_SUMMARY_MAX=1\nGET_STATUS_MAX=3"
                            : facturaRa ? "MODO=FACTURA_PARA_BAJA\nFACTURAS_MAX=1\nSEND_BILL_MAX=1\nSEND_SUMMARY_MAX=0\nGET_STATUS_MAX=0"
                            : flujoRc ? (ncBoleta ? "NC_MAX=1\nBOLETAS_NUEVAS=0" : "BOLETAS_MAX=1")
                            + "\nRC_MAX=1\nSEND_BILL_MAX=0\nSEND_SUMMARY_MAX=1\nGET_STATUS_MAX=3"
                            : (nc ? "NC_MAX=1" : "FACTURAS_MAX=1") + "\nSEND_BILL_MAX=1") + "\nINICIO=" + Instant.now() + "\n"
                            + (continuar ? "MODO=CONTINUACION\nFACTURA_ID=1\nFACTURAS_NUEVAS=0\nCORRELATIVOS_NUEVOS=0\n"
                            : segunda ? "MODO=SEGUNDA_FACTURA\nFACTURAS_NUEVAS_MAX=1\nFACTURA_ID_1=NO_REENVIAR\n"
                            : nc ? "MODO=NC_FACTURA\nNC_NUEVAS_MAX=1\nFACTURA_REFERENCIA=01/F501-2\n" : ""),
                            StandardOpenOption.CREATE_NEW);
                    Long comprobanteId;
                    if (bajaFactura) {
                        ejecutarBajaControlada(ctx, guard);
                        return;
                    } else if (facturaRa) {
                        var request = solicitudFacturaUnica(ctx, empresas, false);
                        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                            exigir(validation.getValidator().validate(request).isEmpty(), "SOLICITUD_FACTURA_DEDICADA_INVALIDA");
                        }
                        tx.executeWithoutResult(status -> guard.validarPreparacionDedicada());
                        etapa = "FACTURA_DEDICADA_CREACION_NORMAL";
                        var resultado = ctx.getBean(FacturacionServiceImpl.class).procesarComprobante("20100066603", FACTURA_RA_KEY, request);
                        comprobanteId = resultado.comprobanteId();
                        exigir(EstadoComprobante.PENDIENTE_ENVIO.name().equals(resultado.estado()), "FACTURA_DEDICADA_ESTADO_INICIAL_INVALIDO");
                    } else if (continuar) {
                        etapa = "PRIMERA_FACTURA_CONTINUACION_ID_1";
                        comprobanteId = 1L;
                        System.out.println("FACTURA_ID=1\nESTADO_PREVIO_ENVIO=PENDIENTE_ENVIO\nFACTURA_REUTILIZADA=SI\nFACTURAS_NUEVAS=0\nCORRELATIVOS_NUEVOS=0");
                        // Fuera de TransactionTemplate: solo las operaciones JPA abren sus TX normales.
                        try { ctx.getBean(EnvioSunatService.class).enviar(comprobanteId); }
                        catch (RuntimeException errorWorker) {
                            // El advice conserva el tipo. Informar tras reload, sin repetir el worker.
                            if ("NINGUNO".equals(guard.error)) guard.error = errorWorker.getClass().getSimpleName();
                        }
                    } else if (ncBoleta) {
                        var request = solicitudNotaCreditoUnica(ctx, guard.boletaReferencia, true);
                        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                            exigir(validation.getValidator().validate(request).isEmpty(), "SOLICITUD_NC_BOLETA_INVALIDA");
                        }
                        tx.executeWithoutResult(status -> guard.validarPreparacionNcBoleta());
                        etapa = "NC_BOLETA_RC_CREACION_NORMAL";
                        var resultado = ctx.getBean(FacturacionServiceImpl.class).procesarNotaCredito("20100066603", NC_BOLETA_RC_KEY, request);
                        guard.id = resultado.comprobanteId();
                        exigir(EstadoComprobante.PENDIENTE_RESUMEN.name().equals(resultado.estado()), "NC_BOLETA_ESTADO_INICIAL_INVALIDO");
                        System.out.println("NC_ID=" + guard.id + "\nESTADO_PREVIO_RESUMEN=" + resultado.estado());
                        ejecutarResumenControlado(ctx, guard);
                        return;
                    } else if (rc) {
                        var request = solicitudFacturaUnica(ctx, empresas, false);
                        request.setTipoComprobante("03"); request.setSerie(guard.serieBoleta);
                        request.setTipoDocCliente("0"); request.setNumDocCliente("-"); request.setRznSocialCliente("CONSUMIDOR FINAL");
                        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                            exigir(validation.getValidator().validate(request).isEmpty(), "SOLICITUD_BOLETA_INVALIDA");
                        }
                        tx.executeWithoutResult(status -> guard.validarPreparacionRc());
                        etapa = "BOLETA_RC_CREACION_NORMAL";
                        var resultado = ctx.getBean(FacturacionServiceImpl.class).procesarComprobante("20100066603", BOLETA_RC_KEY, request);
                        guard.id = resultado.comprobanteId();
                        exigir(EstadoComprobante.PENDIENTE_RESUMEN.name().equals(resultado.estado()), "BOLETA_ESTADO_INICIAL_INVALIDO");
                        System.out.println("BOLETA_ID=" + guard.id + "\nESTADO_PREVIO_RESUMEN=" + resultado.estado());
                        ejecutarResumenControlado(ctx, guard);
                        return;
                    } else if (nc) {
                        var request = solicitudNotaCreditoUnica(ctx, guard.facturaReferencia);
                        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                            exigir(validation.getValidator().validate(request).isEmpty(), "SOLICITUD_NC_INVALIDA");
                        }
                        tx.executeWithoutResult(status -> guard.validarPreparacionNc());
                        etapa = "NC_FACTURA_CREACION_NORMAL";
                        var resultado = ctx.getBean(FacturacionServiceImpl.class).procesarNotaCredito("20100066603", NC_FACTURA_KEY, request);
                        comprobanteId = resultado.comprobanteId();
                        exigir(comprobanteId != null && comprobanteId != 1L && !comprobanteId.equals(guard.facturaReferencia.getId()), "ID_FACTURA_NO_REUTILIZAR");
                        System.out.println("NC_ID=" + comprobanteId + "\nESTADO_PREVIO_ENVIO=" + resultado.estado());
                    } else {
                        var request = solicitudFacturaUnica(ctx, empresas, segunda);
                        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                            exigir(validation.getValidator().validate(request).isEmpty(), "SOLICITUD_INVALIDA");
                        }
                        if (segunda) tx.executeWithoutResult(status -> guard.validarPreparacionSegunda());
                        etapa = segunda ? "SEGUNDA_FACTURA_CREACION_NORMAL" : "PRIMERA_FACTURA_CREACION_NORMAL";
                        var resultado = ctx.getBean(FacturacionServiceImpl.class).procesarComprobante("20100066603",
                                segunda ? SEGUNDA_FACTURA_KEY : "FASE5-PRIMERA-FACTURA-BETA-UNICA", request);
                        comprobanteId = resultado.comprobanteId();
                        if (segunda) exigir(comprobanteId != null && comprobanteId != 1L, "ID_1_PROHIBIDO");
                        System.out.println("FACTURA_ID=" + comprobanteId);
                        System.out.println("ESTADO_PREVIO_ENVIO=" + resultado.estado());
                    }
                    // Un solo worker original. Sin polling/recovery/reintentos. Await excede timeouts normales.
                    etapa = "PRIMERA_FACTURA_ESPERA_RESULTADO";
                    long espera = env.getProperty("sunat.connect-timeout", Long.class, 10000L)
                            + env.getProperty("sunat.read-timeout", Long.class, 20000L) + 120000L;
                    boolean terminado = guard.terminado.await(espera, TimeUnit.MILLISECONDS);
                    exigir(terminado, "WORKER_NO_TERMINO_NO_REENVIAR");
                    etapa = "PRIMERA_FACTURA_RELOAD_RESULTADO";
                    var finalizado = tx.execute(status -> comprobantes.findByIdAndRucEmisor(comprobanteId, "20100066603").orElseThrow());
                    if (segunda) tx.executeWithoutResult(status -> guard.validarPrimeraAceptada());
                    if (nc) tx.executeWithoutResult(status -> { guard.validarPrimeraAceptada(); guard.validarFacturaReferencia(); });
                    if (facturaRa) tx.executeWithoutResult(status -> guard.validarFacturaDedicada(finalizado.getEstadoSfs(), false));
                    guard.informar(finalizado);
                    if (facturaRa) guard.guardarVinculoFacturaDedicada(finalizado);
                    System.out.println(facturaRa ? "DETENIDO_TRAS_FACTURA_DEDICADA=SI" : nc ? "DETENIDO_TRAS_PRIMERA_NC=SI" : segunda ? "DETENIDO_TRAS_SEGUNDA_FACTURA=SI" : "DETENIDO_TRAS_PRIMERA_FACTURA=SI");
                }
            } finally { StandardServiceRegistryBuilder.destroy(registry); }
        }
    }

    private static void ejecutarResumenControlado(AnnotationConfigApplicationContext ctx, ControlFacturaUnica guard) throws Exception {
        var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
        var boleta = tx.execute(status -> guard.validarBoletaRc(EstadoComprobante.PENDIENTE_RESUMEN));
        etapa = "BOLETA_RC_GENERACION_ENVIO_NORMAL";
        try {
            // Sin transaccion exterior ni schedulers: el servicio conserva sus TX cortas y la primera consulta inmediata.
            var servicio = ctx.getBean(ResumenDiarioService.class);
            var rc = servicio.generarYEnviar("20100066603", boleta.getFechaEmision().toLocalDate());
            if (guard.resumenId != null) exigir(guard.resumenId.equals(rc.id()), "RC_ID_NO_COINCIDE");
            else guard.resumenId = rc.id();
            for (int adicional = 0; adicional < 2 && "98".equals(guard.ultimoEstadoTicket)
                    && EstadoResumenDiario.PROCESANDO.name().equals(rc.estado()); adicional++) {
                // Solo una respuesta 98 habilita otra consulta del mismo ticket. Nunca repetir sendSummary.
                TimeUnit.SECONDS.sleep(60);
                etapa = "BOLETA_RC_CONSULTA_TICKET_EXISTENTE";
                rc = servicio.consultarSunat("20100066603", guard.resumenId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); guard.error = "InterruptedException";
        } catch (RuntimeException e) {
            guard.error = e.getClass().getSimpleName();
        }
        etapa = "BOLETA_RC_RELOAD_RESULTADO";
        guard.informarResumen();
        System.out.println((guard.ncBoleta ? "DETENIDO_TRAS_NC_BOLETA_Y_RC=SI" : "DETENIDO_TRAS_BOLETA_Y_RC=SI") + "\nNO_REEJECUTAR=SI");
    }

    private static void ejecutarBajaControlada(AnnotationConfigApplicationContext ctx, ControlFacturaUnica guard) throws Exception {
        var request = new ComunicacionBajaRequest(guard.id, RA_MOTIVO);
        try (var validation = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            exigir(validation.getValidator().validate(request).isEmpty(), "SOLICITUD_RA_INVALIDA");
        }
        var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
        tx.executeWithoutResult(status -> guard.validarPreparacionDedicada());
        etapa = "RA_CREACION_ENVIO_NORMAL";
        try {
            var servicio = ctx.getBean(ComunicacionBajaService.class);
            var ra = servicio.crearYEnviar("20100066603", request);
            exigir(guard.raId == null || guard.raId.equals(ra.id()), "RA_ID_NO_COINCIDE");
            guard.raId = ra.id();
            for (int adicional = 0; adicional < 2 && "98".equals(guard.ultimoEstadoTicket)
                    && EstadoComunicacionBaja.EN_PROCESO.name().equals(ra.estado()); adicional++) {
                TimeUnit.SECONDS.sleep(60);
                etapa = "RA_CONSULTA_TICKET_EXISTENTE";
                ra = servicio.consultarSunat("20100066603", guard.raId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); guard.error = "InterruptedException";
        } catch (RuntimeException e) {
            if ("NINGUNO".equals(guard.error)) guard.error = e.getClass().getSimpleName();
        }
        etapa = "RA_RELOAD_RESULTADO";
        guard.informarBaja();
        System.out.println("DETENIDO_TRAS_RA=SI\nNO_REEJECUTAR=SI");
    }

    private static void validarEvidenciaSinEnvio(Path anterior, Path continuacion) throws IOException {
        rutaExterna(anterior.toString());
        rutaExterna(continuacion.toString());
        exigir(Files.isDirectory(anterior, LinkOption.NOFOLLOW_LINKS), "EVIDENCIA_ORIGINAL_REQUERIDA");
        // Solo admitir la evidencia del fallo local confirmado. Cualquier otro artefacto bloquea.
        try (var archivos = Files.newDirectoryStream(anterior)) {
            for (Path archivo : archivos) {
                if (archivo.equals(continuacion)) {
                    exigir(Files.isDirectory(archivo, LinkOption.NOFOLLOW_LINKS), "CONTINUACION_NO_SEGURA");
                } else {
                    exigir(Set.of("inicio.txt", "resultado.txt").contains(archivo.getFileName().toString())
                            && Files.isRegularFile(archivo, LinkOption.NOFOLLOW_LINKS), "EVIDENCIA_PREVIA_NO_AUTORIZA_CONTINUAR");
                }
            }
        }
        exigir(Files.isRegularFile(anterior.resolve("inicio.txt"), LinkOption.NOFOLLOW_LINKS), "INICIO_ORIGINAL_REQUERIDO");
        Path resultado = anterior.resolve("resultado.txt");
        exigir(Files.isRegularFile(resultado, LinkOption.NOFOLLOW_LINKS) && Files.size(resultado) <= 16384,
                "RESULTADO_ORIGINAL_REQUERIDO");
        var indicadores = new HashMap<String, String>();
        for (String linea : Files.readAllLines(resultado, StandardCharsets.UTF_8)) {
            String[] par = linea.split("=", 2);
            exigir(par.length == 2 && indicadores.putIfAbsent(par[0], par[1]) == null, "RESULTADO_ORIGINAL_AMBIGUO");
        }
        for (String esperado : List.of("SEND_BILL_INVOCACIONES=0", "RETRIES=0", "HTTP=NO_INICIADO", "SOAP=NO_INICIADO",
                "CDR_RECIBIDO=NO", "ESTADO_FINAL=PENDIENTE_ENVIO", "RESPUESTA_SUNAT_RECIBIDA=NO",
                "ERROR_TIPO=TransactionRequiredException", "RESULTADO=BLOQUEADO_ANTES_DE_ENVIAR", "SEGUNDO_ENVIO=NO")) {
            String[] par = esperado.split("=", 2);
            exigir(par[1].equals(indicadores.get(par[0])), "RESULTADO_ORIGINAL_NO_AUTORIZA_CONTINUAR");
        }
        for (String marcador : List.of("intento.txt", "error-tecnico.txt", "cdr-recibido.zip", "cdr-procesado.xml", "resultado.txt")) {
            exigir(!Files.exists(continuacion.resolve(marcador), LinkOption.NOFOLLOW_LINKS), "CONTINUACION_PREVIA_NO_REENVIAR");
        }
    }

    private static <S, R extends Repository<S, Long>> void registrarRepositorioTransaccional(
            AnnotationConfigApplicationContext ctx, EntityManagerFactory factory, Class<R> tipo) {
        // El contexto inicializa el FactoryBean y su interceptor transaccional normal de Spring Data.
        ctx.registerBean(tipo.getName(), JpaRepositoryFactoryBean.class, () -> {
            var repo = new JpaRepositoryFactoryBean<R, S, Long>(tipo);
            repo.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(factory));
            repo.setTransactionManager("transactionManager");
            return repo;
        });
    }

    private static JsonNode datosNotaCreditoConfirmados() throws IOException {
        JsonNode datos;
        try (var in = new ClassPathResource("fase5-beta-datos.json").getInputStream()) { datos = new ObjectMapper().readTree(in); }
        boolean motivoConfirmado = NC_MOTIVO.equals(datos.path("notaCredito").path("motivo").asText());
        if (!motivoConfirmado) System.out.println("BLOQUEADO_POR_MOTIVO_NC_NO_CONFIRMADO");
        exigir(motivoConfirmado, "BLOQUEADO_POR_MOTIVO_NC_NO_CONFIRMADO");
        exigir("20100066603".equals(datos.path("rucEmisor").asText())
                && "F501".equals(datos.path("series").path("factura").asText())
                && "F502".equals(datos.path("series").path("ncFactura").asText()), "ESCENARIO_NC_NO_CONFIRMADO");
        return datos;
    }

    private static NotaCreditoRequest solicitudNotaCreditoUnica(AnnotationConfigApplicationContext ctx, Comprobante origen) throws Exception {
        return solicitudNotaCreditoUnica(ctx, origen, false);
    }

    private static NotaCreditoRequest solicitudNotaCreditoUnica(AnnotationConfigApplicationContext ctx, Comprobante origen, boolean boleta) throws Exception {
        var datos = datosNotaCreditoConfirmados();
        var r = new NotaCreditoRequest();
        r.setTiendaId(origen.getTiendaId()); r.setSerie(datos.get("series").get(boleta ? "ncBoleta" : "ncFactura").asText());
        r.setTipoComprobanteAfectado(boleta ? "03" : "01"); r.setNumComprobanteAfectado(boleta ? "B501-1" : "F501-2");
        r.setCodMotivoSunat(NC_MOTIVO); r.setDescripcionMotivo(NC_DESCRIPCION);
        r.setTipoDocCliente(origen.getTipoDocCliente()); r.setNumDocCliente(origen.getNumDocCliente());
        r.setRznSocialCliente(origen.getRznSocialCliente());
        var i = new NotaCreditoRequest.DetalleItemRequest();
        i.setCantidad(new BigDecimal(datos.get("cantidad").asText()));
        i.setUnidadMedida(datos.get("unidadMedida").asText()); i.setDescripcion(datos.get("descripcion").asText());
        i.setTipoAfectacionIgv(datos.get("afectacion").asText());
        i.setValorUnitario(new BigDecimal(datos.get("notaCredito").get("base").asText()));
        r.setItems(List.of(i));
        // Tasa historica del original. Los totales del DTO salen del calculo productivo, no del JSON.
        var calculado = ctx.getBean(CalculoTributarioService.class).calcular(r, origen.getIgvPorcentaje());
        r.setOpGravada(calculado.opGravada()); r.setOpExonerada(calculado.opExonerada()); r.setOpInafecta(calculado.opInafecta());
        r.setIgv(calculado.igv()); r.setTotal(calculado.total());
        ctx.getBean(ValidacionTributariaService.class).validar(r);
        ctx.getBean(ValidacionTributariaService.class).validarContraComprobanteOriginal(r, origen, calculado);
        exigir(calculado.total().compareTo(new BigDecimal(datos.get("notaCredito").get("total").asText())) == 0
                && calculado.total().compareTo(origen.getMontoTotal()) < 0, "IMPORTES_NC_ESCENARIO_NO_COINCIDEN");
        System.out.println("MOTIVO_NC=" + NC_MOTIVO + "\nDESCRIPCION_MOTIVO=" + NC_DESCRIPCION + "\nSERIE_NC=" + r.getSerie());
        return r;
    }

    private static ComprobanteRequest solicitudFacturaUnica(AnnotationConfigApplicationContext ctx, EmpresaRepository empresas, boolean segunda) throws Exception {
        JsonNode datos;
        try (var in = new ClassPathResource("fase5-beta-datos.json").getInputStream()) { datos = new ObjectMapper().readTree(in); }
        exigir("20100066603".equals(datos.get("rucEmisor").asText()) && "F501".equals(datos.get("series").get("factura").asText()), "DATOS_DOCUMENTALES_DISTINTOS");
        var r = new ComprobanteRequest();
        r.setTiendaId(1L); // Identificador logico del escenario documentado; no existe entidad Tienda.
        r.setTipoComprobante("01"); r.setSerie(datos.get("series").get("factura").asText());
        r.setTipoOperacion(datos.get("operacion").asText()); r.setFormaPago(datos.get("formaPago").asText());
        r.setTipoDocCliente("6"); r.setNumDocCliente(datos.get("rucEmisor").asText());
        r.setRznSocialCliente("RECEPTOR FICTICIO DE ENSAYO BETA"); // Mismo receptor documental del escenario local, no cliente real.
        var i = new ComprobanteRequest.DetalleItemRequest();
        i.setCantidad(new BigDecimal(datos.get("cantidad").asText()));
        i.setUnidadMedida(datos.get("unidadMedida").asText());
        i.setDescripcion(segunda ? "ITEM FICTICIO SEGUNDA FACTURA CONTROLADA BETA" : datos.get("descripcion").asText());
        i.setTipoAfectacionIgv(datos.get("afectacion").asText()); i.setValorUnitario(new BigDecimal(datos.get("original").get("base").asText()));
        r.setItems(List.of(i));
        var calculado = ctx.getBean(CalculoTributarioService.class).calcular(r, empresas.findByRuc("20100066603").orElseThrow().igvPorcentajeEfectivo());
        r.setOpGravada(calculado.opGravada()); r.setOpExonerada(calculado.opExonerada()); r.setOpInafecta(calculado.opInafecta());
        r.setIgv(calculado.igv()); r.setTotal(calculado.total());
        exigir(r.getTotal().compareTo(new BigDecimal("11.80")) == 0, "IMPORTE_DOCUMENTAL_DISTINTO");
        return r;
    }

    /** Advice de control/evidencia, nunca reemplaza calculo, firma, ZIP, SOAP, parser ni estados. */
    private static final class ControlFacturaUnica implements BeanPostProcessor {
        private final ConfigurableEnvironment env;
        private final Path evidencia;
        private final AnnotationConfigApplicationContext ctx;
        private final DriverManagerDataSource ds;
        private final AtomicInteger invocaciones = new AtomicInteger();
        private final AtomicInteger workers = new AtomicInteger();
        private final CountDownLatch terminado = new CountDownLatch(1);
        private volatile Long id;
        private volatile boolean precheck;
        private volatile String http = "NO_INICIADO", soap = "NO_INICIADO", error = "NINGUNO";
        private volatile byte[] respuesta;
        private volatile long auditoriasAntes;
        private volatile boolean evidenciaTecnicaCompleta = true;
        private Path evidenciaAnterior;
        private Comprobante facturaExistente;
        private boolean segunda;
        private Comprobante primeraAceptada;
        private RespuestaSunatRecibida respuestaPrimera;
        private Comprobante nuevaFactura;
        private Integer ultimoNumeroAntes;
        private boolean notaCredito;
        private String serieNc;
        private Comprobante facturaReferencia;
        private RespuestaSunatRecibida respuestaReferencia;
        private Comprobante nuevaNc;
        private Integer ultimoNcAntes;
        private boolean boletaRc;
        private String serieBoleta;
        private Comprobante boletaNueva;
        private Comprobante ncAnterior;
        private Integer ultimoBoletaAntes;
        private Long resumenId;
        private String ticketRecibido;
        private String ultimoEstadoTicket;
        private byte[] ultimoZipRc;
        private byte[] xmlRcInicial;
        private ResumenDiario resumenInicial;
        private JsonNode detalleRcInicial;
        private final Map<String, JsonNode> anterioresRc = new HashMap<>();
        private long ultimaConsultaTicketNanos;
        private final AtomicInteger resumenesEnviados = new AtomicInteger();
        private final AtomicInteger consultasTicket = new AtomicInteger();
        private boolean ncBoleta;
        private Comprobante boletaReferencia;
        private Long resumenAnteriorId;
        private Integer ultimoNcBoletaAntes;
        private final Map<Long, JsonNode> auditoriasAnteriores = new HashMap<>();
        private boolean auditoriasCapturadas;
        private boolean facturaRa, bajaFactura;
        private final Map<String, JsonNode> historicoDedicada = new HashMap<>();
        private Set<Long> idsHistoricos;
        private Comprobante dedicadaInicial;
        private JsonNode fotoDedicada;
        private JsonNode detallesDedicada;
        private JsonNode identidadDedicada;
        private Long raId;
        private ComunicacionBaja raInicial;
        private JsonNode detalleRaInicial;
        private java.time.LocalDate fechaRa;
        private Integer secuenciaRaAntes;

        ControlFacturaUnica(ConfigurableEnvironment env, Path evidencia, AnnotationConfigApplicationContext ctx, DriverManagerDataSource ds) {
            this.env = env; this.evidencia = evidencia; this.ctx = ctx; this.ds = ds;
        }
        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (!(bean instanceof SunatDirectSoapClient) && !(bean instanceof EnvioSunatService)) return bean;
            var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true);
            proxy.addAdvice((MethodInterceptor) call -> {
                if (bean instanceof EnvioSunatService && "enviar".equals(call.getMethod().getName())) {
                    exigir(!boletaRc && !bajaFactura, "SENDBILL_PROHIBIDO_EN_RESUMEN_O_RA");
                    exigir(workers.incrementAndGet() == 1, "SEGUNDO_WORKER_PROHIBIDO");
                    if (evidenciaAnterior != null) exigir(Long.valueOf(1).equals(call.getArguments()[0]), "SOLO_FACTURA_ID_1");
                    id = (Long) call.getArguments()[0];
                    try {
                        if (evidenciaAnterior != null) validarContinuacionEnTransaccion(EstadoComprobante.PENDIENTE_ENVIO);
                        if (segunda) validarSegundaEnTransaccion(EstadoComprobante.PENDIENTE_ENVIO);
                        if (notaCredito) validarNcEnTransaccion(EstadoComprobante.PENDIENTE_ENVIO);
                        if (facturaRa) {
                            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
                            tx.executeWithoutResult(status -> validarFacturaDedicada(EstadoComprobante.PENDIENTE_ENVIO, false));
                        }
                        return call.proceed();
                    }
                    catch (Throwable e) { error = e.getClass().getSimpleName(); throw e; }
                    finally { terminado.countDown(); }
                }
                if (bean instanceof SunatDirectSoapClient) {
                    if (bajaFactura) return controlarClienteBaja(call);
                    if (boletaRc) return controlarClienteResumen(call);
                    exigir(!Set.of("enviarResumenDirecto", "consultarTicket").contains(call.getMethod().getName()), "OPERACION_NO_AUTORIZADA");
                    if ("enviarZipDirecto".equals(call.getMethod().getName())) {
                        // Este advice corre sobre el cliente ya inicializado, ANTES de generar/enviar SOAP.
                        precheckInmediato(call.getArguments());
                        if (evidenciaAnterior != null) validarContinuacionEnTransaccion(EstadoComprobante.ENVIANDO);
                        if (segunda) validarSegundaEnTransaccion(EstadoComprobante.ENVIANDO);
                        if (notaCredito) validarNcEnTransaccion(EstadoComprobante.ENVIANDO);
                        Files.writeString(evidencia.resolve("intento.txt"), "COMPROBANTE_ID=" + id + "\nINICIO_INTENTO=" + Instant.now()
                                + "\nSEND_BILL_MAX=1\n", StandardOpenOption.CREATE_NEW);
                        exigir(invocaciones.compareAndSet(0, 1), "SEGUNDO_SEND_BILL_PROHIBIDO");
                        http = "NO_DETERMINABLE"; soap = "INTENTO_INICIADO";
                        try {
                            respuesta = (byte[]) call.proceed();
                            http = "2XX_EXACTO_NO_EXPUESTO_POR_CLIENTE"; soap = "RESPUESTA_NORMAL";
                            return respuesta;
                        } catch (Throwable e) {
                            error = e.getClass().getSimpleName();
                            if (e instanceof SunatDirectSoapClient.SunatSoapFaultException fault) {
                                http = Integer.toString(fault.getHttpStatus()); soap = "FAULT_TECNICO_NO_RECHAZO_FISCAL";
                            } else soap = "FALLO_TECNICO_RESULTADO_NO_CONFIRMADO";
                            // Solo metadatos; no mensajes/cause/stack ni cuerpo WS-Security.
                            try {
                                Files.writeString(evidencia.resolve("error-tecnico.txt"), "TIPO=" + error + "\nHTTP=" + http + "\nSOAP=" + soap + "\n",
                                        StandardOpenOption.CREATE_NEW);
                            } catch (IOException falloEvidencia) { evidenciaTecnicaCompleta = false; }
                            throw e;
                        }
                    }
                }
                return call.proceed();
            });
            return proxy.getProxy();
        }

        // Guardas/observacion de TEST. Ninguna escritura fiscal: toda mutacion queda en los services originales.
        private JsonNode fotoColumnas(Object entidad) {
            var foto = ctx.getBean(ObjectMapper.class).createObjectNode();
            try {
                for (var campo : entidad.getClass().getDeclaredFields()) {
                    if (!campo.isAnnotationPresent(jakarta.persistence.Column.class)
                            && !campo.isAnnotationPresent(jakarta.persistence.Id.class)) continue;
                    campo.setAccessible(true);
                    foto.set(campo.getName(), ctx.getBean(ObjectMapper.class).valueToTree(campo.get(entidad)));
                }
            } catch (IllegalAccessException e) { throw new IllegalStateException("CONSERVACION_NO_VERIFICABLE"); }
            return foto;
        }

        private void conservarFoto(String clave, JsonNode foto) {
            var anterior = anterioresRc.putIfAbsent(clave, foto);
            exigir(anterior == null || anterior.equals(foto), "DOCUMENTO_O_EVIDENCIA_ANTERIOR_ALTERADO");
        }

        private void validarHistoricoDedicada() {
            var fotos = new HashMap<String, JsonNode>();
            var historicos = ctx.getBean(ComprobanteRepository.class).findAll().stream()
                    .filter(c -> !Objects.equals(id, c.getId())).toList();
            var nombres = new HashSet<String>();
            for (var c : historicos) {
                exigir("20100066603".equals(c.getRucEmisor()), "DOCUMENTO_AJENO_AL_ENSAYO");
                nombres.add(c.getTipoComprobante() + "/" + c.getSerie() + "-" + c.getNumero());
                fotos.put("CPE/" + c.getId(), fotoColumnas(c));
                c.getDetalles().forEach(d -> fotos.put("DETALLE/" + d.getId(), fotoColumnas(d)));
            }
            var obligatorios = Set.of("01/F501-1", "01/F501-2", "07/F502-1", "03/B501-1");
            if (bajaFactura) {
                // Identidades concretas, no un total global heredado; el resto se congela tambien.
                for (String nombre : Set.of("01/F501-1", "01/F501-2", "07/F502-1", "03/B501-1", "07/B502-1")) {
                    exigir(historicos.stream().filter(c -> nombre.equals(c.getTipoComprobante() + "/" + c.getSerie() + "-" + c.getNumero())).count() == 1,
                            "DOCUMENTO_HISTORICO_RA_NO_ACREDITADO");
                }
            } else exigir(nombres.containsAll(obligatorios) && nombres.stream().allMatch(n -> obligatorios.contains(n) || "07/B502-1".equals(n))
                    && nombres.size() == historicos.size(), "HISTORICO_INESPERADO_NO_EMITIR");
            var ids = new HashSet<Long>(); historicos.forEach(c -> ids.add(c.getId()));
            exigir(idsHistoricos == null || idsHistoricos.equals(ids), "IDENTIDADES_HISTORICAS_ALTERADAS");
            if (idsHistoricos == null) idsHistoricos = Set.copyOf(ids);
            for (var s : ctx.getBean(SolicitudEmisionRepository.class).findAll()) {
                if (FACTURA_RA_KEY.equals(s.getIdempotencyKey())) {
                    exigir(id != null && id.equals(s.getComprobanteId()) && "20100066603".equals(s.getRucEmisor()), "IDEMPOTENCIA_DEDICADA_AJENA");
                    if (bajaFactura) fotos.put("SOLICITUD/" + s.getId(), fotoColumnas(s));
                } else {
                    exigir(idsHistoricos.contains(s.getComprobanteId()), "SOLICITUD_AJENA_AL_HISTORICO");
                    fotos.put("SOLICITUD/" + s.getId(), fotoColumnas(s));
                }
            }
            for (var recibida : ctx.getBean(RespuestaSunatRecibidaRepository.class).findAll()) {
                if (facturaRa && id != null && id.equals(recibida.getDocumentoId()) && recibida.getFlujo() == RespuestaSunatRecibida.Flujo.CPE) continue;
                if (raId != null && raId.equals(recibida.getDocumentoId()) && recibida.getFlujo() == RespuestaSunatRecibida.Flujo.RA) continue;
                fotos.put("RECIBIDA/" + recibida.getId(), fotoColumnas(recibida));
            }
            for (var r : ctx.getBean(ResumenDiarioRepository.class).findAll()) {
                var completo = ctx.getBean(ResumenDiarioRepository.class).buscarCompleto(r.getId(), r.getRucEmisor()).orElseThrow();
                fotos.put("RC/" + r.getId(), fotoColumnas(completo));
                completo.getDetalles().forEach(d -> fotos.put("RC_DETALLE/" + d.getId(), fotoColumnas(d)));
            }
            ctx.getBean(SecuenciaResumenDiarioRepository.class).findAll().forEach(s -> fotos.put("RC_SECUENCIA/" + s.getId(), fotoColumnas(s)));
            for (var r : ctx.getBean(ComunicacionBajaRepository.class).findAll()) {
                if (Objects.equals(raId, r.getId())) continue;
                var completo = ctx.getBean(ComunicacionBajaRepository.class).buscarCompleto(r.getId(), r.getRucEmisor()).orElseThrow();
                fotos.put("RA/" + r.getId(), fotoColumnas(completo));
                completo.getDetalles().forEach(d -> fotos.put("RA_DETALLE/" + d.getId(), fotoColumnas(d)));
            }
            for (var s : ctx.getBean(SecuenciaCorrelativoRepository.class).findAll()) {
                var foto = fotoColumnas(s);
                if (facturaRa && "20100066603".equals(s.getRucEmisor()) && "01".equals(s.getTipoComprobante()) && "F501".equals(s.getSerie())) {
                    if (ultimoNumeroAntes == null) ultimoNumeroAntes = s.getUltimoNumero();
                    exigir(ultimoNumeroAntes != null && s.getUltimoNumero() == ultimoNumeroAntes + (id == null ? 0 : 1), "SECUENCIA_FACTURA_DEDICADA_INESPERADA");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) foto).remove(List.of("ultimoNumero", "updatedAt"));
                }
                fotos.put("CPE_SECUENCIA/" + s.getId(), foto);
            }
            if (bajaFactura && fechaRa == null) {
                fechaRa = java.time.LocalDate.now(ctx.getBean(java.time.Clock.class));
                secuenciaRaAntes = ctx.getBean(SecuenciaComunicacionBajaRepository.class)
                        .findByRucEmisorAndFechaReferencia("20100066603", fechaRa).map(org.example.model.SecuenciaComunicacionBaja::getUltimoCorrelativo).orElse(0);
            }
            for (var s : ctx.getBean(SecuenciaComunicacionBajaRepository.class).findAll()) {
                if (bajaFactura && "20100066603".equals(s.getRucEmisor()) && fechaRa.equals(s.getFechaReferencia())) {
                    exigir(s.getUltimoCorrelativo() == secuenciaRaAntes + (raId == null ? 0 : 1), "SECUENCIA_RA_INESPERADA");
                    continue; // Solo esta secuencia puede avanzar una vez por el servicio productivo.
                }
                fotos.put("RA_SECUENCIA/" + s.getId(), fotoColumnas(s));
            }
            if (bajaFactura && raId != null) exigir(ctx.getBean(SecuenciaComunicacionBajaRepository.class)
                    .findByRucEmisorAndFechaReferencia("20100066603", fechaRa).orElseThrow().getUltimoCorrelativo() == secuenciaRaAntes + 1, "SECUENCIA_RA_NO_CONSERVADA");
            if (historicoDedicada.isEmpty()) historicoDedicada.putAll(fotos);
            else exigir(historicoDedicada.equals(fotos), "HISTORICO_ALTERADO_DETENER");
            var auditorias = ctx.getBean(EventoAuditoriaRepository.class);
            if (!auditoriasCapturadas) {
                auditorias.findAll().forEach(a -> auditoriasAnteriores.put(a.getId(), fotoColumnas(a))); auditoriasCapturadas = true;
            } else auditoriasAnteriores.forEach((aid, foto) -> exigir(foto.equals(fotoColumnas(auditorias.findById(aid).orElseThrow())), "AUDITORIA_HISTORICA_ALTERADA"));
        }

        private void validarPreparacionDedicada() {
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll().stream()
                    .filter(s -> FACTURA_RA_KEY.equals(s.getIdempotencyKey())).toList();
            if (facturaRa) exigir(solicitudes.isEmpty() && id == null, "FACTURA_DEDICADA_PREVIA_NO_REEJECUTAR");
            else {
                etapa = "RA_PRECHECK_IDEMPOTENCIA_DEDICADA";
                exigir(solicitudes.size() == 1 && "20100066603".equals(solicitudes.getFirst().getRucEmisor())
                        && "CREADO".equals(solicitudes.getFirst().getEstado()), "FACTURA_DEDICADA_NO_ACREDITADA");
                id = solicitudes.getFirst().getComprobanteId();
                exigir(id != null, "FACTURA_DEDICADA_SIN_ID");
                var exacta = ctx.getBean(ComprobanteRepository.class)
                        .findByRucEmisorAndTipoComprobanteAndSerieAndNumero("20100066603", "01", "F501", 3).orElseThrow();
                exigir(id.equals(exacta.getId()), "FACTURA_RA_DEBE_SER_F501_3_DEDICADA");
            }
            if (bajaFactura) etapa = "RA_PRECHECK_HISTORICO";
            validarHistoricoDedicada();
            if (bajaFactura) etapa = "RA_PRECHECK_AUSENCIA_RA_PREVIO";
            exigir(ctx.getBean(ComunicacionBajaRepository.class).findAll().stream()
                    .noneMatch(r -> ctx.getBean(ComunicacionBajaRepository.class).buscarCompleto(r.getId(), r.getRucEmisor()).orElseThrow()
                            .getDetalles().stream().anyMatch(d -> Objects.equals(id, d.getComprobanteId()))), "RA_PREVIO_NO_REEJECUTAR");
            exigir(invocaciones.get() == 0 && workers.get() == 0 && resumenesEnviados.get() == 0 && consultasTicket.get() == 0, "ESCENARIO_DEDICADO_NO_REPETIR");
            if (facturaRa) exigir(ultimoNumeroAntes != null && ultimoNumeroAntes >= 2, "SECUENCIA_F501_NO_ACREDITADA");
            else {
                etapa = "RA_PRECHECK_ORIGINAL_ACEPTADO";
                var c = ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(id, "20100066603").orElseThrow();
                validarFacturaDedicada(c.getEstadoSfs(), true);
                etapa = "RA_PRECHECK_AUDITORIA_ORIGINAL";
                validarAuditoriaFacturaDedicada(c);
                etapa = "RA_PRECHECK_PLAZO";
                ctx.getBean(SunatPlazoService.class).validarBaja(c.getFechaEmision().toLocalDate());
                try {
                    etapa = "RA_PRECHECK_EVIDENCIA_FACTURA_DEDICADA";
                    Path origen = rutaExterna(evidencia.getParent().resolve("factura-para-baja-beta").toString());
                    for (String archivo : List.of("factura-dedicada.json", c.getNombreArchivoXml(), "cdr-recibido.zip", "cdr-procesado.xml", "resultado.txt"))
                        exigir(Files.isRegularFile(origen.resolve(archivo), LinkOption.NOFOLLOW_LINKS), "EVIDENCIA_FACTURA_DEDICADA_REQUERIDA");
                    var mapper = ctx.getBean(ObjectMapper.class);
                    var reader = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
                    var vinculo = reader.readTree(Files.readAllBytes(origen.resolve("factura-dedicada.json")));
                    // valueToTree contiene BinaryNode/LongNode/DecimalNode; el JSON leido no.
                    // Comparar el contrato serializado, con decimales exactos y sin tolerancias.
                    var comprobanteJson = reader.readTree(mapper.writeValueAsBytes(fotoColumnas(c)));
                    var detallesJson = reader.readTree(mapper.writeValueAsBytes(detallesDedicada));
                    exigir(FACTURA_RA_KEY.equals(vinculo.path("key").asText()), "KEY_EVIDENCIA_DEDICADA_NO_COINCIDE");
                    exigir(mismaEvidenciaJson(comprobanteJson, vinculo.get("comprobante")), "COMPROBANTE_EVIDENCIA_DEDICADA_NO_COINCIDE");
                    exigir(mismaEvidenciaJson(detallesJson, vinculo.get("detalles")), "DETALLES_EVIDENCIA_DEDICADA_NO_COINCIDEN");
                    exigir(Arrays.equals(c.getXmlFirmadoBytes(), Files.readAllBytes(origen.resolve(c.getNombreArchivoXml())))
                            && Arrays.equals(c.getCdrZipBytes(), Files.readAllBytes(origen.resolve("cdr-recibido.zip")))
                            && Arrays.equals(c.getCdrXmlBytes(), Files.readAllBytes(origen.resolve("cdr-procesado.xml"))), "VINCULO_FACTURA_DEDICADA_NO_COINCIDE");
                    var resultado = Files.readAllLines(origen.resolve("resultado.txt"), StandardCharsets.UTF_8);
                    for (String indicador : List.of("MODO=FACTURA_PARA_BAJA", "FACTURA_ID=" + id, "SEND_BILL_INVOCACIONES=1", "RETRIES=0",
                            "RESPONSE_CODE=0", "ESTADO_FINAL=" + c.getEstadoSfs(), "RESULTADO=ACEPTADO_BETA", "ERROR_TIPO=NINGUNO", "SEGUNDO_ENVIO=NO"))
                        exigir(resultado.stream().filter(indicador::equals).count() == 1, "RESULTADO_FACTURA_DEDICADA_NO_ACREDITADO");
                } catch (IOException e) { throw new IllegalStateException("EVIDENCIA_FACTURA_DEDICADA_NO_LEGIBLE"); }
            }
        }

        private Comprobante validarFacturaDedicada(EstadoComprobante estado, boolean paraBaja) {
            validarHistoricoDedicada();
            var c = ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(id, "20100066603").orElseThrow();
            if (!bajaFactura) exigir(ctx.getBean(ComprobanteRepository.class).count() == idsHistoricos.size() + 1, "CANTIDAD_FACTURA_DEDICADA_INESPERADA");
            exigir(!idsHistoricos.contains(id)
                    && "01".equals(c.getTipoComprobante()) && "F501".equals(c.getSerie()) && c.getNumero() > 2
                    && c.getEstadoSfs() == estado && "PEN".equals(c.getMoneda()) && "0101".equals(c.getTipoOperacion())
                    && "Contado".equals(c.getFormaPago()) && "6".equals(c.getTipoDocCliente()) && "20100066603".equals(c.getNumDocCliente())
                    && "RECEPTOR FICTICIO DE ENSAYO BETA".equals(c.getRznSocialCliente())
                    && c.getOpGravada().compareTo(new BigDecimal("10.00")) == 0 && c.getIgv().compareTo(new BigDecimal("1.80")) == 0
                    && c.getIgvPorcentaje() != null && c.getIgvPorcentaje().compareTo(new BigDecimal("18.00")) == 0
                    && c.getMontoTotal().compareTo(new BigDecimal("11.80")) == 0, "FACTURA_DEDICADA_NO_COINCIDE");
            if (facturaRa) exigir(c.getNumero() == ultimoNumeroAntes + 1, "NUMERO_FACTURA_DEDICADA_INESPERADO");
            if (bajaFactura) exigir(Integer.valueOf(3).equals(c.getNumero()), "FACTURA_RA_EXACTA_F501_3_REQUERIDA");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll().stream().filter(s -> FACTURA_RA_KEY.equals(s.getIdempotencyKey())).toList();
            exigir(solicitudes.size() == 1 && id.equals(solicitudes.getFirst().getComprobanteId())
                    && "20100066603".equals(solicitudes.getFirst().getRucEmisor()) && "CREADO".equals(solicitudes.getFirst().getEstado()), "IDEMPOTENCIA_FACTURA_DEDICADA_INVALIDA");
            if (paraBaja) exigir((estado == EstadoComprobante.ACEPTADO || estado == EstadoComprobante.ACEPTADO_CON_OBSERVACION)
                    && "0".equals(c.getCodigoRespuestaSunat()), "FACTURA_DEDICADA_NO_ACEPTADA");
            if (estado == EstadoComprobante.PENDIENTE_ENVIO || estado == EstadoComprobante.ENVIANDO)
                exigir(c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && c.getCodigoRespuestaSunat() == null
                        && ctx.getBean(RespuestaSunatRecibidaRepository.class).findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).isEmpty(), "FACTURA_DEDICADA_ENVIO_PREVIO_PROHIBIDO");
            if (paraBaja || estado == EstadoComprobante.ACEPTADO || estado == EstadoComprobante.ACEPTADO_CON_OBSERVACION) {
                var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class)
                        .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id);
                exigir(recibidas.size() == 1 && Arrays.equals(recibidas.getFirst().getRespuestaZipBytes(), c.getCdrZipBytes())
                        && c.getCdrXmlBytes() != null, "CDR_ORIGINAL_DEDICADO_NO_CONSERVADO");
                var cdr = ctx.getBean(CdrParserService.class).procesar(c.getCdrZipBytes());
                exigir(cdr.aceptado() && "0".equals(cdr.responseCode()) && Arrays.equals(cdr.xml(), c.getCdrXmlBytes())
                        && Set.of("F501-" + c.getNumero(), String.format("F501-%08d", c.getNumero())).contains(cdr.referenceId()), "CDR_ORIGINAL_DEDICADO_INVALIDO");
            }
            try { validarXmlPersistido(c, ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow()); }
            catch (Exception e) { throw new IllegalStateException("XML_FACTURA_DEDICADA_INVALIDO"); }
            var identidad = (com.fasterxml.jackson.databind.node.ObjectNode) fotoColumnas(c);
            identidad.remove(List.of("estadoSfs", "codigoRespuestaSunat", "mensajeSunat", "updatedAt", "cdrZipBytes", "cdrXmlBytes"));
            exigir(identidadDedicada == null || identidadDedicada.equals(identidad), "IDENTIDAD_XML_FACTURA_DEDICADA_ALTERADA");
            if (identidadDedicada == null) identidadDedicada = identidad;
            validarDetallesDedicada(c);
            if (dedicadaInicial == null) dedicadaInicial = c;
            if (paraBaja && fotoDedicada == null) fotoDedicada = fotoColumnas(c);
            if (fotoDedicada != null) exigir(fotoDedicada.equals(fotoColumnas(c)), "FACTURA_DEDICADA_ALTERADA_ANTES_DE_RA");
            return c;
        }

        private void validarDetallesDedicada(Comprobante c) {
            var detalles = ctx.getBean(ObjectMapper.class).createArrayNode();
            c.getDetalles().stream().sorted(Comparator.comparing(org.example.model.DetalleComprobante::getId))
                    .forEach(d -> detalles.add(fotoColumnas(d)));
            exigir(!detalles.isEmpty() && (detallesDedicada == null || detallesDedicada.equals(detalles)), "DETALLES_FACTURA_DEDICADA_ALTERADOS");
            if (detallesDedicada == null) detallesDedicada = detalles;
        }

        private static boolean mismaEvidenciaJson(JsonNode actual, JsonNode evidencia) {
            if (actual == null || evidencia == null) return actual == evidencia;
            if (actual.isNumber() && evidencia.isNumber())
                return actual.decimalValue().compareTo(evidencia.decimalValue()) == 0;
            if (actual.isObject() && evidencia.isObject()) {
                if (actual.size() != evidencia.size()) return false;
                var campos = actual.fields();
                while (campos.hasNext()) {
                    var campo = campos.next();
                    if (!evidencia.has(campo.getKey()) || !mismaEvidenciaJson(campo.getValue(), evidencia.get(campo.getKey()))) return false;
                }
                return true;
            }
            if (actual.isArray() && evidencia.isArray()) {
                if (actual.size() != evidencia.size()) return false;
                for (int i = 0; i < actual.size(); i++)
                    if (!mismaEvidenciaJson(actual.get(i), evidencia.get(i))) return false;
                return true;
            }
            return actual.equals(evidencia);
        }

        private void guardarVinculoFacturaDedicada(Comprobante c) throws IOException {
            if (!"NINGUNO".equals(error) || !precheck || invocaciones.get() != 1
                    || !(c.getEstadoSfs() == EstadoComprobante.ACEPTADO || c.getEstadoSfs() == EstadoComprobante.ACEPTADO_CON_OBSERVACION)) return;
            validarAuditoriaFacturaDedicada(c);
            var vinculo = ctx.getBean(ObjectMapper.class).createObjectNode();
            vinculo.put("key", FACTURA_RA_KEY); vinculo.set("comprobante", fotoColumnas(c));
            vinculo.set("detalles", detallesDedicada);
            Files.writeString(evidencia.resolve("factura-dedicada.json"), vinculo.toString(), StandardOpenOption.CREATE_NEW);
        }

        private void validarAuditoriaFacturaDedicada(Comprobante c) {
            exigir(ctx.getBean(EventoAuditoriaRepository.class).findAll().stream().anyMatch(a -> c.getRucEmisor().equals(a.getRucEmpresa())
                    && "CDR_COMPROBANTE_RECIBIDA".equals(a.getAccion()) && ("01 F501-" + c.getNumero()).equals(a.getDetalle())), "AUDITORIA_FACTURA_DEDICADA_NO_ACREDITADA");
        }

        private ComunicacionBaja cargarBaja() {
            var repo = ctx.getBean(ComunicacionBajaRepository.class);
            var candidatos = repo.findAll().stream().map(r -> repo.buscarCompleto(r.getId(), r.getRucEmisor()).orElseThrow())
                    .filter(r -> r.getDetalles().stream().anyMatch(d -> id.equals(d.getComprobanteId()))).toList();
            exigir(candidatos.size() == 1, "SOLO_UN_RA_DEDICADO");
            var r = candidatos.getFirst();
            exigir(raId == null || raId.equals(r.getId()), "RA_IDENTIDAD_ALTERADA");
            if (raId == null) raId = r.getId();
            exigir("20100066603".equals(r.getRucEmisor()) && fechaRa.equals(r.getFechaGeneracion())
                    && r.getFechaDocumentos().equals(dedicadaInicial.getFechaEmision().toLocalDate())
                    && r.getCorrelativo() == secuenciaRaAntes + 1 && r.getCorrelativo() > 0 && r.getCorrelativo() <= 99999
                    && ("RA-" + fechaRa.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + r.getCorrelativo()).equals(r.getIdentificador())
                    && (r.getRucEmisor() + "-" + r.getIdentificador() + ".xml").equals(r.getNombreArchivoXml()) && r.getDetalles().size() == 1, "RA_IDENTIDAD_INVALIDA");
            var d = r.getDetalles().getFirst();
            exigir(id.equals(d.getComprobanteId()) && Integer.valueOf(1).equals(d.getNumeroLinea())
                    && "01".equals(d.getTipoComprobante()) && "F501".equals(d.getSerie()) && dedicadaInicial.getNumero().equals(d.getNumero())
                    && RA_MOTIVO.equals(d.getMotivo()), "RA_DETALLE_NO_EXCLUSIVO");
            if (raInicial != null) exigir(Arrays.equals(raInicial.getXmlFirmadoBytes(), r.getXmlFirmadoBytes())
                    && Objects.equals(raInicial.getHashCpe(), r.getHashCpe()) && Objects.equals(raInicial.getIdentificador(), r.getIdentificador())
                    && detalleRaInicial.equals(fotoColumnas(d)), "RA_XML_O_DETALLE_ALTERADO");
            exigir(repo.findAll().stream().filter(otra -> r.getRucEmisor().equals(otra.getRucEmisor())
                    && r.getIdentificador().equals(otra.getIdentificador())).count() == 1, "IDENTIFICADOR_RA_DUPLICADO");
            return r;
        }

        private void validarXmlBaja(ComunicacionBaja r) throws Exception {
            ctx.getBean(UblValidatorService.class).validarComunicacionBaja(r.getXmlFirmadoBytes());
            var doc = Fase5BetaPreparacionTest.parse(r.getXmlFirmadoBytes()); var raiz = doc.getDocumentElement();
            String cbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
            String cac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
            String sac = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
            exigir("VoidedDocuments".equals(raiz.getLocalName()) && "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1".equals(raiz.getNamespaceURI())
                    && "2.0".equals(hijoUnico(raiz, cbc, "UBLVersionID").getTextContent().trim())
                    && "1.0".equals(hijoUnico(raiz, cbc, "CustomizationID").getTextContent().trim())
                    && r.getIdentificador().equals(hijoUnico(raiz, cbc, "ID").getTextContent().trim())
                    && r.getFechaDocumentos().toString().equals(hijoUnico(raiz, cbc, "ReferenceDate").getTextContent().trim())
                    && r.getFechaGeneracion().toString().equals(hijoUnico(raiz, cbc, "IssueDate").getTextContent().trim())
                    && r.getRucEmisor().equals(hijoUnico(hijoUnico(raiz, cac, "AccountingSupplierParty"), cbc, "CustomerAssignedAccountID").getTextContent().trim())
                    && doc.getElementsByTagNameNS(sac, "VoidedDocumentsLine").getLength() == 1, "XML_RA_CABECERA_INVALIDA");
            var linea = hijoUnico(raiz, sac, "VoidedDocumentsLine");
            exigir("1".equals(hijoUnico(linea, cbc, "LineID").getTextContent().trim()) && "01".equals(hijoUnico(linea, cbc, "DocumentTypeCode").getTextContent().trim())
                    && "F501".equals(hijoUnico(linea, sac, "DocumentSerialID").getTextContent().trim())
                    && dedicadaInicial.getNumero().toString().equals(hijoUnico(linea, sac, "DocumentNumberID").getTextContent().trim())
                    && RA_MOTIVO.equals(hijoUnico(linea, sac, "VoidReasonDescription").getTextContent().trim()), "XML_RA_LINEA_INVALIDA");
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(r.getRucEmisor()).orElseThrow(); validarEmpresa(empresa);
            validarFirmaDigest(doc, r.getHashCpe(), empresa);
        }

        private Object controlarClienteBaja(org.aopalliance.intercept.MethodInvocation call) throws Throwable {
            String metodo = call.getMethod().getName();
            exigir(!"enviarZipDirecto".equals(metodo), "SENDBILL_PROHIBIDO_EN_RA");
            if (!Set.of("enviarResumenDirecto", "consultarTicket").contains(metodo)) return call.proceed();
            validarAutorizacionRc();
            exigir("true".equals(env.getProperty("FASE5_FACTURA_RA_NO_OTORGADA")), "FACTURA_DEDICADA_NO_OTORGADA_REQUERIDA");
            try (var conexion = ds.getConnection()) { validarConexion(conexion); }
            var args = call.getArguments(); exigir("20100066603".equals(args[0]), "RUC_RA_NO_COINCIDE");
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
            if ("enviarResumenDirecto".equals(metodo)) {
                etapa = "RA_PRECHECK_SEND_SUMMARY";
                var r = tx.execute(status -> {
                    var actual = cargarBaja(); validarFacturaDedicada(dedicadaInicial.getEstadoSfs(), true);
                    exigir(actual.getEstado() == EstadoComunicacionBaja.ENVIANDO && actual.getIntentosEnvio() == 1 && actual.getIntentosConsulta() == 0
                            && actual.getTicketSunat() == null && actual.getCdrZipBytes() == null && actual.getCdrXmlBytes() == null
                            && ctx.getBean(RespuestaSunatRecibidaRepository.class).findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc("20100066603", RespuestaSunatRecibida.Flujo.RA, raId).isEmpty(), "RA_ENVIO_PREVIO_PROHIBIDO");
                    return actual;
                });
                validarXmlBaja(r); ctx.getBean(SunatPlazoService.class).validarBaja(r.getFechaDocumentos());
                byte[] zip = (byte[]) args[4]; String base = r.getNombreArchivoXml().substring(0, r.getNombreArchivoXml().length() - 4);
                exigir((base + ".zip").equals(args[3]), "ZIP_RA_NOMBRE_INVALIDO");
                try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
                    var entrada = in.getNextEntry();
                    exigir(entrada != null && !entrada.isDirectory() && r.getNombreArchivoXml().equals(entrada.getName())
                            && Arrays.equals(r.getXmlFirmadoBytes(), org.example.util.LecturaLimitada.leer(in, r.getXmlFirmadoBytes().length))
                            && in.getNextEntry() == null, "ZIP_RA_INVALIDO");
                }
                rutaExterna(evidencia.toString()); exigir(resumenesEnviados.get() == 0, "RA_NO_REENVIAR");
                Files.write(evidencia.resolve(r.getNombreArchivoXml()), r.getXmlFirmadoBytes(), StandardOpenOption.CREATE_NEW);
                Files.write(evidencia.resolve(base + ".zip"), zip, StandardOpenOption.CREATE_NEW);
                Files.writeString(evidencia.resolve("precheck.txt"), "MODO=RA\nXML=VoidedDocuments\nUBL=2.0\nCUSTOMIZATION=1.0\nFIRMA=VALIDA\nDIGEST=VALIDO\nXSD=VALIDO\nZIP=EXACTO\nFACTURA_ID=" + id + "\nRA_ID=" + raId + "\n", StandardOpenOption.CREATE_NEW);
                Files.writeString(evidencia.resolve("intento-send-summary.txt"), "RA_ID=" + raId + "\nINICIO=" + Instant.now() + "\n", StandardOpenOption.CREATE_NEW);
                exigir(resumenesEnviados.compareAndSet(0, 1), "SEGUNDO_SEND_SUMMARY_PROHIBIDO");
                precheck = true; raInicial = r; detalleRaInicial = fotoColumnas(r.getDetalles().getFirst());
                try { ticketRecibido = (String) call.proceed(); return ticketRecibido; }
                catch (Throwable e) { registrarFalloRc(e, "SEND_SUMMARY_RA"); throw e; }
            }
            etapa = "RA_PRECHECK_GET_STATUS";
            exigir(resumenesEnviados.get() == 1 && ticketRecibido != null && !ticketRecibido.isBlank() && ticketRecibido.equals(args[3])
                    && consultasTicket.get() < 3 && (consultasTicket.get() == 0 || "98".equals(ultimoEstadoTicket)
                    && System.nanoTime() - ultimaConsultaTicketNanos >= TimeUnit.SECONDS.toNanos(60)), "GET_STATUS_RA_NO_AUTORIZADO");
            tx.executeWithoutResult(status -> {
                var r = cargarBaja(); validarFacturaDedicada(dedicadaInicial.getEstadoSfs(), true);
                exigir(ticketRecibido.equals(r.getTicketSunat()) && r.getIntentosConsulta() == consultasTicket.get()
                        && (consultasTicket.get() == 0 ? r.getEstado() == EstadoComunicacionBaja.TICKET_GENERADO : r.getEstado() == EstadoComunicacionBaja.EN_PROCESO)
                        && !ctx.getBean(ComunicacionBajaRepository.class).existsByRucEmisorAndTicketSunatAndIdNot(r.getRucEmisor(), ticketRecibido, raId), "TICKET_RA_CRUZADO");
            });
            int numero = consultasTicket.incrementAndGet(); ultimoEstadoTicket = null;
            Files.writeString(evidencia.resolve("consulta-" + numero + ".txt"), "RA_ID=" + raId + "\nTICKET=CORRELACIONADO_SIN_MOSTRAR\n", StandardOpenOption.CREATE_NEW);
            try {
                var resultado = (SunatDirectSoapClient.EstadoTicketSunat) call.proceed();
                ultimoEstadoTicket = resultado.statusCode(); ultimoZipRc = resultado.contentZip(); ultimaConsultaTicketNanos = System.nanoTime(); return resultado;
            } catch (Throwable e) { registrarFalloRc(e, "GET_STATUS_RA"); throw e; }
        }

        private void informarBaja() throws Exception {
            if (ticketRecibido != null) Files.writeString(evidencia.resolve("ticket-sunat.txt"), ticketRecibido, StandardOpenOption.CREATE_NEW);
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
            var r = tx.execute(status -> {
                var existe = ctx.getBean(ComunicacionBajaRepository.class).existsByDetalles_ComprobanteId(id);
                var actual = existe ? cargarBaja() : null; validarHistoricoDedicada();
                var c = ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(id, "20100066603").orElseThrow();
                validarDetallesDedicada(c);
                var foto = (com.fasterxml.jackson.databind.node.ObjectNode) fotoColumnas(c);
                var original = (com.fasterxml.jackson.databind.node.ObjectNode) fotoDedicada.deepCopy();
                List<String> metadatos = List.of("estadoSfs", "codigoRespuestaSunat", "mensajeSunat", "updatedAt");
                foto.remove(metadatos); original.remove(metadatos);
                exigir(original.equals(foto), "EVIDENCIA_ORIGINAL_DEDICADA_ALTERADA");
                if (actual != null && actual.getEstado() == EstadoComunicacionBaja.ACEPTADO)
                    exigir(c.getEstadoSfs() == EstadoComprobante.ANULADO && "0".equals(c.getCodigoRespuestaSunat()), "FACTURA_NO_ANULADA_POR_RA");
                else exigir(fotoDedicada.equals(fotoColumnas(c)), "FACTURA_CAMBIO_SIN_ACEPTACION_RA");
                return actual;
            });
            String resultado = resumenesEnviados.get() == 0 ? "BLOQUEADO_ANTES_DE_ENVIAR" : "PENDIENTE_REVISION_NO_REENVIAR";
            if (r != null) {
                exigir(r.getTicketSunat() == null || Objects.equals(ticketRecibido, r.getTicketSunat()), "TICKET_FINAL_RA_NO_COINCIDE");
                var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class).findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc("20100066603", RespuestaSunatRecibida.Flujo.RA, raId);
                for (var recibida : recibidas) {
                    exigir(Objects.equals(ticketRecibido, recibida.getTicketSunat()), "RECEPCION_RA_TICKET_NO_COINCIDE");
                    Files.write(evidencia.resolve("respuesta-ra-" + recibida.getId() + ".zip"), recibida.getRespuestaZipBytes(), StandardOpenOption.CREATE_NEW);
                }
                if (r.getCdrZipBytes() != null && r.getCdrXmlBytes() != null) {
                    exigir(Arrays.equals(ultimoZipRc, r.getCdrZipBytes()) && recibidas.stream().anyMatch(x -> Arrays.equals(x.getRespuestaZipBytes(), r.getCdrZipBytes())), "CDR_RA_NO_CONSERVADO");
                    Files.write(evidencia.resolve("cdr-recibido.zip"), r.getCdrZipBytes(), StandardOpenOption.CREATE_NEW);
                    Files.write(evidencia.resolve("cdr-procesado.xml"), r.getCdrXmlBytes(), StandardOpenOption.CREATE_NEW);
                    if ("NINGUNO".equals(error) && precheck) resultado = r.getEstado() == EstadoComunicacionBaja.ACEPTADO ? "ACEPTADO_BETA" : "RECHAZADO_BETA";
                } else if (r.getEstado() == EstadoComunicacionBaja.EN_PROCESO) resultado = "TICKET_EN_PROCESO_LIMITE_ALCANZADO";
                else if (r.getEstado() == EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO) resultado = "ENVIO_INCIERTO";
            }
            if (ultimoZipRc != null && ultimoZipRc.length > 0 && (r == null || r.getCdrZipBytes() == null))
                Files.write(evidencia.resolve("respuesta-no-finalizada.zip"), ultimoZipRc, StandardOpenOption.CREATE_NEW);
            String salida = "MODO=BAJA_FACTURA\nFACTURA_ID=" + id + "\nRA_ID=" + raId + "\nESTADO_RA=" + (r == null ? "NO_CREADO" : r.getEstado())
                    + "\nFACTURA=01/F501-" + dedicadaInicial.getNumero()
                    + "\nESTADO_FACTURA=" + tx.execute(status -> ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(id, "20100066603").orElseThrow().getEstadoSfs())
                    + "\nTICKET_PERSISTIDO=" + (r != null && r.getTicketSunat() != null ? "SI_CORRELACIONADO" : "NO")
                    + "\nRESPONSE_CODE=" + (r == null || r.getCodigoRespuestaSunat() == null ? "NO_DISPONIBLE" : r.getCodigoRespuestaSunat().replaceAll("[^A-Za-z0-9_.:-]", ""))
                    + "\nSEND_BILL=0\nSEND_SUMMARY=" + resumenesEnviados.get() + "\nGET_STATUS=" + consultasTicket.get()
                    + "\nRETRIES_SEND_SUMMARY=0\nHISTORICO=INTACTO_VERIFICADO\nEVIDENCIA_ORIGINAL_FACTURA=INTACTA_VERIFICADA\nERROR_TIPO=" + error + "\nRESULTADO=" + resultado + "\nNO_REEJECUTAR=SI\n";
            Files.writeString(evidencia.resolve("resultado.txt"), salida, StandardOpenOption.CREATE_NEW); System.out.print(salida);
        }

        private void validarAnterioresRc() {
            validarPrimeraAceptada(); validarFacturaReferencia();
            var repos = ctx.getBean(ComprobanteRepository.class);
            var nc = repos.findByRucEmisorAndTipoComprobanteAndSerieAndNumero("20100066603", "07", "F502", 1).orElseThrow();
            exigir(nc.getEstadoSfs() == EstadoComprobante.ACEPTADO && "0".equals(nc.getCodigoRespuestaSunat())
                    && "01".equals(nc.getTipoComprobanteAfectado()) && "F501".equals(nc.getSerieComprobanteAfectado())
                    && Integer.valueOf(2).equals(nc.getNumeroComprobanteAfectado()) && nc.getXmlFirmadoBytes() != null
                    && nc.getHashCpe() != null && nc.getCdrZipBytes() != null && nc.getCdrXmlBytes() != null, "NC_ANTERIOR_NO_ACREDITADA");
            var cdr = ctx.getBean(CdrParserService.class).procesar(nc.getCdrZipBytes());
            exigir(cdr.aceptado() && "0".equals(cdr.responseCode()) && Arrays.equals(cdr.xml(), nc.getCdrXmlBytes())
                    && Set.of("F502-1", "F502-00000001").contains(cdr.referenceId()), "CDR_NC_ANTERIOR_INVALIDO");
            exigir(ncAnterior == null || ncAnterior.getId().equals(nc.getId()), "NC_ANTERIOR_IDENTIDAD_ALTERADA");
            if (ncAnterior == null) ncAnterior = nc;
            var docs = List.of(primeraAceptada.getId(), facturaReferencia.getId(), nc.getId());
            for (Long documentoId : docs) {
                var c = repos.findByIdAndRucEmisor(documentoId, "20100066603").orElseThrow();
                conservarFoto("CPE/" + documentoId, fotoColumnas(c));
                var detalles = ctx.getBean(ObjectMapper.class).createArrayNode();
                c.getDetalles().stream().sorted(Comparator.comparing(org.example.model.DetalleComprobante::getId))
                        .forEach(d -> detalles.add(fotoColumnas(d)));
                conservarFoto("DETALLES/" + documentoId, detalles);
                var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class)
                        .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, documentoId);
                exigir(recibidas.size() == 1 && Arrays.equals(recibidas.getFirst().getRespuestaZipBytes(), c.getCdrZipBytes()), "RECEPCION_ANTERIOR_INVALIDA");
                conservarFoto("RECIBIDA/" + documentoId, fotoColumnas(recibidas.getFirst()));
            }
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll().stream()
                    .filter(s -> docs.contains(s.getComprobanteId())).toList();
            exigir(solicitudes.size() == 3, "IDEMPOTENCIAS_ANTERIORES_NO_COINCIDEN");
            for (var s : solicitudes) {
                String key = Long.valueOf(1).equals(s.getComprobanteId()) ? "FASE5-PRIMERA-FACTURA-BETA-UNICA"
                        : facturaReferencia.getId().equals(s.getComprobanteId()) ? SEGUNDA_FACTURA_KEY : NC_FACTURA_KEY;
                exigir("20100066603".equals(s.getRucEmisor()) && key.equals(s.getIdempotencyKey()) && "CREADO".equals(s.getEstado()), "IDEMPOTENCIA_ANTERIOR_INVALIDA");
                conservarFoto("SOLICITUD/" + s.getId(), fotoColumnas(s));
            }
            var secuencias = ctx.getBean(SecuenciaCorrelativoRepository.class).findAll().stream()
                    .filter(s -> ("01".equals(s.getTipoComprobante()) || "07".equals(s.getTipoComprobante()))
                            && !(ncBoleta && "B502".equals(s.getSerie()))).toList();
            exigir(secuencias.size() == 2, "SECUENCIAS_ANTERIORES_NO_COINCIDEN");
            secuencias.forEach(s -> conservarFoto("SECUENCIA/" + s.getId(), fotoColumnas(s)));
        }

        private void validarPreparacionRc() {
            try {
                var datos = datosNotaCreditoConfirmados();
                serieBoleta = datos.path("series").path("boleta").asText();
                exigir("B501".equals(serieBoleta), "SERIE_BOLETA_NO_CONFIRMADA");
            } catch (IOException e) { throw new IllegalStateException("ESCENARIO_BOLETA_NO_CARGABLE"); }
            validarAnterioresRc();
            exigir(ctx.getBean(ComprobanteRepository.class).count() == 3
                    && ctx.getBean(SolicitudEmisionRepository.class).count() == 3
                    && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 3
                    && ctx.getBean(ResumenDiarioRepository.class).count() == 0, "BOLETA_O_RC_PREVIO_NO_REPETIR");
            var secuencias = ctx.getBean(SecuenciaCorrelativoRepository.class).findAll().stream()
                    .filter(s -> "03".equals(s.getTipoComprobante())).toList();
            exigir(secuencias.size() <= 1 && (secuencias.isEmpty() || "20100066603".equals(secuencias.getFirst().getRucEmisor())
                    && serieBoleta.equals(secuencias.getFirst().getSerie()) && secuencias.getFirst().getUltimoNumero() != null
                    && secuencias.getFirst().getUltimoNumero() >= 0), "SECUENCIA_BOLETA_INVALIDA");
            int ultimo = secuencias.isEmpty() ? 0 : secuencias.getFirst().getUltimoNumero();
            exigir(ultimoBoletaAntes == null || ultimoBoletaAntes == ultimo, "SECUENCIA_BOLETA_CAMBIO");
            ultimoBoletaAntes = ultimo; // Lectura, nunca asignacion manual.
            exigir(resumenesEnviados.get() == 0 && consultasTicket.get() == 0 && workers.get() == 0 && invocaciones.get() == 0, "RC_NO_REPETIR");
        }

        private Comprobante validarBoletaRc(EstadoComprobante estado) {
            if (ncBoleta) return validarNcBoletaRc(estado);
            validarAnterioresRc();
            exigir(id != null && !Set.of(primeraAceptada.getId(), facturaReferencia.getId(), ncAnterior.getId()).contains(id), "BOLETA_ID_NUEVO_REQUERIDO");
            var repos = ctx.getBean(ComprobanteRepository.class);
            var c = repos.findByIdAndRucEmisor(id, "20100066603").orElseThrow();
            exigir(repos.count() == 4 && c.getEstadoSfs() == estado && "03".equals(c.getTipoComprobante()) && serieBoleta.equals(c.getSerie())
                    && c.getNumero() != null && ultimoBoletaAntes != null && c.getNumero() == ultimoBoletaAntes + 1
                    && "PEN".equals(c.getMoneda()) && "0101".equals(c.getTipoOperacion()) && "Contado".equals(c.getFormaPago())
                    && "0".equals(c.getTipoDocCliente()) && "-".equals(c.getNumDocCliente()) && "CONSUMIDOR FINAL".equals(c.getRznSocialCliente())
                    && "1".equals(c.getCodigoEstadoResumen()) && c.getTipoComprobanteAfectado() == null
                    && c.getOpGravada().compareTo(new BigDecimal("10.00")) == 0 && c.getIgv().compareTo(new BigDecimal("1.80")) == 0
                    && c.getMontoTotal().compareTo(new BigDecimal("11.80")) == 0, "BOLETA_NUEVA_NO_COINCIDE");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 4 && solicitudes.stream().anyMatch(s -> "20100066603".equals(s.getRucEmisor())
                    && BOLETA_RC_KEY.equals(s.getIdempotencyKey()) && id.equals(s.getComprobanteId()) && "CREADO".equals(s.getEstado())), "IDEMPOTENCIA_BOLETA_INVALIDA");
            exigir(ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).isEmpty()
                    && c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && workers.get() == 0 && invocaciones.get() == 0, "BOLETA_SENDBILL_PROHIBIDO");
            if (boletaNueva != null) exigir(Objects.equals(boletaNueva.getNombreArchivoXml(), c.getNombreArchivoXml())
                    && Objects.equals(boletaNueva.getHashCpe(), c.getHashCpe()) && Objects.equals(boletaNueva.getFechaEmision(), c.getFechaEmision())
                    && Arrays.equals(boletaNueva.getXmlFirmadoBytes(), c.getXmlFirmadoBytes()), "BOLETA_XML_ALTERADO");
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow(); validarEmpresa(empresa);
            try { validarXmlPersistido(c, empresa); }
            catch (Exception e) { throw new IllegalStateException("XML_BOLETA_INVALIDO"); }
            if (boletaNueva == null) boletaNueva = c;
            return c;
        }

        private void validarBoletaReferenciaAceptada() {
            validarAnterioresRc();
            var c = ctx.getBean(ComprobanteRepository.class)
                    .findByRucEmisorAndTipoComprobanteAndSerieAndNumero("20100066603", "03", "B501", 1).orElseThrow();
            exigir(c.getEstadoSfs() == EstadoComprobante.ACEPTADO && "0".equals(c.getCodigoRespuestaSunat())
                    && c.getXmlFirmadoBytes() != null && c.getXmlFirmadoBytes().length > 0 && c.getHashCpe() != null && !c.getHashCpe().isBlank()
                    && "PEN".equals(c.getMoneda()) && "0".equals(c.getTipoDocCliente()) && "-".equals(c.getNumDocCliente())
                    && "CONSUMIDOR FINAL".equals(c.getRznSocialCliente()) && "1".equals(c.getCodigoEstadoResumen())
                    && c.getMontoTotal().compareTo(new BigDecimal("11.80")) == 0
                    && c.getIgvPorcentaje() != null && c.getIgvPorcentaje().compareTo(new BigDecimal("18.00")) == 0, "BOLETA_REFERENCIA_NO_ACEPTADA");
            exigir(boletaReferencia == null || boletaReferencia.getId().equals(c.getId()), "BOLETA_REFERENCIA_IDENTIDAD_ALTERADA");
            if (boletaReferencia == null) boletaReferencia = c;
            conservarFoto("BOLETA_REFERENCIA", fotoColumnas(c));
            var detalles = ctx.getBean(ObjectMapper.class).createArrayNode();
            c.getDetalles().stream().sorted(Comparator.comparing(org.example.model.DetalleComprobante::getId))
                    .forEach(d -> detalles.add(fotoColumnas(d)));
            conservarFoto("BOLETA_REFERENCIA_DETALLES", detalles);
            var repoRc = ctx.getBean(ResumenDiarioRepository.class);
            var anteriores = repoRc.findAll().stream().filter(r -> "20100066603".equals(r.getRucEmisor())
                    && "RC-20261005-1".equals(r.getIdentificador())).toList();
            exigir(anteriores.size() == 1, "RC_ANTERIOR_NO_ACREDITADO");
            exigir(resumenAnteriorId == null || resumenAnteriorId.equals(anteriores.getFirst().getId()), "RC_ANTERIOR_IDENTIDAD_ALTERADA");
            if (resumenAnteriorId == null) resumenAnteriorId = anteriores.getFirst().getId();
            var r = repoRc.buscarCompleto(resumenAnteriorId, "20100066603").orElseThrow();
            exigir(r.getEstado() == EstadoResumenDiario.ACEPTADO && "0".equals(r.getCodigoRespuestaSunat())
                    && r.getTicketSunat() != null && !r.getTicketSunat().isBlank() && Integer.valueOf(1).equals(r.getIntentosEnvio())
                    && Integer.valueOf(1).equals(r.getIntentosConsulta()) && Integer.valueOf(1).equals(r.getCantidadDocumentos())
                    && r.getDetalles().size() == 1 && r.getXmlFirmadoBytes() != null && r.getHashCpe() != null
                    && r.getCdrZipBytes() != null && r.getCdrXmlBytes() != null && r.getFechaDocumentos().equals(c.getFechaEmision().toLocalDate())
                    && (r.getRucEmisor() + "-" + r.getIdentificador() + ".xml").equals(r.getNombreArchivoXml()), "RC_BOLETA_REFERENCIA_NO_ACEPTADO");
            var d = r.getDetalles().getFirst();
            exigir(c.getId().equals(d.getComprobanteId()) && "03".equals(d.getTipoComprobante()) && "B501".equals(d.getSerie())
                    && Integer.valueOf(1).equals(d.getNumero()) && "1".equals(d.getCodigoEstado())
                    && d.getMontoTotal().compareTo(c.getMontoTotal()) == 0, "BOLETA_REFERENCIA_NO_PERTENECE_AL_RC");
            var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc("20100066603", RespuestaSunatRecibida.Flujo.RC, resumenAnteriorId);
            exigir(recibidas.size() == 1 && r.getTicketSunat().equals(recibidas.getFirst().getTicketSunat())
                    && Arrays.equals(recibidas.getFirst().getRespuestaZipBytes(), r.getCdrZipBytes())
                    && !repoRc.existsByRucEmisorAndTicketSunatAndIdNot(r.getRucEmisor(), r.getTicketSunat(), resumenAnteriorId), "RECEPCION_RC_ANTERIOR_NO_CONSERVADA");
            var cdr = ctx.getBean(CdrParserService.class).procesar(r.getCdrZipBytes());
            exigir(cdr.aceptado() && "0".equals(cdr.responseCode()) && Arrays.equals(cdr.xml(), r.getCdrXmlBytes()), "CDR_RC_ANTERIOR_INVALIDO");
            // CDR/recepcion de la boleta se conservan en el RC: nunca copiarlos ni inventar una recepcion CPE.
            exigir(c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, c.getId()).isEmpty(), "BOLETA_REFERENCIA_FLUJO_INDIVIDUAL_AJENO");
            conservarFoto("RC_ANTERIOR", fotoColumnas(r));
            conservarFoto("RC_ANTERIOR_DETALLE", fotoColumnas(d));
            conservarFoto("RC_ANTERIOR_RECIBIDA", fotoColumnas(recibidas.getFirst()));
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll().stream()
                    .filter(s -> c.getId().equals(s.getComprobanteId())).toList();
            exigir(solicitudes.size() == 1 && BOLETA_RC_KEY.equals(solicitudes.getFirst().getIdempotencyKey())
                    && "20100066603".equals(solicitudes.getFirst().getRucEmisor()) && "CREADO".equals(solicitudes.getFirst().getEstado()), "IDEMPOTENCIA_BOLETA_ANTERIOR_INVALIDA");
            conservarFoto("BOLETA_SOLICITUD_ANTERIOR", fotoColumnas(solicitudes.getFirst()));
            var secuencias = ctx.getBean(SecuenciaCorrelativoRepository.class).findAll().stream()
                    .filter(s -> "03".equals(s.getTipoComprobante())).toList();
            exigir(secuencias.size() == 1 && "20100066603".equals(secuencias.getFirst().getRucEmisor())
                    && "B501".equals(secuencias.getFirst().getSerie()) && secuencias.getFirst().getUltimoNumero() != null
                    && secuencias.getFirst().getUltimoNumero() >= 1, "SECUENCIA_BOLETA_ANTERIOR_INVALIDA");
            conservarFoto("BOLETA_SECUENCIA_ANTERIOR", fotoColumnas(secuencias.getFirst()));
            var auditorias = ctx.getBean(EventoAuditoriaRepository.class);
            if (!auditoriasCapturadas) {
                auditorias.findAll().forEach(a -> auditoriasAnteriores.put(a.getId(), fotoColumnas(a)));
                auditoriasCapturadas = true;
            } else auditoriasAnteriores.forEach((aid, foto) -> exigir(foto.equals(fotoColumnas(auditorias.findById(aid).orElseThrow())), "AUDITORIA_ANTERIOR_ALTERADA"));
            try {
                var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow();
                ctx.getBean(UblValidatorService.class).validarFactura(c.getXmlFirmadoBytes());
                validarFirmaDigest(Fase5BetaPreparacionTest.parse(c.getXmlFirmadoBytes()), c.getHashCpe(), empresa);
                ctx.getBean(UblValidatorService.class).validarResumenDiario(r.getXmlFirmadoBytes());
                validarFirmaDigest(Fase5BetaPreparacionTest.parse(r.getXmlFirmadoBytes()), r.getHashCpe(), empresa);
            } catch (Exception e) { throw new IllegalStateException("XML_BOLETA_O_RC_ANTERIOR_INVALIDO"); }
        }

        private void validarPreparacionNcBoleta() {
            try {
                var datos = datosNotaCreditoConfirmados();
                exigir("B501".equals(datos.path("series").path("boleta").asText())
                        && "B502".equals(datos.path("series").path("ncBoleta").asText()), "SERIES_NC_BOLETA_NO_CONFIRMADAS");
                serieNc = datos.path("series").path("ncBoleta").asText();
            } catch (IOException e) { throw new IllegalStateException("ESCENARIO_NC_BOLETA_NO_CARGABLE"); }
            validarBoletaReferenciaAceptada();
            exigir(ctx.getBean(ComprobanteRepository.class).count() == 4 && ctx.getBean(SolicitudEmisionRepository.class).count() == 4
                    && ctx.getBean(ResumenDiarioRepository.class).count() == 1 && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 4, "NC_BOLETA_O_RC_NUEVO_PREVIO_NO_REPETIR");
            var secuencias = ctx.getBean(SecuenciaCorrelativoRepository.class).findAll().stream()
                    .filter(s -> "20100066603".equals(s.getRucEmisor()) && "07".equals(s.getTipoComprobante()) && serieNc.equals(s.getSerie())).toList();
            exigir(secuencias.size() <= 1 && (secuencias.isEmpty() || secuencias.getFirst().getUltimoNumero() != null
                    && secuencias.getFirst().getUltimoNumero() >= 0), "SECUENCIA_NC_BOLETA_INVALIDA");
            int ultimo = secuencias.isEmpty() ? 0 : secuencias.getFirst().getUltimoNumero();
            exigir(ultimoNcBoletaAntes == null || ultimoNcBoletaAntes == ultimo, "SECUENCIA_NC_BOLETA_CAMBIO");
            ultimoNcBoletaAntes = ultimo; // Solo lectura; CorrelativoService asigna el siguiente dentro de la TX productiva.
            exigir(resumenesEnviados.get() == 0 && consultasTicket.get() == 0 && workers.get() == 0 && invocaciones.get() == 0, "NC_BOLETA_NO_REPETIR");
        }

        private Comprobante validarNcBoletaRc(EstadoComprobante estado) {
            validarBoletaReferenciaAceptada();
            exigir(id != null && !Set.of(primeraAceptada.getId(), facturaReferencia.getId(), ncAnterior.getId(), boletaReferencia.getId()).contains(id), "NC_BOLETA_IDENTIDAD_NUEVA_REQUERIDA");
            var repos = ctx.getBean(ComprobanteRepository.class);
            var c = repos.findByIdAndRucEmisor(id, "20100066603").orElseThrow();
            exigir(repos.count() == 5 && c.getEstadoSfs() == estado && "07".equals(c.getTipoComprobante()) && serieNc.equals(c.getSerie())
                    && c.getNumero() != null && ultimoNcBoletaAntes != null && c.getNumero() == ultimoNcBoletaAntes + 1
                    && "03".equals(c.getTipoComprobanteAfectado()) && "B501".equals(c.getSerieComprobanteAfectado())
                    && Integer.valueOf(1).equals(c.getNumeroComprobanteAfectado()) && NC_MOTIVO.equals(c.getCodigoMotivoNota())
                    && NC_DESCRIPCION.equals(c.getDescripcionMotivoNota()) && "PEN".equals(c.getMoneda())
                    && "0101".equals(c.getTipoOperacion()) && "Contado".equals(c.getFormaPago()) && "1".equals(c.getCodigoEstadoResumen())
                    && Objects.equals(boletaReferencia.getTipoDocCliente(), c.getTipoDocCliente())
                    && Objects.equals(boletaReferencia.getNumDocCliente(), c.getNumDocCliente())
                    && Objects.equals(boletaReferencia.getRznSocialCliente(), c.getRznSocialCliente())
                    && c.getOpGravada().compareTo(new BigDecimal("1.00")) == 0 && c.getIgv().compareTo(new BigDecimal("0.18")) == 0
                    && c.getMontoTotal().compareTo(new BigDecimal("1.18")) == 0 && c.getMontoTotal().compareTo(boletaReferencia.getMontoTotal()) < 0
                    && c.getIgvPorcentaje().compareTo(boletaReferencia.getIgvPorcentaje()) == 0, "NC_BOLETA_NUEVA_NO_COINCIDE");
            exigir(c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && workers.get() == 0 && invocaciones.get() == 0
                    && ctx.getBean(RespuestaSunatRecibidaRepository.class).findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).isEmpty(), "NC_BOLETA_SENDBILL_PROHIBIDO");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 5 && solicitudes.stream().anyMatch(s -> NC_BOLETA_RC_KEY.equals(s.getIdempotencyKey())
                    && "20100066603".equals(s.getRucEmisor()) && id.equals(s.getComprobanteId()) && "CREADO".equals(s.getEstado())), "IDEMPOTENCIA_NC_BOLETA_INVALIDA");
            if (boletaNueva != null) exigir(Objects.equals(boletaNueva.getHashCpe(), c.getHashCpe())
                    && Objects.equals(boletaNueva.getNombreArchivoXml(), c.getNombreArchivoXml()) && Objects.equals(boletaNueva.getFechaEmision(), c.getFechaEmision())
                    && Arrays.equals(boletaNueva.getXmlFirmadoBytes(), c.getXmlFirmadoBytes()), "NC_BOLETA_XML_ALTERADO");
            exigir(!Arrays.equals(c.getXmlFirmadoBytes(), boletaReferencia.getXmlFirmadoBytes())
                    && !Objects.equals(c.getNombreArchivoXml(), boletaReferencia.getNombreArchivoXml())
                    && !Objects.equals(c.getHashCpe(), boletaReferencia.getHashCpe()), "NC_BOLETA_NO_REUTILIZAR_XML_ORIGINAL");
            try { validarXmlPersistido(c, ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow()); }
            catch (Exception e) { throw new IllegalStateException("XML_NC_BOLETA_INVALIDO"); }
            // Variable compartida para el documento nuevo del RC, no para la boleta original.
            if (boletaNueva == null) boletaNueva = c;
            return c;
        }

        private ResumenDiario cargarResumenRc() {
            var repo = ctx.getBean(ResumenDiarioRepository.class);
            var todos = repo.findAll();
            exigir(todos.size() == (ncBoleta ? 2 : 1), "SOLO_UN_RC_NUEVO_BETA");
            var nuevos = todos.stream().filter(r -> !ncBoleta || !r.getId().equals(resumenAnteriorId)).toList();
            exigir(nuevos.size() == 1 && "20100066603".equals(nuevos.getFirst().getRucEmisor()), "RC_NUEVO_NO_CORRESPONDE");
            if (resumenId == null) resumenId = nuevos.getFirst().getId();
            exigir(resumenId.equals(nuevos.getFirst().getId()) && (!ncBoleta || !resumenId.equals(resumenAnteriorId)), "RC_ANTERIOR_O_DISTINTO_PROHIBIDO");
            var r = repo.buscarCompleto(resumenId, "20100066603").orElseThrow();
            exigir(Integer.valueOf(1).equals(r.getCantidadDocumentos()) && r.getDetalles().size() == 1
                    && r.getCorrelativo() != null && r.getCorrelativo() > 0 && r.getCorrelativo() <= 99999
                    && ("RC-" + r.getFechaGeneracion().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + r.getCorrelativo()).equals(r.getIdentificador())
                    && (r.getRucEmisor() + "-" + r.getIdentificador() + ".xml").equals(r.getNombreArchivoXml())
                    && r.getFechaDocumentos().equals(boletaNueva.getFechaEmision().toLocalDate()), "RC_IDENTIDAD_INVALIDA");
            if (ncBoleta) exigir(!"RC-20261005-1".equals(r.getIdentificador()), "RC_ANTERIOR_NO_REUTILIZAR");
            var d = r.getDetalles().getFirst();
            exigir(id.equals(d.getComprobanteId()) && Integer.valueOf(1).equals(d.getNumeroLinea())
                    && (ncBoleta ? "07" : "03").equals(d.getTipoComprobante()) && (ncBoleta ? serieNc : serieBoleta).equals(d.getSerie()) && boletaNueva.getNumero().equals(d.getNumero())
                    && "1".equals(d.getCodigoEstado()) && "0".equals(d.getTipoDocCliente()) && "-".equals(d.getNumDocCliente())
                    && "PEN".equals(d.getMoneda()) && d.getMontoTotal().compareTo(boletaNueva.getMontoTotal()) == 0
                    && d.getOpGravada().compareTo(boletaNueva.getOpGravada()) == 0 && d.getIgv().compareTo(boletaNueva.getIgv()) == 0
                    && (ncBoleta ? "03".equals(d.getTipoComprobanteAfectado()) && "B501".equals(d.getSerieComprobanteAfectado())
                    && Integer.valueOf(1).equals(d.getNumeroComprobanteAfectado()) : d.getTipoComprobanteAfectado() == null), "RC_NO_EXCLUSIVO_DEL_DOCUMENTO_NUEVO");
            if (xmlRcInicial != null) exigir(Arrays.equals(xmlRcInicial, r.getXmlFirmadoBytes())
                    && Objects.equals(resumenInicial.getHashCpe(), r.getHashCpe())
                    && Objects.equals(resumenInicial.getIdentificador(), r.getIdentificador())
                    && Objects.equals(resumenInicial.getNombreArchivoXml(), r.getNombreArchivoXml())
                    && Objects.equals(resumenInicial.getFechaGeneracion(), r.getFechaGeneracion())
                    && Objects.equals(resumenInicial.getFechaDocumentos(), r.getFechaDocumentos())
                    && Objects.equals(resumenInicial.getCorrelativo(), r.getCorrelativo())
                    && detalleRcInicial.equals(fotoColumnas(d)), "RC_XML_IDENTIDAD_O_DETALLE_ALTERADO");
            return r;
        }

        private void validarAutorizacionRc() {
            validarConfiguracion(env);
            exigir("true".equals(env.getProperty("FASE5_MAIN_DETENIDO"))
                    && "true".equals(env.getProperty("FASE5_AUTORIZAR_UN_SEND_SUMMARY"))
                    && "true".equals(env.getProperty("FASE5_AUTORIZAR_GET_STATUS")), "AUTORIZACION_RC_REQUERIDA");
            exigir(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(), "SOAP_FUERA_DE_TX_REQUERIDO");
        }

        private Object controlarClienteResumen(org.aopalliance.intercept.MethodInvocation call) throws Throwable {
            String metodo = call.getMethod().getName();
            exigir(!"enviarZipDirecto".equals(metodo), "SENDBILL_PROHIBIDO_EN_BOLETA_RC");
            if (!Set.of("enviarResumenDirecto", "consultarTicket").contains(metodo)) return call.proceed();
            validarAutorizacionRc();
            try (var conexion = ds.getConnection()) { validarConexion(conexion); }
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
            var args = call.getArguments();
            exigir("20100066603".equals(args[0]), "RUC_CLIENTE_RC_NO_COINCIDE");
            if ("enviarResumenDirecto".equals(metodo)) {
                etapa = "BOLETA_RC_PRECHECK_SEND_SUMMARY";
                var r = tx.execute(status -> {
                    validarBoletaRc(EstadoComprobante.EN_RESUMEN);
                    var actual = cargarResumenRc();
                    exigir(actual.getEstado() == EstadoResumenDiario.ENVIANDO && Integer.valueOf(1).equals(actual.getIntentosEnvio())
                            && Integer.valueOf(0).equals(actual.getIntentosConsulta()) && actual.getTicketSunat() == null
                            && actual.getCdrZipBytes() == null && actual.getCdrXmlBytes() == null
                            && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == (ncBoleta ? 4 : 3), "RC_ENVIO_PREVIO_PROHIBIDO");
                    return actual;
                });
                validarXmlResumen(r);
                byte[] zip = (byte[]) args[4];
                String base = r.getNombreArchivoXml().substring(0, r.getNombreArchivoXml().length() - 4);
                exigir((base + ".zip").equals(args[3]), "ZIP_RC_NOMBRE_INVALIDO");
                try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
                    var entrada = in.getNextEntry();
                    exigir(entrada != null && !entrada.isDirectory() && r.getNombreArchivoXml().equals(entrada.getName())
                            && Arrays.equals(r.getXmlFirmadoBytes(), org.example.util.LecturaLimitada.leer(in, r.getXmlFirmadoBytes().length))
                            && in.getNextEntry() == null, "ZIP_RC_INVALIDO");
                }
                rutaExterna(evidencia.toString());
                exigir(Files.isDirectory(evidencia, LinkOption.NOFOLLOW_LINKS) && resumenesEnviados.get() == 0, "RC_NO_REENVIAR");
                Files.write(evidencia.resolve(boletaNueva.getNombreArchivoXml()), boletaNueva.getXmlFirmadoBytes(), StandardOpenOption.CREATE_NEW);
                Files.write(evidencia.resolve(r.getNombreArchivoXml()), r.getXmlFirmadoBytes(), StandardOpenOption.CREATE_NEW);
                Files.write(evidencia.resolve(base + ".zip"), zip, StandardOpenOption.CREATE_NEW);
                String pre = "PERFIL=beta\nENTORNO=BETA\nBD=" + BASE + "\nENDPOINT=BETA_CONFIGURADO\nSCHEDULERS=NO_REGISTRADOS\n"
                        + (ncBoleta ? "NC=07/" + serieNc : "BOLETA=03/" + serieBoleta) + "-" + boletaNueva.getNumero() + "\nESTADO_PREVIO_RESUMEN=PENDIENTE_RESUMEN\n"
                        + "RC=" + r.getIdentificador() + "\nRC_ID=" + resumenId + "\nFECHA_REFERENCIA=" + r.getFechaDocumentos()
                        + "\nDOCUMENTOS_INCLUIDOS=1\nFIRMA=VALIDA_LOCAL\nDIGEST=VALIDO\nXSD=VALIDO\nZIP=VALIDO\nSEND_BILL_MAX=0\n"
                        + (ncBoleta ? "BOLETA_REFERENCIA=03/B501-1\nBOLETA_REFERENCIA_ESTADO=ACEPTADO\nCDR_ORIGINAL=CONSERVADO_EN_RC_ANTERIOR\n"
                        + "NC_NUEVA=SI\nREFERENCIA_XML_NC=VALIDA\nREFERENCIA_XML_RC=VALIDA\nMOTIVO_NC=" + NC_MOTIVO + "\nDESCRIPCION_MOTIVO=" + NC_DESCRIPCION + "\n" : "");
                Files.writeString(evidencia.resolve("precheck.txt"), pre, StandardOpenOption.CREATE_NEW);
                Files.writeString(evidencia.resolve("intento-send-summary.txt"), "RC_ID=" + resumenId + "\nINICIO=" + Instant.now() + "\n", StandardOpenOption.CREATE_NEW);
                exigir(resumenesEnviados.compareAndSet(0, 1), "SEGUNDO_SEND_SUMMARY_PROHIBIDO");
                precheck = true; xmlRcInicial = r.getXmlFirmadoBytes(); resumenInicial = r;
                detalleRcInicial = fotoColumnas(r.getDetalles().getFirst());
                System.out.print(pre);
                try {
                    ticketRecibido = (String) call.proceed();
                    http = "2XX_EXACTO_NO_EXPUESTO_POR_CLIENTE"; soap = "RESPUESTA_NORMAL";
                    return ticketRecibido; // El servicio persiste el ticket antes de consultar; nunca lo inventamos.
                } catch (Throwable e) { registrarFalloRc(e, "SEND_SUMMARY"); throw e; }
            }
            etapa = "BOLETA_RC_PRECHECK_GET_STATUS";
            exigir(resumenesEnviados.get() == 1 && ticketRecibido != null && !ticketRecibido.isBlank()
                    && ticketRecibido.equals(args[3]) && consultasTicket.get() < 3
                    && (consultasTicket.get() == 0 || "98".equals(ultimoEstadoTicket)
                    && System.nanoTime() - ultimaConsultaTicketNanos >= TimeUnit.SECONDS.toNanos(60)), "CONSULTA_TICKET_NO_AUTORIZADA");
            tx.executeWithoutResult(status -> {
                validarBoletaRc(EstadoComprobante.EN_RESUMEN);
                var r = cargarResumenRc();
                exigir(ticketRecibido.equals(r.getTicketSunat()) && (consultasTicket.get() == 0
                        ? r.getEstado() == EstadoResumenDiario.TICKET_GENERADO : r.getEstado() == EstadoResumenDiario.PROCESANDO)
                        && r.getIntentosConsulta() == consultasTicket.get()
                        && !ctx.getBean(ResumenDiarioRepository.class).existsByRucEmisorAndTicketSunatAndIdNot(r.getRucEmisor(), ticketRecibido, resumenId), "TICKET_RC_EMPRESA_NO_COINCIDE");
            });
            int numero = consultasTicket.incrementAndGet();
            Files.writeString(evidencia.resolve("consulta-" + numero + ".txt"), "RC_ID=" + resumenId + "\nTICKET=CORRELACIONADO_SIN_MOSTRAR\n", StandardOpenOption.CREATE_NEW);
            ultimoEstadoTicket = null;
            try {
                var resultado = (SunatDirectSoapClient.EstadoTicketSunat) call.proceed();
                ultimoEstadoTicket = resultado.statusCode(); ultimoZipRc = resultado.contentZip();
                ultimaConsultaTicketNanos = System.nanoTime();
                return resultado; // Conservacion REQUIRES_NEW, parser y finalizacion permanecen productivos.
            } catch (Throwable e) { registrarFalloRc(e, "GET_STATUS"); throw e; }
        }

        private void registrarFalloRc(Throwable e, String operacion) {
            error = e.getClass().getSimpleName();
            soap = e instanceof SunatDirectSoapClient.SunatSoapFaultException ? "FAULT_TECNICO_NO_RECHAZO_FISCAL" : "FALLO_TECNICO_RESULTADO_NO_CONFIRMADO";
            if (e instanceof SunatDirectSoapClient.SunatSoapFaultException f) http = Integer.toString(f.getHttpStatus());
            try { Files.writeString(evidencia.resolve("error-tecnico.txt"), "OPERACION=" + operacion + "\nTIPO=" + error + "\nSOAP=" + soap + "\n", StandardOpenOption.CREATE_NEW); }
            catch (IOException fallo) { evidenciaTecnicaCompleta = false; }
        }

        private void validarXmlResumen(ResumenDiario r) throws Exception {
            byte[] xml = r.getXmlFirmadoBytes();
            exigir(xml != null && xml.length > 0 && r.getHashCpe() != null && !r.getHashCpe().isBlank(), "XML_RC_REQUERIDO");
            ctx.getBean(UblValidatorService.class).validarResumenDiario(xml);
            var doc = Fase5BetaPreparacionTest.parse(xml);
            var raiz = doc.getDocumentElement();
            String cbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
            String cac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
            String sac = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
            exigir("SummaryDocuments".equals(raiz.getLocalName())
                    && "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1".equals(raiz.getNamespaceURI())
                    && "2.0".equals(hijoUnico(raiz, cbc, "UBLVersionID").getTextContent().trim())
                    && "1.1".equals(hijoUnico(raiz, cbc, "CustomizationID").getTextContent().trim())
                    && r.getIdentificador().equals(hijoUnico(raiz, cbc, "ID").getTextContent().trim())
                    && r.getFechaDocumentos().toString().equals(hijoUnico(raiz, cbc, "ReferenceDate").getTextContent().trim())
                    && r.getFechaGeneracion().toString().equals(hijoUnico(raiz, cbc, "IssueDate").getTextContent().trim()), "XML_RC_IDENTIDAD_FECHAS_INVALIDAS");
            var emisor = hijoUnico(raiz, cac, "AccountingSupplierParty");
            exigir(r.getRucEmisor().equals(hijoUnico(emisor, cbc, "CustomerAssignedAccountID").getTextContent().trim()), "XML_RC_EMISOR_INVALIDO");
            exigir(doc.getElementsByTagNameNS(sac, "SummaryDocumentsLine").getLength() == 1, "XML_RC_SOLO_UNA_BOLETA");
            var linea = hijoUnico(raiz, sac, "SummaryDocumentsLine");
            var cliente = hijoUnico(linea, cac, "AccountingCustomerParty");
            var total = hijoUnico(linea, sac, "TotalAmount");
            exigir("1".equals(hijoUnico(linea, cbc, "LineID").getTextContent().trim())
                    && (ncBoleta ? "07" : "03").equals(hijoUnico(linea, cbc, "DocumentTypeCode").getTextContent().trim())
                    && String.format("%s-%08d", ncBoleta ? serieNc : serieBoleta, boletaNueva.getNumero()).equals(hijoUnico(linea, cbc, "ID").getTextContent().trim())
                    && "1".equals(hijoUnico(hijoUnico(linea, cac, "Status"), cbc, "ConditionCode").getTextContent().trim())
                    && "-".equals(hijoUnico(cliente, cbc, "CustomerAssignedAccountID").getTextContent().trim())
                    && "-".equals(hijoUnico(cliente, cbc, "AdditionalAccountID").getTextContent().trim())
                    && "PEN".equals(total.getAttribute("currencyID"))
                    && new BigDecimal(total.getTextContent().trim()).compareTo(boletaNueva.getMontoTotal()) == 0
                    && doc.getElementsByTagNameNS(cac, "BillingReference").getLength() == (ncBoleta ? 1 : 0), "XML_RC_LINEA_NO_COINCIDE");
            if (ncBoleta) {
                var referencia = hijoUnico(hijoUnico(linea, cac, "BillingReference"), cac, "InvoiceDocumentReference");
                exigir("B501-00000001".equals(hijoUnico(referencia, cbc, "ID").getTextContent().trim())
                        && "03".equals(hijoUnico(referencia, cbc, "DocumentTypeCode").getTextContent().trim()), "XML_RC_REFERENCIA_BOLETA_INVALIDA");
            }
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(r.getRucEmisor()).orElseThrow();
            validarEmpresa(empresa);
            ctx.getBean(SunatPlazoService.class).validarResumenDiario(r.getFechaDocumentos());
            validarFirmaDigest(doc, r.getHashCpe(), empresa);
        }

        private void informarResumen() throws Exception {
            // Fuera del flujo de red/commit: no puede impedir que el service persista el ticket o CDR.
            // El ticket no es una credencial; conservarlo localmente sin imprimir texto externo.
            if (ticketRecibido != null) Files.writeString(evidencia.resolve("ticket-sunat.txt"), ticketRecibido, StandardOpenOption.CREATE_NEW);
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
            var r = tx.execute(status -> {
                if (ncBoleta) validarBoletaReferenciaAceptada(); else validarAnterioresRc();
                if (ctx.getBean(ResumenDiarioRepository.class).count() == (ncBoleta ? 1 : 0)) return null;
                return cargarResumenRc();
            });
            var c = tx.execute(status -> {
                var actual = ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(id, "20100066603").orElseThrow();
                return validarBoletaRc(actual.getEstadoSfs());
            });
            var recibidas = r == null ? List.<RespuestaSunatRecibida>of() : ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc("20100066603", RespuestaSunatRecibida.Flujo.RC, resumenId);
            exigir(r == null || r.getTicketSunat() == null || Objects.equals(ticketRecibido, r.getTicketSunat()), "TICKET_FINAL_NO_COINCIDE");
            for (var recibida : recibidas) {
                exigir(Objects.equals(ticketRecibido, recibida.getTicketSunat()), "RECEPCION_RC_TICKET_NO_COINCIDE");
                Files.write(evidencia.resolve("respuesta-rc-" + recibida.getId() + ".zip"), recibida.getRespuestaZipBytes(), StandardOpenOption.CREATE_NEW);
            }
            boolean finalConCdr = r != null && r.getCdrZipBytes() != null && r.getCdrXmlBytes() != null;
            String resultado = resumenesEnviados.get() == 0 ? "BLOQUEADO_ANTES_DE_ENVIAR" : "PENDIENTE_REVISION_NO_REENVIAR";
            if (r != null && r.getEstado() == EstadoResumenDiario.ERROR_ENVIO_INCIERTO) resultado = "ENVIO_INCIERTO";
            if (r != null && r.getEstado() == EstadoResumenDiario.PROCESANDO) resultado = "TICKET_EN_PROCESO_LIMITE_ALCANZADO";
            if (finalConCdr) {
                exigir(Arrays.equals(ultimoZipRc, r.getCdrZipBytes()) && recibidas.stream().anyMatch(x -> Arrays.equals(x.getRespuestaZipBytes(), r.getCdrZipBytes())), "CDR_RC_NO_CONSERVADO");
                Files.write(evidencia.resolve("cdr-recibido.zip"), r.getCdrZipBytes(), StandardOpenOption.CREATE_NEW);
                Files.write(evidencia.resolve("cdr-procesado.xml"), r.getCdrXmlBytes(), StandardOpenOption.CREATE_NEW);
                if ("NINGUNO".equals(error) && (r.getEstado() == EstadoResumenDiario.ACEPTADO || r.getEstado() == EstadoResumenDiario.ACEPTADO_CON_OBSERVACION)) {
                    exigir(c.getEstadoSfs().name().equals(r.getEstado().name()), "ESTADOS_RC_BOLETA_NO_COINCIDEN");
                    resultado = "ACEPTADO_BETA";
                } else if (r.getEstado() == EstadoResumenDiario.RECHAZADO) {
                    exigir(c.getEstadoSfs() == EstadoComprobante.EN_RESUMEN, "BOLETA_RECHAZO_RC_ESTADO_INVALIDO");
                    resultado = "RECHAZADO_BETA";
                }
            }
            if (ultimoZipRc != null && ultimoZipRc.length > 0 && !finalConCdr)
                Files.write(evidencia.resolve("respuesta-no-finalizada.zip"), ultimoZipRc, StandardOpenOption.CREATE_NEW);
            String salida = (ncBoleta ? "MODO=NC_BOLETA_RC\nNC_ID=" + id + "\nNC=07/" + serieNc : "MODO=BOLETA_RC\nBOLETA_ID=" + id + "\nBOLETA=03/" + serieBoleta) + "-" + c.getNumero()
                    + "\nRC_ID=" + (r == null ? "NO_CREADO" : resumenId) + "\nRC=" + (r == null ? "NO_CREADO" : r.getIdentificador())
                    + "\nTICKET_RECIBIDO=" + (ticketRecibido == null ? "NO" : "SI_SIN_MOSTRAR")
                    + "\nTICKET_PERSISTIDO=" + (r != null && r.getTicketSunat() != null ? "SI_CORRELACIONADO" : "NO")
                    + "\nSEND_BILL_INVOCACIONES=" + invocaciones.get() + "\nSEND_SUMMARY_INVOCACIONES=" + resumenesEnviados.get()
                    + "\nGET_STATUS_INVOCACIONES=" + consultasTicket.get() + "\nSEND_SUMMARY_RETRIES=0\nGET_STATUS_MAX=3\n"
                    + "ESTADO_RC=" + (r == null ? "NO_CREADO" : r.getEstado()) + (ncBoleta ? "\nESTADO_NC=" : "\nESTADO_BOLETA=") + c.getEstadoSfs()
                    + "\nCDR_RECIBIDO=" + (ultimoZipRc != null && ultimoZipRc.length > 0 ? "SI" : "NO")
                    + "\nCDR_FINAL_PERSISTIDO=" + (finalConCdr ? "SI" : "NO")
                    + "\nRESPONSE_CODE=" + (r == null || r.getCodigoRespuestaSunat() == null ? "NO_DISPONIBLE" : r.getCodigoRespuestaSunat().replaceAll("[^A-Za-z0-9_.:-]", ""))
                    + "\nERROR_TIPO=" + error + "\nDOCUMENTOS_ANTERIORES=INTACTOS_VERIFICADOS\nSEGUNDO_ENVIO=NO\nRESULTADO=" + resultado + "\n";
            if (ncBoleta) salida += "BOLETA_REFERENCIA=03/B501-1\nRC_ANTERIOR=RC-20261005-1_CONSERVADO\nAUDITORIAS_ANTERIORES=INTACTAS_VERIFICADAS\n";
            Files.writeString(evidencia.resolve("resultado.txt"), salida, StandardOpenOption.CREATE_NEW);
            System.out.print(salida);
        }

        private void validarEmpresa(Empresa empresa) {
            exigir("20100066603".equals(empresa.getRuc()) && "FASE 5 BETA PRUEBA NO PRODUCTIVA".equals(empresa.getRazonSocial())
                    && empresa.getEstadoPlataforma() == EstadoEmpresa.ACTIVA && empresa.tieneConfiguracionSunat()
                    && empresa.igvPorcentajeEfectivo().compareTo(new BigDecimal("18.00")) == 0, "EMPRESA_BETA_NO_COINCIDE");
            var crypto = ctx.getBean(CryptoService.class);
            boolean credencialesBeta = "MODDATOS".equals(crypto.descifrarUsuarioSol(empresa.getRuc(), empresa.getUsuarioSol()))
                    && "MODDATOS".equals(crypto.descifrarClaveSol(empresa.getRuc(), empresa.getClaveSol()));
            if (!credencialesBeta) System.out.println("BLOQUEADO_POR_RESTRICCION_BETA=CREDENCIALES_PERSISTIDAS_NO_COINCIDEN_CON_ESCENARIO_DOCUMENTADO");
            exigir(credencialesBeta, "BLOQUEADO_POR_RESTRICCION_BETA_CREDENCIALES");
            try { certificado(empresa); } // Validacion interna, nunca regeneracion ni impresion de PFX/password.
            catch (Exception e) { throw new IllegalStateException("CERTIFICADO_BETA_NO_CARGABLE"); }
        }

        private X509Certificate certificado(Empresa empresa) throws Exception {
            var crypto = ctx.getBean(CryptoService.class);
            byte[] pfx = crypto.descifrarPfx(empresa.getRuc(), empresa.getPfxBytes());
            String password = crypto.descifrarPfxPassword(empresa.getRuc(), empresa.getPfxPassword());
            try {
                ctx.getBean(XmlSignerService.class).validarPfx(pfx, password, empresa.getRuc());
                var ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(pfx), password.toCharArray());
                exigir(ks.size() == 1 && ks.isKeyEntry("fase5-beta") && ks.getKey("fase5-beta", password.toCharArray()) != null, "CERTIFICADO_BETA_NO_CARGABLE");
                var cert = (X509Certificate) ks.getCertificate("fase5-beta"); cert.checkValidity(); cert.verify(cert.getPublicKey());
                exigir(((RSAPublicKey) cert.getPublicKey()).getModulus().bitLength() == 2048
                        && cert.getSubjectX500Principal().getName().contains("OU=" + empresa.getRuc()), "CERTIFICADO_BETA_INCOHERENTE");
                return cert;
            } finally { Arrays.fill(pfx, (byte) 0); }
        }

        private void validarPrimeraAceptada() {
            var c = ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(1L, "20100066603").orElseThrow();
            exigir("01".equals(c.getTipoComprobante()) && "F501".equals(c.getSerie())
                    && Integer.valueOf(1).equals(c.getNumero()) && c.getEstadoSfs() == EstadoComprobante.ACEPTADO
                    && "0".equals(c.getCodigoRespuestaSunat()) && c.getCdrZipBytes() != null && c.getCdrZipBytes().length > 0
                    && c.getCdrXmlBytes() != null && c.getCdrXmlBytes().length > 0
                    && c.getXmlFirmadoBytes() != null && c.getXmlFirmadoBytes().length > 0
                    && c.getHashCpe() != null && !c.getHashCpe().isBlank(), "PRIMERA_FACTURA_NO_ACREDITADA");
            var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc("20100066603", RespuestaSunatRecibida.Flujo.CPE, 1L);
            exigir(recibidas.size() == 1 && Arrays.equals(recibidas.getFirst().getRespuestaZipBytes(), c.getCdrZipBytes()),
                    "RECEPCION_PRIMERA_NO_COINCIDE");
            if (primeraAceptada != null) exigir(Objects.equals(primeraAceptada.getNombreArchivoXml(), c.getNombreArchivoXml())
                    && primeraAceptada.getHashCpe().equals(c.getHashCpe())
                    && Objects.equals(primeraAceptada.getFechaEmision(), c.getFechaEmision())
                    && Objects.equals(primeraAceptada.getCreatedAt(), c.getCreatedAt())
                    && Objects.equals(primeraAceptada.getUpdatedAt(), c.getUpdatedAt())
                    && Objects.equals(primeraAceptada.getMensajeSunat(), c.getMensajeSunat())
                    && Arrays.equals(primeraAceptada.getXmlFirmadoBytes(), c.getXmlFirmadoBytes())
                    && Arrays.equals(primeraAceptada.getCdrZipBytes(), c.getCdrZipBytes())
                    && Arrays.equals(primeraAceptada.getCdrXmlBytes(), c.getCdrXmlBytes())
                    && Objects.equals(respuestaPrimera.getId(), recibidas.getFirst().getId())
                    && Objects.equals(respuestaPrimera.getSha256(), recibidas.getFirst().getSha256())
                    && Objects.equals(respuestaPrimera.getRecibidoEn(), recibidas.getFirst().getRecibidoEn()), "ID_1_ALTERADO_DETENER");
            if (primeraAceptada == null) { primeraAceptada = c; respuestaPrimera = recibidas.getFirst(); }
        }

        private void validarPreparacionSegunda() {
            validarPrimeraAceptada();
            exigir(ctx.getBean(ComprobanteRepository.class).count() == 1
                    && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 1, "DOCUMENTOS_PREVIOS_NO_REPETIR");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 1 && "20100066603".equals(solicitudes.getFirst().getRucEmisor())
                    && "FASE5-PRIMERA-FACTURA-BETA-UNICA".equals(solicitudes.getFirst().getIdempotencyKey())
                    && Long.valueOf(1).equals(solicitudes.getFirst().getComprobanteId())
                    && "CREADO".equals(solicitudes.getFirst().getEstado()), "IDEMPOTENCIA_SEGUNDA_YA_EXISTENTE_O_AJENA");
            var secuencias = ctx.getBean(SecuenciaCorrelativoRepository.class).findAll();
            exigir(secuencias.size() == 1 && "20100066603".equals(secuencias.getFirst().getRucEmisor())
                    && "01".equals(secuencias.getFirst().getTipoComprobante()) && "F501".equals(secuencias.getFirst().getSerie())
                    && secuencias.getFirst().getUltimoNumero() != null
                    && secuencias.getFirst().getUltimoNumero() >= primeraAceptada.getNumero(), "SECUENCIA_PREVIA_NO_COINCIDE");
            if (ultimoNumeroAntes != null) exigir(ultimoNumeroAntes.equals(secuencias.getFirst().getUltimoNumero()), "SECUENCIA_CAMBIO_DETENER");
            else ultimoNumeroAntes = secuencias.getFirst().getUltimoNumero(); // Solo lectura, nunca fija el numero del request.
            exigir(invocaciones.get() == 0 && workers.get() == 0, "SEGUNDA_EJECUCION_NO_REPETIR");
        }

        private void validarSegundaEnTransaccion(EstadoComprobante estado) {
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class));
            tx.setReadOnly(true);
            tx.executeWithoutResult(status -> validarSegunda(estado));
        }

        private void validarSegunda(EstadoComprobante estado) {
            validarConfiguracion(env);
            exigir("true".equals(env.getProperty("FASE5_MAIN_DETENIDO"))
                    && "true".equals(env.getProperty("FASE5_AUTORIZAR_UN_SEND_BILL")) && invocaciones.get() == 0, "NO_REENVIAR");
            validarPrimeraAceptada();
            try { rutaExterna(evidencia.toString()); }
            catch (IOException e) { throw new IllegalStateException("EVIDENCIA_SEGUNDA_NO_SEGURA"); }
            exigir(Files.isDirectory(evidencia, LinkOption.NOFOLLOW_LINKS), "EVIDENCIA_SEGUNDA_REQUERIDA");
            exigir(id != null && id != 1L, "ID_1_PROHIBIDO");
            var repos = ctx.getBean(ComprobanteRepository.class);
            var c = repos.findByIdAndRucEmisor(id, "20100066603").orElseThrow();
            exigir(repos.count() == 2 && c.getEstadoSfs() == estado && "01".equals(c.getTipoComprobante())
                    && "F501".equals(c.getSerie()) && c.getNumero() != null && ultimoNumeroAntes != null
                    && c.getNumero() == ultimoNumeroAntes + 1 && c.getNumero() > primeraAceptada.getNumero()
                    && "PEN".equals(c.getMoneda()) && "0101".equals(c.getTipoOperacion()) && "Contado".equals(c.getFormaPago())
                    && "6".equals(c.getTipoDocCliente()) && "20100066603".equals(c.getNumDocCliente())
                    && c.getMontoTotal().compareTo(new BigDecimal("11.80")) == 0, "NUEVA_FACTURA_NO_COINCIDE");
            exigir(c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && c.getCodigoRespuestaSunat() == null
                    && c.getMensajeSunat() == null && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 1
                    && ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).isEmpty(),
                    "RESPUESTA_NUEVA_PREVIA_NO_REENVIAR");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 2 && solicitudes.stream().anyMatch(s -> "20100066603".equals(s.getRucEmisor())
                    && SEGUNDA_FACTURA_KEY.equals(s.getIdempotencyKey()) && id.equals(s.getComprobanteId())
                    && "CREADO".equals(s.getEstado())), "IDENTIDAD_SEGUNDA_NO_COINCIDE");
            for (String marcador : List.of("intento.txt", "error-tecnico.txt", "cdr-recibido.zip", "cdr-procesado.xml", "resultado.txt")) {
                exigir(!Files.exists(evidencia.resolve(marcador), LinkOption.NOFOLLOW_LINKS), "ENVIO_NUEVO_PREVIO_NO_REENVIAR");
            }
            exigir(!Objects.equals(c.getNombreArchivoXml(), primeraAceptada.getNombreArchivoXml())
                    && !Objects.equals(c.getHashCpe(), primeraAceptada.getHashCpe())
                    && !Arrays.equals(c.getXmlFirmadoBytes(), primeraAceptada.getXmlFirmadoBytes()), "XML_DE_ID_1_NO_REUTILIZAR");
            if (nuevaFactura != null) exigir(nuevaFactura.getNumero().equals(c.getNumero())
                    && nuevaFactura.getNombreArchivoXml().equals(c.getNombreArchivoXml())
                    && nuevaFactura.getHashCpe().equals(c.getHashCpe())
                    && Arrays.equals(nuevaFactura.getXmlFirmadoBytes(), c.getXmlFirmadoBytes()), "NUEVA_FACTURA_ALTERADA");
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow();
            validarEmpresa(empresa);
            ctx.getBean(SunatPlazoService.class).validarEnvioIndividual(c.getFechaEmision());
            try { validarXmlPersistido(c, empresa); }
            catch (Exception e) { throw new IllegalStateException("XML_NUEVO_NO_VALIDO"); }
            if (nuevaFactura == null) nuevaFactura = c;
        }

        private void validarFacturaReferencia() {
            var c = ctx.getBean(ComprobanteRepository.class)
                    .findByRucEmisorAndTipoComprobanteAndSerieAndNumero("20100066603", "01", "F501", 2).orElseThrow();
            exigir(c.getId() != 1L && c.getEstadoSfs() == EstadoComprobante.ACEPTADO
                    && "0".equals(c.getCodigoRespuestaSunat()) && c.getXmlFirmadoBytes() != null && c.getXmlFirmadoBytes().length > 0
                    && c.getHashCpe() != null && !c.getHashCpe().isBlank() && c.getCdrZipBytes() != null && c.getCdrXmlBytes() != null
                    && "6".equals(c.getTipoDocCliente()) && "20100066603".equals(c.getNumDocCliente())
                    && "RECEPTOR FICTICIO DE ENSAYO BETA".equals(c.getRznSocialCliente())
                    && c.getMontoTotal().compareTo(new BigDecimal("11.80")) == 0 && c.getIgvPorcentaje() != null
                    && c.getIgvPorcentaje().compareTo(new BigDecimal("18.00")) == 0, "FACTURA_REFERENCIA_NO_APTA");
            var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, c.getId());
            exigir(recibidas.size() == 1 && Arrays.equals(recibidas.getFirst().getRespuestaZipBytes(), c.getCdrZipBytes()), "CDR_REFERENCIA_NO_CONSERVADO");
            var cdr = ctx.getBean(CdrParserService.class).procesar(c.getCdrZipBytes());
            exigir(cdr.aceptado() && "0".equals(cdr.responseCode()) && Arrays.equals(cdr.xml(), c.getCdrXmlBytes())
                    && Set.of("F501-2", "F501-00000002").contains(cdr.referenceId()), "CDR_REFERENCIA_NO_VALIDO");
            if (facturaReferencia != null) exigir(facturaReferencia.getId().equals(c.getId())
                    && Objects.equals(facturaReferencia.getNombreArchivoXml(), c.getNombreArchivoXml())
                    && facturaReferencia.getHashCpe().equals(c.getHashCpe())
                    && Objects.equals(facturaReferencia.getFechaEmision(), c.getFechaEmision())
                    && Objects.equals(facturaReferencia.getCreatedAt(), c.getCreatedAt())
                    && Objects.equals(facturaReferencia.getUpdatedAt(), c.getUpdatedAt())
                    && Objects.equals(facturaReferencia.getMensajeSunat(), c.getMensajeSunat())
                    && Arrays.equals(facturaReferencia.getXmlFirmadoBytes(), c.getXmlFirmadoBytes())
                    && Arrays.equals(facturaReferencia.getCdrZipBytes(), c.getCdrZipBytes())
                    && Arrays.equals(facturaReferencia.getCdrXmlBytes(), c.getCdrXmlBytes())
                    && respuestaReferencia.getId().equals(recibidas.getFirst().getId())
                    && Objects.equals(respuestaReferencia.getSha256(), recibidas.getFirst().getSha256())
                    && Objects.equals(respuestaReferencia.getRecibidoEn(), recibidas.getFirst().getRecibidoEn()), "FACTURA_REFERENCIA_ALTERADA");
            if (facturaReferencia == null) { facturaReferencia = c; respuestaReferencia = recibidas.getFirst(); }
        }

        private void validarPreparacionNc() {
            try { serieNc = datosNotaCreditoConfirmados().get("series").get("ncFactura").asText(); }
            catch (IOException e) { throw new IllegalStateException("ESCENARIO_NC_NO_CARGABLE"); }
            validarPrimeraAceptada(); validarFacturaReferencia();
            exigir(ctx.getBean(ComprobanteRepository.class).count() == 2
                    && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 2, "NC_PREVIA_O_DOCUMENTOS_AJENOS");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 2 && solicitudes.stream().anyMatch(s -> "20100066603".equals(s.getRucEmisor())
                    && "FASE5-PRIMERA-FACTURA-BETA-UNICA".equals(s.getIdempotencyKey()) && Long.valueOf(1).equals(s.getComprobanteId()))
                    && solicitudes.stream().anyMatch(s -> "20100066603".equals(s.getRucEmisor())
                    && SEGUNDA_FACTURA_KEY.equals(s.getIdempotencyKey()) && facturaReferencia.getId().equals(s.getComprobanteId()))
                    && solicitudes.stream().allMatch(s -> "CREADO".equals(s.getEstado())), "IDEMPOTENCIA_NC_PREVIA_O_AJENA");
            var secuencias = ctx.getBean(SecuenciaCorrelativoRepository.class).findAll().stream()
                    .filter(s -> "20100066603".equals(s.getRucEmisor()) && "07".equals(s.getTipoComprobante()) && serieNc.equals(s.getSerie())).toList();
            exigir(secuencias.size() <= 1 && (secuencias.isEmpty()
                    || secuencias.getFirst().getUltimoNumero() != null && secuencias.getFirst().getUltimoNumero() >= 0), "SECUENCIA_NC_NO_VALIDA");
            int ultimo = secuencias.isEmpty() ? 0 : secuencias.getFirst().getUltimoNumero();
            if (ultimoNcAntes != null) exigir(ultimoNcAntes == ultimo, "SECUENCIA_NC_CAMBIO_DETENER");
            else ultimoNcAntes = ultimo; // Solo observacion: el servicio productivo inicializa/incrementa.
            exigir(invocaciones.get() == 0 && workers.get() == 0, "NC_NO_REPETIR");
        }

        private void validarNcEnTransaccion(EstadoComprobante estado) {
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
            tx.executeWithoutResult(status -> validarNc(estado));
        }

        private void validarNc(EstadoComprobante estado) {
            validarConfiguracion(env);
            exigir("true".equals(env.getProperty("FASE5_MAIN_DETENIDO"))
                    && "true".equals(env.getProperty("FASE5_AUTORIZAR_UN_SEND_BILL")) && invocaciones.get() == 0, "NO_REENVIAR");
            validarPrimeraAceptada(); validarFacturaReferencia();
            exigir(id != null && id != 1L && !id.equals(facturaReferencia.getId()), "SOLO_NC_NUEVA");
            var repos = ctx.getBean(ComprobanteRepository.class);
            var c = repos.findByIdAndRucEmisor(id, "20100066603").orElseThrow();
            exigir(repos.count() == 3 && c.getEstadoSfs() == estado && "07".equals(c.getTipoComprobante())
                    && serieNc.equals(c.getSerie()) && c.getNumero() != null && ultimoNcAntes != null && c.getNumero() == ultimoNcAntes + 1
                    && "01".equals(c.getTipoComprobanteAfectado()) && "F501".equals(c.getSerieComprobanteAfectado())
                    && Integer.valueOf(2).equals(c.getNumeroComprobanteAfectado()) && NC_MOTIVO.equals(c.getCodigoMotivoNota())
                    && NC_DESCRIPCION.equals(c.getDescripcionMotivoNota()) && "PEN".equals(c.getMoneda())
                    && "0101".equals(c.getTipoOperacion()) && "Contado".equals(c.getFormaPago())
                    && Objects.equals(c.getTipoDocCliente(), facturaReferencia.getTipoDocCliente())
                    && Objects.equals(c.getNumDocCliente(), facturaReferencia.getNumDocCliente())
                    && Objects.equals(c.getRznSocialCliente(), facturaReferencia.getRznSocialCliente())
                    && c.getOpGravada().compareTo(new BigDecimal("1.00")) == 0 && c.getIgv().compareTo(new BigDecimal("0.18")) == 0
                    && c.getMontoTotal().compareTo(new BigDecimal("1.18")) == 0
                    && c.getMontoTotal().compareTo(facturaReferencia.getMontoTotal()) < 0, "NC_NUEVA_NO_COINCIDE");
            exigir(c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && c.getCodigoRespuestaSunat() == null
                    && c.getMensajeSunat() == null && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 2
                    && ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, id).isEmpty(), "NC_YA_RECIBIDA_NO_REENVIAR");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 3 && solicitudes.stream().anyMatch(s -> "20100066603".equals(s.getRucEmisor())
                    && NC_FACTURA_KEY.equals(s.getIdempotencyKey()) && id.equals(s.getComprobanteId())
                    && "CREADO".equals(s.getEstado())), "IDEMPOTENCIA_NC_NO_COINCIDE");
            try { rutaExterna(evidencia.toString()); }
            catch (IOException e) { throw new IllegalStateException("EVIDENCIA_NC_NO_SEGURA"); }
            exigir(Files.isDirectory(evidencia, LinkOption.NOFOLLOW_LINKS), "EVIDENCIA_NC_REQUERIDA");
            for (String marcador : List.of("intento.txt", "error-tecnico.txt", "cdr-recibido.zip", "cdr-procesado.xml", "resultado.txt"))
                exigir(!Files.exists(evidencia.resolve(marcador), LinkOption.NOFOLLOW_LINKS), "NC_ENVIO_PREVIO_NO_REENVIAR");
            exigir(!Objects.equals(c.getNombreArchivoXml(), facturaReferencia.getNombreArchivoXml())
                    && !Objects.equals(c.getHashCpe(), facturaReferencia.getHashCpe())
                    && !Arrays.equals(c.getXmlFirmadoBytes(), facturaReferencia.getXmlFirmadoBytes()), "NC_NO_REUTILIZAR_XML_ORIGINAL");
            if (nuevaNc != null) exigir(nuevaNc.getNumero().equals(c.getNumero())
                    && nuevaNc.getNombreArchivoXml().equals(c.getNombreArchivoXml()) && nuevaNc.getHashCpe().equals(c.getHashCpe())
                    && Arrays.equals(nuevaNc.getXmlFirmadoBytes(), c.getXmlFirmadoBytes()), "NC_ALTERADA_DETENER");
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow(); validarEmpresa(empresa);
            ctx.getBean(SunatPlazoService.class).validarEnvioIndividual(c.getFechaEmision());
            try { validarXmlPersistido(c, empresa); }
            catch (Exception e) { throw new IllegalStateException("XML_NC_NO_VALIDO"); }
            if (nuevaNc == null) nuevaNc = c;
        }

        private void validarContinuacionEnTransaccion(EstadoComprobante estado) {
            var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class));
            tx.setReadOnly(true);
            tx.executeWithoutResult(status -> validarContinuacion(estado));
        }

        private void validarContinuacion(EstadoComprobante estado) {
            validarConfiguracion(env);
            exigir("true".equals(env.getProperty("FASE5_MAIN_DETENIDO"))
                    && "true".equals(env.getProperty("FASE5_AUTORIZAR_UN_SEND_BILL")) && invocaciones.get() == 0, "NO_REENVIAR");
            try { validarEvidenciaSinEnvio(evidenciaAnterior, evidencia); }
            catch (IOException e) { throw new IllegalStateException("EVIDENCIA_ANTERIOR_NO_VERIFICABLE"); }
            var repos = ctx.getBean(ComprobanteRepository.class);
            var c = repos.findByIdAndRucEmisor(1L, "20100066603").orElseThrow();
            exigir(Long.valueOf(1).equals(id) && repos.count() == 1 && c.getEstadoSfs() == estado
                    && "01".equals(c.getTipoComprobante()) && "F501".equals(c.getSerie())
                    && c.getNumero() != null && c.getNumero() > 0 && "PEN".equals(c.getMoneda())
                    && "0101".equals(c.getTipoOperacion()) && "Contado".equals(c.getFormaPago())
                    && "6".equals(c.getTipoDocCliente()) && "20100066603".equals(c.getNumDocCliente())
                    && c.getMontoTotal().compareTo(new BigDecimal("11.80")) == 0, "FACTURA_EXISTENTE_NO_COINCIDE");
            exigir(c.getCdrZipBytes() == null && c.getCdrXmlBytes() == null && c.getCodigoRespuestaSunat() == null
                    && c.getMensajeSunat() == null && ctx.getBean(RespuestaSunatRecibidaRepository.class).count() == 0,
                    "RESPUESTA_O_CDR_PREVIO_NO_REENVIAR");
            var solicitudes = ctx.getBean(SolicitudEmisionRepository.class).findAll();
            exigir(solicitudes.size() == 1 && "20100066603".equals(solicitudes.getFirst().getRucEmisor())
                    && "FASE5-PRIMERA-FACTURA-BETA-UNICA".equals(solicitudes.getFirst().getIdempotencyKey())
                    && Long.valueOf(1).equals(solicitudes.getFirst().getComprobanteId())
                    && "CREADO".equals(solicitudes.getFirst().getEstado()), "IDEMPOTENCIA_EXISTENTE_NO_COINCIDE");
            if (facturaExistente != null) exigir(facturaExistente.getNumero().equals(c.getNumero())
                    && facturaExistente.getNombreArchivoXml().equals(c.getNombreArchivoXml())
                    && facturaExistente.getHashCpe().equals(c.getHashCpe())
                    && facturaExistente.getFechaEmision().equals(c.getFechaEmision())
                    && Arrays.equals(facturaExistente.getXmlFirmadoBytes(), c.getXmlFirmadoBytes()), "FACTURA_EXISTENTE_ALTERADA");
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow();
            validarEmpresa(empresa);
            ctx.getBean(SunatPlazoService.class).validarEnvioIndividual(c.getFechaEmision());
            try { validarXmlPersistido(c, empresa); }
            catch (Exception e) { throw new IllegalStateException("XML_EXISTENTE_NO_VALIDO"); }
            if (facturaExistente == null) facturaExistente = c;
        }

        private void validarXmlPersistido(Comprobante c, Empresa empresa) throws Exception {
            byte[] xml = c.getXmlFirmadoBytes();
            exigir(xml != null && xml.length > 0 && c.getHashCpe() != null && !c.getHashCpe().isBlank(), "XML_DIGEST_EXISTENTES_REQUERIDOS");
            String base = String.format("%s-%s-%s-%08d", c.getRucEmisor(), c.getTipoComprobante(), c.getSerie(), c.getNumero());
            exigir((base + ".xml").equals(c.getNombreArchivoXml()), "NOMBRE_ARCHIVO_INCOHERENTE");
            if (notaCredito || ncBoleta) ctx.getBean(UblValidatorService.class).validarNotaCredito(xml);
            else ctx.getBean(UblValidatorService.class).validarFactura(xml);
            var doc = Fase5BetaPreparacionTest.parse(xml);
            if (notaCredito || ncBoleta) validarReferenciaXmlNc(doc.getDocumentElement(), c);
            validarFirmaDigest(doc, c.getHashCpe(), empresa);
        }

        private void validarFirmaDigest(org.w3c.dom.Document doc, String hash, Empresa empresa) throws Exception {
            exigir(doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").getLength() == 1, "FIRMA_NO_UNICA");
            var firma = new XMLSignature((Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0), "", true);
            exigir(firma.checkSignatureValue(certificado(empresa))
                    && hash.equals(Base64.getEncoder().encodeToString(firma.getSignedInfo().item(0).getDigestValue())), "FIRMA_DIGEST_INVALIDOS");
        }

        private static Element hijoUnico(Element padre, String ns, String nombre) {
            Element encontrado = null;
            for (var nodo = padre.getFirstChild(); nodo != null; nodo = nodo.getNextSibling()) {
                if (nodo instanceof Element e && ns.equals(e.getNamespaceURI()) && nombre.equals(e.getLocalName())) {
                    exigir(encontrado == null, "REFERENCIA_XML_NC_AMBIGUA"); encontrado = e;
                }
            }
            exigir(encontrado != null, "REFERENCIA_XML_NC_AUSENTE"); return encontrado;
        }

        private void validarReferenciaXmlNc(Element raiz, Comprobante c) {
            String cac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
            String cbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
            exigir("CreditNote".equals(raiz.getLocalName())
                    && "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2".equals(raiz.getNamespaceURI())
                    && String.format("%s-%08d", serieNc, c.getNumero()).equals(hijoUnico(raiz, cbc, "ID").getTextContent().trim()), "XML_NC_IDENTIDAD_INVALIDA");
            var discrepancia = hijoUnico(raiz, cac, "DiscrepancyResponse");
            var referencia = hijoUnico(hijoUnico(raiz, cac, "BillingReference"), cac, "InvoiceDocumentReference");
            String documentoOriginal = ncBoleta ? "B501-1" : "F501-2";
            exigir(documentoOriginal.equals(hijoUnico(discrepancia, cbc, "ReferenceID").getTextContent().trim())
                    && NC_MOTIVO.equals(hijoUnico(discrepancia, cbc, "ResponseCode").getTextContent().trim())
                    && NC_DESCRIPCION.equals(hijoUnico(discrepancia, cbc, "Description").getTextContent().trim())
                    && documentoOriginal.equals(hijoUnico(referencia, cbc, "ID").getTextContent().trim())
                    && (ncBoleta ? "03" : "01").equals(hijoUnico(referencia, cbc, "DocumentTypeCode").getTextContent().trim()), "REFERENCIA_XML_NC_INVALIDA");
        }

        private void precheckInmediato(Object[] args) throws Exception {
            validarConfiguracion(env);
            exigir("true".equals(env.getProperty("FASE5_MAIN_DETENIDO")) && invocaciones.get() == 0, "NO_REENVIAR");
            if (evidenciaAnterior != null) validarContinuacionEnTransaccion(EstadoComprobante.ENVIANDO);
            if (segunda) validarSegundaEnTransaccion(EstadoComprobante.ENVIANDO);
            if (notaCredito) validarNcEnTransaccion(EstadoComprobante.ENVIANDO);
            if (facturaRa) {
                var tx = new TransactionTemplate(ctx.getBean(JpaTransactionManager.class)); tx.setReadOnly(true);
                tx.executeWithoutResult(status -> validarFacturaDedicada(EstadoComprobante.ENVIANDO, false));
            }
            var c = ctx.getBean(ComprobanteRepository.class).findByIdAndRucEmisor(id, "20100066603").orElseThrow();
            exigir(ctx.getBean(ComprobanteRepository.class).count() == (facturaRa ? idsHistoricos.size() + 1 : notaCredito ? 3 : segunda ? 2 : 1) && c.getEstadoSfs() == EstadoComprobante.ENVIANDO
                    && (notaCredito ? "07" : "01").equals(c.getTipoComprobante())
                    && (notaCredito ? serieNc : "F501").equals(c.getSerie()) && "PEN".equals(c.getMoneda())
                    && "6".equals(c.getTipoDocCliente()) && "20100066603".equals(c.getNumDocCliente())
                    && c.getMontoTotal().compareTo(new BigDecimal(notaCredito ? "1.18" : "11.80")) == 0, "DOCUMENTO_UNICO_NO_COINCIDE");
            var empresa = ctx.getBean(EmpresaRepository.class).findByRuc(c.getRucEmisor()).orElseThrow();
            validarEmpresa(empresa);
            try (var conexion = ds.getConnection()) { validarConexion(conexion); }
            byte[] xml = c.getXmlFirmadoBytes(), zip = (byte[]) args[4];
            String base = String.format("%s-%s-%s-%08d", c.getRucEmisor(), c.getTipoComprobante(), c.getSerie(), c.getNumero());
            exigir(c.getNombreArchivoXml().equals(base + ".xml") && (base + ".zip").equals(args[3])
                    && c.getRucEmisor().equals(args[0]), "NOMBRE_ARCHIVO_INCOHERENTE");
            validarXmlPersistido(c, empresa); // Valida bytes persistidos; nunca construye ni firma otro XML.
            if (facturaRa) {
                var doc = Fase5BetaPreparacionTest.parse(xml); var raiz = doc.getDocumentElement();
                String cbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                String cac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
                exigir("Invoice".equals(raiz.getLocalName()) && "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2".equals(raiz.getNamespaceURI())
                        && "2.1".equals(hijoUnico(raiz, cbc, "UBLVersionID").getTextContent().trim())
                        && "01".equals(hijoUnico(raiz, cbc, "InvoiceTypeCode").getTextContent().trim())
                        && String.format("F501-%08d", c.getNumero()).equals(hijoUnico(raiz, cbc, "ID").getTextContent().trim())
                        && c.getRucEmisor().equals(hijoUnico(hijoUnico(hijoUnico(hijoUnico(raiz, cac, "AccountingSupplierParty"), cac, "Party"), cac, "PartyIdentification"), cbc, "ID").getTextContent().trim()), "XML_FACTURA_DEDICADA_IDENTIDAD_INVALIDA");
            }
            // Bytes EXACTOS del ZIP producido por el worker; no se genera otro ZIP para el envio.
            try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
                var entry = in.getNextEntry();
                exigir(entry != null && !entry.isDirectory() && (base + ".xml").equals(entry.getName())
                        && Arrays.equals(xml, org.example.util.LecturaLimitada.leer(in, xml.length)) && in.getNextEntry() == null, "ZIP_INVALIDO");
            }
            String indicadores = "PERFIL=beta\nENTORNO=BETA\nBD=" + BASE + "\nENDPOINT=BETA_CONFIGURADO\nEMPRESA_BETA=CORRECTA\n"
                    + "CREDENCIALES_BETA=CONFIGURADAS\nCERTIFICADO_BETA=CARGABLE\nBOOTSTRAP=OFF\nXML_DEBUG=OFF\n"
                    + "SCHEDULERS=NO_REGISTRADOS\nXML=" + (evidenciaAnterior == null ? "GENERADO" : "REUTILIZADO_PERSISTIDO")
                    + "\nFIRMA=VALIDA_LOCAL\nDIGEST=VALIDO\nXSD=VALIDO\nZIP=VALIDO\n"
                    + (notaCredito ? "NC_ID=" : "FACTURA_ID=") + id + "\nDOCUMENTO=" + c.getTipoComprobante() + "/" + c.getSerie() + "-" + c.getNumero() + "\nTOTAL=" + c.getMontoTotal()
                    + "\nMONEDA=PEN\nEMISOR_BETA=20100066603\nRECEPTOR_FICTICIO=6/20100066603\nCREADO_EN=" + c.getCreatedAt()
                    + "\nHASH_XML=" + c.getHashCpe() + "\nXML_BYTES=" + xml.length + "\nZIP_BYTES=" + zip.length + "\n"
                    + (notaCredito ? "FACTURA_REFERENCIA=F501-2\nFACTURA_REFERENCIA_ESTADO=ACEPTADO\nFACTURA_REFERENCIA_CDR=VALIDO\n"
                    + "NC_NUEVA=SI\nCORRELATIVO_NUEVO=SI\nXML_NC=GENERADO\nREFERENCIA_XML=VALIDA\nMOTIVO_NC=" + NC_MOTIVO
                    + "\nDESCRIPCION_MOTIVO=" + NC_DESCRIPCION + "\n" : "");
            Files.write(evidencia.resolve(base + ".xml"), xml, StandardOpenOption.CREATE_NEW);
            Files.write(evidencia.resolve(base + ".zip"), zip, StandardOpenOption.CREATE_NEW);
            Files.writeString(evidencia.resolve("precheck.txt"), indicadores, StandardOpenOption.CREATE_NEW);
            auditoriasAntes = ctx.getBean(EventoAuditoriaRepository.class).count();
            System.out.print(indicadores);
            precheck = true;
        }

        private void informar(Comprobante c) throws Exception {
            boolean cdr = respuesta != null && respuesta.length > 0;
            var recibidas = ctx.getBean(RespuestaSunatRecibidaRepository.class)
                    .findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(c.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE, c.getId());
            String resultado = invocaciones.get() == 0 ? "BLOQUEADO_ANTES_DE_ENVIAR" : "ENVIO_INCIERTO";
            if (cdr && !recibidas.isEmpty() && c.getCdrZipBytes() != null && c.getCdrXmlBytes() != null && "NINGUNO".equals(error)) {
                if (c.getEstadoSfs() == EstadoComprobante.ACEPTADO || c.getEstadoSfs() == EstadoComprobante.ACEPTADO_CON_OBSERVACION) resultado = "ACEPTADO_BETA";
                else if (c.getEstadoSfs() == EstadoComprobante.RECHAZADO) resultado = "RECHAZADO_BETA";
            }
            // Observacion en archivo DESPUES del worker: un fallo de archivo nunca sustituye
            // la respuesta del cliente ni impide el commit independiente normal antes del parser.
            if (cdr) Files.write(evidencia.resolve("cdr-recibido.zip"), respuesta, StandardOpenOption.CREATE_NEW);
            if (c.getCdrXmlBytes() != null) Files.write(evidencia.resolve("cdr-procesado.xml"), c.getCdrXmlBytes(), StandardOpenOption.CREATE_NEW);
            // No repetir parser ni reinterpretar ResponseCode: informar el resultado persistido por el worker.
            String salida = (facturaRa ? "MODO=FACTURA_PARA_BAJA\nFACTURA_ID=" + c.getId() + "\nHISTORICO=INTACTO_VERIFICADO\n"
                    : notaCredito ? "MODO=NC_FACTURA\nNC_ID=" + c.getId()
                    + "\nNC_NUEVAS=1\nFACTURA_REFERENCIA=01/F501-2\nFACTURAS_ANTERIORES=CONSERVADAS_SIN_CAMBIOS\n"
                    : segunda ? "MODO=SEGUNDA_FACTURA\nFACTURA_ID=" + c.getId()
                    + "\nFACTURAS_NUEVAS=1\nFACTURA_ID_1=CONSERVADA_SIN_CAMBIOS\n"
                    : evidenciaAnterior == null ? "" : "MODO=CONTINUACION\nFACTURA_ID=1\nFACTURAS_NUEVAS=0\nCORRELATIVOS_NUEVOS=0\n")
                    + "PRECHECK_FINAL=" + (precheck ? "VALIDADO" : "NO_COMPLETADO") + "\nSEND_BILL_INVOCACIONES=" + invocaciones.get()
                    + "\nRETRIES=0\nHTTP=" + http + "\nSOAP=" + soap + "\nCDR_RECIBIDO=" + (cdr ? "SI" : "NO")
                    + "\nRESPONSE_CODE=" + (c.getCodigoRespuestaSunat() == null ? "NO_DISPONIBLE" : c.getCodigoRespuestaSunat().replaceAll("[^A-Za-z0-9_.:-]", ""))
                    + "\nESTADO_FINAL=" + c.getEstadoSfs() + "\nRESPUESTA_SUNAT_RECIBIDA=" + (recibidas.isEmpty() ? "NO" : "SI")
                    + "\nDESCRIPCION=" + (c.getMensajeSunat() == null ? "NO_DISPONIBLE" : "CONSERVADA_EN_BD_REQUIERE_REVISION_SANEADA")
                    + "\nAUDITORIA_NUEVA=" + (ctx.getBean(EventoAuditoriaRepository.class).count() > auditoriasAntes ? "SI" : "NO")
                    + "\nEVIDENCIA_TECNICA=" + (evidenciaTecnicaCompleta ? "CONSERVADA_SEGUN_DATOS_EXPUESTOS_POR_CLIENTE" : "ARCHIVO_TECNICO_INCOMPLETO_REVISAR_BD")
                    + "\nERROR_TIPO=" + error + "\nRESULTADO=" + resultado + "\nSEGUNDO_ENVIO=NO\n";
            // Descripcion queda en BD/archivo local, nunca SOAP/cabeceras. No imprimir texto externo no saneado.
            Files.writeString(evidencia.resolve("resultado.txt"), salida, StandardOpenOption.CREATE_NEW);
            System.out.print(salida);
        }
    }

    /** Continuacion de acceso: no DDL/SchemaValidator, empresa/certificado nuevos ni fixtures fiscales.
     * Ejecutar con --preparar-acceso en la MISMA run configuration, una vez acreditado
     * PRECHECK_LOCAL_VALIDADO_SIN_ENVIO. Password solo de FASE5_USUARIO_PASSWORD.
     * La anotacion transaccional de services instanciados directamente no aplica:
     * TransactionTemplate establece los limites reales de creacion, reload y login.
     */
    private static void prepararAcceso(ConfigurableEnvironment env, boolean cambiarPassword) throws Exception {
        etapa = "PASSWORD_ENSAYO_DISPONIBILIDAD";
        String password = passwordEnsayoObligatoria(env);
        String nueva = null;
        if (cambiarPassword) {
            etapa = "PASSWORD_NUEVA_ENSAYO_DISPONIBILIDAD";
            nueva = passwordNuevaEnsayoObligatoria(env);
        }
        final String passwordLogin = cambiarPassword ? nueva : password;
        System.out.println("FASE5_USUARIO_PASSWORD_DISPONIBLE=SI");
        etapa = "ACCESO_CONFIGURACION_SEGURA";
        validarConfiguracion(env);
        var zona = new TimeZoneConfig();
        ReflectionTestUtils.setField(zona, "timeZoneId", env.getRequiredProperty("app.time-zone"));
        zona.configurarZonaHoraria();
        var ds = new DriverManagerDataSource(URL, env.getRequiredProperty("SPRING_DATASOURCE_USERNAME"), env.getRequiredProperty("SPRING_DATASOURCE_PASSWORD"));
        etapa = "ACCESO_CONEXION_BETA";
        try (var c = ds.getConnection()) { validarConexion(c); }
        var opciones = new HashMap<String, Object>();
        opciones.put("hibernate.connection.datasource", ds);
        opciones.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        opciones.put("hibernate.boot.allow_jdbc_metadata_access", false);
        opciones.put("hibernate.hbm2ddl.auto", "none");
        opciones.put("jakarta.persistence.schema-generation.database.action", "none");
        opciones.put("jakarta.persistence.schema-generation.scripts.action", "none");
        opciones.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var registry = new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            var sources = new MetadataSources(registry);
            var scan = new ClassPathScanningCandidateComponentProvider(false);
            scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var entity : scan.findCandidateComponents("org.example")) sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            try (var factory = sources.buildMetadata().buildSessionFactory()) {
                var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var repos = new JpaRepositoryFactory(em);
                var empresas = repos.getRepository(EmpresaRepository.class);
                var usuarios = repos.getRepository(UsuarioRepository.class);
                var refresh = repos.getRepository(RefreshTokenRepository.class);
                var encoder = new BCryptPasswordEncoder(12);
                var policy = new PasswordPolicyService();
                var crear = new SuperAdminUsuarioService(empresas, usuarios, refresh, encoder, policy);
                var jwt = new JwtService(env.getRequiredProperty("security.jwt.secret-base64"),
                        env.getRequiredProperty("security.jwt.issuer"), env.getRequiredProperty("security.jwt.access-minutes", Long.class));
                var auth = new AuthService(usuarios, refresh, encoder, policy, jwt,
                        new AdminAccessService(env.getRequiredProperty("security.admin-key")));
                ReflectionTestUtils.setField(auth, "bootstrapEnabled", false);
                ReflectionTestUtils.setField(auth, "maxIntentos", env.getRequiredProperty("security.auth.max-failed-attempts", Integer.class));
                ReflectionTestUtils.setField(auth, "minutosBloqueo", env.getRequiredProperty("security.auth.lock-minutes", Integer.class));
                ReflectionTestUtils.setField(auth, "refreshDays", env.getRequiredProperty("security.refresh.days", Integer.class));
                auth.init();
                var tx = new TransactionTemplate(new JpaTransactionManager(factory));
                etapa = "EMPRESA_ADMIN_CREACION_COMMIT";
                Long usuarioId = tx.execute(status -> {
                    exigir(empresas.count() == 1, "EMPRESA_BETA_UNICA_REQUERIDA");
                    Empresa empresa = empresas.findByRuc("20100066603").orElseThrow(() -> new IllegalStateException("EMPRESA_BETA_NO_EXISTE"));
                    empresa = empresas.bloquearPorId(empresa.getId()).orElseThrow();
                    exigir(empresa.getEstadoPlataforma() == EstadoEmpresa.ACTIVA && empresa.tieneConfiguracionSunat()
                            && "FASE 5 BETA PRUEBA NO PRODUCTIVA".equals(empresa.getRazonSocial()), "EMPRESA_BETA_NO_PREPARADA");
                    var existente = usuarios.findByEmailIgnoreCase(EMAIL).orElse(null);
                    if (existente == null) {
                        exigir(!cambiarPassword, "CAMBIO_PASSWORD_REQUIERE_USUARIO_EXISTENTE");
                        exigir(usuarios.count() == 0, "USUARIOS_AJENOS_NO_ALTERAR");
                        var nuevo = crear.crear(empresa.getId(), new CrearUsuarioRequest("ENSAYO BETA NO PRODUCTIVO", EMAIL,
                                password, RolUsuario.EMPRESA_ADMIN, Set.of()));
                        exigir(nuevo.rol() == RolUsuario.EMPRESA_ADMIN && nuevo.estado() == EstadoUsuario.ACTIVO
                                && nuevo.debeCambiarPassword(), "CREACION_NO_RESPETA_FLUJO_NORMAL");
                        return nuevo.id();
                    }
                    exigir(usuarios.count() == 1, "USUARIOS_AJENOS_NO_ALTERAR");
                    verificarUsuarioEnsayo(existente, empresa.getId(), encoder, password);
                    return existente.getId(); // Reejecucion idempotente: nunca resetear hash/flag ni crear otro usuario.
                });
                etapa = "EMPRESA_ADMIN_RELOAD";
                boolean cambioInicialAntes = tx.execute(status -> {
                    // TransactionTemplate ya hizo commit y cerro su EntityManager anterior.
                    Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
                    Long empresaId = empresas.findByRuc("20100066603").orElseThrow().getId();
                    verificarUsuarioEnsayo(usuario, empresaId, encoder, password);
                    exigir(usuarios.count() == 1, "USUARIO_ENSAYO_NO_UNICO");
                    return usuario.isDebeCambiarPassword();
                });
                if (cambiarPassword) {
                    exigir(cambioInicialAntes, "CAMBIO_INICIAL_YA_COMPLETADO_NO_ROTAR");
                    etapa = "CAMBIO_PASSWORD_AUTH_SERVICE_COMMIT";
                    tx.executeWithoutResult(status -> auth.cambiarPassword(usuarioId, new ChangePasswordRequest(password, passwordLogin)));
                    etapa = "CAMBIO_PASSWORD_RELOAD";
                }
                boolean cambioInicial = cambiarPassword ? tx.execute(status -> {
                    Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
                    Long empresaId = empresas.findByRuc("20100066603").orElseThrow().getId();
                    verificarUsuarioEnsayo(usuario, empresaId, encoder, passwordLogin);
                    exigir(!usuario.isDebeCambiarPassword() && !encoder.matches(password, usuario.getPasswordHash()),
                            "CAMBIO_PASSWORD_NO_PERSISTIDO");
                    return usuario.isDebeCambiarPassword();
                }) : cambioInicialAntes;
                System.out.println("EMPRESA_ADMIN_BETA=PERSISTIDO_VERIFICADO_TRAS_COMMIT_RELOAD");
                System.out.println("EMPRESA_ASOCIADA=20100066603");
                System.out.println("ROL=EMPRESA_ADMIN");
                System.out.println("ESTADO_USUARIO=ACTIVO");
                System.out.println("PASSWORD_HASH=PRESENTE_VERIFICADO_SIN_MOSTRAR");
                System.out.println("CAMBIO_PASSWORD_INICIAL=" + (cambioInicial ? "REQUERIDO_FLUJO_NORMAL" : "YA_COMPLETADO"));
                etapa = "LOGIN_INTERNO_AUTH_SERVICE";
                // Credenciales correctas ya cotejadas tras reload: un unico login, sin HTTP ni SUNAT.
                var sesion = tx.execute(status -> auth.login(new LoginRequest(EMAIL, passwordLogin)));
                try {
                    exigir(sesion != null && sesion.usuario().id().equals(usuarioId)
                            && "EMPRESA_ADMIN".equals(sesion.usuario().rol()) && "20100066603".equals(sesion.usuario().ruc())
                            && sesion.usuario().debeCambiarPassword() == cambioInicial, "LOGIN_PERFIL_INCONSISTENTE");
                    var claims = jwt.validar(sesion.accessToken());
                    exigir(usuarioId.toString().equals(claims.getSubject()) && "20100066603".equals(claims.getClaimAsString("ruc"))
                            && "EMPRESA_ADMIN".equals(claims.getClaimAsString("role")), "LOGIN_JWT_INCONSISTENTE");
                } finally {
                    // No conservar una sesion de prueba activa ni mostrar/archivar tokens.
                    if (sesion != null) tx.executeWithoutResult(status -> auth.logout(sesion.refreshToken()));
                }
                etapa = "LOGIN_INTERNO_RELOAD";
                tx.executeWithoutResult(status -> {
                    Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
                    exigir(usuario.getUltimoLogin() != null && usuario.getIntentosFallidos() == 0
                            && usuario.isDebeCambiarPassword() == cambioInicial, "LOGIN_NO_PERSISTIDO");
                });
                System.out.println("LOGIN_INTERNO=VALIDADO_AUTH_SERVICE_Y_JWT_SIN_MOSTRAR_TOKEN");
                System.out.println("ACCESO_ENSAYO=CONFIGURADO" + (cambioInicial ? "_CAMBIO_INICIAL_REQUERIDO" : ""));
                System.out.println("LLAMADAS_SUNAT=0");
                // Creacion/login correctos no equivalen a permiso fiscal: el filtro HTTP mantiene
                // el bloqueo hasta el cambio normal de password; nunca alterar ese flag aqui.
                System.out.println("BLOQUEADOR_RESTANTE=" + (cambioInicial ? "CAMBIO_PASSWORD_INICIAL" : "NINGUNO_EN_ACCESO"));
                System.out.println("ESTADO=" + (cambioInicial ? "BLOQUEADO ANTES DE EJECUCIÓN BETA" : "LISTO PARA EJECUCIÓN BETA"));
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    static String passwordEnsayoObligatoria(ConfigurableEnvironment env) {
        String password = env.getProperty("FASE5_USUARIO_PASSWORD");
        exigir(password != null && !password.isBlank(), "FASE5_USUARIO_PASSWORD_NO_DISPONIBLE_EN_PROCESO");
        return password;
    }

    static String passwordNuevaEnsayoObligatoria(ConfigurableEnvironment env) {
        String password = env.getProperty("FASE5_USUARIO_PASSWORD_NUEVA");
        exigir(password != null && !password.isBlank(), "FASE5_USUARIO_PASSWORD_NUEVA_NO_DISPONIBLE_EN_PROCESO");
        return password;
    }

    private static void verificarUsuarioEnsayo(Usuario usuario, Long empresaId, BCryptPasswordEncoder encoder, String password) {
        exigir(usuario.getEmpresa() != null && empresaId.equals(usuario.getEmpresa().getId())
                && "20100066603".equals(usuario.getEmpresa().getRuc()) && EMAIL.equals(usuario.getEmail())
                && usuario.getRol() == RolUsuario.EMPRESA_ADMIN && usuario.getEstado() == EstadoUsuario.ACTIVO,
                "USUARIO_EXISTENTE_NO_SOBRESCRIBIR");
        exigir(usuario.getPasswordHash() != null && !usuario.getPasswordHash().isBlank()
                && encoder.matches(password, usuario.getPasswordHash()), "PASSWORD_EXISTENTE_NO_SOBRESCRIBIR");
        exigir(usuario.getBloqueadoHasta() == null || !usuario.getBloqueadoHasta().isAfter(LocalDateTime.now()), "USUARIO_BLOQUEADO_NO_ALTERAR");
    }

    private static void ejecutar(ConfigurableEnvironment env) throws Exception {
        validarConfiguracion(env);
        var zona = new TimeZoneConfig(); ReflectionTestUtils.setField(zona, "timeZoneId", env.getRequiredProperty("app.time-zone")); zona.configurarZonaHoraria();
        Path metadataPath = rutaExterna(env.getRequiredProperty("FASE5_PADRON_METADATA_PATH"));
        exigir(metadataPath.equals(Path.of(env.getRequiredProperty("sunat.padron-ruc.path")).normalize()), "METADATA_NO_COINCIDE");
        Files.createDirectories(metadataPath);
        try (var files = Files.list(metadataPath)) { exigir(files.findAny().isEmpty(), "METADATA_NO_NUEVA"); }
        Path evidenciaRoot = rutaExterna(env.getRequiredProperty("FASE5_EVIDENCE_PATH"));
        exigir(!evidenciaRoot.startsWith(metadataPath) && !metadataPath.startsWith(evidenciaRoot), "RUTAS_SOLAPADAS");
        Files.createDirectories(evidenciaRoot);
        Path evidencia = Files.createDirectory(evidenciaRoot.resolve("precheck-" + UUID.randomUUID()));
        JsonNode datos;
        try (var in = new ClassPathResource("fase5-beta-datos.json").getInputStream()) { datos = new ObjectMapper().readTree(in); }
        exigir("20100066603".equals(datos.get("rucEmisor").asText()), "RUC_FUERA_DE_AUTORIZACION");
        Fase5BetaPreparacionTest.cargarDatos();
        var ds = new DriverManagerDataSource(URL, env.getRequiredProperty("SPRING_DATASOURCE_USERNAME"), env.getRequiredProperty("SPRING_DATASOURCE_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        etapa = "CONEXION_BETA";
        try (var c = ds.getConnection()) { validarConexion(c); }
        var opciones = new HashMap<String, Object>();
        opciones.put("hibernate.connection.datasource", ds);
        opciones.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        opciones.put("hibernate.boot.allow_jdbc_metadata_access", false);
        opciones.put("hibernate.hbm2ddl.auto", "none");
        opciones.put("jakarta.persistence.schema-generation.database.action", "none");
        opciones.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var script = new StringWriter();
        opciones.put("jakarta.persistence.schema-generation.scripts.action", "create");
        opciones.put("jakarta.persistence.schema-generation.scripts.create-target", script);
        var registry = new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            var sources = new MetadataSources(registry);
            var scan = new ClassPathScanningCandidateComponentProvider(false); scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var entity : scan.findCandidateComponents("org.example")) sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            Metadata model = sources.buildMetadata();
            SchemaManagementToolCoordinator.process(model, registry, opciones, DelayedDropRegistryNotAvailableImpl.INSTANCE);
            var esperado = sentencias(script.toString());
            var propuesto = sentencias(Files.readString(Path.of("target/fase5-precheck/schema-create-propuesto.sql")));
            exigir(esperado.equals(propuesto), "DDL_NO_COINCIDE_CON_CODIGO");
            var tablas = new TreeSet<String>(); model.collectTableMappings().forEach(t -> tablas.add(t.getName()));
            exigir(tablas.size() == 21, "MODELO_DISTINTO_REVISAR");
            var existentes = new TreeSet<>(jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=?", String.class, BASE));
            etapa = "DDL_BETA_AUTOCOMMIT_NO_ROLLBACK";
            if (existentes.isEmpty()) {
                try (var c = ds.getConnection(); var st = c.createStatement()) {
                    for (String sql : propuesto) { validarConexion(c); st.execute(sql); }
                }
            } else exigir(existentes.equals(tablas), "ESQUEMA_PARCIAL_NO_REPARAR_AUTOMATICAMENTE");
            etapa = "SCHEMA_VALIDATOR_BETA";
            var execution = SchemaManagementToolCoordinator.buildExecutionOptions(opciones, ExceptionHandlerHaltImpl.INSTANCE);
            registry.getService(SchemaManagementTool.class).getSchemaValidator(opciones).doValidation(model, execution, ContributableMatcher.ALL);
            verificarMetadata(jdbc, model);
            System.out.println("ESQUEMA_BETA=VALIDADO_21_TABLAS");
            for (String table : List.of("comprobante", "resumen_diario", "comunicacion_baja", "secuencia_correlativo",
                    "secuencia_resumen_diario", "secuencia_comunicacion_baja", "solicitud_emision", "respuesta_sunat_recibida"))
                exigir(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class) == 0, "DATOS_FISCALES_EXISTENTES_NO_ALTERAR");
            opciones.put("jakarta.persistence.schema-generation.scripts.action", "none");
            try (var factory = model.buildSessionFactory()) {
                var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var repos = new JpaRepositoryFactory(em);
                var empresas = repos.getRepository(EmpresaRepository.class);
                var usuarios = repos.getRepository(UsuarioRepository.class);
                var subscriptions = repos.getRepository(SuscripcionEmpresaRepository.class);
                var sec = new SecurityContextService(); var api = new ApiKeyService();
                var crypto = new CryptoService(env.getRequiredProperty("APP_MASTER_KEY_BASE64"));
                var signer = new XmlSignerService(); ReflectionTestUtils.setField(signer, "environment", env.getRequiredProperty("sunat.environment"));
                ReflectionTestUtils.setField(signer, "minRsaBits", env.getRequiredProperty("sunat.certificate.min-rsa-bits", Integer.class)); signer.inicializar();
                var crearEmpresa = new SuperAdminEmpresaService(empresas, usuarios, subscriptions,
                        repos.getRepository(PagoEmpresaRepository.class), repos.getRepository(AnuncioEmpresaRepository.class), sec);
                var encoder = new BCryptPasswordEncoder(12);
                var crearUsuario = new SuperAdminUsuarioService(empresas, usuarios, repos.getRepository(RefreshTokenRepository.class), encoder, new PasswordPolicyService());
                var configurar = new EmpresaConfiguracionService(empresas, sec, new EmpresaAccessService(empresas, api), crypto, signer, api);
                var tx = new TransactionTemplate(new JpaTransactionManager(factory));
                etapa = "EMPRESA_CIFRADO_BETA";
                Long empresaId = tx.execute(status -> {
                    exigir(empresas.count() <= 1 && usuarios.count() <= 1, "DATOS_AJENOS_AL_ENSAYO");
                    var empresa = empresas.findByRuc(datos.get("rucEmisor").asText()).orElse(null);
                    if (empresa == null) {
                        exigir(empresas.count() == 0, "EMPRESA_AJENA_NO_ALTERAR");
                        crearEmpresa.crear(new CrearEmpresaRequest(datos.get("rucEmisor").asText(), datos.get("razonSocial").asText(),
                                datos.get("direccionFiscal").asText(), datos.get("ubigeo").asText(), "LIMA", "LIMA", "LIMA", 1));
                        empresa = empresas.findByRuc(datos.get("rucEmisor").asText()).orElseThrow();
                    }
                    exigir(datos.get("razonSocial").asText().equals(empresa.getRazonSocial()) && empresa.getEstadoPlataforma() == EstadoEmpresa.ACTIVA,
                            "EMPRESA_NO_IDENTIFICADA_COMO_BETA");
                    var principal = new AppPrincipal(null, empresa.getId(), empresa.getTenantId(), empresa.getRuc(), null, "EMPRESA_ADMIN", false);
                    // Contexto LOCAL de provision ya autorizada, no JWT/HTTP ni identidad productiva.
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                    if (!empresa.tieneConfiguracionSunat()) {
                        exigir(empresa.getUsuarioSol() == null && empresa.getClaveSol() == null && empresa.getPfxBytes() == null && empresa.getPfxPassword() == null,
                                "CONFIGURACION_PARCIAL_NO_SOBRESCRIBIR");
                        try { configurarCertificadoNuevo(empresa, configurar); }
                        catch (Exception failure) { throw new IllegalStateException("CERTIFICADO_LOCAL_NO_GENERADO"); }
                    }
                    String usuarioPassword = env.getProperty("FASE5_USUARIO_PASSWORD", "");
                    if (!usuarioPassword.isBlank()) {
                        var existente = usuarios.findByEmailIgnoreCase(EMAIL).orElse(null);
                        if (existente == null) {
                            exigir(usuarios.count() == 0, "USUARIO_AJENO_NO_ALTERAR");
                            crearUsuario.crear(empresa.getId(), new CrearUsuarioRequest("ENSAYO BETA NO PRODUCTIVO", EMAIL, usuarioPassword, RolUsuario.EMPRESA_ADMIN, Set.of()));
                        } else exigir(existente.getEmpresa().getId().equals(empresa.getId()) && existente.getRol() == RolUsuario.EMPRESA_ADMIN && existente.getEstado() == EstadoUsuario.ACTIVO
                                && encoder.matches(usuarioPassword, existente.getPasswordHash()), "USUARIO_EXISTENTE_NO_SOBRESCRIBIR");
                    }
                    return empresa.getId();
                });
                SecurityContextHolder.clearContext();
                etapa = "RELOAD_XML_FIRMA_XSD_ZIP";
                Empresa empresa = tx.execute(status -> empresas.findById(empresaId).orElseThrow());
                verificarArtefactos(empresa, crypto, signer, evidencia);
                System.out.println("EMPRESA_BETA=CONFIGURADO"); System.out.println("CREDENCIALES_BETA=CONFIGURADO");
                System.out.println("CERTIFICADO_BETA=GENERADO_PARA_BETA_VERIFICADO_TRAS_RELOAD");
                System.out.println("XML_FIRMA_DIGEST_XSD_ZIP=VALIDADO_7_FIXTURES_NO_EMITIDOS");
                System.out.println("CORRELATIVOS_CONSUMIDOS=0"); System.out.println("LLAMADAS_SUNAT=0");
                boolean usuarioListo = tx.execute(status -> usuarios.findByEmailIgnoreCase(EMAIL)
                        .filter(u -> u.getEmpresa().getId().equals(empresaId) && u.getRol() == RolUsuario.EMPRESA_ADMIN && u.getEstado() == EstadoUsuario.ACTIVO && !u.isDebeCambiarPassword()).isPresent());
                System.out.println("ACCESO_ENSAYO=" + (usuarioListo ? "CONFIGURADO" : "PENDIENTE_PROVISION_O_CAMBIO_INICIAL_PASSWORD"));
                // Un helper no demuestra login/arranque de Main ni habilita egress/SUNAT.
                String result = "PRECHECK_LOCAL_VALIDADO_SIN_ENVIO\nACCESO_ENSAYO=" + (usuarioListo ? "CONFIGURADO" : "PENDIENTE")
                        + "\nLLAMADAS_SUNAT=0\nESTADO=BLOQUEADO ANTES DE EJECUCION BETA\n";
                Files.writeString(evidencia.resolve("resultado.txt"), result);
                System.out.print(result);
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    private static void configurarCertificadoNuevo(Empresa empresa, EmpresaConfiguracionService service) throws Exception {
        Path dir = Files.createTempDirectory(rutaExterna(System.getProperty("java.io.tmpdir")), "opencode-fase5-cert-");
        Path archivo = dir.resolve("beta.p12"), log = dir.resolve("keytool.log");
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random); Arrays.fill(random, (byte) 0);
        byte[] bytes = null;
        try {
            String exe = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
            var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", exe).toString(), "-genkeypair", "-alias", "fase5-beta",
                    "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=FASE 5 BETA PRUEBA NO PRODUCTIVA,OU=" + empresa.getRuc() + ",O=ENSAYO FICTICIO,C=PE",
                    "-ext", "KU=digitalSignature", "-validity", "30", "-startdate", "-1d", "-storetype", "PKCS12", "-keystore", archivo.toString(),
                    "-storepass:env", "FASE5_PFX_PASSWORD_TEMPORAL", "-noprompt");
            command.environment().put("FASE5_PFX_PASSWORD_TEMPORAL", password);
            var process = command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("KEYTOOL_TIMEOUT"); }
            exigir(process.exitValue() == 0, "KEYTOOL_FALLO"); bytes = Files.readAllBytes(archivo);
            var request = new EmpresaConfigRequest(); request.setUsuarioSol("MODDATOS"); request.setClaveSol("MODDATOS"); request.setPfxPassword(password);
            service.configurarSunat(request, new MockMultipartFile("pfxFile", "beta.p12", "application/octet-stream", bytes));
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
            Files.deleteIfExists(archivo); Files.deleteIfExists(log); Files.deleteIfExists(dir);
        }
    }

    private static void verificarArtefactos(Empresa empresa, CryptoService crypto, XmlSignerService signer, Path evidencia) throws Exception {
        String ruc = empresa.getRuc();
        String usuario = crypto.descifrarUsuarioSol(ruc, empresa.getUsuarioSol());
        exigir("MODDATOS".equals(usuario) && "MODDATOS".equals(crypto.descifrarClaveSol(ruc, empresa.getClaveSol())), "CREDENCIALES_DISTINTAS_NO_CAMBIAR");
        String username = ReflectionTestUtils.invokeMethod(new SunatDirectSoapClient(), "construirUsername", ruc, usuario);
        exigir((ruc + usuario).equals(username), "USERNAME_INESPERADO");
        byte[] pfx = crypto.descifrarPfx(ruc, empresa.getPfxBytes());
        String password = crypto.descifrarPfxPassword(ruc, empresa.getPfxPassword());
        try {
            signer.validarPfx(pfx, password, ruc);
            var ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(pfx), password.toCharArray());
            exigir(ks.size() == 1 && ks.isKeyEntry("fase5-beta") && ks.getKey("fase5-beta", password.toCharArray()) != null, "ALIAS_PRIVADO_INESPERADO");
            var cert = (X509Certificate) ks.getCertificate("fase5-beta"); cert.checkValidity(); cert.verify(cert.getPublicKey());
            exigir(((RSAPublicKey) cert.getPublicKey()).getModulus().bitLength() == 2048 && cert.getSubjectX500Principal().getName().contains("OU=" + ruc), "CERTIFICADO_INESPERADO");
            for (String flujo : List.of("01", "07-01", "03", "RC-03", "07-03", "RC-07", "RA")) {
                var firmado = signer.firmarXml(new Fase5BetaPreparacionTest().construir(flujo, empresa), pfx, password);
                Fase5BetaPreparacionTest.validarXsd(flujo, firmado.xmlFirmado());
                var doc = Fase5BetaPreparacionTest.parse(firmado.xmlFirmado());
                var firma = new XMLSignature((Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0), "", true);
                exigir(firma.checkSignatureValue(cert) && firmado.hashCpe().equals(Base64.getEncoder().encodeToString(firma.getSignedInfo().item(0).getDigestValue())), "FIRMA_DIGEST_INVALIDOS");
                exigir(new String(firmado.xmlFirmado(), StandardCharsets.UTF_8).contains(">" + ruc + "<"), "EMISOR_XML_INESPERADO");
                var out = new ByteArrayOutputStream(); String nombre = "LOCAL-NO-EMITIDO-" + flujo + ".xml";
                try (var zip = new ZipOutputStream(out)) { zip.putNextEntry(new ZipEntry(nombre)); zip.write(firmado.xmlFirmado()); zip.closeEntry(); }
                try (var zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
                    exigir(zip.getNextEntry() != null && Arrays.equals(firmado.xmlFirmado(), zip.readAllBytes()) && zip.getNextEntry() == null, "ZIP_INVALIDO");
                }
                Files.write(evidencia.resolve(nombre), firmado.xmlFirmado()); Files.write(evidencia.resolve(nombre.replace(".xml", ".zip")), out.toByteArray());
                Files.writeString(evidencia.resolve(nombre.replace(".xml", ".hash.txt")), firmado.hashCpe());
            }
        } finally { Arrays.fill(pfx, (byte) 0); }
    }

    private static void verificarMetadata(JdbcTemplate jdbc, Metadata model) {
        for (var table : model.collectTableMappings()) {
            String name = table.getName();
            exigir("InnoDB".equals(jdbc.queryForObject("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?", String.class, BASE, name)), "MOTOR_DISTINTO");
            exigir(table.getColumns().size() == jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?", Integer.class, BASE, name), "COLUMNAS_DISTINTAS");
            for (Column col : table.getColumns()) {
                var actual = jdbc.queryForMap("SELECT COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME=?", BASE, name, col.getName());
                exigir(normalizar(col.getSqlType(model)).equals(normalizar(actual.get("COLUMN_TYPE").toString()))
                        && (col.isNullable() ? "YES" : "NO").equals(actual.get("IS_NULLABLE")), "TIPO_PRECISION_NULOS_DISTINTOS");
            }
            exigir(columnas(table.getPrimaryKey().getColumns()).equals(indice(jdbc, name, "PRIMARY")), "PK_DISTINTA");
            for (var index : table.getIndexes().values()) exigir(columnas(index.getColumns()).equals(indice(jdbc, name, index.getName())), "INDICE_DISTINTO");
            for (var unique : table.getUniqueKeys().values()) {
                exigir(columnas(unique.getColumns()).equals(indice(jdbc, name, unique.getName())), "UNIQUE_DISTINTO");
                exigir(jdbc.queryForObject("SELECT MAX(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_NAME=?", Integer.class, BASE, name, unique.getName()) == 0, "INDICE_NO_UNIQUE");
            }
            for (var fk : table.getForeignKeys().values()) {
                exigir(columnas(fk.getColumns()).equals(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND CONSTRAINT_NAME=? AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY ORDINAL_POSITION", String.class, BASE, name, fk.getName())), "FK_DISTINTA");
                exigir(fk.getReferencedTable().getName().equals(jdbc.queryForObject("SELECT REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND CONSTRAINT_NAME=? LIMIT 1", String.class, BASE, name, fk.getName())), "FK_TABLA_DISTINTA");
                var ref = fk.getReferencedColumns().isEmpty() ? fk.getReferencedTable().getPrimaryKey().getColumns() : fk.getReferencedColumns();
                exigir(columnas(ref).equals(jdbc.queryForList("SELECT REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND CONSTRAINT_NAME=? ORDER BY ORDINAL_POSITION", String.class, BASE, name, fk.getName())), "FK_REFERENCIA_DISTINTA");
            }
        }
    }
    private static List<String> columnas(List<Column> cols) { return cols.stream().map(Column::getName).toList(); }
    private static List<String> indice(JdbcTemplate jdbc, String tabla, String indice) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_NAME=? ORDER BY SEQ_IN_INDEX", String.class, BASE, tabla, indice);
    }
    static String normalizar(String type) {
        String n = type.toLowerCase(Locale.ROOT).replace(" ", "");
        return n.equals("integer") ? "int" : n.equals("bit(1)") ? "bit" : n;
    }
    static void exigir(boolean condicion, String codigo) {
        if (!condicion) {
            // Codigos constantes definidos en este helper; no mensajes de excepciones,
            // valores del entorno, rutas, SQL ni secretos. Identifica la guarda concreta
            // incluso dentro de bloques compartidos como rutaExterna/validarConexion.
            if (etapa.startsWith("PRIMERA_FACTURA_CONFIG_") || controlFacturaActiva != null && controlFacturaActiva.bajaFactura)
                etapa += "_GUARDA_" + codigo;
            throw new IllegalStateException(codigo);
        }
    }
}
