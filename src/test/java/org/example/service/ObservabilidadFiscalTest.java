package org.example.service;

import org.example.util.ObservabilidadFiscal;
import org.example.auth.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservabilidadFiscalTest {
    @Test void auditoriaCaidaNoPropagaNiRegistraMensajePotencialmenteSecreto(){
        var auditoria=mock(AuditoriaService.class);String secreto="SECRETO_FICTICIO_NO_LOG";
        doThrow(new IllegalStateException(secreto)).when(auditoria).registrarSistema(anyString(),anyString(),anyString(),anyString());
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(ObservabilidadFiscal.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        try{
            assertDoesNotThrow(()->ObservabilidadFiscal.auditar(auditoria,"20100130204","CDR_RA","ACEPTADO","RA-TEST"));
            ObservabilidadFiscal.evento("20100130204",42L,"RA-TEST","TICKET-FICTICIO","ACEPTADO","0");
            assertEquals(2,logs.list.size());
            logs.list.forEach(e->{assertFalse(e.getFormattedMessage().contains(secreto));assertTrue(e.getFormattedMessage().contains("nodo="));assertTrue(e.getTimeStamp()>0);});
            String evento=logs.list.getLast().getFormattedMessage();
            for(String campo:java.util.List.of("ruc=20100130204","id=42","documento=RA-TEST","ticket=TICKET-FICTICIO","estado=ACEPTADO"))assertTrue(evento.contains(campo));
        }finally{logger.detachAppender(logs);logs.stop();}
    }
}
