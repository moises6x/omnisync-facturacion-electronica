package org.example.service;

import com.sun.net.httpserver.HttpServer;
import org.example.dto.ImportacionPadronResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PadronRucActualizacionServiceTest {
    @TempDir Path dir;
    @Test void falloSincronoLiberaFlagYPermiteActualizacionPosterior()throws Exception{
        var importer=mock(PadronRucImportService.class);
        var resultado=new ImportacionPadronResponse("COMPLETADO","SINTETICO",1,1,0,1,null,null,"FICTICIO");
        when(importer.estado()).thenReturn(resultado);
        when(importer.iniciarDesdeArchivo(any())).thenThrow(new IllegalStateException("FALLO SINCRONO FICTICIO"))
                .thenReturn(CompletableFuture.completedFuture(resultado));
        var service=new PadronRucActualizacionService(importer);
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/page",e->{byte[] b="Actualizado al 02/10/2026 <a href='/padron_reducido_ruc.zip'>ZIP</a>".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        http.createContext("/padron_reducido_ruc.zip",e->{byte[] b=new byte[2048];b[0]='P';b[1]='K';e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        http.start();ReflectionTestUtils.setField(service,"rutaConfigurada",dir.toString());
        ReflectionTestUtils.setField(service,"paginaSunat","http://127.0.0.1:"+http.getAddress().getPort()+"/page");
        try{
            ReflectionTestUtils.setField(service,"actualizacionEnCurso",true);ReflectionTestUtils.invokeMethod(service,"procesarActualizacion");
            assertFalse(service.estado().actualizacionEnCurso());
            ReflectionTestUtils.setField(service,"actualizacionEnCurso",true);ReflectionTestUtils.invokeMethod(service,"procesarActualizacion");
            assertFalse(service.estado().actualizacionEnCurso());verify(importer,times(2)).iniciarDesdeArchivo(any());
            assertEquals(java.time.LocalDate.of(2026,10,2),service.estado().versionLocal());
        }finally{http.stop(0);service.cerrar();}
    }
}
