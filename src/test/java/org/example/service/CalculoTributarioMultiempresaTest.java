package org.example.service;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculoTributarioMultiempresaTest {

    private CalculoTributarioService service;

    @BeforeEach
    void setUp() {
        ReglasTributariasProperties reglas =
                new ReglasTributariasProperties();

        reglas.setIgvPorcentaje(
                new BigDecimal("18.00")
        );

        service =
                new CalculoTributarioService(
                        reglas
                );
    }

    @Test
    void empresaConTasa105DebeCalcularUsando105() {

        ComprobanteRequest request =
                comprobanteGravado(
                        null,
                        "10.50",
                        "110.50"
                );

        var totales =
                service.calcular(
                        request,
                        new BigDecimal("10.50")
                );

        assertEquals(
                0,
                totales.igv().compareTo(
                        new BigDecimal("10.50")
                )
        );

        assertEquals(
                0,
                totales.total().compareTo(
                        new BigDecimal("110.50")
                )
        );

        assertEquals(
                0,
                totales.items().get(0).porcentajeIgv()
                        .compareTo(
                                new BigDecimal("10.50")
                        )
        );
    }

    @Test
    void empresa105DebeRechazarSiFrontendIntenta18() {

        ComprobanteRequest request =
                comprobanteGravado(
                        new BigDecimal("18.00"),
                        "10.50",
                        "110.50"
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.calcular(
                                request,
                                new BigDecimal("10.50")
                        )
        );
    }

    @Test
    void empresa18DebeRechazarSiFrontendIntenta105() {

        ComprobanteRequest request =
                comprobanteGravado(
                        new BigDecimal("10.50"),
                        "18.00",
                        "118.00"
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.calcular(
                                request,
                                new BigDecimal("18.00")
                        )
        );
    }

    @Test
    void notaCreditoDebePoderUsarTasaHistorica105() {

        NotaCreditoRequest request =
                new NotaCreditoRequest();

        NotaCreditoRequest.DetalleItemRequest item =
                new NotaCreditoRequest.DetalleItemRequest();

        item.setCantidad(BigDecimal.ONE);
        item.setUnidadMedida("NIU");
        item.setDescripcion("AJUSTE");
        item.setValorUnitario(new BigDecimal("100.00"));
        item.setTipoAfectacionIgv("10");

        request.setItems(
                List.of(item)
        );

        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal("10.50"));
        request.setTotal(new BigDecimal("110.50"));

        var totales =
                service.calcular(
                        request,
                        new BigDecimal("10.50")
                );

        assertEquals(
                0,
                totales.total().compareTo(
                        new BigDecimal("110.50")
                )
        );
    }

    private ComprobanteRequest comprobanteGravado(
            BigDecimal porcentajeFrontend,
            String igv,
            String total
    ) {

        ComprobanteRequest.DetalleItemRequest item =
                new ComprobanteRequest.DetalleItemRequest();

        item.setCantidad(BigDecimal.ONE);
        item.setUnidadMedida("NIU");
        item.setDescripcion("ITEM MULTIEMPRESA");
        item.setValorUnitario(new BigDecimal("100.00"));
        item.setTipoAfectacionIgv("10");
        item.setPorcentajeIgv(porcentajeFrontend);

        ComprobanteRequest request =
                new ComprobanteRequest();

        request.setItems(
                List.of(item)
        );

        request.setOpGravada(new BigDecimal("100.00"));
        request.setOpExonerada(BigDecimal.ZERO);
        request.setOpInafecta(BigDecimal.ZERO);
        request.setIgv(new BigDecimal(igv));
        request.setTotal(new BigDecimal(total));

        return request;
    }
}
