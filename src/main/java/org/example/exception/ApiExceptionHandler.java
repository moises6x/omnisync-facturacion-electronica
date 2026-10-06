package org.example.exception;

import lombok.extern.slf4j.Slf4j;

import org.example.auth.exception.AuthException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    // =========================================================
    // 400 - DATOS INVÁLIDOS
    // =========================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(
            IllegalArgumentException e
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message", mensajeSeguro(
                                        e.getMessage(),
                                        "Solicitud inválida."
                                )
                        )
                );
    }

    // =========================================================
    // 400 - BODY FALTANTE / JSON MAL FORMADO
    // =========================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> bodyInvalido(
            HttpMessageNotReadableException e
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message",
                                "El cuerpo de la solicitud es obligatorio o contiene JSON inválido."
                        )
                );
    }

    // =========================================================
    // 401 - ERRORES DE AUTENTICACIÓN DE LA APLICACIÓN
    // =========================================================
    //
    // AuthService utiliza AuthException para:
    // - credenciales inválidas
    // - refresh token inválido/expirado
    // - contraseña actual incorrecta
    // - sesión inválida
    //
    // Antes esta excepción caía en el handler genérico y podía
    // terminar respondiendo HTTP 500. Ahora se responde como 401.
    // =========================================================

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> authException(
            AuthException e
    ) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message", mensajeSeguro(
                                        e.getMessage(),
                                        "No autorizado."
                                )
                        )
                );
    }

    // =========================================================
    // 401 - CREDENCIALES / TOKEN INVÁLIDO
    // =========================================================

    @ExceptionHandler({
            SecurityException.class,
            AuthenticationException.class
    })
    public ResponseEntity<?> unauthorized(
            Exception e
    ) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message", "No autorizado."
                        )
                );
    }

    // =========================================================
    // 403 - AUTENTICADO PERO SIN PERMISO
    // =========================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> forbidden(
            AccessDeniedException e
    ) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message",
                                "No tiene permisos para realizar esta operación."
                        )
                );
    }

    // =========================================================
    // 400 - VALIDACIÓN @VALID
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(
            MethodArgumentNotValidException e
    ) {

        Map<String, String> errores =
                new LinkedHashMap<>();

        for (
                FieldError error :
                e.getBindingResult().getFieldErrors()
        ) {

            errores.put(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }

        return ResponseEntity
                .badRequest()
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message", "Datos inválidos",
                                "errors", errores
                        )
                );
    }

    // =========================================================
    // 500 - ERROR INTERNO REAL
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> internal(
            Exception e
    ) {

        String errorId =
                UUID.randomUUID().toString();

        /*
         * El detalle completo queda únicamente en el servidor.
         * Al cliente no se le expone stack trace ni información
         * interna de la aplicación.
         */
        log.error(
                "Error interno. errorId={}",
                errorId,
                e
        );

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        Map.of(
                                "status", "ERROR",
                                "message",
                                "Error interno del servidor.",
                                "errorId",
                                errorId
                        )
                );
    }

    // =========================================================
    // UTILIDAD
    // =========================================================

    private String mensajeSeguro(
            String mensaje,
            String fallback
    ) {

        if (
                mensaje == null
                        || mensaje.isBlank()
        ) {
            return fallback;
        }

        return mensaje;
    }
}
