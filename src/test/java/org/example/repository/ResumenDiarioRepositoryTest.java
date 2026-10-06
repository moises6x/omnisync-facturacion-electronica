package org.example.repository;

import org.example.model.*;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import org.springframework.data.domain.PageRequest;

import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


/**
 * =============================================================
 * RESUMEN DIARIO REPOSITORY TEST
 * =============================================================
 *
 * A diferencia de:
 *
 * ResumenDiarioServiceTest
 * ResumenDiarioSchedulerTest
 *
 * aquí NO usamos Mockito para simular la base de datos.
 *
 *
 * =============================================================
 * ¿QUÉ HACEMOS AQUÍ?
 * =============================================================
 *
 * Spring levanta JPA realmente.
 *
 * Hibernate crea tablas reales temporalmente dentro de:
 *
 * bd_facturacion_test
 *
 * Y se ejecutan las consultas reales de:
 *
 * - ComprobanteRepository
 * - ResumenDiarioRepository
 *
 *
 * =============================================================
 * OBJETIVOS
 * =============================================================
 *
 * Comprobar:
 *
 * 1. aislamiento por RUC
 *
 * 2. selección correcta de boletas
 *
 * 3. selección correcta de NC de boleta
 *
 * 4. exclusión de facturas
 *
 * 5. exclusión de NC de factura
 *
 * 6. transición atómica:
 *
 *    PENDIENTE_RESUMEN
 *           ↓
 *       EN_RESUMEN
 *
 * 7. impedir que otra empresa tome el documento
 *
 * 8. impedir tomar dos veces el mismo documento
 *
 * 9. actualización final desde el RC
 *
 * 10. aislamiento del propio resumen_diario
 *
 * 11. transición CREADO -> ENVIANDO
 *
 *
 * =============================================================
 * SEGURIDAD
 * =============================================================
 *
 * @ActiveProfiles("test")
 *
 * obliga a utilizar:
 *
 * src/test/resources/application-test.properties
 *
 * y por tanto:
 *
 * bd_facturacion_test
 *
 * NUNCA bd_facturacion.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class ResumenDiarioRepositoryTest {

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void bloqueoDeCabeceraDebeRespetarRuc() {
        ResumenDiario r = resumenDiarioRepository.saveAndFlush(crearResumen(RUC_A, 71, EstadoResumenDiario.CREADO));
        assertTrue(resumenDiarioRepository.bloquearPorIdYRuc(r.getId(), RUC_B).isEmpty());
        assertEquals(r.getId(), resumenDiarioRepository.bloquearPorIdYRuc(r.getId(), RUC_A).orElseThrow().getId());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void dosTransaccionesNoDebenAdquirirNiReemplazarLaMismaEvidencia() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        Long id = tx.execute(s -> resumenDiarioRepository.saveAndFlush(crearResumen(RUC_A, 72, EstadoResumenDiario.CREADO)).getId());
        var bloqueoTomado = new java.util.concurrent.CountDownLatch(1);
        var segundoIniciado = new java.util.concurrent.CountDownLatch(1);
        var permitirCommit = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var primero = executor.submit(() -> tx.execute(s -> {
                    ResumenDiario r = resumenDiarioRepository.bloquearPorIdYRuc(id, RUC_A).orElseThrow();
                    r.setEstado(EstadoResumenDiario.ENVIANDO); r.setIntentosEnvio(1);
                    r.setXmlFirmadoBytes(new byte[]{42}); r.setHashCpe("HASH-GANADOR");
                    resumenDiarioRepository.saveAndFlush(r);
                    bloqueoTomado.countDown();
                    try { assertTrue(permitirCommit.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    return true;
                }));
                assertTrue(bloqueoTomado.await(10, java.util.concurrent.TimeUnit.SECONDS));
                var segundo = executor.submit(() -> tx.execute(s -> {
                    segundoIniciado.countDown();
                    ResumenDiario r = resumenDiarioRepository.bloquearPorIdYRuc(id, RUC_A).orElseThrow();
                    assertEquals(EstadoResumenDiario.ENVIANDO, r.getEstado());
                    assertArrayEquals(new byte[]{42}, r.getXmlFirmadoBytes());
                    assertEquals("HASH-GANADOR", r.getHashCpe());
                    assertEquals(1, r.getIntentosEnvio());
                    return false;
                }));
                assertTrue(segundoIniciado.await(10, java.util.concurrent.TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class, () -> segundo.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
                permitirCommit.countDown();
                assertTrue(primero.get(10, java.util.concurrent.TimeUnit.SECONDS));
                assertFalse(segundo.get(10, java.util.concurrent.TimeUnit.SECONDS));
            } finally { permitirCommit.countDown(); }
        } finally {
            tx.execute(s -> { resumenDiarioRepository.deleteById(id); return null; });
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollbackDebeDeshacerEstadoYXmlHashJuntos() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        Long id = tx.execute(s -> resumenDiarioRepository.saveAndFlush(crearResumen(RUC_A, 73, EstadoResumenDiario.CREADO)).getId());
        try {
            assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
                ResumenDiario r = resumenDiarioRepository.bloquearPorIdYRuc(id, RUC_A).orElseThrow();
                r.setEstado(EstadoResumenDiario.ENVIANDO); r.setXmlFirmadoBytes(new byte[]{42}); r.setHashCpe("HASH-TEST");
                resumenDiarioRepository.saveAndFlush(r);
                throw new IllegalStateException("fallo antes del commit simulado");
            }));
            tx.execute(s -> {
                ResumenDiario r = resumenDiarioRepository.findByIdAndRucEmisor(id, RUC_A).orElseThrow();
                assertEquals(EstadoResumenDiario.CREADO, r.getEstado());
                assertNull(r.getXmlFirmadoBytes()); assertNull(r.getHashCpe());
                return null;
            });
        } finally { tx.execute(s -> { resumenDiarioRepository.deleteById(id); return null; }); }
    }


    // =========================================================
    // CONSTANTES
    // =========================================================

    private static final String RUC_A =
            "20100130204";

    private static final String RUC_B =
            "20600000001";

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


    // =========================================================
    // TEST 1
    // BUSCAR COMPROBANTE POR ID + RUC
    // =========================================================
    //
    // Empresa A crea una boleta.
    //
    // Empresa A:
    // puede recuperarla.
    //
    // Empresa B:
    // NO puede recuperarla usando el mismo ID.
    //
    // =========================================================

    @Test
    void noDebePermitirBuscarComprobanteDeOtraEmpresaPorId() {

        Comprobante guardado =
                comprobanteRepository.saveAndFlush(
                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                )
                        )
                );


        // =====================================================
        // EMPRESA CORRECTA
        // =====================================================

        Optional<Comprobante> empresaA =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                guardado.getId(),
                                RUC_A
                        );


        assertTrue(
                empresaA.isPresent()
        );


        // =====================================================
        // OTRA EMPRESA
        // =====================================================

        Optional<Comprobante> empresaB =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                guardado.getId(),
                                RUC_B
                        );


        assertTrue(
                empresaB.isEmpty()
        );
    }


    // =========================================================
    // TEST 2
    // FILTRO TRIBUTARIO DEL RESUMEN
    // =========================================================
    //
    // Insertamos:
    //
    // 03 -> BOLETA
    //
    // 07 -> NC que afecta BOLETA
    //
    // 01 -> FACTURA
    //
    // 07 -> NC que afecta FACTURA
    //
    //
    // El Resumen Diario solamente puede tomar:
    //
    // 03
    //
    // 07 -> 03
    //
    // =========================================================

    @Test
    void debeSeleccionarSoloBoletasYNotasCreditoDeBoleta() {

        // =====================================================
        // BOLETA
        // DEBE ENTRAR
        // =====================================================

        Comprobante boleta =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        9,
                                        0
                                )
                        )
                );


        // =====================================================
        // NC DE BOLETA
        // DEBE ENTRAR
        // =====================================================

        Comprobante ncBoleta =
                comprobanteRepository.saveAndFlush(

                        crearNotaCredito(
                                RUC_A,
                                1,
                                "03",
                                "B001",
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                ),
                                EstadoComprobante
                                        .PENDIENTE_RESUMEN
                        )
                );


        // =====================================================
        // FACTURA
        // NO DEBE ENTRAR
        // =====================================================

        comprobanteRepository.saveAndFlush(

                crearFactura(
                        RUC_A,
                        1,
                        FECHA.atTime(
                                11,
                                0
                        )
                )
        );


        // =====================================================
        // NC DE FACTURA
        // NO DEBE ENTRAR
        // =====================================================

        comprobanteRepository.saveAndFlush(

                crearNotaCredito(
                        RUC_A,
                        2,
                        "01",
                        "F001",
                        1,
                        FECHA.atTime(
                                12,
                                0
                        ),
                        EstadoComprobante
                                .PENDIENTE_ENVIO
                )
        );


        // =====================================================
        // CONSULTAR RESUMEN DIARIO
        // =====================================================

        List<Comprobante> encontrados =
                comprobanteRepository
                        .buscarPendientesResumenDiarioLimitado(

                                RUC_A,

                                FECHA.atStartOfDay(),

                                FECHA
                                        .plusDays(1)
                                        .atStartOfDay(),

                                PageRequest.of(
                                        0,
                                        500
                                )
                        );


        // =====================================================
        // VALIDAR
        // =====================================================

        assertEquals(
                2,
                encontrados.size()
        );


        List<Long> ids =
                encontrados
                        .stream()
                        .map(
                                Comprobante::getId
                        )
                        .toList();


        assertTrue(
                ids.contains(
                        boleta.getId()
                )
        );


        assertTrue(
                ids.contains(
                        ncBoleta.getId()
                )
        );
    }


    // =========================================================
    // TEST 3
    // AISLAMIENTO POR EMPRESA EN QUERY
    // =========================================================

    @Test
    void buscarPendientesNoDebeMezclarEmpresas() {

        Comprobante empresaA =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                )
                        )
                );


        comprobanteRepository.saveAndFlush(

                crearBoleta(
                        RUC_B,
                        1,
                        FECHA.atTime(
                                10,
                                1
                        )
                )
        );


        List<Comprobante> encontrados =
                comprobanteRepository
                        .buscarPendientesResumenDiarioLimitado(

                                RUC_A,

                                FECHA.atStartOfDay(),

                                FECHA
                                        .plusDays(1)
                                        .atStartOfDay(),

                                PageRequest.of(
                                        0,
                                        500
                                )
                        );


        assertEquals(
                1,
                encontrados.size()
        );


        assertEquals(
                empresaA.getId(),
                encontrados
                        .get(0)
                        .getId()
        );


        assertEquals(
                RUC_A,
                encontrados
                        .get(0)
                        .getRucEmisor()
        );
    }


    // =========================================================
    // TEST 4
    // MARCAR EN RESUMEN
    // =========================================================
    //
    // Probamos el UPDATE NATIVO REAL.
    //
    // PENDIENTE_RESUMEN
    //        ↓
    // EN_RESUMEN
    //
    // =========================================================

    @Test
    void debeCambiarPendienteResumenAEnResumenAtomicamente() {

        Comprobante comprobante =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                )
                        )
                );


        int modificados =
                comprobanteRepository
                        .marcarEnResumen(
                                comprobante.getId(),
                                RUC_A
                        );


        assertEquals(
                1,
                modificados
        );


        Comprobante actualizado =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                comprobante.getId(),
                                RUC_A
                        )
                        .orElseThrow();


        assertEquals(
                EstadoComprobante.EN_RESUMEN,
                actualizado.getEstadoSfs()
        );
    }


    // =========================================================
    // TEST 5
    // DOCUMENTO NO PUEDE TOMARSE DOS VECES
    // =========================================================
    //
    // Primer UPDATE:
    //
    // PENDIENTE_RESUMEN -> EN_RESUMEN
    //
    // devuelve 1.
    //
    // Segundo UPDATE:
    //
    // ya no cumple:
    //
    // estado = PENDIENTE_RESUMEN
    //
    // por tanto devuelve 0.
    //
    // =========================================================

    @Test
    void noDebePermitirTomarDosVecesElMismoComprobante() {

        Comprobante comprobante =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                )
                        )
                );


        int primerIntento =
                comprobanteRepository
                        .marcarEnResumen(
                                comprobante.getId(),
                                RUC_A
                        );


        int segundoIntento =
                comprobanteRepository
                        .marcarEnResumen(
                                comprobante.getId(),
                                RUC_A
                        );


        assertEquals(
                1,
                primerIntento
        );


        assertEquals(
                0,
                segundoIntento
        );
    }


    // =========================================================
    // TEST 6
    // OTRA EMPRESA NO PUEDE TOMAR EL DOCUMENTO
    // =========================================================

    @Test
    void otraEmpresaNoDebePoderMarcarComprobanteEnResumen() {

        Comprobante comprobante =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                )
                        )
                );


        /*
         * Intentamos tomar el documento de Empresa A
         * utilizando RUC B.
         */
        int resultado =
                comprobanteRepository
                        .marcarEnResumen(
                                comprobante.getId(),
                                RUC_B
                        );


        assertEquals(
                0,
                resultado
        );


        /*
         * Debe continuar pendiente.
         */
        Comprobante recargado =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                comprobante.getId(),
                                RUC_A
                        )
                        .orElseThrow();


        assertEquals(
                EstadoComprobante.PENDIENTE_RESUMEN,
                recargado.getEstadoSfs()
        );
    }


    // =========================================================
    // TEST 7
    // ACEPTACIÓN DEL RC ACTUALIZA COMPROBANTES
    // =========================================================

    @Test
    void debeActualizarSoloComprobantesEnResumenDeLaEmpresaCorrecta() {

        Comprobante empresaA =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_A,
                                1,
                                FECHA.atTime(
                                        10,
                                        0
                                )
                        )
                );


        Comprobante empresaB =
                comprobanteRepository.saveAndFlush(

                        crearBoleta(
                                RUC_B,
                                1,
                                FECHA.atTime(
                                        10,
                                        1
                                )
                        )
                );


        // =====================================================
        // AMBOS PASAN A EN_RESUMEN
        // =====================================================

        assertEquals(
                1,
                comprobanteRepository
                        .marcarEnResumen(
                                empresaA.getId(),
                                RUC_A
                        )
        );


        assertEquals(
                1,
                comprobanteRepository
                        .marcarEnResumen(
                                empresaB.getId(),
                                RUC_B
                        )
        );


        // =====================================================
        // FINALIZAMOS SOLAMENTE EMPRESA A
        // =====================================================

        int modificados =
                comprobanteRepository
                        .actualizarEstadoDesdeResumen(

                                RUC_A,

                                List.of(
                                        empresaA.getId(),
                                        empresaB.getId()
                                ),

                                EstadoComprobante.ACEPTADO,

                                "0",

                                "Resumen Diario aceptado"
                        );


        assertEquals(
                1,
                modificados
        );


        // =====================================================
        // EMPRESA A
        // =====================================================

        Comprobante actualizadoA =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                empresaA.getId(),
                                RUC_A
                        )
                        .orElseThrow();


        assertEquals(
                EstadoComprobante.ACEPTADO,
                actualizadoA.getEstadoSfs()
        );


        assertEquals(
                "0",
                actualizadoA
                        .getCodigoRespuestaSunat()
        );


        // =====================================================
        // EMPRESA B
        // =====================================================

        Comprobante actualizadoB =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                empresaB.getId(),
                                RUC_B
                        )
                        .orElseThrow();


        /*
         * Empresa B debe continuar EN_RESUMEN.
         */
        assertEquals(
                EstadoComprobante.EN_RESUMEN,
                actualizadoB.getEstadoSfs()
        );
    }


    // =========================================================
    // TEST 8
    // AISLAMIENTO DE RESUMEN_DIARIO
    // =========================================================

    @Test
    void otraEmpresaNoDebePoderConsultarResumenDiarioPorId() {

        ResumenDiario resumen =
                resumenDiarioRepository.saveAndFlush(

                        crearResumen(
                                RUC_A,
                                1,
                                EstadoResumenDiario.CREADO
                        )
                );


        Optional<ResumenDiario> correcto =
                resumenDiarioRepository
                        .findByIdAndRucEmisor(
                                resumen.getId(),
                                RUC_A
                        );


        Optional<ResumenDiario> otraEmpresa =
                resumenDiarioRepository
                        .findByIdAndRucEmisor(
                                resumen.getId(),
                                RUC_B
                        );


        assertTrue(
                correcto.isPresent()
        );


        assertTrue(
                otraEmpresa.isEmpty()
        );
    }


    // =========================================================
    // TEST 9
    // BUSCAR COMPLETO CON DETALLES
    // =========================================================

    @Test
    void debeRecuperarResumenConTodosSusDetalles() {

        ResumenDiario resumen =
                crearResumen(
                        RUC_A,
                        1,
                        EstadoResumenDiario.CREADO
                );


        resumen.addDetalle(

                crearDetalleResumen(
                        100L,
                        1,
                        1
                )
        );


        resumen.addDetalle(

                crearDetalleResumen(
                        101L,
                        2,
                        2
                )
        );


        ResumenDiario guardado =
                resumenDiarioRepository
                        .saveAndFlush(
                                resumen
                        );


        ResumenDiario encontrado =
                resumenDiarioRepository
                        .buscarCompleto(
                                guardado.getId(),
                                RUC_A
                        )
                        .orElseThrow();


        assertEquals(
                2,
                encontrado
                        .getDetalles()
                        .size()
        );


        assertEquals(
                100L,
                encontrado
                        .getDetalles()
                        .get(0)
                        .getComprobanteId()
        );


        assertEquals(
                101L,
                encontrado
                        .getDetalles()
                        .get(1)
                        .getComprobanteId()
        );
    }


    // =========================================================
    // TEST 10
    // CREADO -> ENVIANDO
    // =========================================================
    //
    // Este UPDATE evita que dos workers ejecuten
    // sendSummary simultáneamente sobre el mismo RC.
    //
    // =========================================================

    @Test
    void resumenCreadoSoloDebePoderMarcarseEnviandoUnaVez() {

        ResumenDiario resumen =
                resumenDiarioRepository.saveAndFlush(

                        crearResumen(
                                RUC_A,
                                1,
                                EstadoResumenDiario.CREADO
                        )
                );


        int primerIntento =
                resumenDiarioRepository
                        .marcarEnviando(
                                resumen.getId(),
                                RUC_A
                        );


        int segundoIntento =
                resumenDiarioRepository
                        .marcarEnviando(
                                resumen.getId(),
                                RUC_A
                        );


        assertEquals(
                1,
                primerIntento
        );


        assertEquals(
                0,
                segundoIntento
        );


        ResumenDiario actualizado =
                resumenDiarioRepository
                        .findByIdAndRucEmisor(
                                resumen.getId(),
                                RUC_A
                        )
                        .orElseThrow();


        assertEquals(
                EstadoResumenDiario.ENVIANDO,
                actualizado.getEstado()
        );


        assertEquals(
                1,
                actualizado.getIntentosEnvio()
        );
    }


    // =========================================================
    // HELPER
    // BOLETA
    // =========================================================

    private Comprobante crearBoleta(
            String ruc,
            int numero,
            LocalDateTime fecha
    ) {

        return crearComprobanteBase(

                ruc,

                "03",

                "B001",

                numero,

                fecha,

                EstadoComprobante.PENDIENTE_RESUMEN
        );
    }


    // =========================================================
    // HELPER
    // FACTURA
    // =========================================================

    private Comprobante crearFactura(
            String ruc,
            int numero,
            LocalDateTime fecha
    ) {

        return crearComprobanteBase(

                ruc,

                "01",

                "F001",

                numero,

                fecha,

                EstadoComprobante.PENDIENTE_ENVIO
        );
    }


    // =========================================================
    // HELPER
    // NOTA DE CRÉDITO
    // =========================================================

    private Comprobante crearNotaCredito(

            String ruc,

            int numero,

            String tipoAfectado,

            String serieAfectada,

            int numeroAfectado,

            LocalDateTime fecha,

            EstadoComprobante estado

    ) {

        Comprobante nota =
                crearComprobanteBase(

                        ruc,

                        "07",

                        "03".equals(
                                tipoAfectado
                        )
                                ? "BC01"
                                : "FC01",

                        numero,

                        fecha,

                        estado
                );


        nota.setTipoComprobanteAfectado(
                tipoAfectado
        );


        nota.setSerieComprobanteAfectado(
                serieAfectada
        );


        nota.setNumeroComprobanteAfectado(
                numeroAfectado
        );


        nota.setCodigoMotivoNota(
                "01"
        );


        nota.setDescripcionMotivoNota(
                "ANULACION DE PRUEBA"
        );


        return nota;
    }


    // =========================================================
    // HELPER
    // COMPROBANTE BASE
    // =========================================================

    private Comprobante crearComprobanteBase(

            String ruc,

            String tipo,

            String serie,

            int numero,

            LocalDateTime fecha,

            EstadoComprobante estado

    ) {

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
                        fecha
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
                        new BigDecimal(
                                "100.00"
                        )
                )

                .opExonerada(
                        BigDecimal.ZERO
                )

                .opInafecta(
                        BigDecimal.ZERO
                )

                .igv(
                        new BigDecimal(
                                "18.00"
                        )
                )

                .montoTotal(
                        new BigDecimal(
                                "118.00"
                        )
                )

                .estadoSfs(
                        estado
                )

                .build();
    }


    // =========================================================
    // HELPER
    // RESUMEN
    // =========================================================

    private ResumenDiario crearResumen(

            String ruc,

            int correlativo,

            EstadoResumenDiario estado

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
                        estado
                )

                .intentosEnvio(
                        0
                )

                .intentosConsulta(
                        0
                )

                .build();
    }


    // =========================================================
    // HELPER
    // DETALLE RESUMEN
    // =========================================================

    private ResumenDiarioDetalle crearDetalleResumen(

            Long comprobanteId,

            int linea,

            int numero

    ) {

        return ResumenDiarioDetalle
                .builder()

                .comprobanteId(
                        comprobanteId
                )

                .numeroLinea(
                        linea
                )

                .tipoComprobante(
                        "03"
                )

                .serie(
                        "B001"
                )

                .numero(
                        numero
                )

                .tipoDocCliente(
                        "1"
                )

                .numDocCliente(
                        "48411579"
                )

                .opGravada(
                        new BigDecimal(
                                "100.00"
                        )
                )

                .opExonerada(
                        BigDecimal.ZERO
                )

                .opInafecta(
                        BigDecimal.ZERO
                )

                .igv(
                        new BigDecimal(
                                "18.00"
                        )
                )

                .montoTotal(
                        new BigDecimal(
                                "118.00"
                        )
                )

                .moneda(
                        "PEN"
                )

                .codigoEstado(
                        "1"
                )

                .build();
    }
}
