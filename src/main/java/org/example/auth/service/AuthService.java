package org.example.auth.service;


import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.example.auth.dto.*;
import org.example.auth.exception.AuthException;
import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.RefreshToken;
import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UsuarioRepository;
import org.example.auth.security.JwtService;
import org.example.dto.*;



import org.example.model.*;


import org.example.service.AdminAccessService;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.SecureRandom;

import java.time.LocalDateTime;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final PasswordPolicyService passwordPolicyService;

    private final JwtService jwtService;

    private final AdminAccessService adminAccessService;

    private final SecureRandom secureRandom =
            new SecureRandom();

    @Value("${security.auth.max-failed-attempts:5}")
    private int maxIntentos;

    @Value("${security.auth.lock-minutes:15}")
    private int minutosBloqueo;

    @Value("${security.refresh.days:30}")
    private int refreshDays;

    @Value("${security.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    private String dummyHash;

    @PostConstruct
    public void init() {

        /*
         * Evita una diferencia excesiva de tiempo
         * entre correo existente y no existente.
         */
        dummyHash =
                passwordEncoder.encode(
                        "dummy-security-password-not-valid"
                );
    }

    @Transactional
    public UsuarioSesionResponse bootstrapSuperAdmin(

            String adminKey,

            BootstrapSuperAdminRequest request
    ) {

        if (!bootstrapEnabled) {

            throw new SecurityException(
                    "Bootstrap deshabilitado."
            );
        }

        adminAccessService.validar(
                adminKey
        );

        if (
                usuarioRepository.existsByRol(
                        RolUsuario.SUPER_ADMIN
                )
        ) {

            throw new IllegalArgumentException(
                    "El SUPER_ADMIN ya fue creado."
            );
        }

        String email =
                normalizarEmail(
                        request.email()
                );

        if (
                usuarioRepository
                        .existsByEmailIgnoreCase(
                                email
                        )
        ) {

            throw new IllegalArgumentException(
                    "El correo ya se encuentra registrado."
            );
        }

        passwordPolicyService.validar(
                request.password(),
                email
        );

        Usuario usuario =
                Usuario
                        .builder()
                        .nombre(
                                request.nombre()
                        )
                        .email(email)
                        .passwordHash(
                                passwordEncoder
                                        .encode(
                                                request.password()
                                        )
                        )
                        .rol(
                                RolUsuario.SUPER_ADMIN
                        )
                        .estado(
                                EstadoUsuario.ACTIVO
                        )
                        .debeCambiarPassword(
                                false
                        )
                        .build();

        Usuario guardado =
                usuarioRepository
                        .saveAndFlush(
                                usuario
                        );

        return perfil(
                guardado
        );
    }

    @Transactional(
            noRollbackFor = AuthException.class
    )
    public AuthResponse login(
            LoginRequest request
    ) {

        String email =
                normalizarEmail(
                        request.email()
                );

        Usuario usuario =
                usuarioRepository
                        .bloquearPorEmail(
                                email
                        )
                        .orElse(null);

        if (usuario == null) {

            passwordEncoder.matches(
                    request.password(),
                    dummyHash
            );

            throw new AuthException(
                    "Credenciales inválidas."
            );
        }

        if (
                usuario.getEstado()
                        != EstadoUsuario.ACTIVO
        ) {

            throw new AuthException(
                    "Credenciales inválidas."
            );
        }

        LocalDateTime ahora =
                LocalDateTime.now();

        if (
                usuario.getBloqueadoHasta() != null
                        &&
                        usuario
                                .getBloqueadoHasta()
                                .isAfter(ahora)
        ) {

            throw new AuthException(
                    "Credenciales inválidas o acceso temporalmente bloqueado."
            );
        }

        boolean correcto =
                passwordEncoder.matches(

                        request.password(),

                        usuario.getPasswordHash()
                );

        if (!correcto) {

            registrarFallo(
                    usuario
            );

            throw new AuthException(
                    "Credenciales inválidas."
            );
        }

        usuario.setIntentosFallidos(0);

        usuario.setBloqueadoHasta(
                null
        );

        usuario.setUltimoLogin(
                ahora
        );

        usuarioRepository.save(
                usuario
        );

        return crearSesion(
                usuario,
                UUID.randomUUID().toString()
        );
    }

    @Transactional(
            noRollbackFor = AuthException.class
    )
    public AuthResponse refresh(
            String refreshPlano
    ) {

        String hash =
                hash(
                        refreshPlano
                );

        // Orden compartido con password/logoutAll: usuario antes de tokens.
        Long usuarioId = refreshTokenRepository.buscarUsuarioIdPorHash(hash)
                .orElseThrow(() -> new AuthException("Sesión inválida."));
        usuarioRepository.bloquearPorId(usuarioId).orElseThrow(() -> new AuthException("Sesión inválida."));

        RefreshToken refresh =
                refreshTokenRepository
                        .bloquearPorTokenHash(
                                hash
                        )
                        .orElseThrow(
                                () ->
                                        new AuthException(
                                                "Sesión inválida."
                                        )
                        );

        Usuario usuario =
                refresh.getUsuario();

        /*
         * Reutilización de token ya rotado:
         * posible robo de sesión.
         *
         * Invalidamos TODAS las sesiones del usuario.
         */
        if (refresh.estaRevocado()) {

            usuario.setTokenVersion(
                    usuario.getTokenVersion()
                            + 1
            );

            usuarioRepository.save(
                    usuario
            );

            refreshTokenRepository
                    .revocarTodosActivos(

                            usuario.getId(),

                            LocalDateTime.now()
                    );

            throw new AuthException(
                    "Sesión inválida."
            );
        }

        if (refresh.estaExpirado()) {

            refresh.setRevocadoEn(
                    LocalDateTime.now()
            );

            refreshTokenRepository.save(
                    refresh
            );

            throw new AuthException(
                    "Sesión expirada."
            );
        }

        if (
                usuario.getEstado()
                        != EstadoUsuario.ACTIVO
        ) {

            throw new AuthException(
                    "Sesión inválida."
            );
        }

        LocalDateTime ahora =
                LocalDateTime.now();

        String nuevoPlano =
                generarRefreshToken();

        String nuevoHash =
                hash(
                        nuevoPlano
                );

        refresh.setRevocadoEn(
                ahora
        );

        refresh.setReemplazadoPorHash(
                nuevoHash
        );

        RefreshToken nuevo =
                RefreshToken
                        .builder()
                        .usuario(usuario)
                        .tokenHash(
                                nuevoHash
                        )
                        .familyId(
                                refresh.getFamilyId()
                        )
                        .creadoEn(ahora)
                        .expiraEn(
                                ahora.plusDays(
                                        refreshDays
                                )
                        )
                        .build();

        refreshTokenRepository.save(
                refresh
        );

        refreshTokenRepository.save(
                nuevo
        );

        return new AuthResponse(

                jwtService.generar(
                        usuario
                ),

                nuevoPlano,

                "Bearer",

                jwtService.accessExpiresSeconds(),

                perfil(usuario)
        );
    }

    @Transactional
    public void logout(
            String refreshPlano
    ) {

        if (
                refreshPlano == null
                        ||
                        refreshPlano.isBlank()
        ) {

            return;
        }

        refreshTokenRepository
                .bloquearPorTokenHash(
                        hash(refreshPlano)
                )
                .ifPresent(
                        token -> {

                            if (
                                    token.getRevocadoEn()
                                            == null
                            ) {

                                token.setRevocadoEn(
                                        LocalDateTime.now()
                                );

                                refreshTokenRepository
                                        .save(token);
                            }
                        }
                );
    }

    @Transactional
    public void logoutAll(
            Long usuarioId
    ) {

        Usuario usuario =
                usuarioRepository
                        .bloquearPorId(
                                usuarioId
                        )
                        .orElseThrow(
                                () ->
                                        new AuthException(
                                                "Usuario no encontrado."
                                        )
                        );

        usuario.setTokenVersion(
                usuario.getTokenVersion()
                        + 1
        );

        usuarioRepository.save(
                usuario
        );

        refreshTokenRepository
                .revocarTodosActivos(

                        usuarioId,

                        LocalDateTime.now()
                );
    }

    @Transactional
    public void cambiarPassword(

            Long usuarioId,

            ChangePasswordRequest request
    ) {

        Usuario usuario =
                usuarioRepository
                        .bloquearPorId(
                                usuarioId
                        )
                        .orElseThrow(
                                () ->
                                        new AuthException(
                                                "Usuario no encontrado."
                                        )
                        );

        if (
                !passwordEncoder.matches(

                        request.passwordActual(),

                        usuario.getPasswordHash()
                )
        ) {

            throw new AuthException(
                    "La contraseña actual es incorrecta."
            );
        }

        passwordPolicyService.validar(

                request.passwordNueva(),

                usuario.getEmail()
        );

        if (
                passwordEncoder.matches(

                        request.passwordNueva(),

                        usuario.getPasswordHash()
                )
        ) {

            throw new IllegalArgumentException(
                    "La nueva contraseña debe ser diferente."
            );
        }

        usuario.setPasswordHash(
                passwordEncoder
                        .encode(
                                request.passwordNueva()
                        )
        );

        usuario.setDebeCambiarPassword(
                false
        );

        usuario.setTokenVersion(
                usuario.getTokenVersion()
                        + 1
        );

        usuarioRepository.save(
                usuario
        );

        refreshTokenRepository
                .revocarTodosActivos(

                        usuarioId,

                        LocalDateTime.now()
                );
    }

    private AuthResponse crearSesion(

            Usuario usuario,

            String familyId
    ) {

        LocalDateTime ahora =
                LocalDateTime.now();

        String refreshPlano =
                generarRefreshToken();

        RefreshToken refresh =
                RefreshToken
                        .builder()
                        .usuario(usuario)
                        .tokenHash(
                                hash(
                                        refreshPlano
                                )
                        )
                        .familyId(
                                familyId
                        )
                        .creadoEn(ahora)
                        .expiraEn(
                                ahora.plusDays(
                                        refreshDays
                                )
                        )
                        .build();

        refreshTokenRepository
                .save(refresh);

        return new AuthResponse(

                jwtService.generar(
                        usuario
                ),

                refreshPlano,

                "Bearer",

                jwtService.accessExpiresSeconds(),

                perfil(usuario)
        );
    }

    private void registrarFallo(
            Usuario usuario
    ) {

        int intentos =
                usuario.getIntentosFallidos()
                        + 1;

        usuario.setIntentosFallidos(
                intentos
        );

        if (intentos >= maxIntentos) {

            usuario.setBloqueadoHasta(

                    LocalDateTime
                            .now()
                            .plusMinutes(
                                    minutosBloqueo
                            )
            );

            usuario.setIntentosFallidos(
                    0
            );
        }

        usuarioRepository.saveAndFlush(
                usuario
        );
    }

    private String generarRefreshToken() {

        byte[] random =
                new byte[64];

        secureRandom.nextBytes(
                random
        );

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        random
                );
    }

    private String hash(
            String token
    ) {

        if (
                token == null
                        ||
                        token.isBlank()
        ) {

            throw new AuthException(
                    "Refresh token requerido."
            );
        }

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat
                    .of()
                    .formatHex(

                            digest.digest(

                                    token.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "No se pudo procesar el token.",
                    e
            );
        }
    }

    private UsuarioSesionResponse perfil(
            Usuario usuario
    ) {

        Empresa empresa =
                usuario.getEmpresa();

        return new UsuarioSesionResponse(

                usuario.getId(),

                usuario.getNombre(),

                usuario.getEmail(),

                usuario
                        .getRol()
                        .name(),

                empresa != null
                        ? empresa.getRuc()
                        : null,

                empresa != null
                        ? empresa.getTenantId()
                        : null,

                usuario
                        .isDebeCambiarPassword()
        );
    }

    private String normalizarEmail(
            String email
    ) {

        return email
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
