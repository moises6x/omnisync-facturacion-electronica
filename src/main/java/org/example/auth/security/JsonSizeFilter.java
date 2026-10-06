package org.example.auth.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Límite técnico de recursos, también para transferencia chunked; nunca registra el contenido. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class JsonSizeFilter extends OncePerRequestFilter {
    public static final int MAX_BYTES=1024*1024;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String tipo=request.getContentType();
        if(tipo==null || !tipo.toLowerCase(Locale.ROOT).split(";",2)[0].trim().matches("application/(json|[^/]+\\+json)")){
            chain.doFilter(request,response); return;
        }
        if(request.getContentLengthLong()>MAX_BYTES){rechazar(response);return;}
        byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES){rechazar(response);return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){
                var in=new ByteArrayInputStream(bytes);
                return new ServletInputStream(){
                    @Override public int read(){return in.read();}
                    @Override public boolean isFinished(){return in.available()==0;}
                    @Override public boolean isReady(){return true;}
                    @Override public void setReadListener(ReadListener l){throw new UnsupportedOperationException("Lectura asíncrona no soportada en API MVC JSON.");}
                };
            }
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
    private void rechazar(HttpServletResponse response)throws IOException{
        response.setStatus(413);response.setContentType("application/json");response.getWriter().write("{\"message\":\"JSON supera el límite técnico de 1 MiB.\"}");
    }
}
