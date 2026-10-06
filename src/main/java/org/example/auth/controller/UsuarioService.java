package org.example.auth.controller;


import lombok.RequiredArgsConstructor;

import org.example.auth.dto.CrearPropietarioRequest;
import org.example.auth.dto.CrearUsuarioRequest;
import org.example.auth.dto.UsuarioResponse;
import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.PermisoUsuario;
import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UsuarioRepository;
import org.example.auth.security.SecurityAuthorities;
import org.example.auth.security.SecurityContextService;

import org.example.auth.service.PasswordPolicyService;
import org.example.model.*;

import org.example.repository.EmpresaRepository;




import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    private final EmpresaRepository empresaRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final PasswordPolicyService passwordPolicyService;

    private final SecurityContextService securityContextService;

    @Transactional(readOnly = true)
    public UsuarioResponse me() {

        Long usuarioId =
                securityContextService
                        .usuarioId();

        Usuario usuario =
                usuarioRepository
                        .findForSecurityById(
                                usuarioId
                        )
                        .orElseThrow();

        return convertir(
                usuario
        );
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse>
    listarMiEmpresa() {

        Long empresaId =
                securityContextService
                        .empresaIdRequerido();

        return usuarioRepository
                .findAllByEmpresaIdOrderByNombreAsc(
                        empresaId
                )
                .stream()
                .map(this::convertir)
                .toList();
    }

    @Transactional
    public UsuarioResponse crear(
            CrearUsuarioRequest request
    ) {

        Usuario actor =
                actor();

        validarPuedeCrear(
                actor,
                request.rol()
        );

        String email =
                request
                        .email()
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

        Usuario nuevo =
                Usuario
                        .builder()

                        .empresa(
                                actor.getEmpresa()
                        )

                        .nombre(
                                request.nombre()
                        )

                        .email(
                                email
                        )

                        .passwordHash(

                                passwordEncoder
                                        .encode(
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

        return convertir(

                usuarioRepository
                        .saveAndFlush(
                                nuevo
                        )
        );
    }

    @Transactional
    public UsuarioResponse actualizarEstado(

            Long usuarioId,

            boolean activo
    ) {

        Usuario actor =
                actor();

        Usuario objetivo =
                usuarioEmpresa(
                        usuarioId,
                        actor.getEmpresa().getId()
                );

        validarPuedeGestionar(
                actor,
                objetivo
        );

        if (
                Objects.equals(
                        actor.getId(),
                        objetivo.getId()
                )
        ) {

            throw new IllegalArgumentException(
                    "No puede desactivar su propio usuario desde este endpoint."
            );
        }

        objetivo.setEstado(

                activo
                        ? EstadoUsuario.ACTIVO
                        : EstadoUsuario.DESACTIVADO
        );

        if (!activo) {

            invalidarSesiones(
                    objetivo
            );
        }

        usuarioRepository.save(
                objetivo
        );

        return convertir(
                objetivo
        );
    }

    @Transactional
    public UsuarioResponse actualizarPermisos(

            Long usuarioId,

            Set<PermisoUsuario> permisos
    ) {

        Usuario actor =
                actor();

        Usuario objetivo =
                usuarioEmpresa(

                        usuarioId,

                        actor
                                .getEmpresa()
                                .getId()
                );

        validarPuedeGestionar(
                actor,
                objetivo
        );

        if (
                objetivo.getRol()
                        != RolUsuario.TRABAJADOR
        ) {

            throw new IllegalArgumentException(
                    "Los permisos personalizados sólo se aplican a trabajadores."
            );
        }

        Set<PermisoUsuario> nuevos =
                resolverPermisos(

                        RolUsuario.TRABAJADOR,

                        permisos
                );

        objetivo.setPermisos(
                nuevos
        );

        invalidarSesiones(
                objetivo
        );

        usuarioRepository.save(
                objetivo
        );

        return convertir(
                objetivo
        );
    }

    @Transactional
    public void passwordTemporal(

            Long usuarioId,

            String password
    ) {

        Usuario actor =
                actor();

        Usuario objetivo =
                usuarioEmpresa(

                        usuarioId,

                        actor
                                .getEmpresa()
                                .getId()
                );

        validarPuedeGestionar(
                actor,
                objetivo
        );

        passwordPolicyService.validar(

                password,

                objetivo.getEmail()
        );

        objetivo.setPasswordHash(

                passwordEncoder
                        .encode(
                                password
                        )
        );

        objetivo.setDebeCambiarPassword(
                true
        );

        invalidarSesiones(
                objetivo
        );

        usuarioRepository.save(
                objetivo
        );
    }

    @Transactional
    public UsuarioResponse crearPropietario(

            String ruc,

            CrearPropietarioRequest request
    ) {

        Empresa empresa =
                empresaRepository
                        .findByRuc(
                                ruc
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Empresa no encontrada."
                                        )
                        );

        if (
                usuarioRepository
                        .existsByEmpresaIdAndRol(

                                empresa.getId(),

                                RolUsuario.EMPRESA_DUENO
                        )
        ) {

            throw new IllegalArgumentException(
                    "La empresa ya tiene un propietario."
            );
        }

        String email =
                request
                        .email()
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

        Usuario usuario =
                Usuario
                        .builder()

                        .empresa(
                                empresa
                        )

                        .nombre(
                                request.nombre()
                        )

                        .email(
                                email
                        )

                        .passwordHash(

                                passwordEncoder
                                        .encode(
                                                request.passwordTemporal()
                                        )
                        )

                        .rol(
                                RolUsuario.EMPRESA_DUENO
                        )

                        .estado(
                                EstadoUsuario.ACTIVO
                        )

                        .debeCambiarPassword(
                                true
                        )

                        .build();

        return convertir(

                usuarioRepository
                        .saveAndFlush(
                                usuario
                        )
        );
    }

    private Usuario actor() {

        Long id =
                securityContextService
                        .usuarioId();

        if (id == null) {

            throw new SecurityException(
                    "Esta operación requiere un usuario."
            );
        }

        Usuario actor =
                usuarioRepository
                        .findForSecurityById(
                                id
                        )
                        .orElseThrow();

        if (actor.getEmpresa() == null) {

            throw new SecurityException(
                    "El usuario no pertenece a una empresa."
            );
        }

        return actor;
    }

    private void validarPuedeCrear(

            Usuario actor,

            RolUsuario nuevoRol
    ) {

        if (
                nuevoRol
                        == RolUsuario.SUPER_ADMIN
                        ||
                        nuevoRol
                                == RolUsuario.EMPRESA_DUENO
        ) {

            throw new SecurityException(
                    "Rol no permitido."
            );
        }

        if (
                actor.getRol()
                        == RolUsuario.EMPRESA_DUENO
        ) {

            return;
        }

        if (
                actor.getRol()
                        == RolUsuario.EMPRESA_ADMIN
                        &&
                        nuevoRol
                                == RolUsuario.TRABAJADOR
        ) {

            return;
        }

        throw new SecurityException(
                "No tiene permisos para crear ese rol."
        );
    }

    private void validarPuedeGestionar(

            Usuario actor,

            Usuario objetivo
    ) {

        if (
                objetivo.getRol()
                        == RolUsuario.EMPRESA_DUENO
        ) {

            throw new SecurityException(
                    "El propietario no puede administrarse desde este endpoint."
            );
        }

        if (
                actor.getRol()
                        == RolUsuario.EMPRESA_DUENO
        ) {

            return;
        }

        if (
                actor.getRol()
                        == RolUsuario.EMPRESA_ADMIN
                        &&
                        objetivo.getRol()
                                == RolUsuario.TRABAJADOR
        ) {

            return;
        }

        throw new SecurityException(
                "Operación no autorizada."
        );
    }

    private Set<PermisoUsuario> resolverPermisos(

            RolUsuario rol,

            Set<PermisoUsuario> solicitados
    ) {

        if (
                rol
                        != RolUsuario.TRABAJADOR
        ) {

            return new HashSet<>();
        }

        Set<PermisoUsuario> permitidos =
                SecurityAuthorities
                        .permisosPermitidosTrabajador();

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

            throw new SecurityException(
                    "Se intentó asignar un permiso no permitido para trabajador."
            );
        }

        return EnumSet.copyOf(
                solicitados
        );
    }

    private Usuario usuarioEmpresa(

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
                                        "Usuario no encontrado."
                                )
                );
    }

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

    private UsuarioResponse convertir(
            Usuario usuario
    ) {

        return new UsuarioResponse(

                usuario.getId(),

                usuario.getNombre(),

                usuario.getEmail(),

                usuario.getEmpresa() != null
                        ? usuario
                        .getEmpresa()
                        .getRuc()
                        : null,

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
