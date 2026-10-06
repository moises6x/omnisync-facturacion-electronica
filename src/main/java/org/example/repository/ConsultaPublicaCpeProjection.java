package org.example.repository;

import org.example.model.EstadoComprobante;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Proyección mínima: evita cargar XML/CDR/PFX para una consulta pública. */
public interface ConsultaPublicaCpeProjection {
    LocalDateTime getFechaEmision();
    BigDecimal getMontoTotal();
    String getTipoDocCliente();
    String getNumDocCliente();
    EstadoComprobante getEstadoSfs();
}
