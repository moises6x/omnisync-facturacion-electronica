package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.*;

import org.example.admin.model.PagoEmpresa;
import org.example.admin.model.SuscripcionEmpresa;

import org.example.admin.repository.AnuncioEmpresaRepository;
import org.example.admin.repository.PagoEmpresaRepository;
import org.example.admin.repository.SuscripcionEmpresaRepository;

import org.example.auth.repository.UsuarioRepository;
import org.example.auth.security.SecurityContextService;

import org.example.model.Empresa;
import org.example.model.EstadoEmpresa;

import org.example.repository.EmpresaRepository;
import org.example.util.RucUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import java.util.List;


@Service
@RequiredArgsConstructor
public class SuperAdminEmpresaService {

    // =========================================================
    // REPOSITORIOS
    // =========================================================

    private final EmpresaRepository
            empresaRepository;

    private final UsuarioRepository
            usuarioRepository;

    private final SuscripcionEmpresaRepository
            suscripcionRepository;

    private final PagoEmpresaRepository
            pagoRepository;

    private final AnuncioEmpresaRepository
            anuncioRepository;


    // =========================================================
    // SEGURIDAD
    // =========================================================

    private final SecurityContextService
            securityContextService;


    // =========================================================
    // LISTAR EMPRESAS
    // =========================================================

    @Transactional(readOnly = true)
    public List<EmpresaAdminResponse>
    listar() {

        return empresaRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(
                        this::convertir
                )
                .toList();
    }


    // =========================================================
    // DETALLE EMPRESA
    // =========================================================

    @Transactional
    public EmpresaAdminResponse obtener(
            Long empresaId
    ) {

        Empresa empresa =
                buscarEmpresa(
                        empresaId
                );


        return convertir(
                empresa
        );
    }


    // =========================================================
    // CREAR EMPRESA
    // =========================================================

    @Transactional
    public EmpresaAdminResponse crear(
            CrearEmpresaRequest request
    ) {

        String ruc =
                request
                        .ruc()
                        .trim();


        // =====================================================
        // VALIDAR RUC
        // =====================================================

        if (
                !RucUtils.esValido(
                        ruc
                )
        ) {

            throw new IllegalArgumentException(
                    "RUC inválido: el dígito verificador no coincide."
            );
        }


        // =====================================================
        // EVITAR EMPRESA DUPLICADA
        // =====================================================

        if (
                empresaRepository
                        .existsByRuc(
                                ruc
                        )
        ) {

            throw new IllegalArgumentException(
                    "Ya existe una empresa registrada con ese RUC."
            );
        }


        // =====================================================
        // CREAR EMPRESA
        // =====================================================

        Empresa empresa =
                Empresa.builder()

                        .ruc(
                                ruc
                        )

                        .razonSocial(
                                request
                                        .razonSocial()
                                        .trim()
                        )

                        .direccionFiscal(
                                limpiar(
                                        request.direccionFiscal()
                                )
                        )

                        .ubigeo(
                                limpiar(
                                        request.ubigeo()
                                )
                        )

                        .departamento(
                                limpiar(
                                        request.departamento()
                                )
                        )

                        .provincia(
                                limpiar(
                                        request.provincia()
                                )
                        )

                        .distrito(
                                limpiar(
                                        request.distrito()
                                )
                        )

                        .codigoEstablecimiento(
                                "0000"
                        )

                        .estadoPlataforma(
                                EstadoEmpresa.ACTIVA
                        )

                        .build();


        empresa =
                empresaRepository
                        .saveAndFlush(
                                empresa
                        );


        // =====================================================
        // CREAR SUSCRIPCIÓN INICIAL
        // =====================================================

        LocalDateTime ahora =
                LocalDateTime.now();


        SuscripcionEmpresa suscripcion =
                SuscripcionEmpresa.builder()

                        .empresa(
                                empresa
                        )

                        .inicioEn(
                                ahora
                        )

                        .venceEn(
                                ahora.plusMonths(
                                        request.mesesIniciales()
                                )
                        )

                        .suspensionAutomatica(
                                true
                        )

                        .diasGracia(
                                0
                        )

                        .build();


        suscripcionRepository
                .saveAndFlush(
                        suscripcion
                );


        return convertir(
                empresa
        );
    }


    // =========================================================
    // SUSPENDER EMPRESA MANUALMENTE
    // =========================================================

    @Transactional
    public EmpresaAdminResponse suspender(

            Long empresaId,

            SuspenderEmpresaRequest request
    ) {

        Empresa empresa =
                buscarEmpresa(
                        empresaId
                );


        // =====================================================
        // CAMBIAR ESTADO
        // =====================================================

        empresa.setEstadoPlataforma(
                EstadoEmpresa.SUSPENDIDA
        );


        empresa.setSuspendidaEn(
                LocalDateTime.now()
        );


        // =====================================================
        // MOTIVO
        // =====================================================

        String motivo =
                limpiar(
                        request.motivo()
                );


        if (
                motivo == null
        ) {

            motivo =
                    "Suspensión administrativa.";
        }


        empresa.setMotivoSuspension(
                motivo
        );


        empresaRepository
                .saveAndFlush(
                        empresa
                );


        return convertir(
                empresa
        );
    }


