package org.example.service;

import org.example.model.Comprobante;
import org.example.model.EstadoComprobante;
import org.example.repository.ComprobanteRepository;
import org.example.repository.ComprobanteRepository.NotaCreditoSaldoProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaldoNotaCreditoServiceTest {
    private ComprobanteRepository repository;
    private SaldoNotaCreditoService service;
    private Comprobante original;

    @BeforeEach
    void preparar() {
        repository = mock(ComprobanteRepository.class);
        service = new SaldoNotaCreditoService(repository, new CdrParserService());
        original = Comprobante.builder().id(1L).rucEmisor("20100130204").tipoComprobante("01")
                .serie("F001").numero(1).moneda("PEN").montoTotal(bd("100"))
                .opGravada(bd("40")).opExonerada(bd("30")).opInafecta(bd("20")).igv(bd("10")).build();
    }

    @Test
    void multiplesParcialesValidasSumanConsumoYReservaPorComponentes() {
        historial(nota("04", "ACEPTADO", "30", "10", "10", "5", "5"),
                nota("07", "PENDIENTE_ENVIO", "20", "10", "5", "5", "0"));
        assertDoesNotThrow(() -> service.validar(original, "09", totales("50", "20", "15", "10", "5")));
    }

    @Test
    void motivo03NoConsultaNiReservaSaldoAunqueHistoricoSeaAmbiguo() {
        service.validar(original, "03", totales("100", "40", "30", "20", "10"));
        verifyNoInteractions(repository);
    }

    @Test
    void nc03HistoricaNoRestaImportesInformativos() {
        historial(nota("03", "ACEPTADO", "100", "40", "30", "20", "10"));
        assertDoesNotThrow(() -> service.validar(original, "01", totales("100", "40", "30", "20", "10")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "06"})
    void motivosTotalesNoAdmitenAfectacionPrevia(String motivo) {
        historial(nota("04", "ACEPTADO", "10", "10", "0", "0", "0"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, motivo, totales("100", "40", "30", "20", "10")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACEPTADO", "ACEPTADO_CON_OBSERVACION", "PENDIENTE_ENVIO", "ENVIANDO", "PENDIENTE_RESUMEN",
            "EN_RESUMEN", "ERROR_ENVIO_INCIERTO", "ERROR_ENVIO", "ERROR_VALIDACION", "FUERA_DE_PLAZO", "ANULADO", "RECHAZADO"})
    void estadosSinLiberacionDemostradaOcupanSaldo(String estado) {
        historial(nota("04", estado, "70", "30", "20", "10", "10"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "09", totales("70", "30", "20", "10", "10")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gravada", "exonerada", "inafecta", "igv"})
    void totalDisponibleNoCompensaExcesoDeComponente(String componente) {
        historial(nota("04", "ACEPTADO", "45", "20", "10", "10", "5"));
        var solicitud = switch (componente) {
            case "gravada" -> totales("21", "21", "0", "0", "0");
            case "exonerada" -> totales("21", "0", "21", "0", "0");
            case "inafecta" -> totales("11", "0", "0", "11", "0");
            default -> totales("6", "0", "0", "0", "6");
        };
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "09", solicitud));
    }

    @Test
    void historicoSobreafectadoBloqueaNuevaNc() {
        historial(nota("04", "ACEPTADO", "110", "40", "30", "30", "10"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "09", totales("1", "1", "0", "0", "0")));
    }

    @Test
    void motivoHistoricoDesconocidoBloqueaNuevaNc() {
        historial(nota(null, "ACEPTADO", "10", "10", "0", "0", "0"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "09", totales("1", "1", "0", "0", "0")));
    }

    @Test
    void referenciaHistoricaIncompletaBloqueaSinInventarOriginal() {
        when(repository.contarNotasCreditoSinReferencia(original.getRucEmisor())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "09", totales("1", "1", "0", "0", "0")));
        verify(repository, never()).bloquearNotasCreditoDelOriginal(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void monedaDiferenteEsAmbigua() {
        var datos = datos("04", "ACEPTADO", "10", "10", "0", "0", "0"); datos.put("moneda", "USD");
        historial(proyeccion(datos));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "09", totales("1", "1", "0", "0", "0")));
    }

    @Test
    void rechazoIndividualConCdrValidoLiberaYNoBloqueaAnulacionTotal() throws Exception {
        historial(rechazo("FC01-1", "01", "2100"));
        assertDoesNotThrow(() -> service.validar(original, "01", totales("100", "40", "30", "20", "10")));
    }

    @Test
    void rechazoDeNcBoletaNoLiberaAunqueExistaZip() throws Exception {
        original.setTipoComprobante("03"); original.setSerie("B001");
        historial(rechazo("FC01-1", "01", "2100"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "01", totales("100", "40", "30", "20", "10")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTRA-1", "FC01-2"})
    void rechazoDeOtroDocumentoNoLibera(String referencia) throws Exception {
        historial(rechazo(referencia, "01", "2100"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "01", totales("100", "40", "30", "20", "10")));
    }

    @Test
    void rechazoConCodigoNoConcordanteMantieneReserva() throws Exception {
        historial(rechazo("FC01-1", "01", "9999"));
        assertThrows(IllegalArgumentException.class, () -> service.validar(original, "01", totales("100", "40", "30", "20", "10")));
    }

    @Test
    void resultadoSeGuardaBajoOriginalAntesDeNc() {
        Comprobante resultado = resultado(EstadoComprobante.ACEPTADO);
        Comprobante actual = resultado(EstadoComprobante.ENVIANDO);
        when(repository.bloquearOriginalNotaCredito("20100130204", "01", "F001", 1)).thenReturn(Optional.of(original));
        when(repository.bloquearPorIdYRuc(2L, "20100130204")).thenReturn(Optional.of(actual));
        service.guardarResultadoIndividual(resultado);
        var orden = inOrder(repository);
        orden.verify(repository).bloquearOriginalNotaCredito("20100130204", "01", "F001", 1);
        orden.verify(repository).bloquearPorIdYRuc(2L, "20100130204");
        orden.verify(repository).saveAndFlush(actual);
        assertEquals(EstadoComprobante.ACEPTADO, actual.getEstadoSfs());
    }

    @Test
    void respuestaTardiaNoReabreRechazoTerminal() {
        Comprobante actual = resultado(EstadoComprobante.RECHAZADO);
        when(repository.bloquearOriginalNotaCredito("20100130204", "01", "F001", 1)).thenReturn(Optional.of(original));
        when(repository.bloquearPorIdYRuc(2L, "20100130204")).thenReturn(Optional.of(actual));
        service.guardarResultadoIndividual(resultado(EstadoComprobante.ACEPTADO));
        assertEquals(EstadoComprobante.RECHAZADO, actual.getEstadoSfs());
        verify(repository, never()).saveAndFlush(any());
    }

    private Comprobante resultado(EstadoComprobante estado) {
        return Comprobante.builder().id(2L).rucEmisor(original.getRucEmisor()).tipoComprobante("07")
                .tipoComprobanteAfectado("01").serieComprobanteAfectado("F001").numeroComprobanteAfectado(1).estadoSfs(estado).build();
    }

    private NotaCreditoSaldoProjection rechazo(String referencia, String motivo, String codigoPersistido) throws Exception {
        String xml = CdrParserServiceTest.applicationResponse("2100", "RECHAZADO", "").replace("F001-00000001", referencia);
        byte[] zip = CdrParserServiceTest.zipXml("R.xml", xml);
        var datos = datos(motivo, "RECHAZADO", "100", "40", "30", "20", "10");
        datos.put("codigo", codigoPersistido); datos.put("cdrZip", zip); datos.put("cdrXml", new CdrParserService().procesar(zip).xml());
        return proyeccion(datos);
    }

    private void historial(NotaCreditoSaldoProjection... notas) {
        when(repository.bloquearNotasCreditoDelOriginal(original.getRucEmisor(), original.getTipoComprobante(), original.getSerie(), 1))
                .thenReturn(List.of(notas));
    }

    private static NotaCreditoSaldoProjection nota(String motivo, String estado, String total, String gravada, String exonerada, String inafecta, String igv) {
        return proyeccion(datos(motivo, estado, total, gravada, exonerada, inafecta, igv));
    }

    private static HashMap<String, Object> datos(String motivo, String estado, String total, String gravada, String exonerada, String inafecta, String igv) {
        var datos = new HashMap<String, Object>();
        datos.put("id", 2L); datos.put("moneda", "PEN"); datos.put("motivo", motivo); datos.put("estado", estado);
        datos.put("total", bd(total)); datos.put("gravada", bd(gravada)); datos.put("exonerada", bd(exonerada));
        datos.put("inafecta", bd(inafecta)); datos.put("igv", bd(igv)); datos.put("serie", "FC01"); datos.put("numero", 1);
        return datos;
    }

    private static NotaCreditoSaldoProjection proyeccion(HashMap<String, Object> datos) {
        return new SpelAwareProxyProjectionFactory().createProjection(NotaCreditoSaldoProjection.class, datos);
    }

    private static CalculoTributarioService.Totales totales(String total, String gravada, String exonerada, String inafecta, String igv) {
        return new CalculoTributarioService.Totales(bd(gravada), bd(exonerada), bd(inafecta), bd(igv), bd(total), List.of());
    }

    private static BigDecimal bd(String importe) { return new BigDecimal(importe); }
}
