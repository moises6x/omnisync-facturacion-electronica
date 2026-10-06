package org.example.repository;

import org.example.model.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Metadata de once tablas fiscales, sin aplicación/servicios ni acceso al padrón. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class Fase3EnsayoEsquemaRepositoryTest {
    static final List<Class<?>> MODELOS = List.of(Comprobante.class, DetalleComprobante.class,
            ResumenDiario.class, ResumenDiarioDetalle.class, ComunicacionBaja.class,
            ComunicacionBajaDetalle.class, RespuestaSunatRecibida.class, SecuenciaCorrelativo.class,
            SecuenciaResumenDiario.class, SecuenciaComunicacionBaja.class, SolicitudEmision.class);
    static final List<String> TABLAS = List.of("comprobante", "detalle_comprobante", "resumen_diario",
            "resumen_diario_detalle", "comunicacion_baja", "comunicacion_baja_detalle",
            "respuesta_sunat_recibida", "secuencia_correlativo", "secuencia_resumen_diario",
            "secuencia_comunicacion_baja", "solicitud_emision");
    @Autowired DataSource dataSource;
    @Autowired ApplicationContext context;

    @Test void validatorYMetadataConcuerdanConLosOnceModelosActuales() {
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals("bd_facturacion_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        Map<String,Object> opciones = new HashMap<>();
        opciones.put("hibernate.connection.datasource", dataSource);
        opciones.put("hibernate.hbm2ddl.auto", "none");
        opciones.put("jakarta.persistence.schema-generation.database.action", "none");
        opciones.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var registry = new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            var sources = new MetadataSources(registry); MODELOS.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();
            var execution = SchemaManagementToolCoordinator.buildExecutionOptions(opciones, ExceptionHandlerHaltImpl.INSTANCE);
            assertDoesNotThrow(() -> registry.getService(SchemaManagementTool.class).getSchemaValidator(opciones)
                    .doValidation(metadata, execution, ContributableMatcher.ALL));
            for (var tabla : metadata.collectTableMappings()) {
                String nombre = tabla.getName(); assertTrue(TABLAS.contains(nombre), nombre);
                assertEquals("InnoDB", jdbc.queryForObject("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", String.class, nombre));
                assertEquals(tabla.getColumns().size(), jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", Integer.class, nombre));
                for (var col : tabla.getColumns()) {
                    var real = jdbc.queryForMap("SELECT COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?", nombre, col.getName());
                    assertEquals(col.isNullable() ? "YES" : "NO", real.get("IS_NULLABLE"), nombre+"."+col.getName());
                    assertEquals(normalizar(col.getSqlType(metadata)), normalizar(real.get("COLUMN_TYPE").toString()), nombre+"."+col.getName());
                }
                assertEquals(columnas(tabla.getPrimaryKey().getColumns()), indice(jdbc, nombre, "PRIMARY"));
                for (var index : tabla.getIndexes().values()) assertEquals(columnas(index.getColumns()), indice(jdbc, nombre, index.getName()));
                for (var unique : tabla.getUniqueKeys().values()) {
                    assertEquals(columnas(unique.getColumns()), indice(jdbc, nombre, unique.getName()));
                    assertEquals(0, jdbc.queryForObject("SELECT MAX(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?", Integer.class, nombre, unique.getName()));
                }
                for (var fk : tabla.getForeignKeys().values()) {
                    assertEquals(columnas(fk.getColumns()), jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=? AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY ORDINAL_POSITION", String.class, nombre, fk.getName()));
                    assertEquals(fk.getReferencedTable().getName(), jdbc.queryForObject("SELECT REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=? LIMIT 1", String.class, nombre, fk.getName()));
                    var referidas=fk.getReferencedColumns().isEmpty()?fk.getReferencedTable().getPrimaryKey().getColumns():fk.getReferencedColumns();
                    assertEquals(columnas(referidas),jdbc.queryForList("SELECT REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=? ORDER BY ORDINAL_POSITION",String.class,nombre,fk.getName()));
                }
            }
            assertEquals(11, metadata.collectTableMappings().size());
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    @Test void contextoNoContieneServiciosConEfectosDeArranque() {
        for (String bean : List.of("padronRucActualizacionService", "resumenDiarioScheduler", "comunicacionBajaScheduler",
                "envioSunatRecoveryScheduler", "sunatDirectSoapClient", "resumenDiarioService", "bootstrapService"))
            assertFalse(context.containsBean(bean), bean);
        for (var processor : context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).values())
            assertTrue(processor.getScheduledTasks().isEmpty());
    }

    @Test void uniqueCompuestoSeEnsayaSoloEnTablaTemporalConNullCollationYRollback() throws Exception {
        var jdbc=new JdbcTemplate(dataSource);
        var col=jdbc.queryForMap("SELECT CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario' AND COLUMN_NAME='ticket_sunat'");
        String charset=col.get("CHARACTER_SET_NAME").toString(),collation=col.get("COLLATION_NAME").toString();
        assertTrue(charset.matches("[a-zA-Z0-9_]+"));assertTrue(collation.matches("[a-zA-Z0-9_]+"));
        try(var conn=dataSource.getConnection();var st=conn.createStatement()) {
            assertEquals("bd_facturacion_test",conn.getCatalog());
            st.execute("CREATE TEMPORARY TABLE fase3_ticket_unique_ensayo (id BIGINT AUTO_INCREMENT PRIMARY KEY,ruc_emisor VARCHAR(11) NOT NULL,ticket_sunat VARCHAR(150) NULL,CONSTRAINT uk_ensayo UNIQUE(ruc_emisor,ticket_sunat)) ENGINE=InnoDB DEFAULT CHARSET="+charset+" COLLATE="+collation);
            try {
                st.executeUpdate("INSERT INTO fase3_ticket_unique_ensayo(ruc_emisor,ticket_sunat) VALUES ('20999999991',NULL),('20999999991',NULL),('20999999991','Ticket'),('20999999992','Ticket')");
                var ex=assertThrows(java.sql.SQLException.class,()->st.executeUpdate("INSERT INTO fase3_ticket_unique_ensayo(ruc_emisor,ticket_sunat) VALUES ('20999999991','Ticket')"));
                assertEquals(1062,ex.getErrorCode());assertEquals("23000",ex.getSQLState());
                if(collation.endsWith("_ci")) {
                    var caseEx=assertThrows(java.sql.SQLException.class,()->st.executeUpdate("INSERT INTO fase3_ticket_unique_ensayo(ruc_emisor,ticket_sunat) VALUES ('20999999991','ticket')"));
                    assertEquals(1062,caseEx.getErrorCode());
                }
                var columnas=new ArrayList<String>();
                try(var rs=st.executeQuery("SHOW INDEX FROM fase3_ticket_unique_ensayo")) {
                    while(rs.next()) if(rs.getString("Key_name").equals("uk_ensayo")) {
                        assertEquals(0,rs.getInt("Non_unique"));assertNull(rs.getObject("Sub_part"));columnas.add(rs.getString("Column_name"));
                    }
                }
                assertEquals(List.of("ruc_emisor","ticket_sunat"),columnas);
                st.execute("ALTER TABLE fase3_ticket_unique_ensayo DROP INDEX uk_ensayo");
                assertEquals(1,st.executeUpdate("INSERT INTO fase3_ticket_unique_ensayo(ruc_emisor,ticket_sunat) VALUES ('20999999991','Ticket')"));
            } finally {st.execute("DROP TEMPORARY TABLE fase3_ticket_unique_ensayo");}
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario' AND COLUMN_NAME='ticket_sunat' AND NON_UNIQUE=0",Integer.class));
    }

    private static List<String> columnas(List<Column> cols) { return cols.stream().map(Column::getName).toList(); }
    private static List<String> indice(JdbcTemplate jdbc, String tabla, String nombre) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=? ORDER BY SEQ_IN_INDEX", String.class, tabla, nombre);
    }
    private static String normalizar(String tipo) {
        String normal = tipo.toLowerCase(Locale.ROOT).replace(" ", "");
        // MySQL publica INT en COLUMN_TYPE para el sinónimo INTEGER del DDL.
        return normal.equals("integer") ? "int" : normal;
    }
}
