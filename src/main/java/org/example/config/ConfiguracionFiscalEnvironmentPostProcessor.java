package org.example.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import java.net.URI;
import java.util.Arrays;

/** Antes de crear datasource, Hibernate, bootstrap o schedulers. No consulta redes ni secretos. */
public class ConfiguracionFiscalEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) { validar(env); }
    public static void validar(ConfigurableEnvironment env) {
        var perfiles=Arrays.asList(env.getActiveProfiles());
        long fiscales=perfiles.stream().filter(p -> p.equals("beta") || p.equals("prod") || p.equals("test")).count();
        if(fiscales!=1) throw new IllegalStateException("Exigir exactamente un perfil explícito beta, prod o test; sin perfil fiscal predeterminado.");
        if(perfiles.contains("test")) {
            String url=env.getProperty("spring.datasource.url", "");
            if(!url.contains("/bd_facturacion_test?") && !url.endsWith("/bd_facturacion_test"))
                throw new IllegalStateException("Perfil test sólo admite bd_facturacion_test.");
            return;
        }
        boolean prod=perfiles.contains("prod");
        if(!"none".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy","none")))
            throw new IllegalStateException("Forwarded headers requieren proxy confiable verificado; en esta fase sólo se admite strategy=none.");
        String destino=env.getProperty("app.deployment.environment", "");
        if(!destino.equals(prod?"PRODUCCION":"BETA"))
            throw new IllegalStateException("APP_DEPLOYMENT_ENVIRONMENT debe identificar explícitamente el destino y coincidir con el perfil fiscal.");
        if(!prod){
            String aprobado=env.getProperty("app.beta.datasource-url","");
            if(aprobado.isBlank() || !aprobado.equals(env.getProperty("spring.datasource.url","")))
                throw new IllegalStateException("Beta exige APP_BETA_DATASOURCE_URL aprobado y coincidente; no heredar datasource externo silenciosamente.");
        }
        if(!env.getProperty("sunat.environment", "").equals(prod?"PRODUCCION":"BETA"))
            throw new IllegalStateException("Ambiente SUNAT incompatible con perfil fiscal.");
        try {
            URI uri=URI.create(env.getProperty("sunat.billservice.url", ""));
            String host=prod?"e-factura.sunat.gob.pe":"e-beta.sunat.gob.pe";
            String path=prod?"/ol-ti-itcpfegem/billService":"/ol-ti-itcpfegem-beta/billService";
            if(!"https".equals(uri.getScheme()) || !host.equals(uri.getHost()) || !path.equals(uri.getPath())
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || (uri.getPort()!=-1 && uri.getPort()!=443))
                throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) { throw new IllegalStateException("Endpoint SUNAT incompatible con el perfil fiscal."); }
        if(prod && !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")))
            throw new IllegalStateException("Producción exige ddl-auto=validate.");
        if(env.getProperty("spring.flyway.enabled",Boolean.class,false))
            throw new IllegalStateException("Flyway continúa desactivado hasta autorización de migraciones.");
    }
}
