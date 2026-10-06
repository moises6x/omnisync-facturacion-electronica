package org.example.service;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalculoTributarioServiceTest {

    private CalculoTributarioService service;

    @BeforeEach
    void setUp() {
        ReglasTributariasProperties reglas = new ReglasTributariasProperties();
        reglas.setIgvPorcentaje(new BigDecimal("18.00"));
        service = new CalculoTributarioService(reglas);
    }

    @Test
    void debeCalcularOperacionGravadaConIgv18() {
        ComprobanteRequest request = requestConUnItem("10", "100.00", null);
        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal("18.00"));
        request.setTotal(new BigDecimal("118.00"));

        var total = service.calcular(request);

        assertEquals(0, total.opGravada().compareTo(new BigDecimal("100.00")));
        assertEquals(0, total.igv().compareTo(new BigDecimal("18.00")));
        assertEquals(0, total.total().compareTo(new BigDecimal("118.00")));
        assertEquals(1, total.items().size());
        assertEquals(0, total.items().get(0).precioUnitario().compareTo(new BigDecimal("118.0000")));
    }

    @Test
    void debeCalcularOperacionExoneradaSinIgv() {
        ComprobanteRequest request = requestConUnItem("20", "100.00", BigDecimal.ZERO);
        request.setOpGravada(BigDecimal.ZERO);
        request.setOpExonerada(new BigDecimal("100.00"));
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(BigDecimal.ZERO);
        request.setTotal(new BigDecimal("100.00"));

        var total = service.calcular(request);

        assertEquals(0, total.opExonerada().compareTo(new BigDecimal("100.00")));
        assertEquals(0, total.igv().compareTo(BigDecimal.ZERO));
    }

    @Test
    void debeCalcularOperacionInafectaSinIgv() {
        ComprobanteRequest request = requestConUnItem("30", "75.50", BigDecimal.ZERO);
        request.setOpGravada(BigDecimal.ZERO);
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(new BigDecimal("75.50"));
        request.setIgv(BigDecimal.ZERO);
        request.setTotal(new BigDecimal("75.50"));

        var total = service.calcular(request);
        assertEquals(0, total.opInafecta().compareTo(new BigDecimal("75.50")));
    }

    @Test
    void debeSumarCorrectamenteItemsMixtos() {
        ComprobanteRequest request = new ComprobanteRequest();
        request.setItems(List.of(
                item("10", "100.00", null),
                item("20", "50.00", BigDecimal.ZERO),
                item("30", "25.00", BigDecimal.ZERO)
        ));
        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(new BigDecimal("50.00"));
        request.setOpInafecta(new BigDecimal("25.00"));
        request.setIgv(new BigDecimal("18.00"));
        request.setTotal(new BigDecimal("193.00"));

        var total = service.calcular(request);
        assertEquals(0, total.total().compareTo(new BigDecimal("193.00")));
        assertEquals(3, total.items().size());
    }

    @Test
    void debeRechazarTotalManipuladoPorFrontend() {
        ComprobanteRequest request = requestConUnItem("10", "100.00", null);
        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal("18.00"));
        request.setTotal(new BigDecimal("100.00"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.calcular(request)
        );
        assertTrue(ex.getMessage().contains("total incorrecto"));
    }

    @Test
    void debeAceptarDiferenciaExactaDeUnCentimo() {
        ComprobanteRequest request = requestConUnItem("10", "100.00", null);
        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal("18.00"));
        request.setTotal(new BigDecimal("118.01"));

        assertDoesNotThrow(() -> service.calcular(request));
    }

    @Test
    void debeRechazarPrecioUnitarioManipulado() {
        ComprobanteRequest request = requestConUnItem("10", "100.00", null);
        request.getItems().get(0).setPrecioUnitario(new BigDecimal("120.00"));
        completarTotalesGravados(request, "100.00", "18.00", "118.00");

        assertThrows(IllegalArgumentException.class, () -> service.calcular(request));
    }

    @Test
    void debeRechazarGravadoConPorcentajeCero() {
        ComprobanteRequest request = requestConUnItem("10", "100.00", BigDecimal.ZERO);
        completarTotalesGravados(request, "100.00", "0.00", "100.00");

        assertThrows(IllegalArgumentException.class, () -> service.calcular(request));
    }

    @Test
    void debeRechazarExoneradoConIgvNoCero() {
        ComprobanteRequest request = requestConUnItem("20", "100.00", new BigDecimal("18"));
        request.setOpGravada(BigDecimal.ZERO);
        request.setOpExonerada(new BigDecimal("100.00"));
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(BigDecimal.ZERO);
        request.setTotal(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () -> service.calcular(request));
    }

    @Test
    void notaCreditoDebeUsarMismaLogicaTributaria() {
        NotaCreditoRequest request = new NotaCreditoRequest();
        NotaCreditoRequest.DetalleItemRequest item = new NotaCreditoRequest.DetalleItemRequest();
        item.setCantidad(BigDecimal.ONE);
        item.setUnidadMedida("NIU");
        item.setDescripcion("ANULACION");
        item.setValorUnitario(new BigDecimal("100.00"));
        item.setTipoAfectacionIgv("10");
        request.setItems(List.of(item));
        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal("18.00"));
        request.setTotal(new BigDecimal("118.00"));

        var total = service.calcular(request);
        assertEquals(0, total.total().compareTo(new BigDecimal("118.00")));
    }

    private ComprobanteRequest requestConUnItem(String afectacion, String valor, BigDecimal porcentaje) {
        ComprobanteRequest request = new ComprobanteRequest();
        request.setItems(List.of(item(afectacion, valor, porcentaje)));
        return request;
    }

    private ComprobanteRequest.DetalleItemRequest item(String afectacion, String valor, BigDecimal porcentaje) {
        ComprobanteRequest.DetalleItemRequest item = new ComprobanteRequest.DetalleItemRequest();
        item.setCantidad(BigDecimal.ONE);
        item.setUnidadMedida("NIU");
        item.setDescripcion("ITEM TEST");
        item.setValorUnitario(new BigDecimal(valor));
        item.setTipoAfectacionIgv(afectacion);
        item.setPorcentajeIgv(porcentaje);
        return item;
    }

    private void completarTotalesGravados(ComprobanteRequest request, String base, String igv, String total) {
        request.setOpGravada(new BigDecimal(base));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal(igv));
        request.setTotal(new BigDecimal(total));
    }
}
