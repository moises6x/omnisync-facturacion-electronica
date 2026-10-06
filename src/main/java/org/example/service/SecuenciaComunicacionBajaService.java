package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.repository.SecuenciaComunicacionBajaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class SecuenciaComunicacionBajaService {
    private final SecuenciaComunicacionBajaRepository repository;
    @Transactional
    public int siguiente(String ruc, LocalDate fecha){
        if(ruc==null || !ruc.matches("^[0-9]{11}$")) throw new IllegalArgumentException("RUC inválido para Comunicación de Baja.");
        if(fecha==null) throw new IllegalArgumentException("Fecha requerida para Comunicación de Baja.");
        repository.incrementar(ruc,fecha);
        int n=repository.findByRucEmisorAndFechaReferencia(ruc,fecha).orElseThrow().getUltimoCorrelativo();
        if(n<1 || n>99999) throw new IllegalStateException("Correlativo RA fuera del rango 1..99999.");
        return n;
    }
}
