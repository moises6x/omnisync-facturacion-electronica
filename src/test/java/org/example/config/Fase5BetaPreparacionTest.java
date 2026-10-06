package org.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import org.apache.xml.security.signature.XMLSignature;
import org.example.auth.security.SecurityContextService;
import org.example.dto.ComprobanteRequest;
import org.example.dto.EmpresaConfigRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.model.*;
import org.example.repository.EmpresaRepository;
import org.example.service.*;
import org.example.util.ObservabilidadFiscal;
import org.example.util.RucUtils;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Soporte de test, sin datasource, Spring Boot completo, red ni consumo de correlativos.
 * No acredita provision de empresa/certificado en la BD beta ni autorizacion de SUNAT.
 */
@ExtendWith(OutputCaptureExtension.class)
class Fase5BetaPreparacionTest {
    @TempDir static Path temporal;
    private static JsonNode datos;
    private static String password;
    private static byte[] pfx;
    private static X509Certificate certificado;
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @BeforeAll
    static void certificadoEfimeroAutofirmado() throws Exception {
        cargarDatos();
        byte[] aleatorio = new byte[32]; new SecureRandom().nextBytes(aleatorio);
        password = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        Arrays.fill(aleatorio, (byte) 0);
        Path archivo = temporal.resolve("certificado-efimero-beta.p12");
        String exe = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        var proceso = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", exe).toString(),
                "-genkeypair", "-alias", "fase5-beta", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=FASE 5 BETA PRUEBA NO PRODUCTIVA,OU=" + ruc() + ",O=ENSAYO FICTICIO,C=PE",
                "-ext", "KU=digitalSignature", "-validity", "30", "-startdate", "-1d",
                "-storetype", "PKCS12", "-keystore", archivo.toString(),
                "-storepass:env", "FASE5_PFX_PASSWORD_TEST", "-noprompt");
        proceso.environment().put("FASE5_PFX_PASSWORD_TEST", password);
        var ejecutado = proceso.redirectErrorStream(true).redirectOutput(temporal.resolve("keytool.log").toFile()).start();
        if (!ejecutado.waitFor(30, TimeUnit.SECONDS)) {
            ejecutado.destroyForcibly(); fail("Timeout generando certificado efimero; no revelar logs o password");
        }
        assertEquals(0, ejecutado.exitValue(), "No se pudo generar certificado efimero");
        pfx = Files.readAllBytes(archivo);
        var ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(pfx), password.toCharArray());
        assertEquals(1, ks.size()); assertTrue(ks.isKeyEntry("fase5-beta"));
        certificado = (X509Certificate) ks.getCertificate("fase5-beta");
    }

    @Test void overlayUsaPerfilBetaYEndpointOriginalSinInventarOtro() throws Exception {
        var env = entorno();
        assertArrayEquals(new String[]{"beta"}, env.getActiveProfiles());
        assertEquals("BETA", env.getProperty("sunat.environment"));
        assertEquals(propiedades("application-beta.properties").getProperty("sunat.billservice.url"),
                env.getProperty("sunat.billservice.url"));
        assertDoesNotThrow(() -> ConfiguracionFiscalEnvironmentPostProcessor.validar(env));
    }

    @ParameterizedTest @ValueSource(strings={"prod", "test"})
    void mezclarPerfilesConBetaSigueRechazado(String otro) throws Exception {
        var env = entorno(); env.setActiveProfiles("beta", otro);
        assertThrows(IllegalStateException.class, () -> ConfiguracionFiscalEnvironmentPostProcessor.validar(env));
    }

    @Test void bootstrapSchedulersYDebugQuedanApagadosSoloEnOverlay() throws Exception {
        var env = entorno();
        for (String key : List.of("security.bootstrap.enabled", "security.bootstrap.auto-create",
                "sunat.resumen-diario.auto.enabled", "sunat.comunicacion-baja.auto.enabled",
                "sunat.padron-ruc.actualizacion.enabled", "sunat.certificate.monitor.enabled",
                "security.refresh.cleanup.enabled", "sunat.debug.xml", "spring.flyway.enabled"))
            assertEquals("false", env.getProperty(key), key);
        assertEquals("-", env.getProperty("admin.suscripciones.cron"));
        assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("127.0.0.1", env.getProperty("server.address"));
        assertEquals("none", env.getProperty("server.forward-headers-strategy"));
        assertEquals("1", env.getProperty("sunat.async.max-pool-size"));
        assertTrue(env.getProperty("sunat.envio-recovery.initial-delay-ms", Long.class) > TimeUnit.MINUTES.toMillis(45));
        assertEquals("1", env.getProperty("sunat.envio-recovery.batch-size"));
    }

    @Test void configuracionProdNoCargaOverlayNiPierdeSusProtecciones() throws Exception {
        var prod = new MockEnvironment(); aplicar(prod, "application.properties"); aplicar(prod, "application-prod.properties");
        prod.withProperty("SPRING_PROFILES_ACTIVE", "prod").withProperty("APP_DEPLOYMENT_ENVIRONMENT", "PRODUCCION");
        prod.setActiveProfiles("prod");
        assertEquals("validate", prod.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("2048", prod.getProperty("sunat.certificate.min-rsa-bits"));
        assertEquals("PRODUCCION", prod.getProperty("sunat.environment"));
        assertFalse(prod.getProperty("sunat.billservice.url").equals(entorno().getProperty("sunat.billservice.url")));
        assertDoesNotThrow(() -> ConfiguracionFiscalEnvironmentPostProcessor.validar(prod));
    }

    @Test void postConstructPadronUsaMetadataEfimeraInactivaSinImportacion() throws Exception {
        var importador = mock(PadronRucImportService.class);
        var servicio = new PadronRucActualizacionService(importador);
        Path ruta = Files.createDirectory(temporal.resolve("metadata-nueva"));
        assertFalse(Files.isSymbolicLink(ruta));
        ReflectionTestUtils.setField(servicio, "rutaConfigurada", ruta.toString());
        ReflectionTestUtils.setField(servicio, "habilitadoPorDefecto", false);
        ReflectionTestUtils.setField(servicio, "versionInicial", "");
        ReflectionTestUtils.setField(servicio, "modoPorDefecto", "SEMANAL");
        ReflectionTestUtils.setField(servicio, "diaPorDefecto", "DOMINGO");
        ReflectionTestUtils.setField(servicio, "horaPorDefecto", "02:00");
        ReflectionTestUtils.setField(servicio, "zonaHorariaPorDefecto", "America/Lima");
        try {
            servicio.inicializar();
            assertFalse(servicio.programacion().activo());
            assertNull(ReflectionTestUtils.getField(servicio, "tareaProgramada"));
            assertTrue(Files.exists(ruta.resolve(".programacion_actualizacion.properties")));
            verifyNoInteractions(importador);
        } finally { servicio.cerrar(); }
    }

    @Test void configuracionNormalCifraCredencialesYClienteConstruyeUsernameSinRed(CapturedOutput output) {
        Empresa empresa = configurarEmpresa();
        CryptoService crypto = crypto();
        String usuario = crypto.descifrarUsuarioSol(ruc(), empresa.getUsuarioSol());
        String clave = crypto.descifrarClaveSol(ruc(), empresa.getClaveSol());
        assertTrue("MODDATOS".equals(usuario) && "MODDATOS".equals(clave), "Round trip de datos beta");
        String username = ReflectionTestUtils.invokeMethod(new SunatDirectSoapClient(), "construirUsername", ruc(), usuario);
        assertTrue((ruc() + usuario).equals(username), "Username compuesto por el cliente compartido");
        assertTrue(empresa.getUsuarioSol().startsWith("ENCv1:") && empresa.getClaveSol().startsWith("ENCv1:"));
        assertFalse(Arrays.equals(pfx, empresa.getPfxBytes()));
        assertTrue(Arrays.equals(pfx, crypto.descifrarPfx(ruc(), empresa.getPfxBytes())));
        assertTrue(password.equals(crypto.descifrarPfxPassword(ruc(), empresa.getPfxPassword())));
        assertThrows(IllegalStateException.class, () -> crypto.descifrarPfx("20100130204", empresa.getPfxBytes()));
        assertFalse(output.getAll().contains("MODDATOS")); assertFalse(output.getAll().contains(password));
    }

    @Test void certificadoEfimeroEsAutofirmadoVigenteRsa2048YConOuDelEmisor() throws Exception {
        certificado.checkValidity(); certificado.verify(certificado.getPublicKey());
        assertEquals(certificado.getIssuerX500Principal(), certificado.getSubjectX500Principal());
        assertEquals(2048, ((RSAPublicKey) certificado.getPublicKey()).getModulus().bitLength());
        assertTrue(certificado.getSubjectX500Principal().getName().contains("OU=" + ruc()));
        assertDoesNotThrow(() -> firmador().validarPfx(pfx, password, ruc()));
    }

    @ParameterizedTest @ValueSource(strings={"01", "07-01", "03", "RC-03", "07-03", "RC-07", "RA"})
    void datosBetaConfiguracionCifradaXmlFirmaXsdYZipSinRedNiSecuencias(String flujo, CapturedOutput output) throws Exception {
        Empresa empresa = configurarEmpresa(); CryptoService crypto = crypto();
        var firmado = firmador().firmarXml(construir(flujo, empresa), crypto.descifrarPfx(ruc(), empresa.getPfxBytes()),
                crypto.descifrarPfxPassword(ruc(), empresa.getPfxPassword()));
        validarXsd(flujo, firmado.xmlFirmado());
        Document doc = parse(firmado.xmlFirmado());
        var xpath = XPathFactory.newInstance().newXPath();
        String emisor = xpath.evaluate("//*[local-name()='AccountingSupplierParty']/*[local-name()='CustomerAssignedAccountID'] | "
                + "//*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='PartyIdentification']/*[local-name()='ID']", doc);
        assertEquals(ruc(), emisor);
        var firma = new XMLSignature((Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0), "", true);
        assertTrue(firma.checkSignatureValue(certificado));
        assertEquals(firmado.hashCpe(), Base64.getEncoder().encodeToString(firma.getSignedInfo().item(0).getDigestValue()));
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("fixture-local-" + flujo + ".xml")); zip.write(firmado.xmlFirmado()); zip.closeEntry();
        }
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertNotNull(zip.getNextEntry()); assertArrayEquals(firmado.xmlFirmado(), zip.readAllBytes()); assertNull(zip.getNextEntry());
        }
        assertFalse(output.getAll().contains("MODDATOS")); assertFalse(output.getAll().contains(password));
        assertFalse(output.getAll().contains("<ds:Signature"));
    }

    @Test void ddlCompletoDelModeloActualSeGeneraSinConexionNiAccionDeBase() throws Exception {
        var opciones = new HashMap<String, Object>();
        opciones.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        opciones.put("hibernate.boot.allow_jdbc_metadata_access", false);
        opciones.put("hibernate.hbm2ddl.auto", "none");
        opciones.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        opciones.put("jakarta.persistence.schema-generation.database.action", "none");
        opciones.put("jakarta.persistence.schema-generation.scripts.action", "create");
        var escritor = new StringWriter(); opciones.put("jakarta.persistence.schema-generation.scripts.create-target", escritor);
        var registro = new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            var fuentes = new MetadataSources(registro);
            var scan = new ClassPathScanningCandidateComponentProvider(false);
            scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            var entidades = scan.findCandidateComponents("org.example");
            assertFalse(entidades.isEmpty());
            for (var entidad : entidades) fuentes.addAnnotatedClass(Class.forName(entidad.getBeanClassName()));
            var metadata = fuentes.buildMetadata();
            SchemaManagementToolCoordinator.process(metadata, registro, opciones, DelayedDropRegistryNotAvailableImpl.INSTANCE);
            String ddl = escritor.toString(); String normal = ddl.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            for (String columna : List.of("cantidad decimal(12,4)", "valor_unitario decimal(22,10)", "precio_unitario decimal(22,10)",
                    "igv_item decimal(14,2)", "monto_total_item decimal(14,2)", "descripcion varchar(500)",
                    "idempotency_hash varchar(64)", "request_hash varchar(64)", "uk_pago_empresa_operacion",
                    "respuesta_sunat_recibida", "ticket_sunat varchar(150)")) assertTrue(normal.contains(columna), columna);
            assertEquals("none", opciones.get("jakarta.persistence.schema-generation.database.action"));
            Path directorio = Path.of("target", "fase5-precheck"); Files.createDirectories(directorio);
            Files.writeString(directorio.resolve("schema-create-propuesto.sql"),
                    "-- PROPUESTA SOLO PARA BD BETA NUEVA VACIA. NO EJECUTADA. Revisar metadata/version/charset antes.\n" + ddl);
        } finally { StandardServiceRegistryBuilder.destroy(registro); }
    }

    @Test void observabilidadIncluyeNodoEmpresaDocumentoYTicketSinSecretos(CapturedOutput output) {
        ObservabilidadFiscal.evento(ruc(), 1L, "PRECHECK_LOCAL_NO_EMITIDO", "TICKET_FICTICIO", "SIN_RED", null);
        assertTrue(output.getAll().contains("FISCAL nodo="));
        assertTrue(output.getAll().contains("ruc=" + ruc()));
        assertTrue(output.getAll().contains("id=1 documento=PRECHECK_LOCAL_NO_EMITIDO ticket=TICKET_FICTICIO"));
        assertFalse(output.getAll().contains("MODDATOS")); assertFalse(output.getAll().contains(password));
    }

    private static String ruc() { return datos.get("rucEmisor").asText(); }
    static void cargarDatos() throws IOException {
        try (var in = new ClassPathResource("fase5-beta-datos.json").getInputStream()) {
            datos = new ObjectMapper().readTree(in);
        }
        assertTrue(RucUtils.esValido(ruc()), "RUC documental debe pasar la validacion local");
    }
    private static BigDecimal valor(String grupo, String campo) { return new BigDecimal(datos.get(grupo).get(campo).asText()); }
    private static String serie(String campo) { return datos.get("series").get(campo).asText(); }
    private static Properties propiedades(String nombre) throws IOException {
        var p = new Properties();
        try (var in = new InputStreamReader(new ClassPathResource(nombre).getInputStream(), StandardCharsets.UTF_8)) { p.load(in); }
        return p;
    }
    private static void aplicar(MockEnvironment env, String nombre) throws IOException {
        propiedades(nombre).forEach((k,v) -> env.setProperty(k.toString(), v.toString()));
    }
    private MockEnvironment entorno() throws IOException {
        var env = new MockEnvironment(); aplicar(env, "application.properties"); aplicar(env, "application-beta.properties");
        aplicar(env, "fase5-beta-precheck.properties"); env.setActiveProfiles("beta");
        return env.withProperty("SPRING_PROFILES_ACTIVE", "beta").withProperty("APP_DEPLOYMENT_ENVIRONMENT", "BETA")
                .withProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://beta.invalid:3306/bd_facturacion_beta_ensayo")
                .withProperty("APP_BETA_DATASOURCE_URL", "jdbc:mysql://beta.invalid:3306/bd_facturacion_beta_ensayo")
                .withProperty("FASE5_PADRON_METADATA_PATH", temporal.toString());
    }
    private static CryptoService crypto() { return new CryptoService(Base64.getEncoder().encodeToString(new byte[32])); }
    private static XmlSignerService firmador() {
        var s = new XmlSignerService(); ReflectionTestUtils.setField(s, "environment", "BETA");
        ReflectionTestUtils.setField(s, "minRsaBits", 2048); s.inicializar(); return s;
    }
    private Empresa configurarEmpresa() {
        var empresa = Empresa.builder().id(1L).ruc(ruc()).razonSocial(datos.get("razonSocial").asText())
                .direccionFiscal(datos.get("direccionFiscal").asText()).ubigeo(datos.get("ubigeo").asText())
                .departamento("LIMA").provincia("LIMA").distrito("LIMA").codigoEstablecimiento("0000").build();
        var repo = mock(EmpresaRepository.class); var sesion = mock(SecurityContextService.class);
        when(sesion.empresaIdRequerido()).thenReturn(1L); when(sesion.rucEmpresaRequerido()).thenReturn(ruc());
        when(repo.bloquearPorId(1L)).thenReturn(Optional.of(empresa)); when(repo.saveAndFlush(empresa)).thenReturn(empresa);
        var servicio = new EmpresaConfiguracionService(repo, sesion, mock(EmpresaAccessService.class), crypto(), firmador(), mock(ApiKeyService.class));
        var request = new EmpresaConfigRequest(); request.setUsuarioSol("MODDATOS"); request.setClaveSol("MODDATOS"); request.setPfxPassword(password);
        servicio.configurarSunat(request, new MockMultipartFile("pfxFile", "fixture.p12", "application/octet-stream", pfx.clone()));
        verify(repo).saveAndFlush(empresa); assertTrue(empresa.tieneConfiguracionSunat()); return empresa;
    }
    byte[] construir(String flujo, Empresa e) {
        var calculo = new CalculoTributarioService(new ReglasTributariasProperties()); var builder = new UblXmlBuilderService();
        if (flujo.startsWith("07")) {
            boolean boleta = flujo.endsWith("03"); var r = new NotaCreditoRequest(); r.setTiendaId(1L);
            r.setSerie(serie(boleta ? "ncBoleta" : "ncFactura")); r.setTipoComprobanteAfectado(boleta ? "03" : "01");
            r.setNumComprobanteAfectado(serie(boleta ? "boleta" : "factura") + "-1"); r.setCodMotivoSunat("04");
            r.setDescripcionMotivo("DESCUENTO FICTICIO DE ENSAYO BETA"); r.setTipoDocCliente(boleta ? "0" : "6");
            r.setNumDocCliente(boleta ? "-" : ruc()); r.setRznSocialCliente(boleta ? "CONSUMIDOR FINAL" : "RECEPTOR FICTICIO DE ENSAYO BETA");
            var i = new NotaCreditoRequest.DetalleItemRequest(); i.setCantidad(BigDecimal.ONE); i.setValorUnitario(valor("notaCredito", "base"));
            i.setTipoAfectacionIgv("10"); i.setUnidadMedida("NIU"); i.setDescripcion(datos.get("descripcion").asText()); r.setItems(List.of(i));
            return builder.construirXmlNotaCredito21(e, r, 1, calculo.calcular(r, new BigDecimal("18.00")), FECHA);
        }
        if ("RA".equals(flujo)) {
            var b = ComunicacionBaja.builder().rucEmisor(ruc()).identificador("RA-20261002-1")
                    .fechaDocumentos(FECHA.toLocalDate()).fechaGeneracion(FECHA.toLocalDate()).build();
            b.addDetalle(ComunicacionBajaDetalle.builder().comprobanteId(1L).numeroLinea(1).tipoComprobante("07")
                    .serie(serie("ncFactura")).numero(1).motivo("BAJA FICTICIA DE ENSAYO BETA").build());
            return new ComunicacionBajaXmlService().construir(b, e);
        }
        if (flujo.startsWith("RC")) {
            boolean nc = flujo.endsWith("07"); String grupo = nc ? "notaCredito" : "original";
            var r = ResumenDiario.builder().rucEmisor(ruc()).identificador("RC-20261002-" + (nc ? "2" : "1"))
                    .fechaDocumentos(FECHA.toLocalDate()).fechaGeneracion(FECHA.toLocalDate()).correlativo(nc ? 2 : 1).build();
            r.addDetalle(ResumenDiarioDetalle.builder().comprobanteId(1L).numeroLinea(1).tipoComprobante(nc ? "07" : "03")
                    .serie(serie(nc ? "ncBoleta" : "boleta")).numero(1).tipoDocCliente("0").numDocCliente("-")
                    .moneda("PEN").codigoEstado("1").opGravada(valor(grupo,"base")).opExonerada(BigDecimal.ZERO)
                    .opInafecta(BigDecimal.ZERO).igv(valor(grupo,"igv")).montoTotal(valor(grupo,"total"))
                    .tipoComprobanteAfectado(nc ? "03" : null).serieComprobanteAfectado(nc ? serie("boleta") : null)
                    .numeroComprobanteAfectado(nc ? 1 : null).build());
            return new ResumenDiarioXmlService().construir(r, e);
        }
        boolean boleta = "03".equals(flujo); var r = new ComprobanteRequest(); r.setTiendaId(1L); r.setTipoComprobante(flujo);
        r.setSerie(serie(boleta ? "boleta" : "factura")); r.setTipoOperacion("0101"); r.setFormaPago("Contado");
        r.setTipoDocCliente(boleta ? "0" : "6"); r.setNumDocCliente(boleta ? "-" : ruc());
        r.setRznSocialCliente(boleta ? "CONSUMIDOR FINAL" : "RECEPTOR FICTICIO DE ENSAYO BETA");
        var i = new ComprobanteRequest.DetalleItemRequest(); i.setCantidad(BigDecimal.ONE); i.setValorUnitario(valor("original", "base"));
        i.setTipoAfectacionIgv("10"); i.setUnidadMedida("NIU"); i.setDescripcion(datos.get("descripcion").asText()); r.setItems(List.of(i));
        return builder.construirXmlUbl21(e, r, 1, calculo.calcular(r, new BigDecimal("18.00")), FECHA);
    }
    static Document parse(byte[] xml) throws Exception {
        var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }
    static void validarXsd(String flujo, byte[] xml) {
        var v = new UblValidatorService(new DefaultResourceLoader()); ReflectionTestUtils.setField(v, "xsdEnabled", true);
        ReflectionTestUtils.setField(v, "invoiceXsd", "classpath:sunat-xsd/2.1/maindoc/UBL-Invoice-2.1.xsd");
        ReflectionTestUtils.setField(v, "creditNoteXsd", "classpath:sunat-xsd/2.1/maindoc/UBL-CreditNote-2.1.xsd");
        ReflectionTestUtils.setField(v, "summaryXsd", "classpath:sunat-xsd/2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd");
        ReflectionTestUtils.setField(v, "voidedXsd", "classpath:sunat-xsd/2.0/maindoc/UBLPE-VoidedDocuments-1.0.xsd");
        if (flujo.startsWith("07")) v.validarNotaCredito(xml);
        else if (flujo.startsWith("RC")) v.validarResumenDiario(xml);
        else if ("RA".equals(flujo)) v.validarComunicacionBaja(xml); else v.validarFactura(xml);
    }
}
