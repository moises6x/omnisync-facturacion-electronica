package org.example.repository;

import org.example.model.Comprobante;
import org.example.model.EstadoComprobante;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ComprobanteRepositoryTest {

    @Autowired
    private ComprobanteRepository repository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void recoveryDebeMarcarSoloEnviosIndividualesVencidosSinReenviarlos() {
        Comprobante viejo = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 81, EstadoComprobante.ENVIANDO));
        Comprobante fresco = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 82, EstadoComprobante.ENVIANDO));
        Comprobante boleta = repository.saveAndFlush(comprobante("20100130204", "03", "B001", 81, EstadoComprobante.ENVIANDO));
        Comprobante pendiente = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 83, EstadoComprobante.PENDIENTE_ENVIO));
        Comprobante terminal = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 84, EstadoComprobante.ACEPTADO));
        for (Comprobante c : List.of(viejo, boleta, pendiente, terminal))
            entityManager.createNativeQuery("UPDATE comprobante SET updated_at=:fecha WHERE id=:id")
                    .setParameter("fecha", LocalDateTime.of(2026, 9, 30, 9, 0)).setParameter("id", c.getId()).executeUpdate();
        entityManager.createNativeQuery("UPDATE comprobante SET updated_at=:fecha WHERE id=:id")
                .setParameter("fecha", LocalDateTime.of(2026, 9, 30, 10, 0)).setParameter("id", fresco.getId()).executeUpdate();
        assertEquals(1, repository.marcarEnviosVencidosComoInciertos(LocalDateTime.of(2026, 9, 30, 9, 50)));
        assertEquals(EstadoComprobante.ERROR_ENVIO_INCIERTO, repository.findById(viejo.getId()).orElseThrow().getEstadoSfs());
        assertEquals(EstadoComprobante.ENVIANDO, repository.findById(fresco.getId()).orElseThrow().getEstadoSfs());
        assertEquals(EstadoComprobante.ENVIANDO, repository.findById(boleta.getId()).orElseThrow().getEstadoSfs());
        assertEquals(EstadoComprobante.ACEPTADO, repository.findById(terminal.getId()).orElseThrow().getEstadoSfs());
        assertEquals(List.of(pendiente.getId()), repository.buscarIdsPendientesEnvio(PageRequest.of(0, 20)));
        assertEquals(0, repository.marcarEnviando(viejo.getId()));
    }

    @Test
    void fechaVencidaYDocumentosDeOtraEmpresaNoDebenOcultarPendientesElegibles() {
        LocalDateTime desde = LocalDateTime.of(2026, 9, 23, 0, 0);
        Comprobante vencido = comprobante("20100130204", "03", "B001", 81, EstadoComprobante.PENDIENTE_RESUMEN);
        vencido.setFechaEmision(desde.minusDays(10)); repository.saveAndFlush(vencido);
        Comprobante vigente = comprobante("20100130204", "03", "B001", 82, EstadoComprobante.PENDIENTE_RESUMEN);
        vigente.setFechaEmision(desde); repository.saveAndFlush(vigente);
        Comprobante ajeno = comprobante("20600000001", "03", "B001", 81, EstadoComprobante.PENDIENTE_RESUMEN);
        ajeno.setFechaEmision(desde.plusHours(1)); repository.saveAndFlush(ajeno);
        assertEquals(desde, repository.buscarFechaMasAntiguaPendienteResumenDesde("20100130204", desde));
        assertNull(repository.buscarFechaMasAntiguaPendienteResumenDesde("20100130204", desde.plusDays(1)));
        assertEquals(EstadoComprobante.PENDIENTE_RESUMEN, repository.findById(vencido.getId()).orElseThrow().getEstadoSfs());
    }

    @Test
    void marcarEnviandoSoloDebeTomarPendienteEnvio() {
        Comprobante c = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 1, EstadoComprobante.PENDIENTE_ENVIO));

        assertEquals(1, repository.marcarEnviando(c.getId()));
        assertEquals(0, repository.marcarEnviando(c.getId()));

        Comprobante recargado = repository.findById(c.getId()).orElseThrow();
        assertEquals(EstadoComprobante.ENVIANDO, recargado.getEstadoSfs());
    }

    @Test
    void marcarEnviandoNoDebeTomarBoletaPendienteResumen() {
        Comprobante c = repository.saveAndFlush(comprobante("20100130204", "03", "B001", 1, EstadoComprobante.PENDIENTE_RESUMEN));
        assertEquals(0, repository.marcarEnviando(c.getId()));
    }

    @Test
    void recoveryDebeListarSoloPendienteEnvio() {
        Comprobante pendiente = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 1, EstadoComprobante.PENDIENTE_ENVIO));
        repository.saveAndFlush(comprobante("20100130204", "01", "F001", 2, EstadoComprobante.ERROR_ENVIO));
        repository.saveAndFlush(comprobante("20100130204", "03", "B001", 1, EstadoComprobante.PENDIENTE_RESUMEN));

        List<Long> ids = repository.buscarIdsPendientesEnvio(PageRequest.of(0, 20));
        assertEquals(List.of(pendiente.getId()), ids);
    }

    @Test
    void devolverAPendienteResumenDebeExigirMismoRucYEstadoEnResumen() {
        Comprobante c = repository.saveAndFlush(comprobante("20100130204", "03", "B001", 1, EstadoComprobante.EN_RESUMEN));

        assertEquals(0, repository.devolverAPendienteResumen(c.getId(), "20600000001"));
        assertEquals(1, repository.devolverAPendienteResumen(c.getId(), "20100130204"));
        assertEquals(0, repository.devolverAPendienteResumen(c.getId(), "20100130204"));

        assertEquals(EstadoComprobante.PENDIENTE_RESUMEN,
                repository.findById(c.getId()).orElseThrow().getEstadoSfs());
    }

    @Test
    void busquedaNaturalDebeSerUnicaPorRucTipoSerieNumero() {
        Comprobante a = repository.saveAndFlush(comprobante("20100130204", "01", "F001", 1, EstadoComprobante.ACEPTADO));
        repository.saveAndFlush(comprobante("20600000001", "01", "F001", 1, EstadoComprobante.ACEPTADO));

        var encontrado = repository.findByRucEmisorAndTipoComprobanteAndSerieAndNumero(
                "20100130204", "01", "F001", 1
        ).orElseThrow();
        assertEquals(a.getId(), encontrado.getId());
    }

    private Comprobante comprobante(String ruc, String tipo, String serie, int numero, EstadoComprobante estado) {
        return Comprobante.builder()
                .tiendaId(1L)
                .rucEmisor(ruc)
                .tipoOperacion("0101")
                .tipoComprobante(tipo)
                .serie(serie)
                .numero(numero)
                .fechaEmision(LocalDateTime.of(2026, 9, 19, 10, 0).plusSeconds(numero))
                .moneda("PEN")
                .formaPago("Contado")
                .tipoDocCliente("1")
                .numDocCliente("48411579")
                .rznSocialCliente("CLIENTE TEST")
                .opGravada(new BigDecimal("100.00"))
                .opExonerada(BigDecimal.ZERO)
                .opInafecta(BigDecimal.ZERO)
                .igv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .estadoSfs(estado)
                .build();
    }
}
