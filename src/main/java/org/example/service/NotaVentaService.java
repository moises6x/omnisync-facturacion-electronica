package org.example.service;


import org.example.dto.NotaVentaRequest;
import org.example.model.NotaVenta;

public interface NotaVentaService {
    NotaVenta procesarNotaVenta(String rucEmisor, NotaVentaRequest request);
}