package org.example.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.auth.dto.*;
import org.example.auth.model.RolUsuario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthDtoValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void loginDebeExigirEmailValidoYPassword() {
        assertFalse(validator.validate(new LoginRequest("correo-invalido", "x")).isEmpty());
        assertFalse(validator.validate(new LoginRequest("user@test.com", "")).isEmpty());
    }

    @Test
    void bootstrapDebeExigirPasswordMinimo12() {
        var r = new BootstrapSuperAdminRequest("Admin", "admin@test.com", "corta");
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void cambioPasswordDebeExigirNuevaDe12() {
        var r = new ChangePasswordRequest("actual", "corta");
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void crearUsuarioDebeExigirRolYPasswordTemporal() {
        var r = new CrearUsuarioRequest("U", "u@test.com", "123456789012", null, Set.of());
        assertFalse(validator.validate(r).isEmpty());
    }

    @Test
    void crearUsuarioValidoDebePasarBeanValidation() {
        var r = new CrearUsuarioRequest(
                "Usuario Test",
                "user@test.com",
                "Temporal#2026",
                RolUsuario.TRABAJADOR,
                Set.of()
        );
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void refreshTokenDebeExigirValorYMaximo256() {
        assertFalse(validator.validate(new RefreshRequest("")).isEmpty());
        assertFalse(validator.validate(new RefreshRequest("x".repeat(257))).isEmpty());
        assertTrue(validator.validate(new RefreshRequest("token")).isEmpty());
    }
}
