package org.example.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.dto.NotaVentaRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void comprobanteDebeExigirTiendaTipoSerieTotalesEItems() {
        ComprobanteRequest r = new ComprobanteRequest();
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void comprobanteDebeRechazarTipoDistintoFacturaOBoleta() {
        ComprobanteRequest r = comprobanteMinimoValido();
        r.setTipoComprobante("99");
        assertTrue(validator.validate(r).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("tipoComprobante")));
    }

    @Test
    void comprobanteDebeRechazarSerieFueraDePatron() {
        ComprobanteRequest r = comprobanteMinimoValido();
        r.setSerie("X001");
        assertTrue(validator.validate(r).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("serie")));
    }

    @Test
    void itemDebeExigirCantidadYValorPositivos() {
        ComprobanteRequest r = comprobanteMinimoValido();
        r.getItems().get(0).setCantidad(BigDecimal.ZERO);
        r.getItems().get(0).setValorUnitario(BigDecimal.ZERO);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void notaVentaDebeRechazarMontoCero() {
        NotaVentaRequest r = new NotaVentaRequest();
        r.setTiendaId(1L);
        r.setMontoTotal(BigDecimal.ZERO);
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void notaCreditoDebeExigirComprobanteAfectadoYMotivo() {
        NotaCreditoRequest r = new NotaCreditoRequest();
        r.setTiendaId(1L);
        assertFalse(validator.validate(r).isEmpty());
    }

    private ComprobanteRequest comprobanteMinimoValido() {
        ComprobanteRequest r = new ComprobanteRequest();
        r.setTiendaId(1L);
        r.setTipoOperacion("0101");
        r.setTipoComprobante("03");
        r.setSerie("B001");
        r.setFormaPago("Contado");
        r.setOpGravada(new BigDecimal("100.00"));
        r.setOpExonerada(BigDecimal.ZERO);
        r.setOpInafecta(BigDecimal.ZERO);
        r.setIgv(new BigDecimal("18.00"));
        r.setTotal(new BigDecimal("118.00"));

        ComprobanteRequest.DetalleItemRequest i = new ComprobanteRequest.DetalleItemRequest();
        i.setCantidad(BigDecimal.ONE);
        i.setUnidadMedida("NIU");
        i.setDescripcion("ITEM");
        i.setValorUnitario(new BigDecimal("100.00"));
        i.setTipoAfectacionIgv("10");
        r.setItems(List.of(i));
        return r;
    }
}
