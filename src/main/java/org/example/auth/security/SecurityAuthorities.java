package org.example.auth.security;


import org.example.auth.model.PermisoUsuario;
import org.example.auth.model.Usuario;
import org.example.model.*;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;

public final class SecurityAuthorities {

    private SecurityAuthorities() {
    }

    private static final Set<PermisoUsuario>
            PERMISOS_TRABAJADOR =
            EnumSet.of(

                    PermisoUsuario.FACTURACION_EMITIR,

                    PermisoUsuario.FACTURACION_CONSULTAR,

                    PermisoUsuario.NOTA_VENTA_EMITIR,

                    PermisoUsuario.CONSULTA_RUC
            );

    public static Set<PermisoUsuario>
    permisosEfectivos(
            Usuario usuario
    ) {

        EnumSet<PermisoUsuario> resultado =
                EnumSet.noneOf(
                        PermisoUsuario.class
                );

        switch (usuario.getRol()) {

            case SUPER_ADMIN ->
                    resultado.addAll(
                            EnumSet.allOf(
                                    PermisoUsuario.class
                            )
                    );

            case EMPRESA_DUENO ->
                    resultado.addAll(
                            EnumSet.allOf(
                                    PermisoUsuario.class
                            )
                    );

            case EMPRESA_ADMIN -> {

                resultado.add(
                        PermisoUsuario.FACTURACION_EMITIR
                );

                resultado.add(
                        PermisoUsuario.FACTURACION_CONSULTAR
                );

                resultado.add(
                        PermisoUsuario.NOTA_VENTA_EMITIR
                );

                resultado.add(
                        PermisoUsuario.CONSULTA_RUC
                );

                resultado.add(
                        PermisoUsuario.CORRELATIVOS_CONFIGURAR
                );

                resultado.add(
                        PermisoUsuario.USUARIOS_GESTIONAR
                );
            }

            case TRABAJADOR -> {

                if (usuario.getPermisos() != null) {

                    for (
                            PermisoUsuario permiso :
                            usuario.getPermisos()
                    ) {

                        if (
                                PERMISOS_TRABAJADOR
                                        .contains(permiso)
                        ) {

                            resultado.add(permiso);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableSet(
                resultado
        );
    }

    public static Collection<? extends GrantedAuthority>
    paraUsuario(
            Usuario usuario
    ) {

        List<GrantedAuthority> authorities =
                new ArrayList<>();

        authorities.add(
                new SimpleGrantedAuthority(
                        "ROLE_"
                                + usuario
                                .getRol()
                                .name()
                )
        );

        for (
                PermisoUsuario permiso :
                permisosEfectivos(usuario)
        ) {

            authorities.add(
                    new SimpleGrantedAuthority(
                            "PERM_"
                                    + permiso.name()
                    )
            );
        }

        return authorities;
    }

    public static Collection<? extends GrantedAuthority>
    paraApiClient() {

        List<GrantedAuthority> authorities =
                new ArrayList<>();

        authorities.add(
                new SimpleGrantedAuthority(
                        "ROLE_API_CLIENT"
                )
        );

        authorities.add(
                new SimpleGrantedAuthority(
                        "PERM_FACTURACION_EMITIR"
                )
        );

        authorities.add(
                new SimpleGrantedAuthority(
                        "PERM_FACTURACION_CONSULTAR"
                )
        );

        authorities.add(
                new SimpleGrantedAuthority(
                        "PERM_NOTA_VENTA_EMITIR"
                )
        );

        authorities.add(
                new SimpleGrantedAuthority(
                        "PERM_CONSULTA_RUC"
                )
        );

        authorities.add(
                new SimpleGrantedAuthority(
                        "PERM_CORRELATIVOS_CONFIGURAR"
                )
        );

        return authorities;
    }

    public static Set<PermisoUsuario>
    permisosPermitidosTrabajador() {

        return Collections.unmodifiableSet(
                PERMISOS_TRABAJADOR
        );
    }
}