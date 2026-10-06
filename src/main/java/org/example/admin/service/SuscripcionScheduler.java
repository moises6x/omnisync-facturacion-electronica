package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class SuscripcionScheduler {

    private final SuscripcionAutomaticaService
            suscripcionService;


    /*
     * Protección sencilla para una sola instancia
     * de la aplicación.
     *
     * Cuando despleguemos varias instancias,
     * esta protección se sustituirá por un lock
     * distribuido.
     */
    private final AtomicBoolean ejecutando =
            new AtomicBoolean(false);


    @Scheduled(
            cron =
                    "${admin.suscripciones.cron:0 */15 * * * *}",

            zone =
                    "${admin.suscripciones.zone:America/Lima}"
    )
    public void revisarSuscripciones() {

        if (
                !ejecutando.compareAndSet(
                        false,
                        true
                )
        ) {

            return;
        }


        try {

            suscripcionService
                    .procesar();

        } finally {

            ejecutando.set(
                    false
            );
        }
    }
}