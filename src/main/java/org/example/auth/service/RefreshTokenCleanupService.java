package org.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.auth.repository.RefreshTokenRepository;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;


    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    @Value("${security.refresh.cleanup.enabled:true}")
    private boolean cleanupEnabled;


    @Value("${security.refresh.cleanup.zone:America/Lima}")
    private String cleanupZone;


    // =========================================================
    // LIMPIEZA AUTOMÁTICA
    // =========================================================
    //
    // Por defecto:
    //
    // todos los días a las 03:30 a.m.
    //
    // La hora puede modificarse desde application.properties.
    // =========================================================

    @Scheduled(
            cron = "${security.refresh.cleanup.cron:0 30 3 * * *}",
            zone = "${security.refresh.cleanup.zone:America/Lima}"
    )
    @Transactional
    public void limpiarRefreshTokensExpirados() {

        if (!cleanupEnabled) {

            return;
        }

        LocalDateTime ahora =
                LocalDateTime.now(
                        ZoneId.of(
                                cleanupZone
                        )
                );

        int eliminados =
                refreshTokenRepository
                        .eliminarExpirados(
                                ahora
                        );

        if (eliminados > 0) {

            log.info(
                    "Limpieza de refresh tokens completada. Tokens expirados eliminados: {}",
                    eliminados
            );

        } else {

            log.debug(
                    "Limpieza de refresh tokens completada. No existen tokens expirados."
            );
        }
    }
}