package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repository.EmpresaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

/** Alerta preventivamente por certificados vencidos o próximos a vencer. */
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="sunat.certificate.monitor.enabled",havingValue="true",matchIfMissing=true)
public class CertificadoMonitorScheduler {
    private final EmpresaRepository empresaRepository;
    private final Clock clock;

    @Scheduled(cron="${sunat.certificate.monitor.cron:0 0 8 * * *}",zone="${app.time-zone:America/Lima}")
    public void revisar(){
        LocalDate hoy=LocalDate.now(clock);
        for(var e:empresaRepository.listarCertificadosParaMonitoreo()){
            LocalDate vence=e.getCertificadoVigenteHasta();
            if(vence==null) continue;
            long dias=Duration.between(hoy.atStartOfDay(),vence.atStartOfDay()).toDays();
            if(dias<0) log.error("CERTIFICADO VENCIDO: empresa={} ruc={} venció={}",e.getRazonSocial(),e.getRuc(),vence);
            else if(dias<=7) log.error("CERTIFICADO CRÍTICO: empresa={} ruc={} vence en {} día(s) ({})",e.getRazonSocial(),e.getRuc(),dias,vence);
            else if(dias<=30) log.warn("Certificado próximo a vencer: empresa={} ruc={} vence en {} día(s) ({})",e.getRazonSocial(),e.getRuc(),dias,vence);
        }
    }
}
