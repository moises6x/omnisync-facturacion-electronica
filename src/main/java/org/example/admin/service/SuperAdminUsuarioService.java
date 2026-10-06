package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.example.auth.dto.CrearUsuarioRequest;
import org.example.auth.dto.UsuarioResponse;

import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.PermisoUsuario;
import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;

import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UsuarioRepository;

import org.example.auth.security.SecurityAuthorities;

import org.example.auth.service.PasswordPolicyService;

import org.example.model.Empresa;

import org.example.repository.EmpresaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SuperAdminUsuarioService {

    private final EmpresaRepository
            empresaRepository;

    private final UsuarioRepository
            usuarioRepository;

    private final RefreshTokenRepository
            refreshTokenRepository;

    private final PasswordEncoder
            passwordEncoder;

    private final PasswordPolicyService
            passwordPolicyService;


    // =========================================================
    // LISTAR USUARIOS DE CUALQUIER EMPRESA
    // =========================================================

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(
            Long empresaId
    ) {

        validarEmpresaExiste(
                empresaId
        );


        return usuarioRepository
                .findAllByEmpresaIdOrderByNombreAsc(
                        empresaId
                )
                .stream()
                .map(
                        this::convertir
                )
                .toList();
    }


    // =========================================================
    // CREAR ADMINISTRADOR / TRABAJADOR
    // =========================================================

    @Transactional
    public UsuarioResponse crear(

            Long empresaId,

            CrearUsuarioRequest request
    ) {

        Empresa empresa =
                buscarEmpresa(
                        empresaId
                );


        // -----------------------------------------------------
        // Desde este endpoint NO creamos SUPER_ADMIN ni dueño.
        // -----------------------------------------------------

        if (
                request.rol()
                        != RolUsuario.EMPRESA_ADMIN
                        &&
                        request.rol()
                                != RolUsuario.TRABAJADOR
        ) {

            throw new IllegalArgumentException(
                    "El SUPER_ADMIN sólo puede crear EMPRESA_ADMIN o TRABAJADOR desde este endpoint."
            );
        }


        String email =
                request.email()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
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

                request.passwordTemporal(),

                email
        );


        Set<PermisoUsuario> permisos =
                resolverPermisos(

                        request.rol(),

                        request.permisos()
                );


        Usuario usuario =
                Usuario.builder()

                        .empresa(
                                empresa
                        )

                        .nombre(
                                request
                                        .nombre()
                                        .trim()
                        )

                        .email(
                                email
                        )

                        .passwordHash(
                                passwordEncoder.encode(
                                        request.passwordTemporal()
                                )
                        )

                        .rol(
                                request.rol()
                        )

                        .estado(
                                EstadoUsuario.ACTIVO
                        )

                        .permisos(
                                permisos
                        )

                        .debeCambiarPassword(
                                true
                        )

                        .build();


        usuario =
                usuarioRepository
                        .saveAndFlush(
                                usuario
                        );


        return convertir(
                usuario
        );
    }


    // =========================================================
    // ACTIVAR / DESACTIVAR USUARIO
    // =========================================================

    @Transactional
    public UsuarioResponse actualizarEstado(

            Long empresaId,

            Long usuarioId,

            boolean activo
    ) {

        validarEmpresaExiste(
                empresaId
        );


        Usuario usuario =
                buscarUsuarioEmpresa(

                        usuarioId,

                        empresaId
                );


        usuario.setEstado(

                activo
                        ?
                        EstadoUsuario.ACTIVO
                        :
                        EstadoUsuario.DESACTIVADO
        );


        /*
         * Al desactivar:
         *
         * - Access tokens existentes dejan de funcionar.
         * - Refresh tokens quedan revocados.
         */
        if (
                !activo
        ) {

            invalidarSesiones(
                    usuario
            );
        }


        usuarioRepository
                .saveAndFlush(
                        usuario
                );


        return convertir(
                usuario
        );
    }


    // =========================================================
    // CONTRASEÑA TEMPORAL
    // =========================================================

    @Transactional
    public UsuarioResponse passwordTemporal(

            Long empresaId,

            Long usuarioId,

            String nuevaPassword
    ) {

        validarEmpresaExiste(
                empresaId
        );


        Usuario usuario =
                buscarUsuarioEmpresa(

                        usuarioId,

                        empresaId
                );


        passwordPolicyService.validar(

                nuevaPassword,

                usuario.getEmail()
        );


        usuario.setPasswordHash(

                passwordEncoder.encode(
                        nuevaPassword
                )
        );


        /*
         * Cuando ingrese nuevamente estará obligado
         * a cambiarla.
         */
        usuario.setDebeCambiarPassword(
                true
        );


        /*
         * Invalidamos cualquier sesión anterior.
         */
        invalidarSesiones(
                usuario
        );


        usuario =
                usuarioRepository
                        .saveAndFlush(
                                usuario
                        );


        return convertir(
                usuario
        );
    }


    // =========================================================
    // PERMISOS DE TRABAJADOR
    // =========================================================

    @Transactional
    public UsuarioResponse actualizarPermisos(

            Long empresaId,

            Long usuarioId,

            Set<PermisoUsuario> permisos
    ) {

        Usuario usuario =
                buscarUsuarioEmpresa(

                        usuarioId,

                        empresaId
                );


        if (
                usuario.getRol()
                        != RolUsuario.TRABAJADOR
        ) {

            throw new IllegalArgumentException(
                    "Los permisos personalizados solamente pueden modificarse en usuarios TRABAJADOR."
            );
        }


        Set<PermisoUsuario> nuevos =
                resolverPermisos(

                        RolUsuario.TRABAJADOR,

                        permisos
                );


        usuario.setPermisos(
                nuevos
        );


        /*
         * Es importante invalidar sesiones porque
         * cambiaron sus permisos.
         */
        invalidarSesiones(
                usuario
        );


        usuario =
                usuarioRepository
                        .saveAndFlush(
                                usuario
                        );


        return convertir(
                usuario
        );
    }


    // =========================================================
    // RESOLVER PERMISOS
    // =========================================================

    private Set<PermisoUsuario> resolverPermisos(

            RolUsuario rol,

            Set<PermisoUsuario> solicitados
    ) {

        /*
         * EMPRESA_ADMIN obtiene sus permisos
         * automáticamente mediante SecurityAuthorities.
         */
        if (
                rol != RolUsuario.TRABAJADOR
        ) {

            return new HashSet<>();
        }


        Set<PermisoUsuario> permitidos =
                SecurityAuthorities
                        .permisosPermitidosTrabajador();


        /*
         * Permisos predeterminados.
         */
        if (
                solicitados == null
                        ||
                        solicitados.isEmpty()
        ) {

            return EnumSet.of(

                    PermisoUsuario.FACTURACION_EMITIR,

                    PermisoUsuario.FACTURACION_CONSULTAR,

                    PermisoUsuario.CONSULTA_RUC
            );
        }


        if (
                !permitidos.containsAll(
                        solicitados
                )
        ) {

            throw new IllegalArgumentException(
                    "Se intentó asignar un permiso no permitido para un trabajador."
            );
        }


        return EnumSet.copyOf(
                solicitados
        );
    }


    // =========================================================
    // INVALIDAR TODAS LAS SESIONES
    // =========================================================

    private void invalidarSesiones(
            Usuario usuario
    ) {

        usuario.setTokenVersion(

                usuario.getTokenVersion()
                        + 1
        );


        refreshTokenRepository
                .revocarTodosActivos(

                        usuario.getId(),

                        LocalDateTime.now()
                );
    }


    // =========================================================
    // EMPRESA
    // =========================================================

    private Empresa buscarEmpresa(
            Long empresaId
    ) {

        return empresaRepository
                .findById(
                        empresaId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Empresa no encontrada."
                                )
                );
    }


    private void validarEmpresaExiste(
            Long empresaId
    ) {

        if (
                !empresaRepository
                        .existsById(
                                empresaId
                        )
        ) {

            throw new IllegalArgumentException(
                    "Empresa no encontrada."
            );
        }
    }


    // =========================================================
    // USUARIO PERTENECIENTE A EMPRESA
    // =========================================================

    private Usuario buscarUsuarioEmpresa(

            Long usuarioId,

            Long empresaId
    ) {

        return usuarioRepository
                .bloquearPorIdYEmpresa(

                        usuarioId,

                        empresaId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "El usuario no existe o no pertenece a esta empresa."
                                )
                );
    }


    // =========================================================
    // RESPONSE SEGURO
    // =========================================================

    private UsuarioResponse convertir(
            Usuario usuario
    ) {

        return new UsuarioResponse(

                usuario.getId(),

                usuario.getNombre(),

                usuario.getEmail(),

                usuario.getEmpresa() != null
                        ?
                        usuario
                                .getEmpresa()
                                .getRuc()
                        :
                        null,

                usuario.getRol(),

                usuario.getEstado(),

                SecurityAuthorities
                        .permisosEfectivos(
                                usuario
                        ),

                usuario.isDebeCambiarPassword(),

                usuario.getUltimoLogin(),

                usuario.getCreatedAt()
        );
    }
}
