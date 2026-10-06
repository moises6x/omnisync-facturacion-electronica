package org.example.auth.service;


import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class PasswordPolicyService {

    private static final Set<String> PATRONES_DEBILES =
            Set.of(
                    "password",
                    "password123",
                    "contraseña",
                    "123456789",
                    "1234567890",
                    "qwerty",
                    "qwerty123",
                    "admin123",
                    "administrador"
            );

    public void validar(

            String password,

            String email
    ) {

        if (password == null) {

            throw new IllegalArgumentException(
                    "La contraseña es obligatoria."
            );
        }

        if (
                password.length() < 12
                        ||
                        password.length() > 128
        ) {

            throw new IllegalArgumentException(
                    "La contraseña debe tener entre 12 y 128 caracteres."
            );
        }

        String lower =
                password.toLowerCase(
                        Locale.ROOT
                );

        if (
                PATRONES_DEBILES
                        .contains(lower)
        ) {

            throw new IllegalArgumentException(
                    "La contraseña es demasiado fácil de adivinar."
            );
        }

        if (
                email != null
                        &&
                        email.contains("@")
        ) {

            String usuarioEmail =
                    email
                            .substring(
                                    0,
                                    email.indexOf('@')
                            )
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (
                    usuarioEmail.length() >= 4
                            &&
                            lower.contains(
                                    usuarioEmail
                            )
            ) {

                throw new IllegalArgumentException(
                        "La contraseña no debe contener el correo del usuario."
                );
            }
        }

        long caracteresDistintos =
                password
                        .chars()
                        .distinct()
                        .count();

        if (caracteresDistintos < 6) {

            throw new IllegalArgumentException(
                    "La contraseña es demasiado repetitiva."
            );
        }
    }
}