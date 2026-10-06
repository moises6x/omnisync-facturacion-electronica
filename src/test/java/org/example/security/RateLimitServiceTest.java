package org.example.security;

import org.example.auth.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService();
        ReflectionTestUtils.setField(service, "loginLimit", 2);
        ReflectionTestUtils.setField(service, "loginWindowSeconds", 60L);
        ReflectionTestUtils.setField(service, "refreshLimit", 3);
        ReflectionTestUtils.setField(service, "refreshWindowSeconds", 60L);
        ReflectionTestUtils.setField(service, "generalLimit", 4);
        ReflectionTestUtils.setField(service, "generalWindowSeconds", 60L);
    }

    @Test
    void optionsSiempreDebePermitirse() {
        MockHttpServletRequest req = request("OPTIONS", "/api/v1/auth/login", "10.0.0.1");
        assertTrue(service.verificar(req).permitido());
    }

    @Test
    void loginDebeBloquearDespuesDelLimite() {
        MockHttpServletRequest req = request("POST", "/api/v1/auth/login", "10.0.0.2");
        assertTrue(service.verificar(req).permitido());
        assertTrue(service.verificar(req).permitido());
        var tercera = service.verificar(req);
        assertFalse(tercera.permitido());
        assertTrue(tercera.retryAfterSeconds() >= 1);
    }

    @Test
    void gruposLoginRefreshYGeneralDebenSerIndependientes() {
        MockHttpServletRequest login = request("POST", "/api/v1/auth/login", "10.0.0.3");
        service.verificar(login);
        service.verificar(login);
        assertFalse(service.verificar(login).permitido());

        MockHttpServletRequest refresh = request("POST", "/api/v1/auth/refresh", "10.0.0.3");
        assertTrue(service.verificar(refresh).permitido());

        MockHttpServletRequest general = request("GET", "/api/v1/facturacion", "10.0.0.3");
        assertTrue(service.verificar(general).permitido());
    }

    @Test
    void ipsDiferentesDebenTenerVentanasIndependientes() {
        MockHttpServletRequest a = request("POST", "/api/v1/auth/login", "10.0.0.4");
        MockHttpServletRequest b = request("POST", "/api/v1/auth/login", "10.0.0.5");
        service.verificar(a); service.verificar(a); service.verificar(a);
        assertFalse(service.verificar(a).permitido());
        assertTrue(service.verificar(b).permitido());
    }

    private MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRemoteAddr(ip);
        return r;
    }
}
