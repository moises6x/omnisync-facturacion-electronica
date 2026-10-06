package org.example.repository;

import org.example.model.RespuestaSunatRecibida;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Metadatos de la base create-drop autorizada y SchemaValidator; nunca arranca la aplicación ni migra. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RespuestaSunatEsquemaRepositoryTest {
    @Autowired private DataSource dataSource;

    @Test
    void metadatosMySqlConfirmanEnumNativoTiposNulosPkEIndice() {
        JdbcTemplate jdbc = basePruebas();
        Map<String, Map<String, Object>> columnas = new HashMap<>();
        jdbc.queryForList("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA, DATETIME_PRECISION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='respuesta_sunat_recibida'
                """).forEach(c -> columnas.put((String) c.get("COLUMN_NAME"), c));
        assertEquals(8, columnas.size());
        assertEquals("enum('CPE','RA','RC')", columnas.get("flujo").get("COLUMN_TYPE"));
        assertEquals("longblob", columnas.get("respuesta_zip_bytes").get("COLUMN_TYPE"));
        assertEquals("datetime(6)", columnas.get("recibido_en").get("COLUMN_TYPE"));
        assertEquals("varchar(11)", columnas.get("ruc_emisor").get("COLUMN_TYPE"));
        assertEquals("varchar(64)", columnas.get("sha256").get("COLUMN_TYPE"));
        assertEquals("varchar(150)", columnas.get("ticket_sunat").get("COLUMN_TYPE"));
        assertTrue(columnas.get("id").get("COLUMN_TYPE").toString().startsWith("bigint"));
        assertTrue(columnas.get("documento_id").get("COLUMN_TYPE").toString().startsWith("bigint"));
        assertEquals("auto_increment", columnas.get("id").get("EXTRA"));
        columnas.forEach((nombre, c) -> assertEquals(nombre.equals("ticket_sunat") ? "YES" : "NO", c.get("IS_NULLABLE")));
        assertEquals(List.of("ruc_emisor", "flujo", "documento_id", "recibido_en"), jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='respuesta_sunat_recibida'
                  AND INDEX_NAME='idx_respuesta_sunat_documento' ORDER BY SEQ_IN_INDEX
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='respuesta_sunat_recibida'
                  AND INDEX_NAME='idx_respuesta_sunat_documento'
                """, Integer.class));
        assertEquals(List.of("id"), jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='respuesta_sunat_recibida' AND INDEX_NAME='PRIMARY'
                """, String.class));
    }

    @Test
    void ddlDelArtefactoSeGeneraSoloEnMemoriaSinEjecutarloEnLaBase() {
        basePruebas();
        StringWriter script = new StringWriter();
        Map<String, Object> opciones = opciones();
        opciones.put("jakarta.persistence.schema-generation.scripts.action", "create");
        opciones.put("jakarta.persistence.schema-generation.scripts.create-target", script);
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            Metadata metadata = new MetadataSources(registry).addAnnotatedClass(RespuestaSunatRecibida.class).buildMetadata();
            SchemaManagementToolCoordinator.process(metadata, registry, opciones, DelayedDropRegistryNotAvailableImpl.INSTANCE);
            String ddl = script.toString().replaceAll("\\s+", " ").replace("enum (", "enum(");
            assertTrue(ddl.contains("flujo enum('CPE','RA','RC') not null"), ddl);
            assertTrue(ddl.contains("recibido_en datetime(6) not null"), ddl);
            assertTrue(ddl.contains("respuesta_zip_bytes LONGBLOB not null"), ddl);
            assertTrue(ddl.contains("idx_respuesta_sunat_documento"), ddl);
            assertEquals("none", opciones.get("jakarta.persistence.schema-generation.database.action"));
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    @Test
    void schemaValidatorPasaParaElMapeoActualSinCambiarDdlAutoNiFlyway() {
        basePruebas();
        assertEquals("6.6.39.Final", org.hibernate.Version.getVersionString());
        Map<String, Object> opciones = opciones();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            Metadata metadata = new MetadataSources(registry).addAnnotatedClass(RespuestaSunatRecibida.class).buildMetadata();
            var ejecucion = SchemaManagementToolCoordinator.buildExecutionOptions(opciones, ExceptionHandlerHaltImpl.INSTANCE);
            assertDoesNotThrow(() -> registry.getService(SchemaManagementTool.class).getSchemaValidator(opciones)
                    .doValidation(metadata, ejecucion, ContributableMatcher.ALL));
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    private JdbcTemplate basePruebas() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("bd_facturacion_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        return jdbc;
    }

    @Test
    void todasLasColumnasDeTicketSunatDebenAdmitir150() {
        JdbcTemplate jdbc = basePruebas();
        for (String tabla : List.of("respuesta_sunat_recibida", "resumen_diario", "comunicacion_baja")) {
            assertEquals("varchar(150)", jdbc.queryForObject("""
                    SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='ticket_sunat'
                    """, String.class, tabla));
        }
    }

    @Test
    void constraintFisicaDeTicketSigueSinAplicarseNiSerGlobal() {
        JdbcTemplate jdbc = basePruebas();
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario'
                  AND COLUMN_NAME='ticket_sunat' AND NON_UNIQUE=0
                """, Integer.class));
        assertEquals("YES", jdbc.queryForObject("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario' AND COLUMN_NAME='ticket_sunat'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT NON_UNIQUE FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario'
                  AND INDEX_NAME='idx_resumen_diario_ticket'
                """, Integer.class));
    }

    private Map<String, Object> opciones() {
        Map<String, Object> opciones = new HashMap<>();
        opciones.put("hibernate.connection.datasource", dataSource);
        opciones.put("hibernate.hbm2ddl.auto", "none");
        opciones.put("jakarta.persistence.schema-generation.database.action", "none");
        opciones.put("hibernate.show_sql", false);
        return opciones;
    }
}