    // =========================================================
    // REACTIVAR EMPRESA MANUALMENTE
    // =========================================================

    @Transactional
    public EmpresaAdminResponse reactivar(
            Long empresaId
    ) {

        Empresa empresa =
                buscarEmpresa(
                        empresaId
                );


        SuscripcionEmpresa suscripcion =
                obtenerOCrearSuscripcion(
                        empresa
                );


        // =====================================================
        // NO REACTIVAR SI SIGUE VENCIDA
        // =====================================================

        if (
                suscripcion.vencida()
        ) {

            throw new IllegalArgumentException(
                    "No se puede reactivar la empresa porque su suscripción está vencida. Primero registre una renovación."
            );
        }


        // =====================================================
        // REACTIVAR
        // =====================================================

        empresa.setEstadoPlataforma(
                EstadoEmpresa.ACTIVA
        );


        empresa.setSuspendidaEn(
                null
        );


        empresa.setMotivoSuspension(
                null
        );


        empresaRepository
                .saveAndFlush(
                        empresa
                );


        // =====================================================
        // QUITAR AVISOS ANTIGUOS DE SUSCRIPCIÓN
        // =====================================================

        anuncioRepository
                .desactivarTodosAvisosSuscripcion(

                        empresaId,

                        LocalDateTime.now()
                );


        return convertir(
                empresa
        );
    }


    // =========================================================
    // RENOVAR SUSCRIPCIÓN
    // =========================================================

    @Transactional
    public EmpresaAdminResponse renovar(

            Long empresaId,

            RenovarSuscripcionRequest request,
            String idempotencyKey
    ) {

        Empresa empresa =
                buscarEmpresa(
                        empresaId
                );

        if(idempotencyKey==null || idempotencyKey.isBlank() || idempotencyKey.length()>128)
            throw new IllegalArgumentException("Renovación exige X-Idempotency-Key de hasta 128 caracteres.");
        java.math.BigDecimal monto;
        try { monto=request.monto().setScale(2,java.math.RoundingMode.UNNECESSARY); }
        catch(ArithmeticException e){ throw new IllegalArgumentException("Monto del pago admite dos decimales sin recorte."); }
        if(monto.signum()<0 || monto.compareTo(new java.math.BigDecimal("9999999999.99"))>0)
            throw new IllegalArgumentException("Monto del pago fuera de capacidad DECIMAL(12,2).");
        String keyHash=hashOperacion(idempotencyKey);
        String requestHash=hashOperacion(request.meses()+"|"+monto.toPlainString()+"|"+java.util.Objects.toString(request.observacion(),""));
        var anterior=pagoRepository.buscarOperacionActual(empresaId,keyHash);
        if(anterior.isPresent()) {
            if(!requestHash.equals(anterior.get())) throw new IllegalArgumentException("Clave de renovación reutilizada con otro payload.");
            return convertir(empresa);
        }


        SuscripcionEmpresa suscripcion =
                obtenerOCrearSuscripcion(
                        empresa
                );


        LocalDateTime ahora =
                LocalDateTime.now();


        LocalDateTime vencimientoAnterior =
                suscripcion.getVenceEn();


        // =====================================================
        // CALCULAR INICIO DEL NUEVO PERÍODO
        // =====================================================

        LocalDateTime inicioNuevoPeriodo;


        /*
         * Si todavía está vigente:
         *
         * Ejemplo:
         *
         * vence 17/11
         * paga 20/10
         * compra 1 mes
         *
         * nuevo vencimiento = 17/12
         *
         * No pierde el tiempo que ya había pagado.
         */
        if (
                vencimientoAnterior != null
                        &&
                        vencimientoAnterior.isAfter(
                                ahora
                        )
        ) {

            inicioNuevoPeriodo =
                    vencimientoAnterior;

        } else {

            /*
             * Si ya venció:
             *
             * el nuevo período comienza
             * desde el momento de la renovación.
             */
            inicioNuevoPeriodo =
                    ahora;
        }


        LocalDateTime nuevoVencimiento =
                inicioNuevoPeriodo
                        .plusMonths(
                                request.meses()
                        );


        // =====================================================
        // ACTUALIZAR SUSCRIPCIÓN
        // =====================================================

        suscripcion.setVenceEn(
                nuevoVencimiento
        );


        suscripcionRepository
                .saveAndFlush(
                        suscripcion
                );


        // =====================================================
        // REGISTRAR HISTORIAL DE PAGO
        // =====================================================

        PagoEmpresa pago =
                PagoEmpresa.builder()

                        .idempotencyHash(keyHash)
                        .requestHash(requestHash)

                        .empresa(
                                empresa
                        )

                        .periodoInicio(
                                inicioNuevoPeriodo
                        )

                        .periodoFin(
                                nuevoVencimiento
                        )

                        .fechaVencimiento(

                                vencimientoAnterior != null

                                        ? vencimientoAnterior

                                        : ahora
                        )

                        .pagadoEn(
                                ahora
                        )

                        .monto(
                                request.monto()
                        )

                        .observacion(
                                limpiar(
                                        request.observacion()
                                )
                        )

                        .registradoPorUsuarioId(

                                securityContextService
                                        .usuarioId()
                        )

                        .build();


        pagoRepository
                .saveAndFlush(
                        pago
                );


        // =====================================================
        // DESACTIVAR AVISOS DEL PERÍODO ANTERIOR
        // =====================================================

        anuncioRepository
                .desactivarTodosAvisosSuscripcion(

                        empresaId,

                        ahora
                );


        // =====================================================
        // REACTIVACIÓN AUTOMÁTICA
        // =====================================================

        /*
         * Solamente reactivamos automáticamente
         * cuando la suspensión fue provocada
         * por vencimiento de suscripción.
         *
         * Si el SUPER_ADMIN suspendió manualmente
         * la empresa por otro motivo,
         * registrar un pago NO debe levantar
         * esa suspensión.
         */
        if (
                suspensionPorSuscripcion(
                        empresa
                )
        ) {

            empresa.setEstadoPlataforma(
                    EstadoEmpresa.ACTIVA
            );


            empresa.setSuspendidaEn(
                    null
            );


            empresa.setMotivoSuspension(
                    null
            );


            empresaRepository
                    .saveAndFlush(
                            empresa
                    );
        }


        return convertir(
                empresa
        );
    }


