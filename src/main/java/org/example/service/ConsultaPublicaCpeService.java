package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.repository.ComprobanteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;

@Service @RequiredArgsConstructor
public class ConsultaPublicaCpeService {
    private final ComprobanteRepository repository;

    @Transactional(readOnly=true)
    public ConsultaPublicaCpeResponse consultar(ConsultaPublicaCpeRequest r){
        if(r==null) throw new IllegalArgumentException("Datos de consulta requeridos.");
        var opt=repository.buscarParaConsultaPublica(
                r.rucEmisor().trim(),r.tipoComprobante().trim(),r.serie().trim().toUpperCase(),r.numero());
        if(opt.isEmpty()) return noEncontrado();
        var c=opt.get();
        if(c.getFechaEmision()==null || !c.getFechaEmision().toLocalDate().equals(r.fechaEmision())) return noEncontrado();
        if(c.getMontoTotal()==null || c.getMontoTotal().setScale(2,RoundingMode.HALF_UP).compareTo(r.importeTotal().setScale(2,RoundingMode.HALF_UP))!=0) return noEncontrado();
        if(r.tipoDocReceptor()!=null && !r.tipoDocReceptor().isBlank() && !seguro(c.getTipoDocCliente()).equals(r.tipoDocReceptor().trim())) return noEncontrado();
        if(r.numDocReceptor()!=null && !r.numDocReceptor().isBlank() && !seguro(c.getNumDocCliente()).equals(r.numDocReceptor().trim())) return noEncontrado();
        String estado=switch(c.getEstadoSfs()){
            case ACEPTADO,ACEPTADO_CON_OBSERVACION -> "VALIDO";
            case ANULADO -> "ANULADO";
            case RECHAZADO -> "RECHAZADO";
            default -> "EN_PROCESO";
        };
        return new ConsultaPublicaCpeResponse(true,estado,"El comprobante coincide con los datos proporcionados.");
    }
    private ConsultaPublicaCpeResponse noEncontrado(){ return new ConsultaPublicaCpeResponse(false,"NO_ENCONTRADO","No se encontró un comprobante que coincida con todos los datos proporcionados."); }
    private String seguro(String s){ return s==null?"":s.trim(); }
}
