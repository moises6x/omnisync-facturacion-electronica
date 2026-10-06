package org.example.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Test de contrato HTTP/SOAP local.
 * Levanta un servidor HTTP efímero en localhost y comprueba el SOAP real
 * generado/interpretado por SunatDirectSoapClient sin tocar Internet ni SUNAT.
 */
class SunatDirectSoapClientContractTest {

    private HttpServer server;
    private SunatDirectSoapClient client;
    private AtomicReference<String> ultimoBody;
    private AtomicReference<String> ultimaSoapAction;
    private AtomicReference<Respuesta> respuesta;
    private boolean chunked;

    @BeforeEach
    void setUp() throws Exception {
        ultimoBody = new AtomicReference<>();
        ultimaSoapAction = new AtomicReference<>();
        respuesta = new AtomicReference<>(new Respuesta(200, ""));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sunat", this::atender);
        server.start();

        client = new SunatDirectSoapClient();
        ReflectionTestUtils.setField(client, "environment", "BETA");
        ReflectionTestUtils.setField(client, "sunatUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/sunat");
        ReflectionTestUtils.setField(client, "connectTimeout", 2000);
        ReflectionTestUtils.setField(client, "readTimeout", 2000);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendBillDebeEnviarSoapEsperadoYDecodificarCdr() {
        byte[] cdr = "ZIP-CDR".getBytes(StandardCharsets.UTF_8);
        responder(200, envelope("<applicationResponse>" + b64(cdr) + "</applicationResponse>"));

        byte[] resultado = client.enviarZipDirecto(
                "20100130204", "MODDATOS", "moddatos", "20100130204-01-F001-1.zip", new byte[]{1,2,3});

        assertArrayEquals(cdr, resultado);
        assertEquals("urn:sendBill", ultimaSoapAction.get());
        assertTrue(ultimoBody.get().contains("sendBill"));
        assertTrue(ultimoBody.get().contains("20100130204MODDATOS"));
        assertTrue(ultimoBody.get().contains("20100130204-01-F001-1.zip"));
        assertTrue(ultimoBody.get().contains(Base64.getEncoder().encodeToString(new byte[]{1,2,3})));
    }

    @Test
    void usuarioSolQueYaIncluyeRucNoDebeDuplicarlo() {
        responder(200, envelope("<applicationResponse>" + b64(new byte[]{1}) + "</applicationResponse>"));

        client.enviarZipDirecto(
                "20100130204", "20100130204MODDATOS", "clave", "x.zip", new byte[]{9});

        assertTrue(ultimoBody.get().contains("20100130204MODDATOS"));
        assertFalse(ultimoBody.get().contains("2010013020420100130204MODDATOS"));
    }

    @Test
    void sendSummaryDebeExtraerTicket() {
        responder(200, envelope("<ticket>178984300001</ticket>"));

        String ticket = client.enviarResumenDirecto(
                "20100130204", "MODDATOS", "moddatos", "rc.zip", new byte[]{1});

        assertEquals("178984300001", ticket);
        assertEquals("urn:sendSummary", ultimaSoapAction.get());
        assertTrue(ultimoBody.get().contains("sendSummary"));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 101, 150})
    void ticketHasta150DebeConservarseYConsultarseSinTruncar(int longitud) {
        String ticket = "T".repeat(longitud);
        responder(200, envelope("<ticket>" + ticket + "</ticket>"));
        assertEquals(ticket, client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "rc.zip", new byte[]{1}));
        responder(200, envelope("<statusCode>98</statusCode>"));
        assertTrue(client.consultarTicket("20100130204", "USR_TEST", "PWD_TEST", ticket).enProceso());
        assertTrue(ultimoBody.get().contains("<ticket>" + ticket + "</ticket>"));
    }

    @Test
    void ticketRecibidoMayorDe150DebeSerErrorDeProtocoloSinTruncar() {
        responder(200, envelope("<ticket>" + "T".repeat(151) + "</ticket>"));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "rc.zip", new byte[]{1}));
    }

    @Test
    void ticketDeConsultaMayorDe150DebeRechazarseAntesDeRed() {
        assertThrows(IllegalArgumentException.class,
                () -> client.consultarTicket("20100130204", "USR_TEST", "PWD_TEST", "T".repeat(151)));
        assertNull(ultimoBody.get());
    }

    @Test
    void getStatus98DebeQuedarEnProcesoSinContenido() {
        responder(200, envelope("<statusCode>98</statusCode>"));

        SunatDirectSoapClient.EstadoTicketSunat estado = client.consultarTicket(
                "20100130204", "MODDATOS", "moddatos", "TICKET-1");

        assertTrue(estado.enProceso());
        assertNull(estado.contentZip());
        assertEquals("urn:getStatus", ultimaSoapAction.get());
        assertTrue(ultimoBody.get().contains("TICKET-1"));
    }

    @Test
    void getStatus0DebeDecodificarContenidoCdr() {
        byte[] cdr = new byte[]{7,8,9};
        responder(200, envelope(
                "<statusCode>0</statusCode><content>" + b64(cdr) + "</content>"));

        SunatDirectSoapClient.EstadoTicketSunat estado = client.consultarTicket(
                "20100130204", "MODDATOS", "moddatos", "TICKET-2");

        assertTrue(estado.finalizadoCorrectamente());
        assertArrayEquals(cdr, estado.contentZip());
    }

    @Test
    void soapFaultDebeConvertirseEnExcepcionEspecifica() {
        String fault = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <soapenv:Fault>
                      <faultcode>soap-env:Client.3205</faultcode>
                      <faultstring>XML INVALIDO</faultstring>
                    </soapenv:Fault>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;
        responder(500, fault);

        SunatDirectSoapClient.SunatSoapFaultException ex = assertThrows(
                SunatDirectSoapClient.SunatSoapFaultException.class,
                () -> client.enviarZipDirecto(
                        "20100130204", "MODDATOS", "moddatos", "x.zip", new byte[]{1}));

        assertEquals("Client.3205", ex.getFaultCode());
        assertEquals("Respuesta SOAP Fault; requiere revisión técnica.", ex.getFaultString());
        assertFalse(ex.getMessage().contains("XML INVALIDO"));
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    void httpErrorNoSoapDebeSerTransportException() {
        responder(503, "SERVICIO NO DISPONIBLE");

        SunatDirectSoapClient.SunatTransportException ex = assertThrows(
                SunatDirectSoapClient.SunatTransportException.class,
                () -> client.enviarZipDirecto(
                        "20100130204", "MODDATOS", "moddatos", "x.zip", new byte[]{1}));

        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    void base64InvalidoDeSunatDebeSerProtocolException() {

        /*
         * "A" es Base64 estructuralmente inválido:
         * tiene únicamente un carácter útil y no puede
         * formar una unidad Base64 válida.
         *
         * Esto sí debe provocar IllegalArgumentException
         * en Base64.getDecoder(), que nuestro cliente
         * transforma en SunatProtocolException.
         */
        responder(
                200,
                envelope(
                        "<applicationResponse>A</applicationResponse>"
                )
        );

        assertThrows(
                SunatDirectSoapClient.SunatProtocolException.class,
                () ->
                        client.enviarZipDirecto(
                                "20100130204",
                                "MODDATOS",
                                "moddatos",
                                "x.zip",
                                new byte[]{1}
                        )
        );
    }

    @Test
    void validacionDeEndpointDebeRechazarHttp() {
        assertThrows(IllegalStateException.class, client::validarEndpoint);
    }

    private void atender(HttpExchange exchange) throws IOException {
        ultimaSoapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
        ultimoBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        Respuesta r = respuesta.get();
        byte[] body = r.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(r.status(), chunked ? 0 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void responder(int status, String body) {
        respuesta.set(new Respuesta(status, body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "fault", "xml"})
    void bodySensibleNoDebeAparecerEnExcepcionLogsNiResultadoPropagado(String caso) {
        String marcador = "SECRETO_CONTRIBUYENTE_FICTICIO_987654";
        String body = switch (caso) {
            case "fault" -> """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                    <soapenv:Body><soapenv:Fault><faultcode>%s</faultcode><faultstring>%s</faultstring>
                    </soapenv:Fault></soapenv:Body></soapenv:Envelope>
                    """.formatted(marcador, marcador);
            case "xml" -> "<" + marcador + "><sinCierre>";
            default -> "<html>" + marcador + "</html>";
        };
        responder(caso.equals("xml") ? 200 : 503, body);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        var stderr = new java.io.ByteArrayOutputStream();
        var anterior = System.err;
        try (var capturado = new java.io.PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setErr(capturado);
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    client.enviarZipDirecto("20100130204", "USR_TEST", "PWD_TEST", "f.zip", new byte[]{1}));
            var stack = new java.io.StringWriter(); ex.printStackTrace(new java.io.PrintWriter(stack));
            assertFalse(stack.toString().contains(marcador));
            if (!caso.equals("xml")) assertTrue(ex.getMessage().contains("503"));

            var docs = mock(org.example.repository.ComprobanteRepository.class);
            var empresas = mock(org.example.repository.EmpresaRepository.class);
            var crypto = mock(CryptoService.class);
            var empresa = mock(org.example.model.Empresa.class);
            when(empresa.getRuc()).thenReturn("20100130204");
            when(empresa.tieneConfiguracionSunat()).thenReturn(true);
            when(crypto.descifrarUsuarioSol(any(), any())).thenReturn("USR_TEST");
            when(crypto.descifrarClaveSol(any(), any())).thenReturn("PWD_TEST");
            var doc = org.example.model.Comprobante.builder().id(1L).rucEmisor("20100130204")
                    .tipoComprobante("01").nombreArchivoXml("f.xml").xmlFirmadoBytes(new byte[]{1}).build();
            when(docs.marcarEnviando(1L)).thenReturn(1);
            when(docs.findById(1L)).thenReturn(java.util.Optional.of(doc));
            when(empresas.findByRuc("20100130204")).thenReturn(java.util.Optional.of(empresa));
            var service = new EnvioSunatService(docs, empresas, client, mock(CdrParserService.class),
                    crypto, mock(SunatPlazoService.class), mock(RespuestaSunatRecibidaService.class), mock(SaldoNotaCreditoService.class));
            service.enviar(1L);
            assertFalse(doc.getMensajeSunat().contains(marcador));
            assertFalse(stderr.toString(StandardCharsets.UTF_8).contains(marcador));
            assertFalse(logs.list.isEmpty());
            for (var evento : logs.list) {
                assertFalse(evento.getFormattedMessage().contains(marcador));
                if (evento.getThrowableProxy() != null)
                    assertFalse(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(evento.getThrowableProxy()).contains(marcador));
            }
        } finally { System.setErr(anterior); logger.detachAppender(logs); logs.stop(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cierre", "timeout"})
    void conexionRecibePeticionPeroNoRespuestaDebeSerTransporteIncierto(String caso) throws Exception {
        var recibida = new java.util.concurrent.CountDownLatch(1);
        var liberar = new java.util.concurrent.CountDownLatch(1);
        var peticiones = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/sin-respuesta", exchange -> {
            ultimoBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            peticiones.incrementAndGet();
            recibida.countDown();
            try {
                if (caso.equals("timeout")) liberar.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        ReflectionTestUtils.setField(client, "sunatUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/sin-respuesta");
        ReflectionTestUtils.setField(client, "readTimeout", 150);
        try {
            assertThrows(SunatDirectSoapClient.SunatTransportException.class, () ->
                    client.enviarZipDirecto("20100130204", "USR_TEST", "PWD_TEST", "f.zip", new byte[]{1}));
            assertTrue(recibida.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(ultimoBody.get().contains("sendBill"));
            assertEquals(1, peticiones.get(), "El JDK no debe repetir el POST recibido");
        } finally { liberar.countDown(); }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void redirectNuncaDebeTransmitirSoapAlSegundoServidor(int codigo) throws Exception {
        var peticiones = new java.util.concurrent.atomic.AtomicInteger();
        var bytes = new java.util.concurrent.atomic.AtomicInteger();
        var segundo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        segundo.createContext("/destino", exchange -> {
            peticiones.incrementAndGet();
            bytes.addAndGet(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        segundo.start();
        server.createContext("/redirect", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + segundo.getAddress().getPort() + "/destino");
            exchange.sendResponseHeaders(codigo, -1); exchange.close();
        });
        ReflectionTestUtils.setField(client, "sunatUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");
        try {
            assertThrows(SunatDirectSoapClient.SunatTransportException.class, () ->
                    client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "r.zip", new byte[]{1}));
            assertEquals(0, peticiones.get()); assertEquals(0, bytes.get());
        } finally { segundo.stop(0); }
    }

    private String envelope(String inner) {
        if (inner.startsWith("<applicationResponse>")) inner = operacion("sendBillResponse", inner);
        else if (inner.startsWith("<ticket>")) inner = operacion("sendSummaryResponse", inner);
        else if (inner.startsWith("<statusCode>")) inner = operacion("getStatusResponse", "<status>" + inner + "</status>");
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body>" + inner + "</soapenv:Body></soapenv:Envelope>";
    }

    private String operacion(String nombre, String campos) {
        return "<s:" + nombre + " xmlns:s=\"http://service.sunat.gob.pe\">" + campos + "</s:" + nombre + ">";
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<applicationResponse>AQ==</applicationResponse>",
            "<s:sendSummaryResponse xmlns:s='http://service.sunat.gob.pe'><applicationResponse>AQ==</applicationResponse></s:sendSummaryResponse>",
            "<s:sendBillResponse xmlns:s='urn:incorrecto'><applicationResponse>AQ==</applicationResponse></s:sendBillResponse>",
            "<s:sendBillResponse xmlns:s='http://service.sunat.gob.pe'><otra><applicationResponse>AQ==</applicationResponse></otra></s:sendBillResponse>",
            "<s:sendBillResponse xmlns:s='http://service.sunat.gob.pe'><applicationResponse>AQ==</applicationResponse><applicationResponse>Ag==</applicationResponse></s:sendBillResponse>",
            "<s:sendBillResponse xmlns:s='http://service.sunat.gob.pe'><applicationResponse><otro>AQ==</otro></applicationResponse></s:sendBillResponse>",
            "<s:sendBillResponse xmlns:s='http://service.sunat.gob.pe'><s:applicationResponse>AQ==</s:applicationResponse></s:sendBillResponse>",
            "<s:sendBillResponse xmlns:s='http://service.sunat.gob.pe'><applicationResponse>AQ==</applicationResponse></s:sendBillResponse><otro/>"
    })
    void debeRechazarRespuestasFueraDeContextoOAmbiguas(String body) {
        // Envoltura explícita: no usar el helper que completa fixtures positivos.
        responder(200, "<soap:Envelope xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/'><soap:Body>" + body + "</soap:Body></soap:Envelope>");
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.enviarZipDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "97", "100", "00", "ABC"})
    void estadoDesconocidoNoDebeFinalizarAunqueAdjunteContenido(String codigo) {
        responder(200, envelope("<statusCode>" + codigo + "</statusCode><content>AQ==</content>"));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.consultarTicket("20100130204", "USR_TEST", "PWD_TEST", "TICKET_TEST"));
    }

    @Test
    void debeAceptar99YWhitespaceBase64Documentado() {
        responder(200, envelope("<statusCode>99</statusCode><content> A Q==\r\n\t</content>"));
        var estado = client.consultarTicket("20100130204", "USR_TEST", "PWD_TEST", "TICKET_TEST");
        assertEquals("99", estado.statusCode()); assertArrayEquals(new byte[]{1}, estado.contentZip());
    }

    @Test
    void noDebeIgnorarBasuraNoWhitespaceDentroDeBase64() {
        responder(200, envelope("<applicationResponse>A!Q==</applicationResponse>"));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.enviarZipDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @Test
    void faultEnHeaderNoDebeSustituirRespuestaDelBody() {
        responder(200, "<soap:Envelope xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/'>"
                + "<soap:Header><soap:Fault><faultcode>FAKE</faultcode><faultstring>FAKE</faultstring></soap:Fault></soap:Header>"
                + "<soap:Body>" + operacion("sendSummaryResponse", "<ticket>TICKET_TEST</ticket>") + "</soap:Body></soap:Envelope>");
        assertEquals("TICKET_TEST", client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @Test
    void statusCodeFueraDelObjetoStatusNoDebeDeterminarResultado() {
        responder(200, envelope(operacion("getStatusResponse", "<statusCode>0</statusCode><status><statusCode>98</statusCode></status>")));
        assertTrue(client.consultarTicket("20100130204", "USR_TEST", "PWD_TEST", "TICKET_TEST").enProceso());
    }

    private String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private record Respuesta(int status, String body) {}

    @Test
    void debeRechazarHttpDemasiadoGrandePorContentLength() {
        ReflectionTestUtils.setField(client, "maxSoapBytes", 32);
        responder(200, envelope("<ticket>TICKET_TEST</ticket>"));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @Test
    void debeDetenerRespuestaChunkedSinContentLength() {
        chunked = true; ReflectionTestUtils.setField(client, "maxSoapBytes", 32);
        responder(200, envelope("<ticket>TICKET_TEST</ticket>"));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @Test
    void debeAceptarRespuestaEnLimiteHttpExacto() {
        String body = envelope("<ticket>TICKET_TEST</ticket>");
        ReflectionTestUtils.setField(client, "maxSoapBytes", body.getBytes(StandardCharsets.UTF_8).length);
        responder(200, body);
        assertEquals("TICKET_TEST", client.enviarResumenDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @Test
    void debeRechazarZipDecodificadoDemasiadoGrandeAntesDeAsignarlo() {
        ReflectionTestUtils.setField(client, "maxZipBytes", 2);
        responder(200, envelope("<applicationResponse>" + b64(new byte[]{1,2,3}) + "</applicationResponse>"));
        assertThrows(SunatDirectSoapClient.SunatProtocolException.class,
                () -> client.enviarZipDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }

    @Test
    void debeAceptarZipDecodificadoEnLimiteExactoIncluidoPadding() {
        ReflectionTestUtils.setField(client, "maxZipBytes", 2);
        responder(200, envelope("<applicationResponse>" + b64(new byte[]{1,2}) + "</applicationResponse>"));
        assertArrayEquals(new byte[]{1,2}, client.enviarZipDirecto("20100130204", "USR_TEST", "PWD_TEST", "test.zip", new byte[]{1}));
    }
}
