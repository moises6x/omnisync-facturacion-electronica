package org.example.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityContextService {

    public AppPrincipal principal() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (
                authentication == null
                        ||
                        !authentication.isAuthenticated()
                        ||
                        !(authentication.getPrincipal()
                                instanceof AppPrincipal principal)
        ) {

            throw new SecurityException(
                    "Autenticación requerida."
            );
        }

        return principal;
    }

    public String rucEmpresaRequerido() {

        AppPrincipal principal =
                principal();

        if (
                principal.ruc() == null
                        ||
                        principal.ruc().isBlank()
        ) {

            throw new SecurityException(
                    "La sesión no pertenece a una empresa."
            );
        }

        return principal.ruc();
    }

    public Long empresaIdRequerido() {

        AppPrincipal principal =
                principal();

        if (principal.empresaId() == null) {

            throw new SecurityException(
                    "La sesión no pertenece a una empresa."
            );
        }

        return principal.empresaId();
    }

    public Long usuarioId() {

        return principal().usuarioId();
    }
}