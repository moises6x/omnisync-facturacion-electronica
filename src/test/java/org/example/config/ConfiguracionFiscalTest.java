package org.example.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionFiscalTest {
    @Test void validadorRegistradoComoEnvironmentPostProcessorAntesDeBeans(){
        assertTrue(org.springframework.core.io.support.SpringFactoriesLoader.loadFactoryNames(
                org.springframework.boot.env.EnvironmentPostProcessor.class,getClass().getClassLoader())
                .contains(ConfiguracionFiscalEnvironmentPostProcessor.class.getName()));
    }
    private MockEnvironment env(String perfil){
        var e=new MockEnvironment(); if(perfil!=null)e.setActiveProfiles(perfil);
        boolean prod="prod".equals(perfil);
        return e.withProperty("app.deployment.environment",prod?"PRODUCCION":"BETA")
                .withProperty("spring.datasource.url","jdbc:mysql://beta.invalid/bd_beta_controlada")
                .withProperty("app.beta.datasource-url","jdbc:mysql://beta.invalid/bd_beta_controlada")
                .withProperty("sunat.environment",prod?"PRODUCCION":"BETA")
                .withProperty("spring.jpa.hibernate.ddl-auto",prod?"validate":"update")
                .withProperty("sunat.billservice.url",prod?"https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService":"https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService");
    }
    @Test void sinPerfilSeRechazaAntesDeDatasource(){assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(env(null)));}
    @Test void betaExplicitoPermitido(){assertDoesNotThrow(()->ConfiguracionFiscalEnvironmentPostProcessor.validar(env("beta")));}
    @Test void prodExplicitoPermitido(){assertDoesNotThrow(()->ConfiguracionFiscalEnvironmentPostProcessor.validar(env("prod")));}
    @Test void perfilesCombinadosSeRechazan(){var e=env("beta");e.setActiveProfiles("beta","prod");assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(e));}
    @Test void betaAccidentalEnDestinoProductivoSeRechaza(){var e=env("beta").withProperty("app.deployment.environment","PRODUCCION").withProperty("spring.datasource.url","jdbc:mysql://db.invalid/bd_facturacion");assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(e));}
    @Test void datasourceProductivoNoPuedeSustituirDatasourceBetaAprobado(){var e=env("beta").withProperty("spring.datasource.url","jdbc:mysql://prod.invalid/bd_facturacion");assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(e));}
    @Test void betaSinDestinoDatasourceAprobadoSeRechaza(){var e=env("beta").withProperty("app.beta.datasource-url","");assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(e));}
    @Test void destinoNoIdentificadoSeRechaza(){assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(env("beta").withProperty("app.deployment.environment","")));}
    @Test void forwardedHeadersSinProxyVerificadoSeRechazan(){assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(env("beta").withProperty("server.forward-headers-strategy","framework")));}
    @Test void endpointBetaEnProdSeRechaza(){assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(env("prod").withProperty("sunat.billservice.url",env("beta").getProperty("sunat.billservice.url"))));}
    @Test void testNoPuedeUsarBaseGeneral(){var e=env("test").withProperty("spring.datasource.url","jdbc:mysql://localhost/bd_facturacion");assertThrows(IllegalStateException.class,()->ConfiguracionFiscalEnvironmentPostProcessor.validar(e));}
}
