package org.example.service;

import org.example.auth.exception.EmpresaSuspendidaException;
import org.example.model.Empresa;
import org.example.model.EstadoEmpresa;
import org.example.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaAccessServiceTest {

    @Mock
    private EmpresaRepository repository;
    @Mock
    private ApiKeyService apiKeyService;

    private EmpresaAccessService service;

    @BeforeEach
    void setUp() {
        service = new EmpresaAccessService(repository, apiKeyService);
    }

    @Test
    void empresaInexistenteDebeRechazarse() {
        when(repository.findByRuc("20100130204")).thenReturn(Optional.empty());
        assertThrows(SecurityException.class,
                () -> service.validar("20100130204", "api"));
    }

    @Test
    void apiKeyIncorrectaDebeRechazarseAntesDeRevelarEstado() {
        Empresa e = empresa(EstadoEmpresa.SUSPENDIDA);
        when(repository.findByRuc(e.getRuc())).thenReturn(Optional.of(e));
        when(apiKeyService.coincide("mala", e.getApiKeyHash())).thenReturn(false);

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validar(e.getRuc(), "mala"));
        assertTrue(ex.getMessage().contains("Credenciales"));
    }

    @Test
    void empresaActivaConApiKeyValidaDebePasar() {
        Empresa e = empresa(EstadoEmpresa.ACTIVA);
        when(repository.findByRuc(e.getRuc())).thenReturn(Optional.of(e));
        when(apiKeyService.coincide("correcta", e.getApiKeyHash())).thenReturn(true);
        assertSame(e, service.validar(e.getRuc(), "correcta"));
    }

    @Test
    void empresaSuspendidaDebeBloquearse() {
        Empresa e = empresa(EstadoEmpresa.SUSPENDIDA);
        e.setMotivoSuspension("FALTA DE PAGO");
        when(repository.findByRuc(e.getRuc())).thenReturn(Optional.of(e));
        when(apiKeyService.coincide("correcta", e.getApiKeyHash())).thenReturn(true);

        EmpresaSuspendidaException ex = assertThrows(EmpresaSuspendidaException.class,
                () -> service.validar(e.getRuc(), "correcta"));
        assertTrue(ex.getMessage().contains("FALTA DE PAGO"));
    }

    @Test
    void validarEmpresaActivaDebeRechazarNull() {
        assertThrows(SecurityException.class, () -> service.validarEmpresaActiva(null));
    }

    private Empresa empresa(EstadoEmpresa estado) {
        Empresa e = new Empresa();
        e.setRuc("20100130204");
        e.setApiKeyHash("hash");
        e.setEstadoPlataforma(estado);
        return e;
    }
}
