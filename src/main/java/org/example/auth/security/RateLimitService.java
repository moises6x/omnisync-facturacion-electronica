package org.example.auth.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimitService {

    private record Window(
            long inicio,
            int cantidad
    ) {
    }

    public record Decision(
            boolean permitido,
            long retryAfterSeconds
    ) {
    }

    private final Map<String, Window> windows =
            new ConcurrentHashMap<>();

    private final AtomicLong operaciones =
            new AtomicLong();

    @Value("${security.rate-limit.login-limit:10}")
    private int loginLimit;

    @Value("${security.rate-limit.login-window-seconds:300}")
    private long loginWindowSeconds;

    @Value("${security.rate-limit.refresh-limit:30}")
    private int refreshLimit;

    @Value("${security.rate-limit.refresh-window-seconds:60}")
    private long refreshWindowSeconds;

    @Value("${security.rate-limit.general-limit:300}")
    private int generalLimit;

    @Value("${security.rate-limit.general-window-seconds:60}")
    private long generalWindowSeconds;

    public Decision verificar(
            HttpServletRequest request
    ) {

        if (
                "OPTIONS".equalsIgnoreCase(
                        request.getMethod()
                )
        ) {

            return new Decision(
                    true,
                    0
            );
        }

        String path =
                request.getRequestURI();

        String grupo;

        int limite;

        long ventanaSegundos;

        if (
                "/api/v1/auth/login"
                        .equals(path)
        ) {

            grupo = "LOGIN";
            limite = loginLimit;
            ventanaSegundos =
                    loginWindowSeconds;

        } else if (
                "/api/v1/auth/refresh"
                        .equals(path)
        ) {

            grupo = "REFRESH";
            limite = refreshLimit;
            ventanaSegundos =
                    refreshWindowSeconds;

        } else {

            grupo = "GENERAL";
            limite = generalLimit;
            ventanaSegundos =
                    generalWindowSeconds;
        }

        long ahora =
                System.currentTimeMillis();

        long ventanaMillis =
                ventanaSegundos * 1000L;

        String ip =
                request.getRemoteAddr();

        String key =
                ip + "|" + grupo;

        Window window =
                windows.compute(
                        key,
                        (k, anterior) -> {

                            if (
                                    anterior == null
                                            ||
                                            ahora
                                                    - anterior.inicio()
                                                    >= ventanaMillis
                            ) {

                                return new Window(
                                        ahora,
                                        1
                                );
                            }

                            return new Window(
                                    anterior.inicio(),
                                    anterior.cantidad() + 1
                            );
                        }
                );

        /*
         * Limpieza periódica para evitar crecimiento
         * indefinido del mapa.
         */
        if (
                operaciones.incrementAndGet()
                        % 1024 == 0
        ) {

            long maxVentana =
                    Math.max(
                            loginWindowSeconds,
                            Math.max(
                                    refreshWindowSeconds,
                                    generalWindowSeconds
                            )
                    ) * 1000L;

            windows
                    .entrySet()
                    .removeIf(
                            entry ->
                                    ahora
                                            - entry
                                            .getValue()
                                            .inicio()
                                            > maxVentana * 2
                    );
        }

        if (
                window.cantidad()
                        <= limite
        ) {

            return new Decision(
                    true,
                    0
            );
        }

        long restante =
                (
                        window.inicio()
                                + ventanaMillis
                                - ahora
                                + 999
                ) / 1000;

        return new Decision(
                false,
                Math.max(
                        1,
                        restante
                )
        );
    }
}