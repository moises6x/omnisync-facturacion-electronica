package org.example.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.auth.model.EventoAuditoria;
import org.example.auth.repository.EventoAuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Punto único para escribir eventos de auditoría sin guardar secretos ni XML completos. */
@Service @RequiredArgsConstructor
public class AuditoriaService {
    private final EventoAuditoriaRepository repository;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void registrarSistema(String ruc,String accion,String resultado,String detalle){
        repository.save(EventoAuditoria.builder()
                .rucEmpresa(ruc).accion(limpiar(accion,100)).resultado(limpiar(resultado,20))
                .detalle(limpiar(detalle,1000)).build());
    }
    private String limpiar(String s,int max){ if(s==null) return null; String x=s.replaceAll("[\\r\\n\\t]+"," ").trim(); return x.length()<=max?x:x.substring(0,max); }
}
