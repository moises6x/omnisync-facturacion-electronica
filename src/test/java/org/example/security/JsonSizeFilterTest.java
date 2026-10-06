package org.example.security;

import org.example.auth.security.JsonSizeFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JsonSizeFilterTest {
    @Test void aceptaJsonYConservaBytes() throws Exception {
        var request=new MockHttpServletRequest();request.setContentType("application/json");request.setContent("{\"test\":1}".getBytes());
        var response=new MockHttpServletResponse();var llamadas=new AtomicInteger();
        new JsonSizeFilter().doFilter(request,response,(r,s)->{llamadas.incrementAndGet();assertArrayEquals(request.getContentAsByteArray(),r.getInputStream().readAllBytes());});
        assertEquals(1,llamadas.get());
    }
    @Test void jsonExcesivoNoLlegaAlControlador() throws Exception {
        var request=new MockHttpServletRequest();request.setContentType("application/json");request.setContent(new byte[JsonSizeFilter.MAX_BYTES+1]);
        var response=new MockHttpServletResponse();new JsonSizeFilter().doFilter(request,response,(r,s)->fail("No procesar body excesivo"));assertEquals(413,response.getStatus());
    }
    @Test void chunkedExcesivoTambienSeRechaza() throws Exception {
        var request=new MockHttpServletRequest(){@Override public long getContentLengthLong(){return -1;}};
        request.setContentType("application/problem+json");request.setContent(new byte[JsonSizeFilter.MAX_BYTES+1]);
        var response=new MockHttpServletResponse();new JsonSizeFilter().doFilter(request,response,(r,s)->fail("No procesar chunked excesivo"));assertEquals(413,response.getStatus());
    }
}
