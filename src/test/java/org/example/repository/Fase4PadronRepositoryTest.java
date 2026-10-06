package org.example.repository;

import org.example.service.PadronRucImportService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties={
        "spring.datasource.url=${TEST_PADRON_DB_URL:jdbc:mysql://127.0.0.1:33307/bd_facturacion_test?allowLoadLocalInfile=true&allowPublicKeyRetrieval=true}",
        "spring.datasource.username=${TEST_PADRON_DB_USERNAME:root}","spring.datasource.password=${TEST_PADRON_DB_PASSWORD:}"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class Fase4PadronRepositoryTest {
    @Autowired DataSource ds; @Autowired JdbcTemplate jdbc;
    @TempDir Path dir;
    private final java.util.List<PadronRucImportService> servicios=new java.util.ArrayList<>();
    @BeforeEach void fixture(){
        assertEquals("bd_facturacion_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(33307,jdbc.queryForObject("SELECT @@port",Integer.class),"Nunca usar servidor habitual para estas cargas");
        jdbc.update("DELETE FROM padron_ruc");jdbc.update("INSERT INTO padron_ruc(ruc,razon_social) VALUES ('20100130204','FICTICIO ANTERIOR')");
    }
    @AfterEach void limpiar(){
        servicios.forEach(s->ReflectionTestUtils.invokeMethod(s,"cerrar"));
        // Sólo tablas de ensayo en base desechable: no tocar fuentes ni archivos del padrón real.
        jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND (TABLE_NAME IN ('padron_ruc_raw','padron_ruc_carga','padron_ruc_anterior') OR TABLE_NAME LIKE 'padron_ruc_hist_%')",String.class)
                .forEach(t->jdbc.execute("DROP TABLE `"+t+"`"));
        jdbc.update("DELETE FROM padron_ruc");
    }
    @ParameterizedTest @ValueSource(strings={"cabecera","invalido","parcial","corrupto"})
    void noActivaArchivoIncompleto(String caso)throws Exception{
        var s=servicio(ds);String contenido=switch(caso){
            case "cabecera"->"ruc|razon_social\n";
            case "invalido"->"ruc|razon_social\n"+fila("INVALIDO","FICTICIO");
            case "parcial"->"ruc|razon_social\n20100130204|FICTICIO\n";
            default->"PK archivo corrupto";
        };
        var p=dir.resolve(caso.equals("corrupto")?"synthetic.zip":"synthetic.txt");Files.writeString(p,contenido);
        var r=s.iniciarDesdeArchivo(p).get(15,TimeUnit.SECONDS);assertEquals("ERROR",r.estado());
        assertEquals("FICTICIO ANTERIOR",jdbc.queryForObject("SELECT razon_social FROM padron_ruc",String.class));
    }
    @Test void cargaRealRetieneAnteriorYSegundaVersionHistorica()throws Exception{
        var s=servicio(ds);var p=archivo("FICTICIO NUEVO");
        var resultado=s.iniciarDesdeArchivo(p).get(15,TimeUnit.SECONDS);
        assertEquals("COMPLETADO",resultado.estado(),resultado.mensaje());
        assertEquals("FICTICIO ANTERIOR",jdbc.queryForObject("SELECT razon_social FROM padron_ruc_anterior",String.class));
        Files.writeString(p,"ruc|razon_social\n"+fila("20100130204","FICTICIO TERCERO"));
        assertEquals("COMPLETADO",servicio(ds).iniciarDesdeArchivo(p).get(15,TimeUnit.SECONDS).estado());
        assertEquals("FICTICIO NUEVO",jdbc.queryForObject("SELECT razon_social FROM padron_ruc_anterior",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME LIKE 'padron_ruc_hist_%'",Integer.class));
    }
    @Test void subsetEstructuralmenteValidoNoSustituyeVersionMasCompleta()throws Exception{
        jdbc.update("INSERT INTO padron_ruc(ruc,razon_social) VALUES ('20600000001','SEGUNDO FICTICIO ANTERIOR')");
        var r=servicio(ds).iniciarDesdeArchivo(archivo("SUBSET FICTICIO")).get(15,TimeUnit.SECONDS);
        assertEquals("ERROR",r.estado());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM padron_ruc",Integer.class));
        assertEquals("FICTICIO ANTERIOR",jdbc.queryForObject("SELECT razon_social FROM padron_ruc WHERE ruc='20100130204'",String.class));
    }
    @Test void dosImportadoresNoPreparanNiBorranTablasDeOtro()throws Exception{
        var dentro=new CountDownLatch(1);var continuar=new CountDownLatch(1);
        var bloqueado=envolver(sql->{if(sql.contains("CREATE TABLE padron_ruc_carga")){dentro.countDown();try{assertTrue(continuar.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}},sql->{});
        var primero=servicio(bloqueado);var segundo=servicio(ds);var p=archivo("NUEVO FICTICIO");
        var f=primero.iniciarDesdeArchivo(p);
        try{
            assertTrue(dentro.await(10,TimeUnit.SECONDS));
            assertEquals("ERROR",segundo.iniciarDesdeArchivo(p).get(5,TimeUnit.SECONDS).estado());
            continuar.countDown();assertEquals("COMPLETADO",f.get(15,TimeUnit.SECONDS).estado());
        }finally{continuar.countDown();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void falloAlrededorDeRenameYServicioRecreadoPreservaVersion(boolean despues)throws Exception{
        var f=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> fallo=sql->{if(sql.startsWith("RENAME TABLE")&&f.compareAndSet(false,true))throw new IllegalStateException("FALLO CONTROLADO RENAME");};
        var s=servicio(envolver(despues?sql->{}:fallo,despues?fallo:sql->{}));var p=archivo("NUEVO FICTICIO");
        assertEquals("ERROR",s.iniciarDesdeArchivo(p).get(15,TimeUnit.SECONDS).estado());
        assertEquals(despues?"NUEVO FICTICIO":"FICTICIO ANTERIOR",jdbc.queryForObject("SELECT razon_social FROM padron_ruc",String.class));
        if(despues)assertEquals("FICTICIO ANTERIOR",jdbc.queryForObject("SELECT razon_social FROM padron_ruc_anterior",String.class));
        assertEquals("COMPLETADO",servicio(ds).iniciarDesdeArchivo(p).get(15,TimeUnit.SECONDS).estado());
    }
    private Path archivo(String razon)throws Exception{var p=dir.resolve("synthetic.txt");Files.writeString(p,"ruc|razon_social\n"+fila("20100130204",razon));return p;}
    private String fila(String ruc,String razon){return ruc+"|"+razon+"|ACTIVO|HABIDO|150101|AV|FICTICIA|-|-|1|-|-|-|-|-|\n";}
    private PadronRucImportService servicio(DataSource source){
        var s=new PadronRucImportService(jdbc,source);ReflectionTestUtils.setField(s,"charsetConfigurado","UTF-8");
        ReflectionTestUtils.setField(s,"rutaConfigurada",dir.toString());servicios.add(s);return s;
    }
    private DataSource envolver(java.util.function.Consumer<String> antes,java.util.function.Consumer<String> despues){
        return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{
            try{
                Object result=m.invoke(ds,a);if(!(result instanceof Connection c))return result;
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(pc,mc,ac)->{
                    try{Object v=mc.invoke(c,ac);if(!(v instanceof Statement st))return v;
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{v instanceof PreparedStatement?PreparedStatement.class:Statement.class},(ps,ms,as)->{
                            String sql=as!=null&&as.length>0&&as[0] instanceof String text?text:"";
                            try{if(ms.getName().startsWith("execute"))antes.accept(sql);Object r=ms.invoke(st,as);if(ms.getName().startsWith("execute"))despues.accept(sql);return r;}
                            catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                        });
                    }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
    }
}
