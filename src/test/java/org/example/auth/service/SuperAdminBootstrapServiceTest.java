package org.example.auth.service;

import org.example.auth.model.Usuario;
import org.example.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuperAdminBootstrapServiceTest {
    @Test void bootstrapNuncaImprimeCredencialProporcionada() {
        var usuarios = mock(UsuarioRepository.class); var encoder = mock(PasswordEncoder.class);
        String credencial = "Password-Ficticio-Autorizado-4!";
        when(encoder.encode(credencial)).thenReturn("HASH_FICTICIO");
        var service = new SuperAdminBootstrapService(usuarios, encoder, mock(PasswordPolicyService.class));
        ReflectionTestUtils.setField(service,"autoCreateEnabled",true);
        ReflectionTestUtils.setField(service,"emailSuperAdmin","bootstrap@example.invalid");
        ReflectionTestUtils.setField(service,"initialPassword",credencial);
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        var out = System.out; var err = System.err;
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); logs.start();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME); logger.addAppender(logs);
        try {
            System.setOut(new PrintStream(stdout)); System.setErr(new PrintStream(stderr)); service.run(null);
            assertFalse(stdout.toString().contains(credencial)); assertFalse(stderr.toString().contains(credencial));
            logs.list.forEach(e -> assertFalse(e.getFormattedMessage().contains(credencial)));
            var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class); verify(usuarios).saveAndFlush(captor.capture());
            assertEquals("HASH_FICTICIO",captor.getValue().getPasswordHash()); assertTrue(captor.getValue().isDebeCambiarPassword());
        } finally { System.setOut(out); System.setErr(err); logger.detachAppender(logs); logs.stop(); }
    }
    @Test void bootstrapSinCanalDeProvisionFallaSinCrearUsuario() {
        var usuarios = mock(UsuarioRepository.class);
        var service = new SuperAdminBootstrapService(usuarios,mock(PasswordEncoder.class),mock(PasswordPolicyService.class));
        ReflectionTestUtils.setField(service,"autoCreateEnabled",true); ReflectionTestUtils.setField(service,"emailSuperAdmin","bootstrap@example.invalid");
        assertThrows(IllegalStateException.class, () -> service.run(null)); verify(usuarios,never()).saveAndFlush(any());
    }
}
