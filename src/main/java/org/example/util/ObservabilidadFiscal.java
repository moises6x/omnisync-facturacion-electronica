package org.example.util;

import org.example.auth.service.AuditoriaService;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Identidad de instancia y auditoría posterior al commit sin alterar el resultado fiscal. */
public final class ObservabilidadFiscal {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ObservabilidadFiscal.class);
    private static final String NODO = UUID.randomUUID().toString();
    private ObservabilidadFiscal() { }
    public static void evento(String ruc, Long id, String documento, String ticket, String estado, String error) {
        try { LOG.info("FISCAL nodo={} ruc={} id={} documento={} ticket={} estado={} error={}",
                NODO, seguro(ruc), id, seguro(documento), seguro(ticket), seguro(estado), seguro(error));
        } catch(RuntimeException ignored) { /* Observabilidad no cambia el resultado confirmado. */ }
    }
    public static void auditar(AuditoriaService auditoria, String ruc, String accion, String resultado, String detalle) {
        if (auditoria == null) return;
        try { auditoria.registrarSistema(ruc, accion, resultado, detalle); }
        catch (RuntimeException e) {
            try { LOG.error("AUDITORIA_POST_COMMIT_FALLIDA nodo={} ruc={} accion={} tipo={}; resultado fiscal no revertido",
                    NODO, seguro(ruc), seguro(accion), e.getClass().getSimpleName());
            } catch(RuntimeException ignored) { /* No escritura fiscal/auditoría recursiva. */ }
        }
    }
    private static String seguro(String s) {
        return s == null ? "-" : s.substring(0, Math.min(s.length(), 160)).replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", "_");
    }
}
