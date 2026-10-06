package org.example.auth.repository;


import org.example.auth.model.EventoAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository
        extends JpaRepository<EventoAuditoria, Long> {
}