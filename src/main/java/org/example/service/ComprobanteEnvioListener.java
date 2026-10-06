package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.event.ComprobanteCreadoEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ComprobanteEnvioListener {

    private final
    EnvioSunatService envioSunatService;

    /**
     * IMPORTANTÍSIMO:
     *
     * sólo manda a SUNAT DESPUÉS
     * de que la factura quedó grabada
     * correctamente en MySQL.
     */
    @Async("sunatExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void enviar(
            ComprobanteCreadoEvent event
    ) {

        envioSunatService.enviar(
                event.comprobanteId()
        );
    }
}