package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.Comprobante;
import org.example.model.EstadoComprobante;
import org.example.repository.ComprobanteRepository;
import org.example.repository.ComprobanteRepository.NotaCreditoSaldoProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Política de integridad SaaS autorizada; no afirma una regla acumulativa SUNAT. Sin contadores persistidos. */
@Service
@RequiredArgsConstructor
public class SaldoNotaCreditoService {
    private static final Set<String> MONETARIOS = Set.of("01", "04", "05", "06", "07", "08", "09");
    private final ComprobanteRepository repository;
    private final CdrParserService cdrParserService;

    /** El llamador ya tiene bloqueado el original dentro de la transacción de emisión/idempotencia. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validar(Comprobante original, String motivo, CalculoTributarioService.Totales nuevos) {
        if ("03".equals(motivo)) return;
        if (motivo == null || !MONETARIOS.contains(motivo)) throw ambiguo();
        if (!"PEN".equals(original.getMoneda()) || repository.contarNotasCreditoSinReferencia(original.getRucEmisor()) > 0)
            throw ambiguo();

        BigDecimal[] limite = importes(original.getMontoTotal(), original.getOpGravada(), original.getOpExonerada(),
                original.getOpInafecta(), original.getIgv());
        BigDecimal[] consumido = ceros();
        BigDecimal[] reservado = ceros();
        boolean afectacionPrevia = false;
        for (NotaCreditoSaldoProjection nc : repository.bloquearNotasCreditoDelOriginal(original.getRucEmisor(),
                original.getTipoComprobante(), original.getSerie(), original.getNumero())) {
            if ("03".equals(nc.getMotivo())) continue;
            if (nc.getMotivo() == null || !MONETARIOS.contains(nc.getMotivo()) || !Objects.equals(original.getMoneda(), nc.getMoneda())) throw ambiguo();
            EstadoComprobante estado;
            try { estado = EstadoComprobante.valueOf(nc.getEstado()); }
            catch (RuntimeException e) { throw ambiguo(); }
            BigDecimal[] importes = importes(nc.getTotal(), nc.getGravada(), nc.getExonerada(), nc.getInafecta(), nc.getIgv());
            if (importes[0].signum() <= 0) throw ambiguo();
            if (rechazoIndividualDefinitivo(original, nc, estado)) continue;
            afectacionPrevia = true;
            BigDecimal[] destino = estado == EstadoComprobante.ACEPTADO || estado == EstadoComprobante.ACEPTADO_CON_OBSERVACION
                    ? consumido : reservado;
            for (int i = 0; i < destino.length; i++) destino[i] = destino[i].add(importes[i]);
        }
        BigDecimal[] solicitud = importes(nuevos.total(), nuevos.opGravada(), nuevos.opExonerada(), nuevos.opInafecta(), nuevos.igv());
        String[] componentes = {"total", "base gravada", "base exonerada", "base inafecta", "IGV"};
        for (int i = 0; i < limite.length; i++) {
            BigDecimal ocupado = consumido[i].add(reservado[i]);
            if (ocupado.compareTo(limite[i]) > 0) throw ambiguo();
            if (solicitud[i].compareTo(limite[i].subtract(ocupado)) > 0)
                throw new IllegalArgumentException("La Nota de Crédito supera el saldo disponible de " + componentes[i] + ".");
        }
        if (afectacionPrevia && ("01".equals(motivo) || "06".equals(motivo)))
            throw new IllegalArgumentException("El motivo " + motivo + " no admite afectaciones monetarias previas activas del original.");
    }

    /** Sólo sendBill terminal con ZIP/XML/código/referencia concordantes; RC y errores jamás liberan. */
    private boolean rechazoIndividualDefinitivo(Comprobante original, NotaCreditoSaldoProjection nc, EstadoComprobante estado) {
        if (estado != EstadoComprobante.RECHAZADO || !"01".equals(original.getTipoComprobante())
                || nc.getCdrZip() == null || nc.getCdrXml() == null) return false;
        try {
            var cdr = cdrParserService.procesar(nc.getCdrZip());
            // No introducir nuevas normalizaciones: se exige una de las representaciones ya emitidas por el SaaS.
            String referencia = cdr.referenceId();
            boolean propia = Objects.equals(referencia, nc.getSerie() + "-" + nc.getNumero())
                    || Objects.equals(referencia, nc.getSerie() + "-" + String.format(java.util.Locale.ROOT, "%08d", nc.getNumero()));
            return !cdr.aceptado() && propia && Objects.equals(cdr.responseCode(), nc.getCodigo())
                    && Arrays.equals(cdr.xml(), nc.getCdrXml());
        } catch (RuntimeException e) { return false; }
    }

    /** Commit corto posterior a SOAP. Un resultado tardío no puede reabrir una NC terminal ni restituir su reserva. */
    @Transactional
    public Comprobante guardarResultadoIndividual(Comprobante resultado) {
        repository.bloquearOriginalNotaCredito(resultado.getRucEmisor(), resultado.getTipoComprobanteAfectado(),
                resultado.getSerieComprobanteAfectado(), resultado.getNumeroComprobanteAfectado()).orElseThrow(SaldoNotaCreditoService::ambiguo);
        Comprobante actual = repository.bloquearPorIdYRuc(resultado.getId(), resultado.getRucEmisor()).orElseThrow();
        if (actual.getEstadoSfs() != EstadoComprobante.ENVIANDO && actual.getEstadoSfs() != EstadoComprobante.ERROR_ENVIO_INCIERTO) return actual;
        actual.setEstadoSfs(resultado.getEstadoSfs());
        actual.setCodigoRespuestaSunat(resultado.getCodigoRespuestaSunat());
        actual.setMensajeSunat(resultado.getMensajeSunat());
        actual.setCdrZipBytes(resultado.getCdrZipBytes());
        actual.setCdrXmlBytes(resultado.getCdrXmlBytes());
        return repository.saveAndFlush(actual);
    }

    private static BigDecimal[] ceros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static BigDecimal[] importes(BigDecimal... valores) {
        BigDecimal[] resultado = new BigDecimal[valores.length];
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == null || valores[i].signum() < 0 || valores[i].scale() > 2 && valores[i].stripTrailingZeros().scale() > 2)
                throw ambiguo();
            resultado[i] = valores[i].setScale(2, RoundingMode.UNNECESSARY);
        }
        return resultado;
    }

    private static IllegalArgumentException ambiguo() {
        return new IllegalArgumentException("Histórico de Notas de Crédito ambiguo o sobreafectado. Requiere conciliación antes de emitir una nueva NC monetaria.");
    }
}
