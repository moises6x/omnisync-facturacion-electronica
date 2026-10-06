package org.example.service;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.NotaCreditoRequest;
import org.example.model.Comprobante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotaCreditoReglasServiceTest {

    private ValidacionTributariaService service;

    @BeforeEach
    void setUp() {
        service = new ValidacionTributariaService(new ReglasTributariasProperties());
    }

    @Test
    void debeRechazarTipoAfectadoDistintoAlOriginal() {
        NotaCreditoRequest r = request("03", "01");
        Comprobante origen = origen("01", "118.00");
        assertThrows(IllegalArgumentException.class,
                () -> service.validarContraComprobanteOriginal(r, origen, total("118.00")));
    }

    @Test
    void debeRechazarCambioDeDocumentoCliente() {
        NotaCreditoRequest r = request("01", "01");
        r.setNumDocCliente("87654321");
        assertThrows(IllegalArgumentException.class,
                () -> service.validarContraComprobanteOriginal(r, origen("01", "118.00"), total("118.00")));
    }

    @Test
    void notaCreditoNoPuedeSuperarMontoOriginal() {
        NotaCreditoRequest r = request("01", "04");
        assertThrows(IllegalArgumentException.class,
                () -> service.validarContraComprobanteOriginal(r, origen("01", "118.00"), total("118.01")));
    }

    @Test
    void motivo01DebeAfectarTotalCompleto() {
        NotaCreditoRequest r = request("01", "01");
        assertThrows(IllegalArgumentException.class,
                () -> service.validarContraComprobanteOriginal(r, origen("01", "118.00"), total("100.00")));
    }

    @Test
    void motivo03NoDebeCambiarImportes() {
        NotaCreditoRequest r = request("01", "03");
        assertThrows(IllegalArgumentException.class,
                () -> service.validarContraComprobanteOriginal(r, origen("01", "118.00"), total("100.00")));
    }

    @Test
    void motivosParcialesImplementadosPuedenSerMenoresAlOriginal() {
        for (String motivo : List.of("04", "05", "07", "08", "09")) {
            NotaCreditoRequest r = request("01", motivo);
            assertDoesNotThrow(
                    () -> service.validarContraComprobanteOriginal(r, origen("01", "118.00"), total("50.00")),
                    "Motivo " + motivo
            );
        }
    }

    @Test
    void motivoNoImplementadoDebeRechazarse() {
        NotaCreditoRequest r = request("01", "02");
        assertThrows(IllegalArgumentException.class,
                () -> service.validarContraComprobanteOriginal(r, origen("01", "118.00"), total("118.00")));
    }

    private NotaCreditoRequest request(String tipoAfectado, String motivo) {
        NotaCreditoRequest r = new NotaCreditoRequest();
        r.setTipoComprobanteAfectado(tipoAfectado);
        r.setTipoDocCliente("1");
        r.setNumDocCliente("48411579");
        r.setRznSocialCliente("CLIENTE TEST");
        r.setCodMotivoSunat(motivo);
        return r;
    }

    private Comprobante origen(String tipo, String total) {
        Comprobante c = new Comprobante();
        c.setTipoComprobante(tipo);
        c.setTipoDocCliente("1");
        c.setNumDocCliente("48411579");
        c.setRznSocialCliente("CLIENTE TEST");
        c.setOpGravada(new BigDecimal("100.00"));
        c.setOpExonerada(BigDecimal.ZERO);
        c.setOpInafecta(BigDecimal.ZERO);
        c.setIgv(new BigDecimal("18.00"));
        c.setMontoTotal(new BigDecimal(total));
        return c;
    }

    private CalculoTributarioService.Totales total(String total) {
        // Para totales completos 118 => base 100 / IGV 18.
        // Para parciales usamos el total como gravada simplificada en este test de límites.
        if ("118.00".equals(total)) {
            return new CalculoTributarioService.Totales(
                    new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("18.00"), new BigDecimal(total), List.of()
            );
        }
        return new CalculoTributarioService.Totales(
                new BigDecimal(total), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(total), List.of()
        );
    }
}
