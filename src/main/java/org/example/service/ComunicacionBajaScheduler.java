package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.model.EstadoComunicacionBaja;
import org.example.repository.ComunicacionBajaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.time.Clock;
import java.time.LocalDateTime;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="sunat.comunicacion-baja.auto.enabled",havingValue="true")
public class ComunicacionBajaScheduler {
    private final ComunicacionBajaRepository repository;
    private final ComunicacionBajaService service;
    private final Clock clock;
    @Value("${sunat.comunicacion-baja.enviando-timeout-minutes:10}")
    private int enviandoTimeoutMinutes=10;
    private int paginaCreadas, paginaTickets;

    @Scheduled(fixedDelayString="${sunat.comunicacion-baja.recovery-delay-ms:120000}",initialDelayString="${sunat.comunicacion-baja.recovery-initial-delay-ms:60000}")
    public synchronized void recuperarCreadas(){
        int inciertos=repository.marcarEnviosVencidosComoInciertos(LocalDateTime.now(clock).minusMinutes(Math.max(1,enviandoTimeoutMinutes)));
        if(inciertos>0) log.warn("{} RA ENVIANDO vencidas requieren conciliación; no se reenvían.",inciertos);
        var creadas=repository.buscarCreadasSinTicket(PageRequest.of(paginaCreadas,20));
        if(creadas.isEmpty() && paginaCreadas>0){paginaCreadas=0;creadas=repository.buscarCreadasSinTicket(PageRequest.of(0,20));}
        paginaCreadas=creadas.isEmpty() || paginaCreadas==Integer.MAX_VALUE?0:paginaCreadas+1;
        for(var b:creadas){
            try{ service.prepararYEnviar(b.getRucEmisor(),b.getId()); }
            catch(Exception e){ log.warn("No se pudo recuperar RA {}: {}",b.getIdentificador(),e.getMessage()); }
        }
    }
    @Scheduled(fixedDelayString="${sunat.comunicacion-baja.ticket-delay-ms:60000}", initialDelayString="${sunat.comunicacion-baja.ticket-initial-delay-ms:45000}")
    public synchronized void consultarTickets(){
        var estados=Set.of(EstadoComunicacionBaja.TICKET_GENERADO,EstadoComunicacionBaja.EN_PROCESO,EstadoComunicacionBaja.ERROR_CONSULTA);
        var pendientes=repository.buscarPendientes(estados, PageRequest.of(paginaTickets,30));
        if(pendientes.isEmpty() && paginaTickets>0){paginaTickets=0;pendientes=repository.buscarPendientes(estados,PageRequest.of(0,30));}
        paginaTickets=pendientes.isEmpty() || paginaTickets==Integer.MAX_VALUE?0:paginaTickets+1;
        for(var b:pendientes){ try{ service.consultarTicket(b.getRucEmisor(),b.getId()); }catch(Exception e){ log.warn("No se pudo consultar RA {}: {}",b.getIdentificador(),e.getMessage()); } }
    }
}
