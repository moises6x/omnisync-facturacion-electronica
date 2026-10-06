package org.example.service;


import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SunatPlazoServiceTestt {
    private SunatPlazoService service(LocalDate hoy){
        var s=new SunatPlazoService(Clock.fixed(hoy.atTime(12,0).atZone(ZoneId.of("America/Lima")).toInstant(),ZoneId.of("America/Lima")));
        ReflectionTestUtils.setField(s,"envioIndividualDias",3);
        ReflectionTestUtils.setField(s,"resumenDiarioDias",7);
        ReflectionTestUtils.setField(s,"bajaDias",7);
        return s;
    }
    @Test
    void envioIndividualAceptaDiaLimite(){ assertDoesNotThrow(()->service(LocalDate.of(2026,9,19)).validarEnvioIndividual(LocalDateTime.of(2026,9,16,10,0))); }
    @Test
    void envioIndividualBloqueaDespuesDelLimite(){ assertThrows(SunatPlazoService.FueraDePlazoSunatException.class,()->service(LocalDate.of(2026,9,20)).validarEnvioIndividual(LocalDateTime.of(2026,9,16,10,0))); }
    @Test void resumenAceptaSeptimoDia(){ assertDoesNotThrow(()->service(LocalDate.of(2026,9,19)).validarResumenDiario(LocalDate.of(2026,9,12))); }
    @Test void resumenBloqueaOctavoDia(){ assertThrows(SunatPlazoService.FueraDePlazoSunatException.class,()->service(LocalDate.of(2026,9,20)).validarResumenDiario(LocalDate.of(2026,9,12))); }
    @Test void bajaAceptaSeptimoDia(){ assertDoesNotThrow(()->service(LocalDate.of(2026,9,19)).validarBaja(LocalDate.of(2026,9,12))); }
    @Test void fechaFuturaSeRechaza(){ assertThrows(IllegalArgumentException.class,()->service(LocalDate.of(2026,9,19)).validarResumenDiario(LocalDate.of(2026,9,20))); }
}
