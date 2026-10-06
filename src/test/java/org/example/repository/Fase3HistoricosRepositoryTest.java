package org.example.repository;

import jakarta.persistence.EntityManager;
import org.example.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures transaccionales sintéticos: los detectores son únicamente SELECT, no correctores. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class Fase3HistoricosRepositoryTest {
    static final String RUC="20999999991";
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest @ValueSource(strings={"duplicado","ausente","terminal","huerfana","enResumen","secuencia","secuenciaRC","secuenciaRA","multiple","hash","legacy"})
    void detectoresSelectDistinguenAnomaliasControladas(String caso) {
        assertEquals("bd_facturacion_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String sql;
        switch(caso) {
            case "duplicado" -> {
                em.persist(rc(1,"IGUAL")); em.persist(rc(2,"IGUAL"));
                var otro=rc(1,"IGUAL"); otro.setRucEmisor("20999999992"); em.persist(otro);
                sql="SELECT COUNT(*) FROM (SELECT ruc_emisor,ticket_sunat FROM resumen_diario WHERE ticket_sunat IS NOT NULL GROUP BY ruc_emisor,ticket_sunat HAVING COUNT(*)>1) d";
            }
            case "ausente" -> { em.persist(rc(1,null)); sql="SELECT COUNT(*) FROM resumen_diario WHERE estado IN ('TICKET_GENERADO','PROCESANDO','ERROR_CONSULTA','ACEPTADO','ACEPTADO_CON_OBSERVACION','RECHAZADO') AND (ticket_sunat IS NULL OR TRIM(ticket_sunat)='')"; }
            case "terminal" -> { var r=rc(1,"T"); r.setEstado(EstadoResumenDiario.ACEPTADO); em.persist(r); sql="SELECT COUNT(*) FROM resumen_diario WHERE estado IN ('ACEPTADO','ACEPTADO_CON_OBSERVACION') AND (COALESCE(codigo_respuesta_sunat,'')<>'0' OR COALESCE(OCTET_LENGTH(cdr_zip_bytes),0)=0 OR COALESCE(OCTET_LENGTH(cdr_xml_bytes),0)=0 OR finalizado_en IS NULL)"; }
            case "huerfana" -> { em.persist(evidencia(999999L,"0".repeat(64))); sql="SELECT COUNT(*) FROM respuesta_sunat_recibida e LEFT JOIN resumen_diario r ON e.flujo='RC' AND r.id=e.documento_id AND r.ruc_emisor=e.ruc_emisor LEFT JOIN comprobante c ON e.flujo='CPE' AND c.id=e.documento_id AND c.ruc_emisor=e.ruc_emisor LEFT JOIN comunicacion_baja b ON e.flujo='RA' AND b.id=e.documento_id AND b.ruc_emisor=e.ruc_emisor WHERE r.id IS NULL AND c.id IS NULL AND b.id IS NULL"; }
            case "enResumen" -> { var c=doc(1); c.setEstadoSfs(EstadoComprobante.EN_RESUMEN); em.persist(c); sql="SELECT COUNT(*) FROM comprobante c WHERE c.estado_sfs='EN_RESUMEN' AND NOT EXISTS (SELECT 1 FROM resumen_diario_detalle d JOIN resumen_diario r ON r.id=d.resumen_id WHERE d.comprobante_id=c.id AND r.ruc_emisor=c.ruc_emisor AND r.estado IN ('CREADO','ENVIANDO','TICKET_GENERADO','PROCESANDO','ERROR_CONSULTA','ERROR_ENVIO_INCIERTO'))"; }
            case "secuencia" -> { em.persist(doc(5)); em.persist(SecuenciaCorrelativo.builder().rucEmisor(RUC).tipoComprobante("03").serie("B990").ultimoNumero(1).build()); sql="SELECT COUNT(*) FROM (SELECT c.ruc_emisor,c.tipo_comprobante,c.serie FROM comprobante c LEFT JOIN secuencia_correlativo s ON s.ruc_emisor=c.ruc_emisor AND s.tipo_comprobante=c.tipo_comprobante AND s.serie=c.serie GROUP BY c.ruc_emisor,c.tipo_comprobante,c.serie,s.id,s.ultimo_numero HAVING s.id IS NULL OR s.ultimo_numero<MAX(c.numero)) d"; }
            case "secuenciaRC" -> { em.persist(rc(5,"T")); sql="SELECT COUNT(*) FROM (SELECT h.ruc_emisor,h.fecha FROM (SELECT ruc_emisor,fecha_generacion fecha,correlativo FROM resumen_diario UNION ALL SELECT ruc_emisor,fecha_documentos fecha,correlativo FROM resumen_diario) h LEFT JOIN secuencia_resumen_diario s ON s.ruc_emisor=h.ruc_emisor AND s.fecha_referencia=h.fecha GROUP BY h.ruc_emisor,h.fecha,s.id,s.ultimo_correlativo HAVING s.id IS NULL OR s.ultimo_correlativo<MAX(h.correlativo)) d"; }
            case "secuenciaRA" -> { em.persist(ComunicacionBaja.builder().rucEmisor(RUC).fechaDocumentos(LocalDate.of(2026,10,1)).fechaGeneracion(LocalDate.of(2026,10,1)).correlativo(5).identificador("RA-20261001-5").nombreArchivoXml("ra.xml").estado(EstadoComunicacionBaja.TICKET_GENERADO).ticketSunat("T").build()); sql="SELECT COUNT(*) FROM (SELECT r.ruc_emisor,r.fecha_generacion FROM comunicacion_baja r LEFT JOIN secuencia_comunicacion_baja s ON s.ruc_emisor=r.ruc_emisor AND s.fecha_referencia=r.fecha_generacion GROUP BY r.ruc_emisor,r.fecha_generacion,s.id,s.ultimo_correlativo HAVING s.id IS NULL OR s.ultimo_correlativo<MAX(r.correlativo)) d"; }
            case "multiple" -> { var c=doc(1); em.persist(c); var a=rc(1,"A"); var b=rc(2,"B"); a.addDetalle(detalle(c.getId())); b.addDetalle(detalle(c.getId())); em.persist(a); em.persist(b); sql="SELECT COUNT(*) FROM (SELECT d.comprobante_id FROM resumen_diario_detalle d JOIN resumen_diario r ON r.id=d.resumen_id WHERE r.estado IN ('CREADO','ENVIANDO','TICKET_GENERADO','PROCESANDO','ERROR_CONSULTA','ERROR_ENVIO_INCIERTO') GROUP BY d.comprobante_id HAVING COUNT(DISTINCT r.id)>1) x"; }
            case "hash" -> { var r=rc(1,"T"); em.persist(r); em.persist(evidencia(r.getId(),"0".repeat(64))); sql="SELECT COUNT(*) FROM respuesta_sunat_recibida WHERE COALESCE(OCTET_LENGTH(respuesta_zip_bytes),0)=0 OR sha256<>SHA2(respuesta_zip_bytes,256)"; }
            default -> { var r=rc(1,"T"); r.setIdentificador("RC-20200101-1"); em.persist(r); sql="SELECT COUNT(*) FROM resumen_diario WHERE identificador NOT LIKE CONCAT('RC-',DATE_FORMAT(fecha_generacion,'%Y%m%d'),'-%') OR nombre_archivo_xml<>CONCAT(ruc_emisor,'-',identificador,'.xml') OR correlativo NOT BETWEEN 1 AND 99999"; }
        }
        em.flush();
        assertEquals(1, jdbc.queryForObject(sql, Integer.class), caso);
    }

    @Test void detectorDeLongitudVe151EnFixtureDerivadaYElEsquemaRealNoLoAdmite() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT REPEAT('T',151) ticket_sunat) fixture WHERE CHAR_LENGTH(ticket_sunat)>150", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM resumen_diario WHERE CHAR_LENGTH(ticket_sunat)>150", Integer.class));
        for (String tabla : java.util.List.of("resumen_diario","comunicacion_baja","respuesta_sunat_recibida"))
            assertEquals(150L, jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='ticket_sunat'", Long.class, tabla));
    }

    static Comprobante doc(int numero) {
        return Comprobante.builder().tiendaId(1L).rucEmisor(RUC).tipoComprobante("03").serie("B990").numero(numero)
                .fechaEmision(LocalDateTime.of(2026,10,1,10,0)).moneda("PEN").tipoDocCliente("1")
                .numDocCliente("00000001").rznSocialCliente("CLIENTE FICTICIO ENSAYO").opGravada(BigDecimal.ZERO)
                .opExonerada(BigDecimal.TEN).opInafecta(BigDecimal.ZERO).igv(BigDecimal.ZERO).montoTotal(BigDecimal.TEN)
                .estadoSfs(EstadoComprobante.PENDIENTE_RESUMEN).build();
    }
    static ResumenDiario rc(int n,String ticket) {
        return ResumenDiario.builder().rucEmisor(RUC).fechaDocumentos(LocalDate.of(2026,10,1)).fechaGeneracion(LocalDate.of(2026,10,1))
                .correlativo(n).identificador("RC-20261001-"+n).nombreArchivoXml(RUC+"-RC-20261001-"+n+".xml")
                .ticketSunat(ticket).estado(EstadoResumenDiario.TICKET_GENERADO).build();
    }
    static ResumenDiarioDetalle detalle(Long id) {
        return ResumenDiarioDetalle.builder().comprobanteId(id).numeroLinea(1).tipoComprobante("03").serie("B990").numero(1)
                .tipoDocCliente("1").numDocCliente("00000001").opGravada(BigDecimal.ZERO).opExonerada(BigDecimal.TEN)
                .opInafecta(BigDecimal.ZERO).igv(BigDecimal.ZERO).montoTotal(BigDecimal.TEN).build();
    }
    static RespuestaSunatRecibida evidencia(Long id,String hash) {
        return RespuestaSunatRecibida.builder().rucEmisor(RUC).flujo(RespuestaSunatRecibida.Flujo.RC).documentoId(id)
                .recibidoEn(LocalDateTime.of(2026,10,1,10,0)).sha256(hash).respuestaZipBytes(new byte[]{42}).build();
    }
}
