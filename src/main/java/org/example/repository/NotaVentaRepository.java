package org.example.repository;

import org.example.model.NotaVenta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaVentaRepository
        extends JpaRepository<NotaVenta, Long> {
}