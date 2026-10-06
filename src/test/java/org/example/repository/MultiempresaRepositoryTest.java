package org.example.repository;

import org.example.model.*;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


/**
 * =============================================================
 * MULTIEMPRESA REPOSITORY TEST
 * =============================================================
 *
 * OBJETIVO:
 *
 * Comprobar con MySQL REAL de pruebas que los datos
 * permanecen aislados por empresa.
 *
 *
 * =============================================================
 * BASE UTILIZADA
 * =============================================================
 *
 * bd_facturacion_test
 *
 * NUNCA:
 *
 * bd_facturacion
 *
 *
 * =============================================================
 * ESCENARIOS
 * =============================================================
 *
 * 1. Dos empresas pueden usar la misma serie/número.
 *
 * 2. Buscar comprobante por serie/número no cruza RUC.
 *
 * 3. Historial sólo devuelve datos de su empresa.
 *
 * 4. Dashboard sólo suma datos de su empresa.
 *
 * 5. Una empresa no puede cambiar a EN_RESUMEN
 *    un comprobante de otra.
 *
 * 6. Aceptación de RC no actualiza documentos ajenos.
 *
 * 7. Dos empresas pueden usar el mismo identificador RC.
 *
 * 8. Un RC sólo puede consultarse usando su propio RUC.
 *
 * 9. La misma idempotency-key puede existir en
 *    empresas diferentes, pero no duplicarse dentro
 *    de la misma empresa.
 *
 * 10. Los correlativos son independientes por RUC.
 *
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class MultiempresaRepositoryTest {


    // =========================================================
    // EMPRESA A
    // =========================================================

    private static final String RUC_A =
            "20100130204";


    // =========================================================
    // EMPRESA B
    // =========================================================

    private static final String RUC_B =
            "20600000001";


    // =========================================================
    // FECHA DE PRUEBA
    // =========================================================

    private static final LocalDate FECHA =
            LocalDate.of(
                    2026,
                    9,
                    19
            );


    // =========================================================
    // REPOSITORIES REALES
    // =========================================================

    @Autowired
    private ComprobanteRepository comprobanteRepository;


    @Autowired
    private ResumenDiarioRepository resumenDiarioRepository;


    @Autowired
    private SolicitudEmisionRepository solicitudEmisionRepository;


    @Autowired
    private SecuenciaCorrelativoRepository secuenciaCorrelativoRepository;


    // =========================================================
    // TEST 1
    // MISMA SERIE + NÚMERO EN EMPRESAS DIFERENTES
    // =========================================================
    //
    // Empresa A:
    //
    // F001-1
    //
    // Empresa B:
    //
    // F001-1
    //
    // Debe permitirse porque el RUC forma parte
    // de la clave única.
    //
    // =========================================================

    @Test
    void dosEmpresasPuedenTenerLaMismaSerieYNumero() {

        Comprobante empresaA =
                comprobanteRepository.saveAndFlush(

                        crearComprobante(
                                RUC_A,
                                "01",
                                "F001",
                                1,
                                EstadoComprobante.ACEPTADO,
                                new BigDecimal("118.00")
                        )
                );


        Comprobante empresaB =
                comprobanteRepository.saveAndFlush(

                        crearComprobante(
                                RUC_B,
                                "01",
                                "F001",
                                1,
                                EstadoComprobante.ACEPTADO,
                                new BigDecimal("236.00")
                        )
                );


        assertNotNull(
                empresaA.getId()
        );


        assertNotNull(
                empresaB.getId()
        );


        assertNotEquals(
                empresaA.getId(),
                empresaB.getId()
        );


        assertEquals(
                "F001",
                empresaA.getSerie()
        );


        assertEquals(
                "F001",
                empresaB.getSerie()
        );


        assertEquals(
                1,
                empresaA.getNumero()
        );


        assertEquals(
                1,
                empresaB.getNumero()
        );
    }


    // =========================================================
    // TEST 2
    // BÚSQUEDA NATURAL AISLADA POR RUC
    // =========================================================

    @Test
    void busquedaPorSerieYNumeroDebeRespetarEmpresa() {

        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_A,
                        "01",
                        "F001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("118.00")
                )
        );


        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_B,
                        "01",
                        "F001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("236.00")
                )
        );


        Optional<Comprobante> resultadoA =
                comprobanteRepository
                        .findByRucEmisorAndTipoComprobanteAndSerieAndNumero(

                                RUC_A,

                                "01",

                                "F001",

                                1
                        );


        Optional<Comprobante> resultadoB =
                comprobanteRepository
                        .findByRucEmisorAndTipoComprobanteAndSerieAndNumero(

                                RUC_B,

                                "01",

                                "F001",

                                1
                        );


        assertTrue(
                resultadoA.isPresent()
        );


        assertTrue(
                resultadoB.isPresent()
        );


        assertEquals(
                RUC_A,
                resultadoA
                        .orElseThrow()
                        .getRucEmisor()
        );


        assertEquals(
                RUC_B,
                resultadoB
                        .orElseThrow()
                        .getRucEmisor()
        );


        assertEquals(
                new BigDecimal("118.00"),
                resultadoA
                        .orElseThrow()
                        .getMontoTotal()
        );


        assertEquals(
                new BigDecimal("236.00"),
                resultadoB
                        .orElseThrow()
                        .getMontoTotal()
        );
    }


    // =========================================================
    // TEST 3
    // HISTORIAL AISLADO POR EMPRESA
    // =========================================================

    @Test
    void historialNoDebeMostrarComprobantesDeOtraEmpresa() {

        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_A,
                        "03",
                        "B001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("118.00")
                )
        );


        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_A,
                        "03",
                        "B001",
                        2,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("59.00")
                )
        );


        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_B,
                        "03",
                        "B001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("999.00")
                )
        );


        Page<Comprobante> historial =
                comprobanteRepository
                        .buscarHistorial(

                                RUC_A,

                                null,

                                null,

                                FECHA.atStartOfDay(),

                                FECHA
                                        .plusDays(1)
                                        .atStartOfDay(),

                                PageRequest.of(
                                        0,
                                        50
                                )
                        );


        assertEquals(
                2,
                historial.getTotalElements()
        );


        assertTrue(
                historial
                        .getContent()
                        .stream()
                        .allMatch(
                                comprobante ->
                                        RUC_A.equals(
                                                comprobante
                                                        .getRucEmisor()
                                        )
                        )
        );


        assertTrue(
                historial
                        .getContent()
                        .stream()
                        .noneMatch(
                                comprobante ->
                                        RUC_B.equals(
                                                comprobante
                                                        .getRucEmisor()
                                        )
                        )
        );
    }


    // =========================================================
    // TEST 4
    // DASHBOARD AISLADO POR EMPRESA
    // =========================================================
    //
    // Empresa A:
    //
    // 1 factura = 118
    // 1 boleta  = 59
    //
    // Empresa B:
    //
    // 1 factura = 999
    //
    // El dashboard A jamás debe sumar los 999 de B.
    //
    // =========================================================

    @Test
    void dashboardDebeCalcularSoloDatosDeSuEmpresa() {

        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_A,
                        "01",
                        "F001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("118.00")
                )
        );


        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_A,
                        "03",
                        "B001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("59.00")
                )
        );


        comprobanteRepository.saveAndFlush(

                crearComprobante(
                        RUC_B,
                        "01",
                        "F001",
                        1,
                        EstadoComprobante.ACEPTADO,
                        new BigDecimal("999.00")
                )
        );


        ComprobanteRepository.ResumenProjection resumenA =
                comprobanteRepository
                        .obtenerResumen(

                                RUC_A,

                                FECHA.atStartOfDay(),

                                FECHA
                                        .plusDays(1)
                                        .atStartOfDay()
                        );


        assertNotNull(
                resumenA
        );


        assertEquals(
                2L,
                resumenA.getTotalComprobantes()
        );


        assertEquals(
                1L,
                resumenA.getFacturas()
        );


        assertEquals(
                1L,
                resumenA.getBoletas()
        );


        assertEquals(
                0L,
                resumenA.getNotasCredito()
        );


        assertEquals(
                2L,
                resumenA.getAceptados()
        );


        assertEquals(
                0,
                resumenA
                        .getMontoFacturas()
                        .compareTo(
                                new BigDecimal("118.00")
                        )
        );


        assertEquals(
                0,
                resumenA
                        .getMontoBoletas()
                        .compareTo(
                                new BigDecimal("59.00")
                        )
        );
    }


    // =========================================================
    // TEST 5
    // EMPRESA B NO PUEDE TOMAR COMPROBANTE DE EMPRESA A
    // =========================================================

    @Test
    void otraEmpresaNoDebePoderApropiarseDeComprobanteParaResumen() {

        Comprobante comprobanteA =
                comprobanteRepository.saveAndFlush(

                        crearComprobante(
                                RUC_A,
                                "03",
                                "B001",
                                1,
                                EstadoComprobante.PENDIENTE_RESUMEN,
                                new BigDecimal("118.00")
                        )
                );


        int intentoEmpresaB =
                comprobanteRepository
                        .marcarEnResumen(

                                comprobanteA.getId(),

                                RUC_B
                        );


        assertEquals(
                0,
                intentoEmpresaB
        );


        Comprobante recargado =
                comprobanteRepository
                        .findByIdAndRucEmisor(

                                comprobanteA.getId(),

                                RUC_A
                        )
                        .orElseThrow();


        assertEquals(
                EstadoComprobante.PENDIENTE_RESUMEN,
                recargado.getEstadoSfs()
        );
    }


    // =========================================================
    // TEST 6
    // FINALIZACIÓN DE RC NO MODIFICA EMPRESA AJENA
    // =========================================================

    @Test
    void aceptarResumenNoDebeActualizarComprobanteDeOtraEmpresa() {

        Comprobante comprobanteA =
                comprobanteRepository.saveAndFlush(

                        crearComprobante(
                                RUC_A,
                                "03",
                                "B001",
                                1,
                                EstadoComprobante.PENDIENTE_RESUMEN,
                                new BigDecimal("118.00")
                        )
                );


        Comprobante comprobanteB =
                comprobanteRepository.saveAndFlush(

                        crearComprobante(
                                RUC_B,
                                "03",
                                "B001",
                                1,
                                EstadoComprobante.PENDIENTE_RESUMEN,
                                new BigDecimal("236.00")
                        )
                );


        assertEquals(
                1,
                comprobanteRepository
                        .marcarEnResumen(
                                comprobanteA.getId(),
                                RUC_A
                        )
        );


        assertEquals(
                1,
                comprobanteRepository
                        .marcarEnResumen(
                                comprobanteB.getId(),
                                RUC_B
                        )
        );


        int actualizados =
                comprobanteRepository
                        .actualizarEstadoDesdeResumen(

                                RUC_A,

                                List.of(
                                        comprobanteA.getId(),
                                        comprobanteB.getId()
                                ),

                                EstadoComprobante.ACEPTADO,

                                "0",

                                "RC aceptado"
                        );


        /*
         * Sólo debe actualizar el documento de A.
         */
        assertEquals(
                1,
                actualizados
        );


        Comprobante resultadoA =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                comprobanteA.getId(),
                                RUC_A
                        )
                        .orElseThrow();


        Comprobante resultadoB =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                comprobanteB.getId(),
                                RUC_B
                        )
                        .orElseThrow();


        assertEquals(
                EstadoComprobante.ACEPTADO,
                resultadoA.getEstadoSfs()
        );


        assertEquals(
                EstadoComprobante.EN_RESUMEN,
                resultadoB.getEstadoSfs()
        );
    }


    // =========================================================
    // TEST 7
    // MISMO RC EN DOS EMPRESAS
    // =========================================================
    //
    // Ambas empresas pueden tener:
    //
    // RC-20260919-1
    //
    // porque el identificador es único dentro de cada RUC,
    // no globalmente.
    //
    // =========================================================

    @Test
    void dosEmpresasPuedenTenerMismoIdentificadorResumenDiario() {

        ResumenDiario resumenA =
                resumenDiarioRepository.saveAndFlush(

                        crearResumen(
                                RUC_A,
                                1
                        )
                );


        ResumenDiario resumenB =
                resumenDiarioRepository.saveAndFlush(

                        crearResumen(
                                RUC_B,
                                1
                        )
                );


        assertNotNull(
                resumenA.getId()
        );


        assertNotNull(
                resumenB.getId()
        );


        assertNotEquals(
                resumenA.getId(),
                resumenB.getId()
        );


        assertEquals(
                "RC-20260919-1",
                resumenA.getIdentificador()
        );


        assertEquals(
                "RC-20260919-1",
                resumenB.getIdentificador()
        );
    }


    // =========================================================
    // TEST 8
    // CONSULTA DE RC POR ID + RUC
    // =========================================================

    @Test
    void unaEmpresaNoDebeConsultarResumenDiarioDeOtra() {

        ResumenDiario resumenA =
                resumenDiarioRepository.saveAndFlush(

                        crearResumen(
                                RUC_A,
                                1
                        )
                );


        Optional<ResumenDiario> usandoRucCorrecto =
                resumenDiarioRepository
                        .findByIdAndRucEmisor(

                                resumenA.getId(),

                                RUC_A
                        );


        Optional<ResumenDiario> usandoRucIncorrecto =
                resumenDiarioRepository
                        .findByIdAndRucEmisor(

                                resumenA.getId(),

                                RUC_B
                        );


        assertTrue(
                usandoRucCorrecto.isPresent()
        );


        assertTrue(
                usandoRucIncorrecto.isEmpty()
        );
    }


    // =========================================================
    // TEST 9
    // IDEMPOTENCIA INDEPENDIENTE POR EMPRESA
    // =========================================================
    //
    // Ambas empresas pueden enviar:
    //
    // X-Idempotency-Key: PEDIDO-001
    //
    // sin colisionar entre ellas.
    //
    // Pero dentro de la MISMA empresa la clave sólo puede
    // inicializarse una vez.
    //
    // =========================================================

    @Test
    void idempotencyKeyDebeSerIndependientePorEmpresa() {

        String key =
                "PEDIDO-001";


        String hashA =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        +
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";


        String hashB =
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                        +
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";


        int primeraA =
                solicitudEmisionRepository
                        .inicializarSiNoExiste(

                                RUC_A,

                                key,

                                hashA
                        );


        int duplicadaA =
                solicitudEmisionRepository
                        .inicializarSiNoExiste(

                                RUC_A,

                                key,

                                hashA
                        );


        int primeraB =
                solicitudEmisionRepository
                        .inicializarSiNoExiste(

                                RUC_B,

                                key,

                                hashB
                        );


        assertEquals(
                1,
                primeraA
        );


        assertEquals(
                0,
                duplicadaA
        );


        assertEquals(
                1,
                primeraB
        );


        SolicitudEmision solicitudA =
                solicitudEmisionRepository
                        .obtenerBloqueada(
                                RUC_A,
                                key
                        )
                        .orElseThrow();


        SolicitudEmision solicitudB =
                solicitudEmisionRepository
                        .obtenerBloqueada(
                                RUC_B,
                                key
                        )
                        .orElseThrow();


        assertEquals(
                RUC_A,
                solicitudA.getRucEmisor()
        );


        assertEquals(
                RUC_B,
                solicitudB.getRucEmisor()
        );


        assertEquals(
                hashA,
                solicitudA.getRequestHash()
        );


        assertEquals(
                hashB,
                solicitudB.getRequestHash()
        );
    }


    // =========================================================
    // TEST 10
    // CORRELATIVOS INDEPENDIENTES POR EMPRESA
    // =========================================================
    //
    // Empresa A:
    //
    // 01 / F001
    //
    // Empresa B:
    //
    // 01 / F001
    //
    // Deben existir como secuencias independientes.
    //
    // =========================================================

    @Test
    void correlativosDebenSerIndependientesPorEmpresa() {

        int creadoA =
                secuenciaCorrelativoRepository
                        .inicializarSiNoExiste(

                                RUC_A,

                                "01",

                                "F001"
                        );


        int creadoB =
                secuenciaCorrelativoRepository
                        .inicializarSiNoExiste(

                                RUC_B,

                                "01",

                                "F001"
                        );


        assertEquals(
                1,
                creadoA
        );


        assertEquals(
                1,
                creadoB
        );


        SecuenciaCorrelativo secuenciaA =
                secuenciaCorrelativoRepository
                        .obtenerSecuenciaBloqueada(

                                RUC_A,

                                "01",

                                "F001"
                        )
                        .orElseThrow();


        SecuenciaCorrelativo secuenciaB =
                secuenciaCorrelativoRepository
                        .obtenerSecuenciaBloqueada(

                                RUC_B,

                                "01",

                                "F001"
                        )
                        .orElseThrow();


        assertNotEquals(
                secuenciaA.getId(),
                secuenciaB.getId()
        );


        assertEquals(
                RUC_A,
                secuenciaA.getRucEmisor()
        );


        assertEquals(
                RUC_B,
                secuenciaB.getRucEmisor()
        );


        assertEquals(
                "01",
                secuenciaA.getTipoComprobante()
        );


        assertEquals(
                "01",
                secuenciaB.getTipoComprobante()
        );


        assertEquals(
                "F001",
                secuenciaA.getSerie()
        );


        assertEquals(
                "F001",
                secuenciaB.getSerie()
        );


        assertEquals(
                0,
                secuenciaA.getUltimoNumero()
        );


        assertEquals(
                0,
                secuenciaB.getUltimoNumero()
        );
    }


    // =========================================================
    // HELPER
    // COMPROBANTE
    // =========================================================

    private Comprobante crearComprobante(

            String ruc,

            String tipo,

            String serie,

            int numero,

            EstadoComprobante estado,

            BigDecimal total

    ) {

        /*
         * Calculamos una base simple para que la entidad
         * tenga información tributaria coherente.
         */
        BigDecimal igv =
                total
                        .multiply(
                                new BigDecimal("18")
                        )
                        .divide(
                                new BigDecimal("118"),
                                2,
                                java.math.RoundingMode.HALF_UP
                        );


        BigDecimal gravada =
                total.subtract(
                        igv
                );


        return Comprobante
                .builder()

                .tiendaId(
                        1L
                )

                .rucEmisor(
                        ruc
                )

                .tipoOperacion(
                        "0101"
                )

                .tipoComprobante(
                        tipo
                )

                .serie(
                        serie
                )

                .numero(
                        numero
                )

                .fechaEmision(
                        FECHA.atTime(
                                10,
                                0,
                                numero
                        )
                )

                .moneda(
                        "PEN"
                )

                .formaPago(
                        "Contado"
                )

                .tipoDocCliente(
                        "1"
                )

                .numDocCliente(
                        "48411579"
                )

                .rznSocialCliente(
                        "CLIENTE TEST"
                )

                .opGravada(
                        gravada
                )

                .opExonerada(
                        BigDecimal.ZERO
                )

                .opInafecta(
                        BigDecimal.ZERO
                )

                .igv(
                        igv
                )

                .montoTotal(
                        total
                )

                .estadoSfs(
                        estado
                )

                .build();
    }


    // =========================================================
    // HELPER
    // RESUMEN DIARIO
    // =========================================================

    private ResumenDiario crearResumen(

            String ruc,

            int correlativo

    ) {

        String identificador =
                "RC-20260919-"
                        +
                        correlativo;


        return ResumenDiario
                .builder()

                .rucEmisor(
                        ruc
                )

                .fechaDocumentos(
                        FECHA
                )

                .fechaGeneracion(
                        FECHA
                )

                .correlativo(
                        correlativo
                )

                .identificador(
                        identificador
                )

                .nombreArchivoXml(
                        ruc
                                +
                                "-"
                                +
                                identificador
                                +
                                ".xml"
                )

                .cantidadDocumentos(
                        0
                )

                .estado(
                        EstadoResumenDiario.CREADO
                )

                .intentosEnvio(
                        0
                )

                .intentosConsulta(
                        0
                )

                .build();
    }
}