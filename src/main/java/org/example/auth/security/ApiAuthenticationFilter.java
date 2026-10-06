package org.example.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.example.auth.exception.EmpresaSuspendidaException;

import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.Usuario;

import org.example.auth.repository.UsuarioRepository;

import org.example.model.Empresa;
import org.example.model.EstadoEmpresa;

import org.example.service.EmpresaAccessService;

import org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken;

import org.springframework.security.core.context
        .SecurityContextHolder;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.filter
        .OncePerRequestFilter;

import java.io.IOException;

import java.time.LocalDateTime;

import java.util.Map;

public class ApiAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final UsuarioRepository
            usuarioRepository;

    private final EmpresaAccessService
            empresaAccessService;

    private final RateLimitService
            rateLimitService;

    private final ObjectMapper
            objectMapper;


    public ApiAuthenticationFilter(

            JwtService jwtService,

            UsuarioRepository usuarioRepository,

            EmpresaAccessService empresaAccessService,

            RateLimitService rateLimitService,

            ObjectMapper objectMapper
    ) {

        this.jwtService =
                jwtService;

        this.usuarioRepository =
                usuarioRepository;

        this.empresaAccessService =
                empresaAccessService;

        this.rateLimitService =
                rateLimitService;

        this.objectMapper =
                objectMapper;
    }


    // =========================================================
    // FILTRO
    // =========================================================

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,

            HttpServletResponse response,

            FilterChain filterChain

    ) throws ServletException, IOException {


        // =====================================================
        // RATE LIMIT
        // =====================================================

        RateLimitService.Decision decision =
                rateLimitService
                        .verificar(
                                request
                        );


        if (
                !decision.permitido()
        ) {

            response.setHeader(
                    "Retry-After",
                    Long.toString(
                            decision
                                    .retryAfterSeconds()
                    )
            );


            escribir(
                    response,
                    429,
                    "Demasiadas solicitudes. Intente nuevamente más tarde."
            );

            return;
        }


        // =====================================================
        // CORS PREFLIGHT
        // =====================================================

        if (
                "OPTIONS"
                        .equalsIgnoreCase(
                                request.getMethod()
                        )
        ) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }


        // =====================================================
        // ENDPOINTS PÚBLICOS
        // =====================================================

        if (
                esPublico(
                        request
                )
        ) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }


        // =====================================================
        // CABECERAS
        // =====================================================

        String authorization =
                request.getHeader(
                        "Authorization"
                );


        String ruc =
                request.getHeader(
                        "X-Empresa-RUC"
                );


        String empresaKey =
                request.getHeader(
                        "X-Empresa-Key"
                );


        boolean bearer =
                authorization != null
                        &&
                        authorization.startsWith(
                                "Bearer "
                        );


        boolean tieneHeaderEmpresa =
                (
                        ruc != null
                                &&
                                !ruc.isBlank()
                )
                        ||
                        (
                                empresaKey != null
                                        &&
                                        !empresaKey.isBlank()
                        );


        // =====================================================
        // NO MEZCLAR JWT + API KEY
        // =====================================================

        if (
                bearer
                        &&
                        tieneHeaderEmpresa
        ) {

            escribir(
                    response,
                    400,
                    "No mezcle Bearer Token con credenciales X-Empresa."
            );

            return;
        }


        try {

            // =================================================
            // JWT
            // =================================================

            if (
                    bearer
            ) {

                autenticarJwt(
                        request,
                        authorization.substring(
                                7
                        )
                );
            }


            // =================================================
            // API KEY
            // =================================================

            else if (
                    tieneHeaderEmpresa
            ) {

                autenticarApiKey(
                        ruc,
                        empresaKey
                );
            }


            filterChain.doFilter(
                    request,
                    response
            );

        }


        // =====================================================
        // EMPRESA SUSPENDIDA
        // =====================================================

        catch (
                EmpresaSuspendidaException e
        ) {

            SecurityContextHolder
                    .clearContext();


            /*
             * Aquí utilizamos 403.
             *
             * El usuario/API está identificado,
             * pero la empresa no puede utilizar
             * las operaciones de la plataforma.
             */
            escribir(
                    response,
                    403,
                    e.getMessage()
            );
        }


        // =====================================================
        // TOKEN / API KEY INVÁLIDO
        // =====================================================

        catch (
                Exception e
        ) {

            SecurityContextHolder
                    .clearContext();


            escribir(
                    response,
                    401,
                    "No autorizado."
            );
        }
    }


    // =========================================================
    // AUTENTICACIÓN JWT
    // =========================================================

    private void autenticarJwt(

            HttpServletRequest request,

            String token
    ) {

        Jwt jwt =
                jwtService.validar(
                        token
                );


        Long usuarioId;


        try {

            usuarioId =
                    Long.valueOf(
                            jwt.getSubject()
                    );

        } catch (
                Exception e
        ) {

            throw new SecurityException(
                    "Token inválido."
            );
        }


        Usuario usuario =
                usuarioRepository
                        .findForSecurityById(
                                usuarioId
                        )
                        .orElseThrow(
                                () ->
                                        new SecurityException(
                                                "Sesión inválida."
                                        )
                        );


        // =====================================================
        // USUARIO ACTIVO
        // =====================================================

        if (
                usuario.getEstado()
                        != EstadoUsuario.ACTIVO
        ) {

            throw new SecurityException(
                    "Usuario desactivado."
            );
        }


        // =====================================================
        // BLOQUEO POR INTENTOS
        // =====================================================

        if (
                usuario.getBloqueadoHasta()
                        != null
                        &&
                        usuario
                                .getBloqueadoHasta()
                                .isAfter(
                                        LocalDateTime.now()
                                )
        ) {

            throw new SecurityException(
                    "Usuario bloqueado."
            );
        }


        // =====================================================
        // TOKEN VERSION
        // =====================================================

        Number version =
                jwt.getClaim(
                        "ver"
                );


        if (
                version == null
                        ||
                        version.longValue()
                                != usuario
                                .getTokenVersion()
        ) {

            throw new SecurityException(
                    "Sesión revocada."
            );
        }


        Empresa empresa =
                usuario.getEmpresa();


        // =====================================================
        // EMPRESA SUSPENDIDA
        // =====================================================
        //
        // SUPER_ADMIN no tiene empresa y por lo tanto
        // nunca pasa por este bloqueo.
        //
        // Una empresa suspendida puede usar solamente
        // endpoints básicos para:
        //
        // - consultar su sesión
        // - cambiar contraseña
        // - cerrar sesiones
        //
        // El resto queda bloqueado.
        // =====================================================

        if (
                empresa != null
                        &&
                        empresa.getEstadoPlataforma()
                                != EstadoEmpresa.ACTIVA
                        &&
                        !permitidoDuranteSuspension(
                                request.getRequestURI()
                        )
        ) {

            String mensaje =
                    "La empresa se encuentra suspendida temporalmente.";


            if (
                    empresa.getMotivoSuspension()
                            != null
                            &&
                            !empresa
                                    .getMotivoSuspension()
                                    .isBlank()
            ) {

                mensaje +=
                        " Motivo: "
                                +
                                empresa.getMotivoSuspension();
            }


            throw new EmpresaSuspendidaException(
                    mensaje
            );
        }


        // =====================================================
        // PRINCIPAL
        // =====================================================

        AppPrincipal principal =
                new AppPrincipal(

                        usuario.getId(),

                        empresa != null
                                ?
                                empresa.getId()
                                :
                                null,

                        empresa != null
                                ?
                                empresa.getTenantId()
                                :
                                null,

                        empresa != null
                                ?
                                empresa.getRuc()
                                :
                                null,

                        usuario.getEmail(),

                        usuario
                                .getRol()
                                .name(),

                        false
                );


        var authentication =
                new UsernamePasswordAuthenticationToken(

                        principal,

                        null,

                        SecurityAuthorities
                                .paraUsuario(
                                        usuario
                                )
                );


        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        authentication
                );


        // =====================================================
        // CONTRASEÑA TEMPORAL
        // =====================================================

        if (
                usuario.isDebeCambiarPassword()
                        &&
                        !permitidoDuranteCambioPassword(
                                request.getRequestURI()
                        )
        ) {

            throw new SecurityException(
                    "Debe cambiar su contraseña."
            );
        }
    }


    // =========================================================
    // AUTENTICACIÓN API KEY
    // =========================================================

    private void autenticarApiKey(

            String ruc,

            String empresaKey
    ) {

        if (
                ruc == null
                        ||
                        ruc.isBlank()
                        ||
                        empresaKey == null
                        ||
                        empresaKey.isBlank()
        ) {

            throw new SecurityException(
                    "Credenciales incompletas."
            );
        }


        /*
         * EmpresaAccessService también comprobará
         * si la empresa está ACTIVA.
         */
        Empresa empresa =
                empresaAccessService
                        .validar(
                                ruc,
                                empresaKey
                        );


        AppPrincipal principal =
                new AppPrincipal(

                        null,

                        empresa.getId(),

                        empresa.getTenantId(),

                        empresa.getRuc(),

                        null,

                        "API_CLIENT",

                        true
                );


        var authentication =
                new UsernamePasswordAuthenticationToken(

                        principal,

                        null,

                        SecurityAuthorities
                                .paraApiClient()
                );


        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        authentication
                );
    }


    // =========================================================
    // ENDPOINTS PÚBLICOS
    // =========================================================

    private boolean esPublico(
            HttpServletRequest request
    ) {

        String path =
                request.getRequestURI();

        if (
                "GET".equalsIgnoreCase(request.getMethod())
                        && "/actuator/health".equals(path)
        ) {
            return true;
        }

        if (
                !"POST".equalsIgnoreCase(
                                request.getMethod()
                        )
        ) {

            return false;
        }

        if (path.equals("/api/public/cpe/consulta")) {
            return true;
        }


        return path.equals(
                "/api/v1/auth/login"
        )
                ||
                path.equals(
                        "/api/v1/auth/refresh"
                )
                ||
                path.equals(
                        "/api/v1/auth/logout"
                )
                ||
                path.equals(
                        "/api/v1/auth/bootstrap-superadmin"
                );
    }


    // =========================================================
    // CONTRASEÑA TEMPORAL
    // =========================================================

    private boolean permitidoDuranteCambioPassword(
            String path
    ) {

        return path.equals(
                "/api/v1/auth/change-password"
        )
                ||
                path.equals(
                        "/api/v1/auth/logout-all"
                )
                ||
                path.equals(
                        "/api/v1/usuarios/me"
                );
    }


    // =========================================================
    // EMPRESA SUSPENDIDA
    // =========================================================

    private boolean permitidoDuranteSuspension(
            String path
    ) {

        return path.equals(
                "/api/v1/usuarios/me"
        )
                ||
                path.equals(
                        "/api/v1/auth/change-password"
                )
                ||
                path.equals(
                        "/api/v1/auth/logout-all"
                )
                ||
                path.equals(
                        "/api/v1/anuncios/activos"
                );
    }


    // =========================================================
    // RESPUESTA JSON
    // =========================================================

    private void escribir(

            HttpServletResponse response,

            int status,

            String mensaje

    ) throws IOException {

        response.setStatus(
                status
        );


        response.setContentType(
                "application/json;charset=UTF-8"
        );


        response.setHeader(
                "Cache-Control",
                "no-store"
        );


        objectMapper.writeValue(

                response.getOutputStream(),

                Map.of(

                        "status",
                        "ERROR",

                        "message",
                        mensaje
                )
        );
    }
}