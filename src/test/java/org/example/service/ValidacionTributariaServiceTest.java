package org.example.service;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidacionTributariaServiceTest {

    private ValidacionTributariaService service;

    @BeforeEach
    void setUp() {
        ReglasTributariasProperties reglas = new ReglasTributariasProperties();
        reglas.setBoletaIdentificacionUmbral(new BigDecimal("700.00"));
        reglas.setTiposOperacionSoportados(Set.of("0101"));
        service = new ValidacionTributariaService(reglas);
    }

    @Test
    void debeRechazarTipoOperacionNoSoportado() {
        ComprobanteRequest r = facturaValida();
        r.setTipoOperacion("9999");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void facturaDebeUsarSerieF() {
        ComprobanteRequest r = facturaValida();
        r.setSerie("B001");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void facturaDebeTenerClienteRucCompleto() {
        ComprobanteRequest r = facturaValida();
        r.setNumDocCliente(null);
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void facturaDebeUsarTipoDocumento6() {
        ComprobanteRequest r = facturaValida();
        r.setTipoDocCliente("1");
        r.setNumDocCliente("48411579");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void facturaDebeRechazarRucInvalido() {
        ComprobanteRequest r = facturaValida();
        r.setNumDocCliente("20100130205");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void facturaValidaDebePasar() {
        assertDoesNotThrow(() -> service.validar(facturaValida(), totales("118.00")));
    }

    @Test
    void boletaAnonimaMenorAlUmbralDebeNormalizarConsumidorFinal() {
        ComprobanteRequest r = boletaBase();
        service.validar(r, totales("699.99"));
        assertEquals("0", r.getTipoDocCliente());
        assertEquals("-", r.getNumDocCliente());
        assertEquals("CONSUMIDOR FINAL", r.getRznSocialCliente());
    }

    @Test
    void boletaAnonimaExactamenteEnUmbralDebePasar() {
        ComprobanteRequest r = boletaBase();
        assertDoesNotThrow(() -> service.validar(r, totales("700.00")));
    }

    @Test
    void boletaAnonimaSobreUmbralDebeExigirIdentificacion() {
        ComprobanteRequest r = boletaBase();
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("700.01")));
    }

    @Test
    void boletaConIdentificacionParcialDebeRechazarse() {
        ComprobanteRequest r = boletaBase();
        r.setTipoDocCliente("1");
        r.setNumDocCliente("48411579");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("100.00")));
    }

    @Test
    void boletaConDniDebeTenerOchoDigitos() {
        ComprobanteRequest r = boletaBase();
        r.setTipoDocCliente("1");
        r.setNumDocCliente("123");
        r.setRznSocialCliente("CLIENTE");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("100.00")));
    }

    @Test
    void boletaConDniValidoDebePasar() {
        ComprobanteRequest r = boletaBase();
        r.setTipoDocCliente("1");
        r.setNumDocCliente("48411579");
        r.setRznSocialCliente(" GERARDO ");
        assertDoesNotThrow(() -> service.validar(r, totales("100.00")));
        assertEquals("GERARDO", r.getRznSocialCliente());
    }

    @Test
    void tipoDocumentoCeroSoloDebeAceptarGuion() {
        ComprobanteRequest r = boletaBase();
        r.setTipoDocCliente("0");
        r.setNumDocCliente("123");
        r.setRznSocialCliente("CONSUMIDOR FINAL");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("100.00")));
    }

    @Test
    void ventaCreditoDebeExigirMontoPendiente() {
        ComprobanteRequest r = facturaValida();
        r.setFormaPago("Credito");
        r.setMontoPendientePago(null);
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void ventaCreditoDebeExigirCuotas() {
        ComprobanteRequest r = facturaValida();
        r.setFormaPago("Credito");
        r.setMontoPendientePago(new BigDecimal("118.00"));
        r.setCuotas(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void ventaCreditoDebeExigirSumaDeCuotasExacta() {
        ComprobanteRequest r = facturaValida();
        r.setFormaPago("Credito");
        r.setMontoPendientePago(new BigDecimal("118.00"));
        r.setCuotas(List.of(cuota("100.00")));
        assertThrows(IllegalArgumentException.class, () -> service.validar(r, totales("118.00")));
    }

    @Test
    void ventaCreditoConCuotasCorrectasDebePasar() {
        ComprobanteRequest r = facturaValida();
        r.setFormaPago("Credito");
        r.setMontoPendientePago(new BigDecimal("118.00"));
        r.setCuotas(List.of(cuota("60.00"), cuota("58.00")));
        assertDoesNotThrow(() -> service.validar(r, totales("118.00")));
    }

    @Test
    void notaCreditoDeFacturaDebeUsarSerieF() {
        NotaCreditoRequest r = ncValida("01", "BC01");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r));
    }

    @Test
    void notaCreditoDeBoletaDebeUsarSerieB() {
        NotaCreditoRequest r = ncValida("03", "FC01");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r));
    }

    @Test
    void notaCreditoDebeConservarDatosClienteValidos() {
        NotaCreditoRequest r = ncValida("03", "BC01");
        r.setTipoDocCliente("1");
        r.setNumDocCliente("123");
        assertThrows(IllegalArgumentException.class, () -> service.validar(r));
    }

    @Test
    void notaCreditoDeConsumidorFinalNormalizadoDebePasar() {
        NotaCreditoRequest r = ncValida("03", "BC01");
        r.setTipoDocCliente("0");
        r.setNumDocCliente("-");
        r.setRznSocialCliente("CONSUMIDOR FINAL");
        assertDoesNotThrow(() -> service.validar(r));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "AB", "CLIENTE\tTEST", "CLIENTE\nTEST", "CLIENTE\rTEST", "CLIENTE\u0000TEST"})
    void debeRechazarNombreReceptorIncompatibleCon2022(String nombre) {
        ComprobanteRequest factura = facturaValida(); factura.setRznSocialCliente(nombre);
        assertThrows(IllegalArgumentException.class, () -> service.validar(factura, totales("118")));
        ComprobanteRequest boleta = new ComprobanteRequest(); boleta.setTipoOperacion("0101"); boleta.setTipoComprobante("03"); boleta.setSerie("B001");
        boleta.setFormaPago("Contado"); boleta.setTipoDocCliente("1"); boleta.setNumDocCliente("12345678"); boleta.setRznSocialCliente(nombre);
        assertThrows(IllegalArgumentException.class, () -> service.validar(boleta, totales("100")));
        NotaCreditoRequest nc = new NotaCreditoRequest(); nc.setSerie("FC01"); nc.setTipoComprobanteAfectado("01");
        nc.setNumComprobanteAfectado("F001-1"); nc.setCodMotivoSunat("07"); nc.setDescripcionMotivo("DEVOLUCION TEST");
        nc.setTipoDocCliente("6"); nc.setNumDocCliente("20100130204"); nc.setRznSocialCliente(nombre);
        assertThrows(IllegalArgumentException.class, () -> service.validar(nc));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "José & Compañía S.A.C.", "CLIENTE <TEST>", "O'CONNOR"})
    void debeAceptarNombresConEspaciosYCaracteresEspecialesSinRestringirAAscii(String nombre) {
        ComprobanteRequest factura = facturaValida(); factura.setRznSocialCliente(nombre);
        assertDoesNotThrow(() -> service.validar(factura, totales("118")));
    }

    private ComprobanteRequest facturaValida() {
        ComprobanteRequest r = new ComprobanteRequest();
        r.setTipoOperacion("0101");
        r.setTipoComprobante("01");
        r.setSerie("F001");
        r.setTipoDocCliente("6");
        r.setNumDocCliente("20100130204");
        r.setRznSocialCliente("CLIENTE RUC");
        r.setFormaPago("Contado");
        return r;
    }

    private ComprobanteRequest boletaBase() {
        ComprobanteRequest r = new ComprobanteRequest();
        r.setTipoOperacion("0101");
        r.setTipoComprobante("03");
        r.setSerie("B001");
        r.setFormaPago("Contado");
        return r;
    }

    private CalculoTributarioService.Totales totales(String total) {
        return new CalculoTributarioService.Totales(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(total), List.of()
        );
    }

    private ComprobanteRequest.CuotaRequest cuota(String monto) {
        ComprobanteRequest.CuotaRequest c = new ComprobanteRequest.CuotaRequest();
        c.setMonto(new BigDecimal(monto));
        c.setFechaVencimiento(LocalDate.now().plusDays(30));
        return c;
    }

    private NotaCreditoRequest ncValida(String tipoAfectado, String serie) {
        NotaCreditoRequest r = new NotaCreditoRequest();
        r.setTiendaId(1L);
        r.setSerie(serie);
        r.setTipoComprobanteAfectado(tipoAfectado);
        r.setNumComprobanteAfectado(("01".equals(tipoAfectado) ? "F001" : "B001") + "-00000001");
        r.setCodMotivoSunat("01");
        r.setDescripcionMotivo("ANULACION");
        r.setTipoDocCliente("1");
        r.setNumDocCliente("48411579");
        r.setRznSocialCliente("CLIENTE");
        return r;
    }
}