    // =========================================================
    // CONVERTIR EMPRESA A RESPONSE
    // =========================================================

    private EmpresaAdminResponse convertir(
            Empresa empresa
    ) {

        SuscripcionEmpresa suscripcion =
                obtenerOCrearSuscripcion(
                        empresa
                );


        long usuarios =
                usuarioRepository
                        .countByEmpresaId(
                                empresa.getId()
                        );


        long diasRestantes =
                ChronoUnit.DAYS.between(

                        LocalDate.now(),

                        suscripcion
                                .getVenceEn()
                                .toLocalDate()
                );


        return new EmpresaAdminResponse(

                empresa.getId(),

                empresa.getTenantId(),

                empresa.getRuc(),

                empresa.getRazonSocial(),

                empresa.getEstadoPlataforma(),

                empresa.tieneConfiguracionSunat(),

                usuarios,

                suscripcion.getInicioEn(),

                suscripcion.getVenceEn(),

                diasRestantes,

                suscripcion.isSuspensionAutomatica(),

                suscripcion.getDiasGracia(),

                empresa.getMotivoSuspension(),

                empresa.getCreatedAt()
        );
    }


    // =========================================================
    // OBTENER O CREAR SUSCRIPCIÓN
    // =========================================================

    private SuscripcionEmpresa
    obtenerOCrearSuscripcion(
            Empresa empresa
    ) {

        return suscripcionRepository
                .findByEmpresaId(
                        empresa.getId()
                )
                .orElseGet(
                        () -> {

                            LocalDateTime ahora =
                                    LocalDateTime.now();


                            SuscripcionEmpresa nueva =
                                    SuscripcionEmpresa
                                            .builder()

                                            .empresa(
                                                    empresa
                                            )

                                            .inicioEn(
                                                    ahora
                                            )

                                            .venceEn(
                                                    ahora.plusMonths(
                                                            1
                                                    )
                                            )

                                            .suspensionAutomatica(
                                                    true
                                            )

                                            .diasGracia(
                                                    0
                                            )

                                            .build();


                            return suscripcionRepository
                                    .saveAndFlush(
                                            nueva
                                    );
                        }
                );
    }


    // =========================================================
    // ¿LA SUSPENSIÓN FUE GENERADA POR VENCIMIENTO?
    // =========================================================

    private boolean suspensionPorSuscripcion(
            Empresa empresa
    ) {

        if (
                empresa.getEstadoPlataforma()
                        !=
                        EstadoEmpresa.SUSPENDIDA
        ) {

            return false;
        }


        String motivo =
                empresa.getMotivoSuspension();


        if (
                motivo == null
                        ||
                        motivo.isBlank()
        ) {

            return false;
        }


        /*
         * SuscripcionAutomaticaService actualmente
         * genera un motivo que comienza con:
         *
         * "Suscripción vencida."
         *
         * Por eso podemos distinguirlo de una
         * suspensión administrativa.
         */
        return motivo.startsWith(
                "Suscripción vencida."
        );
    }


    // =========================================================
    // BUSCAR EMPRESA
    // =========================================================

    private Empresa buscarEmpresa(
            Long id
    ) {

        return empresaRepository
                        .bloquearPorId(
                        id
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Empresa no encontrada."
                                )
                );
    }


    // =========================================================
    // NORMALIZAR STRING
    // =========================================================

    private static String hashOperacion(String texto){
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e){ throw new IllegalStateException("SHA-256 no disponible."); }
    }

    private String limpiar(
            String valor
    ) {

        if (
                valor == null
        ) {

            return null;
        }


        String limpio =
                valor.trim();


        return limpio.isBlank()

                ? null

                : limpio;
    }
}
