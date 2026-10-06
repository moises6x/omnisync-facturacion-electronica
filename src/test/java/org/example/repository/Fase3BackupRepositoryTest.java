package org.example.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.example.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Export real con mysqldump: sólo once tablas, sólo fixtures; no secreto incluido en artefactos. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class Fase3BackupRepositoryTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;

    @Test void backupRealIncluyeSchemaFilasBlobsYManifestDeComparacion() throws Exception {
        assertEquals("bd_facturacion_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        for (String tabla : Fase3EnsayoEsquemaRepositoryTest.TABLAS)
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `"+tabla+"`", Integer.class), "No exportar datos previos desconocidos: "+tabla);
        byte[] xml="<Invoice>XML SINTETICO ENSAYO, NO FIRMA CRIPTOGRAFICA</Invoice>".getBytes(StandardCharsets.UTF_8);
        byte[] cdr=("""
                <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                 xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                 xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                 <cac:DocumentResponse><cac:Response><cbc:ReferenceID>F990-1</cbc:ReferenceID>
                 <cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>FICTICIO</cbc:Description></cac:Response>
                 <cac:DocumentReference><cbc:ID>F990-1</cbc:ID></cac:DocumentReference></cac:DocumentResponse>
                </ApplicationResponse>
                """).getBytes(StandardCharsets.UTF_8);
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("R.xml"));zip.write(cdr);zip.closeEntry();}
        byte[] zip=out.toByteArray(); String hash=sha(zip);
        new TransactionTemplate(tm).executeWithoutResult(s -> {
            var factura=Fase3HistoricosRepositoryTest.doc(1); factura.setTipoComprobante("01");factura.setSerie("F990");
            factura.setEstadoSfs(EstadoComprobante.ACEPTADO); factura.setNombreArchivoXml("f.xml");
            factura.setXmlFirmadoBytes(xml);factura.setHashCpe("DIGEST FICTICIO");factura.setCdrZipBytes(zip);
            factura.setCdrXmlBytes(cdr);factura.setCodigoRespuestaSunat("0");em.persist(factura);
            em.persist(DetalleComprobante.builder().comprobante(factura).itemOrden(1).descripcion("ITEM FICTICIO")
                    .unidadMedida("NIU").cantidad(BigDecimal.ONE).valorUnitario(BigDecimal.TEN).precioUnitario(BigDecimal.TEN)
                    .igvItem(BigDecimal.ZERO).tipoAfectacionIgv("20").montoTotalItem(BigDecimal.TEN).build());
            var boleta=Fase3HistoricosRepositoryTest.doc(1);boleta.setEstadoSfs(EstadoComprobante.ACEPTADO);
            boleta.setNombreArchivoXml("b.xml");boleta.setXmlFirmadoBytes(xml);boleta.setHashCpe("DIGEST FICTICIO");em.persist(boleta);
            var rc=Fase3HistoricosRepositoryTest.rc(1,"R".repeat(150));rc.addDetalle(Fase3HistoricosRepositoryTest.detalle(boleta.getId()));
            rc.setXmlFirmadoBytes(xml);rc.setHashCpe("DIGEST FICTICIO");rc.setCdrZipBytes(zip);rc.setCdrXmlBytes(cdr);
            rc.setEstado(EstadoResumenDiario.ACEPTADO);rc.setCodigoRespuestaSunat("0");rc.setFinalizadoEn(LocalDateTime.of(2026,10,1,12,0));em.persist(rc);
            var ra=ComunicacionBaja.builder().rucEmisor(Fase3HistoricosRepositoryTest.RUC).fechaDocumentos(LocalDate.of(2026,10,1))
                    .fechaGeneracion(LocalDate.of(2026,10,1)).correlativo(1).identificador("RA-20261001-1")
                    .nombreArchivoXml("ra.xml").estado(EstadoComunicacionBaja.TICKET_GENERADO).ticketSunat("A".repeat(150))
                    .xmlFirmadoBytes(xml).hashCpe("DIGEST FICTICIO").build();
            ra.addDetalle(ComunicacionBajaDetalle.builder().comprobanteId(factura.getId()).numeroLinea(1).tipoComprobante("01")
                    .serie("F990").numero(1).motivo("MOTIVO FICTICIO").build());em.persist(ra);
            for(var flujo:RespuestaSunatRecibida.Flujo.values()) {
                long id=switch(flujo){case CPE -> factura.getId();case RC -> rc.getId();case RA -> ra.getId();};
                em.persist(RespuestaSunatRecibida.builder().rucEmisor(Fase3HistoricosRepositoryTest.RUC).flujo(flujo).documentoId(id)
                        .ticketSunat(flujo==RespuestaSunatRecibida.Flujo.CPE?null:flujo==RespuestaSunatRecibida.Flujo.RC?rc.getTicketSunat():ra.getTicketSunat())
                        .recibidoEn(LocalDateTime.of(2026,10,1,12,0)).sha256(hash).respuestaZipBytes(zip).build());
            }
            em.persist(SecuenciaCorrelativo.builder().rucEmisor(Fase3HistoricosRepositoryTest.RUC).tipoComprobante("01").serie("F990").ultimoNumero(1).build());
            em.persist(SecuenciaResumenDiario.builder().rucEmisor(Fase3HistoricosRepositoryTest.RUC).fechaReferencia(LocalDate.of(2026,10,1)).ultimoCorrelativo(1).build());
            em.persist(SecuenciaComunicacionBaja.builder().rucEmisor(Fase3HistoricosRepositoryTest.RUC).fechaReferencia(LocalDate.of(2026,10,1)).ultimoCorrelativo(1).build());
            em.persist(SolicitudEmision.builder().rucEmisor(Fase3HistoricosRepositoryTest.RUC).idempotencyKey("FIXTURE ENSAYO")
                    .requestHash("0".repeat(64)).comprobanteId(factura.getId()).estado("COMPLETADO").build());
        });
        var dir=Path.of("target","fase3-ensayo").toAbsolutePath(); Files.createDirectories(dir);
        var manifest=new TreeMap<String,Object>();
        int filas=0;
        for (String tabla:Fase3EnsayoEsquemaRepositoryTest.TABLAS) {
            var rows=new ArrayList<Map<String,String>>();
            for(var row:jdbc.queryForList("SELECT * FROM `"+tabla+"` ORDER BY id")) {
                var canonical=new TreeMap<String,String>();
                row.forEach((key,value)->canonical.put(key,value==null?null:value instanceof byte[] bytes?"HEX:"+HexFormat.of().formatHex(bytes):value.toString()));
                rows.add(canonical);
            }
            filas+=rows.size();manifest.put(tabla,rows);
        }
        assertEquals(14,filas);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM respuesta_sunat_recibida WHERE sha256<>SHA2(respuesta_zip_bytes,256)",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM respuesta_sunat_recibida e LEFT JOIN comprobante c ON e.flujo='CPE' AND c.id=e.documento_id AND c.ruc_emisor=e.ruc_emisor LEFT JOIN resumen_diario r ON e.flujo='RC' AND r.id=e.documento_id AND r.ruc_emisor=e.ruc_emisor LEFT JOIN comunicacion_baja b ON e.flujo='RA' AND b.id=e.documento_id AND b.ruc_emisor=e.ruc_emisor WHERE c.id IS NULL AND r.id IS NULL AND b.id IS NULL",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM resumen_diario_detalle d JOIN resumen_diario r ON r.id=d.resumen_id LEFT JOIN comprobante c ON c.id=d.comprobante_id WHERE c.id IS NULL OR c.ruc_emisor<>r.ruc_emisor",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM comunicacion_baja_detalle d JOIN comunicacion_baja b ON b.id=d.comunicacion_baja_id LEFT JOIN comprobante c ON c.id=d.comprobante_id WHERE c.id IS NULL OR c.ruc_emisor<>b.ruc_emisor",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_emision s LEFT JOIN comprobante c ON c.id=s.comprobante_id AND c.ruc_emisor=s.ruc_emisor WHERE c.id IS NULL",Integer.class));
        assertEquals(zip.length,jdbc.queryForObject("SELECT MIN(bytes) FROM (SELECT OCTET_LENGTH(respuesta_zip_bytes) bytes,CUME_DIST() OVER (ORDER BY OCTET_LENGTH(respuesta_zip_bytes)) percentil FROM respuesta_sunat_recibida) p WHERE percentil>=0.95",Integer.class));
        manifest.put("metadata_columns",jdbc.queryForList("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('"+String.join("','",Fase3EnsayoEsquemaRepositoryTest.TABLAS)+"') ORDER BY TABLE_NAME,ORDINAL_POSITION"));
        manifest.put("metadata_indexes",jdbc.queryForList("SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('"+String.join("','",Fase3EnsayoEsquemaRepositoryTest.TABLAS)+"') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"));
        manifest.put("metadata_fk",jdbc.queryForList("SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME,ORDINAL_POSITION FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL AND TABLE_NAME IN ('"+String.join("','",Fase3EnsayoEsquemaRepositoryTest.TABLAS)+"') ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"));
        var mapper=new ObjectMapper(); Files.writeString(dir.resolve("manifest-source.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        String bin=System.getenv("TEST_MYSQL_BIN_DIR");assertNotNull(bin,"Configurar directorio de clientes MySQL de pruebas, sin credenciales en archivos");
        // El dump debe usar el mismo servidor/cuenta que el datasource de esta prueba.
        // Fijar 3306 exportaba otra instancia al ejecutar contra el MySQL aislado 33307.
        int puerto=jdbc.queryForObject("SELECT @@port",Integer.class);
        var source=Objects.requireNonNull(jdbc.getDataSource()).unwrap(com.zaxxer.hikari.HikariDataSource.class);
        var dump=dir.resolve("backup-test.sql");
        var command=new ArrayList<>(List.of(Path.of(bin,"mysqldump.exe").toString(),"--no-defaults","--no-login-paths","--host=127.0.0.1","--port="+puerto,
                "--user="+source.getUsername(),"--single-transaction","--skip-lock-tables","--no-tablespaces",
                "--hex-blob","--set-gtid-purged=OFF","--column-statistics=0","--skip-comments","--skip-add-drop-table",
                "--skip-add-locks","--skip-disable-keys","--result-file="+dump,"bd_facturacion_test"));
        command.addAll(Fase3EnsayoEsquemaRepositoryTest.TABLAS);
        var processBuilder=new ProcessBuilder(command);processBuilder.environment().put("MYSQL_PWD",Objects.requireNonNullElse(source.getPassword(),""));
        var process=processBuilder.start();
        assertTrue(process.waitFor(30,TimeUnit.SECONDS),"mysqldump excedió timeout");
        assertEquals(0,process.exitValue(),"mysqldump falló; no volcar stderr/credenciales");
        String sql=Files.readString(dump);
        for(String tabla:Fase3EnsayoEsquemaRepositoryTest.TABLAS) assertTrue(sql.contains("CREATE TABLE `"+tabla+"`"),tabla);
        assertTrue(sql.contains("0x"+HexFormat.of().formatHex(zip).toUpperCase(Locale.ROOT)) || sql.toLowerCase(Locale.ROOT).contains("0x"+HexFormat.of().formatHex(zip)));
        assertFalse(sql.contains("CREATE DATABASE"));assertFalse(sql.contains("USE `"));assertFalse(sql.contains("DROP TABLE"));
        Files.writeString(dir.resolve("backup-sha256.txt"),sha(Files.readAllBytes(dump))+"\n");
        System.out.println("FASE3_BACKUP: 11 tablas, 14 filas ficticias, schema+blobs+manifest; restore independiente pendiente de permisos.");
    }
    static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
