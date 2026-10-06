package org.example.auth.service;

import lombok.RequiredArgsConstructor;

import org.example.auth.model.EstadoUsuario;
import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;
import org.example.auth.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;


@Service
@RequiredArgsConstructor
public class SuperAdminBootstrapService
        implements ApplicationRunner {

    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    /*
     * Se puede cambiar mediante variable/property.
     *
     * Si no se configura nada:
     *
     * superadmin@omnisync.com
     */
    @Value(
            "${security.bootstrap.superadmin-email:superadmin@omnisync.com}"
    )
    private String emailSuperAdmin;


    /*
     * Permite desactivar completamente
     * la creación automática.
     */
    @Value(
            "${security.bootstrap.auto-create:true}"
    )
    private boolean autoCreateEnabled;

    @Value("${security.bootstrap.initial-password:}")
    private String initialPassword;


    private static final int PASSWORD_LENGTH =
            20;


    /*
     * Caracteres utilizados para crear
     * la contraseña temporal.
     *
     * Evitamos caracteres demasiado
     * confusos visualmente.
     */
    private static final String MAYUSCULAS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final String MINUSCULAS =
            "abcdefghijkmnopqrstuvwxyz";

    private static final String NUMEROS =
            "23456789";

    private static final String SIMBOLOS =
            "!@#$%&*_-";

    private static final String TODOS =
            MAYUSCULAS
                    + MINUSCULAS
                    + NUMEROS
                    + SIMBOLOS;


    private final UsuarioRepository
            usuarioRepository;

    private final PasswordEncoder
            passwordEncoder;

    private final PasswordPolicyService
            passwordPolicyService;


    private final SecureRandom secureRandom =
            new SecureRandom();


    // =========================================================
    // ARRANQUE DE SPRING
    // =========================================================

    @Override
    @Transactional
    public void run(
            ApplicationArguments args
    ) {

        if (!autoCreateEnabled) {

            return;
        }


        // =====================================================
        // SI YA EXISTE SUPER_ADMIN, NO HACER NADA
        // =====================================================

        if (
                usuarioRepository.existsByRol(
                        RolUsuario.SUPER_ADMIN
                )
        ) {

            return;
        }


        // =====================================================
        // NORMALIZAR EMAIL
        // =====================================================

        String email =
                emailSuperAdmin
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        // =====================================================
        // EVITAR CONFLICTO DE CORREO
        // =====================================================

        if (
                usuarioRepository
                        .existsByEmailIgnoreCase(
                                email
                        )
        ) {

            throw new IllegalStateException(
                    "No se pudo crear el SUPER_ADMIN inicial: "
                            + "el correo configurado ya pertenece "
                            + "a otro usuario."
            );
        }


        // =====================================================
        // CREDENCIAL TEMPORAL PROVISTA POR CANAL AUTORIZADO
        // =====================================================

        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException("Bootstrap automático exige SECURITY_BOOTSTRAP_INITIAL_PASSWORD por canal autorizado, sin logs.");
        }
        String passwordTemporal = initialPassword;


        /*
         * La credencial suministrada pasa por la misma política de seguridad
         * utilizada por todos los demás usuarios.
         */
        passwordPolicyService.validar(
                passwordTemporal,
                email
        );


        // =====================================================
        // CREAR SUPER ADMIN
        // =====================================================

        Usuario usuario =
                Usuario
                        .builder()

                        .nombre(
                                "Super Administrador"
                        )

                        .email(
                                email
                        )

                        /*
                         * Nunca se guarda la contraseña
                         * temporal en texto plano.
                         */
                        .passwordHash(
                                passwordEncoder
                                        .encode(
                                                passwordTemporal
                                        )
                        )

                        .rol(
                                RolUsuario.SUPER_ADMIN
                        )

                        .estado(
                                EstadoUsuario.ACTIVO
                        )

                        /*
                         * OBLIGATORIO:
                         *
                         * Al iniciar sesión por primera vez
                         * Angular lo enviará a cambiar password.
                         */
                        .debeCambiarPassword(
                                true
                        )

                        /*
                         * SUPER_ADMIN no pertenece
                         * a ninguna empresa.
                         */
                        .empresa(
                                null
                        )

                        .build();


        usuarioRepository
                .saveAndFlush(
                        usuario
                );


        // =====================================================
        // NUNCA MOSTRAR CREDENCIALES DE INSTALACIÓN
        // =====================================================
        //
        // IMPORTANTE:
        //
        // Sólo llega aquí cuando NO existía SUPER_ADMIN.
        //
        // En el siguiente reinicio:
        // existsByRol() será TRUE
        // y no se volverá a crear el usuario.
        //
        // =====================================================

        // La credencial ya la posee el operador por el canal de provisión autorizado.
        // Nunca imprimirla, ni siquiera durante la primera instalación.
    }


    // =========================================================
    // GENERAR CONTRASEÑA
    // =========================================================

    private String generarPasswordTemporal() {

        char[] password =
                new char[PASSWORD_LENGTH];


        /*
         * Garantizamos al menos:
         *
         * 1 mayúscula
         * 1 minúscula
         * 1 número
         * 1 símbolo
         */

        password[0] =
                obtenerCaracter(
                        MAYUSCULAS
                );

        password[1] =
                obtenerCaracter(
                        MINUSCULAS
                );

        password[2] =
                obtenerCaracter(
                        NUMEROS
                );

        password[3] =
                obtenerCaracter(
                        SIMBOLOS
                );


        /*
         * Resto completamente aleatorio.
         */

        for (
                int i = 4;
                i < PASSWORD_LENGTH;
                i++
        ) {

            password[i] =
                    obtenerCaracter(
                            TODOS
                    );
        }


        /*
         * Barajamos posiciones para que los
         * primeros caracteres no tengan
         * siempre el mismo patrón.
         */

        for (
                int i = password.length - 1;
                i > 0;
                i--
        ) {

            int j =
                    secureRandom
                            .nextInt(
                                    i + 1
                            );


            char temporal =
                    password[i];

            password[i] =
                    password[j];

            password[j] =
                    temporal;
        }


        return new String(
                password
        );
    }


    // =========================================================
    // OBTENER CARÁCTER ALEATORIO
    // =========================================================

    private char obtenerCaracter(
            String caracteres
    ) {

        int indice =
                secureRandom
                        .nextInt(
                                caracteres.length()
                        );


        return caracteres.charAt(
                indice
        );
    }


    // =========================================================
    // MOSTRAR CREDENCIALES
    // =========================================================

    private void mostrarCredenciales(

            String email,

            String passwordTemporal

    ) {

        throw new IllegalStateException("La entrega de credenciales por stdout está prohibida.");
    }
}
