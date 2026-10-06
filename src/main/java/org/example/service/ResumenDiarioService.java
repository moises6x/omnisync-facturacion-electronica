
package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ResumenDiarioResponse;
import org.example.dto.BajaBoletaResponse;
import org.example.auth.service.AuditoriaService;
import org.example.model.Comprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;
import org.example.model.EstadoResumenDiario;
import org.example.model.ResumenDiario;
import org.example.model.ResumenDiarioDetalle;
import org.example.model.RespuestaSunatRecibida;
import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;
import org.example.repository.ResumenDiarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * =============================================================
 * RESUMEN DIARIO SERVICE
 * =============================================================
 *
 * Orquesta todo el flujo SUNAT de Boletas y NC de Boletas:
 *
 * PENDIENTE_RESUMEN
 *        ↓
 * EN_RESUMEN
 *        ↓
 * crear SummaryDocuments
 *        ↓
 * firmar + validar XSD
 *        ↓
 * sendSummary
 *        ↓
 * ticket
 *        ↓
 * getStatus
 *        ↓
 * CDR
 *        ↓
 * ACEPTADO / RECHAZADO
 *
 * El diseño persiste cada etapa para sobrevivir reinicios del backend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumenDiarioService {

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    // =========================================================
    // REPOSITORIES
    // =========================================================
    private final ResumenDiarioRepository resumenRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final EmpresaRepository empresaRepository;
    // =========================================================
    // SERVICIOS
    // =========================================================
    private final SecuenciaResumenDiarioService secuenciaService;
    private final ResumenDiarioXmlService xmlService;
    private final XmlSignerService xmlSignerService;
    private final UblValidatorService ublValidatorService;
    private final SunatDirectSoapClient sunatClient;
    private final CdrParserService cdrParserService;
    private final CryptoService cryptoService;
    private final SunatPlazoService sunatPlazoService;
    @Autowired(required = false)
    private AuditoriaService auditoriaService;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final RespuestaSunatRecibidaService respuestaSunatRecibidaService;
    // =========================================================
    // CONFIGURACIÓN
    // =========================================================
    @Value("${sunat.resumen-diario.max-lineas:500}")
    private int maxLineasConfigurado;
    // =========================================================
    // API - GENERAR Y ENVIAR
    // =========================================================
    /**
     * Genera un único bloque RC para una empresa/fecha.
     *
     * Si existen más de 500 pendientes, una siguiente invocación generará
     * otro correlativo con las líneas restantes.
     */
    public ResumenDiarioResponse generarYEnviar(
            String ruc,
            LocalDate fechaDocumentos
    ) {

        validarRucYFecha(
                ruc,
                fechaDocumentos
        );

        Long resumenId =
                crearResumenTransaccional(
                        ruc,
                        fechaDocumentos
                );

        if (resumenId == null) {
            throw new IllegalArgumentException(
                    "No existen boletas o notas de boleta PENDIENTE_RESUMEN para la fecha indicada."
            );
        }

        prepararYEnviar(
                ruc,
                resumenId
        );

        return obtener(
                ruc,
                resumenId
        );
    }

    /**
     * Variante utilizada por el scheduler.
     *
     * Devuelve false cuando ya no quedan documentos para esa fecha.
     */
    public boolean generarYEnviarAutomatico(
            String ruc,
            LocalDate fechaDocumentos
    ) {

        validarRucYFecha(
                ruc,
                fechaDocumentos
        );

        Long resumenId =
                crearResumenTransaccional(
                        ruc,
                        fechaDocumentos
                );

        if (resumenId == null) {
            return false;
        }

        prepararYEnviar(
                ruc,
                resumenId
        );

        return true;
    }

    // =========================================================
    // CREAR CABECERA + DETALLES
    // =========================================================

    /**
     * Esta operación sí necesita ser atómica:
     *
     * - toma los comprobantes
     * - cambia PENDIENTE_RESUMEN -> EN_RESUMEN
     * - obtiene correlativo
     * - crea cabecera y snapshots
     *
     * Si cualquier paso falla, todo se revierte.
     */
    private Long crearResumenTransaccional(
            String ruc,
            LocalDate fechaDocumentos
    ) {

        return enTransaccion(
                () -> {

                    int maxLineas =
                            Math.max(
                                    1,
                                    Math.min(
                                            500,
                                            maxLineasConfigurado
                                    )
                            );

                    LocalDateTime desde =
                            fechaDocumentos.atStartOfDay();

                    LocalDateTime hastaExclusivo =
                            fechaDocumentos
                                    .plusDays(1)
                                    .atStartOfDay();

                    List<Comprobante> candidatos =
                            comprobanteRepository
                                    .buscarPendientesResumenDiarioLimitado(
                                            ruc,
                                            desde,
                                            hastaExclusivo,
                                            PageRequest.of(
                                                    0,
                                                    maxLineas
                                            )
                                    );

                    if (candidatos.isEmpty()) {
                        return null;
                    }

                    List<Comprobante> tomados =
                            new ArrayList<>();

                    /*
                     * UPDATE atómico por documento.
                     * Si otro worker ya lo tomó, el UPDATE devuelve 0
                     * y esa fila simplemente no entra en este resumen.
                     */
                    for (Comprobante candidato : candidatos) {

                        int tomado =
                                comprobanteRepository
                                        .marcarEnResumen(
                                                candidato.getId(),
                                                ruc
                                        );

                        if (tomado == 1) {
                            tomados.add(candidato);
                        }
                    }

                    if (tomados.isEmpty()) {
                        return null;
                    }

                    LocalDate fechaGeneracion = LocalDate.now(clock);

                    int correlativo =
                            secuenciaService.siguiente(
                                    ruc,
                                    fechaGeneracion,
                                    fechaDocumentos
                            );

                    String identificador =
                            "RC-"
                                    + fechaGeneracion.format(YYYYMMDD)
                                    + "-"
                                    + correlativo;

                    String nombreXml =
                            ruc
                                    + "-"
                                    + identificador
                                    + ".xml";

                    ResumenDiario resumen =
                            ResumenDiario.builder()

                                    .rucEmisor(ruc)

                                    .fechaDocumentos(
                                            fechaDocumentos
                                    )

                                    .fechaGeneracion(
                                            fechaGeneracion
                                    )

                                    .correlativo(
                                            correlativo
                                    )

                                    .identificador(
                                            identificador
                                    )

                                    .nombreArchivoXml(
                                            nombreXml
                                    )

                                    .cantidadDocumentos(
                                            tomados.size()
                                    )

                                    .estado(
                                            EstadoResumenDiario.CREADO
                                    )

                                    .mensajeSunat(
                                            "Resumen creado localmente. Pendiente de sendSummary."
                                    )

                                    .build();

                    int linea = 1;

                    for (Comprobante c : tomados) {

                        validarComprobanteParaResumen(
                                c,
                                fechaDocumentos,
                                ruc
                        );

                        ResumenDiarioDetalle detalle =
                                ResumenDiarioDetalle.builder()

                                        .comprobanteId(
                                                c.getId()
                                        )

                                        .numeroLinea(
                                                linea++
                                        )

                                        .tipoComprobante(
                                                c.getTipoComprobante()
                                        )

                                        .serie(
                                                c.getSerie()
                                        )

                                        .numero(
                                                c.getNumero()
                                        )

                                        .tipoDocCliente(
                                                c.getTipoDocCliente()
                                        )

                                        .numDocCliente(
                                                c.getNumDocCliente()
                                        )

                                        .opGravada(
                                                ceroSiNull(
                                                        c.getOpGravada()
                                                )
                                        )

                                        .opExonerada(
                                                ceroSiNull(
                                                        c.getOpExonerada()
                                                )
                                        )

                                        .opInafecta(
                                                ceroSiNull(
                                                        c.getOpInafecta()
                                                )
                                        )

                                        .igv(
                                                ceroSiNull(
                                                        c.getIgv()
                                                )
                                        )

                                        .montoTotal(
                                                ceroSiNull(
                                                        c.getMontoTotal()
                                                )
                                        )

                                        .moneda(
                                                c.getMoneda() == null
                                                        ? "PEN"
                                                        : c.getMoneda()
                                        )

                                        .tipoComprobanteAfectado(
                                                c.getTipoComprobanteAfectado()
                                        )

                                        .serieComprobanteAfectado(
                                                c.getSerieComprobanteAfectado()
                                        )

                                        .numeroComprobanteAfectado(
                                                c.getNumeroComprobanteAfectado()
                                        )

                                        .codigoEstado(
                                                c.getCodigoEstadoResumen() == null
                                                        ? "1"
                                                        : c.getCodigoEstadoResumen()
                                        )

                                        .build();

                        resumen.addDetalle(
                                detalle
                        );
                    }

                    ResumenDiario guardado =
                            resumenRepository.saveAndFlush(
                                    resumen
                            );

                    return guardado.getId();
                }
        );
    }

    // =========================================================
    // PREPARAR XML + SENDSUMMARY
    // =========================================================

    /**
     * Construye, firma y valida el XML antes de tocar la red.
     *
     * Sólo después de tener un archivo local válido se marca ENVIANDO y se
     * ejecuta sendSummary.
     */
    public void prepararYEnviar(
            String ruc,
            Long resumenId
    ) {

        validarRuc(
                ruc
        );

        if (resumenId == null) {
            throw new IllegalArgumentException(
                    "ID de Resumen Diario requerido."
            );
        }

        ResumenDiario resumen;
        Empresa empresa;
        byte[] xmlFirmado;
        String hash;
        String usuarioSol;
        String claveSol;

        try {

            resumen =
                    cargarCompleto(
                            ruc,
                            resumenId
                    );

            if (!preparable(resumen)) {
                return;
            }

            sunatPlazoService.validarResumenDiario(resumen.getFechaDocumentos());

            empresa =
                    empresaListaParaSunat(
                            ruc
                    );

            byte[] xmlRaw =
                    xmlService.construir(
                            resumen,
                            empresa
                    );

            byte[] pfxPlano =
                    cryptoService.descifrarPfx(
                            ruc,
                            empresa.getPfxBytes()
                    );

            String pfxPasswordPlano =
                    cryptoService.descifrarPfxPassword(
                            ruc,
                            empresa.getPfxPassword()
                    );

            try {

                XmlSignerService.ResultadoFirma firma =
                        xmlSignerService.firmarXml(
                                xmlRaw,
                                pfxPlano,
                                pfxPasswordPlano
                        );

                xmlFirmado =
                        firma.xmlFirmado();

                hash =
                        firma.hashCpe();

            } finally {

                if (pfxPlano != null) {
                    java.util.Arrays.fill(
                            pfxPlano,
                            (byte) 0
                    );
                }
            }

            ublValidatorService.validarResumenDiario(
                    xmlFirmado
            );

            usuarioSol =
                    cryptoService.descifrarUsuarioSol(
                            ruc,
                            empresa.getUsuarioSol()
                    );

            claveSol =
                    cryptoService.descifrarClaveSol(
                            ruc,
                            empresa.getClaveSol()
                    );

        } catch (Exception e) {
            marcarErrorPreparacion(ruc, resumenId, e);
            return;
        }

        // Adquirir el envío y guardar su evidencia en la misma transacción corta.
        // Una preparación perdedora no puede reemplazar el archivo del ganador.
        final byte[] xmlFinal = xmlFirmado;
        final String hashFinal = hash;
        boolean tomado = enTransaccion(
                    () -> {

                        ResumenDiario actual =
                                resumenRepository
                                        .bloquearPorIdYRuc(
                                                resumenId,
                                                ruc
                                        )
                                        .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                        "Resumen Diario no encontrado."
                                                )
                                        );

                        if (!preparable(actual)) {
                            return false;
                        }
                        actual.setEstado(EstadoResumenDiario.ENVIANDO);
                        actual.setIntentosEnvio(actual.getIntentosEnvio() + 1);
                        actual.setXmlFirmadoBytes(
                                xmlFinal
                        );

                        actual.setHashCpe(
                                hashFinal
                        );

                        actual.setMensajeSunat(
                                "XML SummaryDocuments firmado y validado. Envío adquirido."
                        );

                        resumenRepository.save(
                                actual
                        );

                        return true;
                    }
            );
        if (!tomado) {
            return;
        }

        try {

            String nombreXml =
                    resumen.getNombreArchivoXml();

            String nombreBase =
                    nombreXml.endsWith(".xml")
                            ? nombreXml.substring(
                            0,
                            nombreXml.length() - 4
                    )
                            : nombreXml;

            byte[] zip =
                    comprimir(
                            nombreXml,
                            xmlFirmado
                    );

            String ticket =
                    sunatClient.enviarResumenDirecto(
                            ruc,
                            usuarioSol,
                            claveSol,
                            nombreBase + ".zip",
                            zip
                    );

            if (!registrarTicket(ruc, resumenId, ticket)) {
                return;
            }

            /*
             * Primera consulta inmediata.
             * Si SUNAT responde 98, el scheduler continuará después.
             */
            consultarTicketInterno(
                    ruc,
                    resumenId
            );

        } catch (SunatDirectSoapClient.SunatSoapFaultException e) {

            marcarErrorEnvioIncierto(
                    ruc,
                    resumenId,
                    e
            );

        } catch (
                SunatDirectSoapClient.SunatTransportException
                |
                SunatDirectSoapClient.SunatProtocolException e
        ) {

            marcarErrorEnvioIncierto(
                    ruc,
                    resumenId,
                    e
            );

        } catch (Exception e) {

            /*
             * Una excepción inesperada después de marcar ENVIANDO también se
             * trata como incierta. Es más seguro bloquear que duplicar un RC.
             */
            marcarErrorEnvioIncierto(
                    ruc,
                    resumenId,
                    e
            );
        }
    }

    // =========================================================
    // GETSTATUS
    // =========================================================

    private boolean registrarTicket(String ruc, Long resumenId, String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalStateException("SUNAT no entregó un ticket válido.");
        }
        return enTransaccion(() -> {
            // Orden único: empresa antes de cabecera. Nunca mantener estos locks durante SOAP.
            empresaRepository.bloquearPorRuc(ruc).orElseThrow();
            ResumenDiario actual = resumenRepository.bloquearPorIdYRuc(resumenId, ruc).orElseThrow();
            if (actual.getTicketSunat() != null) {
                throw new IllegalStateException("El RC ya tiene un ticket asociado; no se reasigna.");
            }
            if (actual.getEstado() != EstadoResumenDiario.ENVIANDO
                    && actual.getEstado() != EstadoResumenDiario.ERROR_ENVIO_INCIERTO) {
                return false;
            }
            if (!resumenRepository.buscarOtrosConTicketActual(ruc, ticket, resumenId).isEmpty()) {
                log.warn("RC_TICKET_ASSOCIATION_CONFLICT resumenId={} ruc={} ticket={}",
                        resumenId, ruc, datoTecnicoLog(ticket));
                throw new IllegalStateException("El ticket ya pertenece a otro RC de la empresa; requiere revisión.");
            }
            actual.setTicketSunat(ticket);
            actual.setEstado(EstadoResumenDiario.TICKET_GENERADO);
            actual.setEnviadoEn(LocalDateTime.now(clock));
            actual.setCodigoRespuestaSunat(null);
            actual.setMensajeSunat("SUNAT recibió sendSummary. Ticket: " + ticket);
            resumenRepository.save(actual);
            return true;
        });
    }

    public ResumenDiarioResponse consultarSunat(
            String ruc,
            Long resumenId
    ) {

        consultarTicketInterno(
                ruc,
                resumenId
        );

        return obtener(
                ruc,
                resumenId
        );
    }

    /**
     * getStatus es seguro de repetir porque sólo consulta un ticket existente.
     */
    public void consultarTicketInterno(
            String ruc,
            Long resumenId
    ) {

        ResumenDiario resumen =
                resumenRepository
                        .findByIdAndRucEmisor(
                                resumenId,
                                ruc
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Resumen Diario no encontrado."
                                )
                        );

        if (
                resumen.getEstado() == EstadoResumenDiario.ACEPTADO
                        ||
                        resumen.getEstado() == EstadoResumenDiario.ACEPTADO_CON_OBSERVACION
                        ||
                        resumen.getEstado() == EstadoResumenDiario.RECHAZADO
                        ||
                        resumen.getEstado() == EstadoResumenDiario.LIBERADO
        ) {
            return;
        }

        String ticket =
                resumen.getTicketSunat();

        if (ticket == null || ticket.isBlank()) {
            throw new IllegalStateException(
                    "El Resumen Diario todavía no tiene ticket SUNAT."
            );
        }

        // Un histórico ambiguo no se resuelve eligiendo uno de los RC ni por ReferenceID.
        if (!ticketCorresponde(resumen, ticket)) {
            return;
        }

        try {
            Empresa empresa =
                    empresaListaParaSunat(ruc);
            String usuarioSol =
                    cryptoService.descifrarUsuarioSol(ruc,empresa.getUsuarioSol());
            String claveSol =
                    cryptoService.descifrarClaveSol(ruc,empresa.getClaveSol());

            SunatDirectSoapClient.EstadoTicketSunat estadoTicket =
                    sunatClient.consultarTicket(
                            ruc,
                            usuarioSol,
                            claveSol,
                            ticket
                    );

            respuestaSunatRecibidaService.conservar(ruc, RespuestaSunatRecibida.Flujo.RC, resumenId, ticket, estadoTicket.contentZip());

            if (estadoTicket.enProceso()) {

                registrarConsultaEnProceso(
                        ruc,
                        resumenId,
                        ticket,
                        estadoTicket.statusCode()
                );

                return;
            }

            byte[] cdrZip =
                    estadoTicket.contentZip();

            if (cdrZip == null || cdrZip.length == 0) {

                if (estadoTicket.finalizadoConErrores()) {

                    marcarRechazoSinCdr(
                            ruc,
                            resumenId,
                            ticket,
                            estadoTicket.statusCode()
                    );

                } else {

                    marcarErrorConsulta(
                            ruc,
                            resumenId,
                            ticket,
                            "SUNAT devolvió estado "
                                    + estadoTicket.statusCode()
                                    + " sin CDR."
                    );
                }

                return;
            }

            CdrParserService.ResultadoCdr cdr =
                    cdrParserService.procesar(
                            cdrZip
                    );

            finalizarConCdr(
                    ruc,
                    resumenId,
                    ticket,
                    cdrZip,
                    cdr
            );

        } catch (
                SunatDirectSoapClient.SunatTransportException
                |
                SunatDirectSoapClient.SunatProtocolException
                |
                SunatDirectSoapClient.SunatSoapFaultException e
        ) {

            marcarErrorConsulta(
                    ruc,
                    resumenId,
                    ticket,
                    mensajeSeguro(e)
            );

        } catch (Exception e) {

            marcarErrorConsulta(
                    ruc,
                    resumenId,
                    ticket,
                    mensajeSeguro(e)
            );
        }
    }

    // =========================================================
    // FINALIZAR CON CDR
    // =========================================================

    private void finalizarConCdr(
            String ruc,
            Long resumenId,
            String ticket,
            byte[] cdrZip,
            CdrParserService.ResultadoCdr cdr
    ) {

        boolean aplicado = enTransaccion(
                () -> {

                    ResumenDiario cabecera = resumenRepository.bloquearPorIdYRuc(resumenId, ruc).orElseThrow();
                    if (!consultable(cabecera) || !ticketCorresponde(cabecera, ticket)) {
                        return false;
                    }
                    ResumenDiario resumen =
                            resumenRepository
                                    .buscarCompleto(
                                            resumenId,
                                            ruc
                                    )
                                    .orElseThrow(
                                            () -> new IllegalArgumentException(
                                                    "Resumen Diario no encontrado."
                                            )
                                    );

                    String mensaje =
                            cdr.description();

                    if (
                            cdr.observaciones() != null
                                    &&
                                    !cdr.observaciones().isEmpty()
                    ) {
                        mensaje =
                                mensaje
                                        + " | Observaciones: "
                                        + String.join(
                                        " | ",
                                        cdr.observaciones()
                                );
                    }

                    boolean conObservaciones =
                            cdr.aceptado()
                                    &&
                                    cdr.observaciones() != null
                                    &&
                                    !cdr.observaciones().isEmpty();

                    EstadoResumenDiario estadoResumen =
                            cdr.aceptado()
                                    ?
                                    (
                                            conObservaciones
                                                    ? EstadoResumenDiario.ACEPTADO_CON_OBSERVACION
                                                    : EstadoResumenDiario.ACEPTADO
                                    )
                                    : EstadoResumenDiario.RECHAZADO;

                    resumen.setCdrZipBytes(
                            cdrZip
                    );

                    resumen.setCdrXmlBytes(
                            cdr.xml()
                    );

                    resumen.setCodigoRespuestaSunat(
                            cdr.responseCode()
                    );

                    resumen.setMensajeSunat(
                            org.example.util.DiagnosticoSunat.acotar(mensaje)
                    );

                    resumen.setEstado(
                            estadoResumen
                    );

                    resumen.setUltimaConsultaEn(
                            LocalDateTime.now(clock)
                    );

                    resumen.setIntentosConsulta(
                            resumen.getIntentosConsulta() + 1
                    );

                    resumen.setFinalizadoEn(
                            LocalDateTime.now(clock)
                    );

                    resumenRepository.save(
                            resumen
                    );

                    /*
                     * Solamente una CDR aceptada convierte las boletas/notas
                     * EN_RESUMEN a ACEPTADO.
                     *
                     * Si la CDR fue rechazada dejamos EN_RESUMEN para que el
                     * operador inspeccione el rechazo y decida liberar/corregir.
                     */
                    if (cdr.aceptado()) {

                        comprobanteRepository.bloquearOriginalesDeNotas(ruc, resumen.getDetalles().stream()
                                .map(ResumenDiarioDetalle::getComprobanteId).toList());

                        List<Long> documentosNormales =
                                resumen
                                        .getDetalles()
                                        .stream()
                                        .filter(d -> !"3".equals(d.getCodigoEstado()))
                                        .map(ResumenDiarioDetalle::getComprobanteId)
                                        .toList();

                        List<Long> documentosAnulados =
                                resumen
                                        .getDetalles()
                                        .stream()
                                        .filter(d -> "3".equals(d.getCodigoEstado()))
                                        .map(ResumenDiarioDetalle::getComprobanteId)
                                        .toList();

                        if (!documentosNormales.isEmpty()) {

                            EstadoComprobante estadoComprobante =
                                    conObservaciones
                                            ? EstadoComprobante.ACEPTADO_CON_OBSERVACION
                                            : EstadoComprobante.ACEPTADO;

                            int actualizados=comprobanteRepository.actualizarEstadoDesdeResumen(
                                    ruc,
                                    documentosNormales,
                                    estadoComprobante,
                                    cdr.responseCode(),
                                    "Resumen "
                                            + resumen.getIdentificador()
                                            + ": "
                                             + org.example.util.DiagnosticoSunat.acotar(mensaje)
                            );
                            if(actualizados!=documentosNormales.size())throw new IllegalStateException("RC: documentos ausentes o con estado concurrente; no se confirma parcialmente.");
                        }

                        if (!documentosAnulados.isEmpty()) {
                            int actualizados=comprobanteRepository.actualizarEstadoDesdeResumen(
                                    ruc,
                                    documentosAnulados,
                                    EstadoComprobante.ANULADO,
                                    cdr.responseCode(),
                                    "Baja de boleta aceptada en Resumen "
                                            + resumen.getIdentificador()
                                            + ": "
                                             + org.example.util.DiagnosticoSunat.acotar(mensaje)
                            );
                            if(actualizados!=documentosAnulados.size())throw new IllegalStateException("RC: bajas ausentes o con estado concurrente; no se confirma parcialmente.");
                        }
                    }

                    return true;
                }
        );
        if (aplicado) {
            // Evidencia íntegra en ZIP/XML; la diferencia textual sólo es observabilidad.
            ResumenDiario confirmado = resumenRepository.findByIdAndRucEmisor(resumenId, ruc).orElseThrow();
            if (!confirmado.getIdentificador().equals(cdr.referenceId())
                    || !confirmado.getIdentificador().equals(cdr.documentReferenceId())) {
                log.warn("CDR_REFERENCE_MISMATCH resumenId={} rc={} ticket={} referenceId={} documentReferenceId={}",
                        resumenId, confirmado.getIdentificador(), datoTecnicoLog(ticket),
                        datoTecnicoLog(cdr.referenceId()), datoTecnicoLog(cdr.documentReferenceId()));
            }
            if (auditoriaService != null) {
                org.example.util.ObservabilidadFiscal.auditar(auditoriaService, ruc, "CDR_RESUMEN_DIARIO", confirmado.getEstado().name(),
                        confirmado.getIdentificador() + " / codigo=" + cdr.responseCode());
            }
            org.example.util.ObservabilidadFiscal.evento(ruc, resumenId, confirmado.getIdentificador(), ticket,
                    confirmado.getEstado().name(), cdr.responseCode());
        }
    }

    // =========================================================
    // REINTENTAR / REPROCESAR
    // =========================================================

    /**
     * Permite reintentar únicamente estados cuyo reenvío sabemos que no es
     * ambiguo:
     *
     * - ERROR_ENVIO/ERROR_VALIDACION sin intentos previos: fallo exclusivamente local.
     * - ERROR_VALIDACION: todavía no hubo red; volvemos a construir el XML.
     *
     * ERROR_ENVIO_INCIERTO queda bloqueado deliberadamente.
     */
    public ResumenDiarioResponse reintentarEnvio(
            String ruc,
            Long resumenId
    ) {

        EstadoResumenDiario estado =
                enTransaccion(
                        () -> {

                            resumenRepository.bloquearPorIdYRuc(resumenId, ruc).orElseThrow();
                            ResumenDiario resumen =
                                    resumenRepository
                                            .findByIdAndRucEmisor(
                                                    resumenId,
                                                    ruc
                                            )
                                            .orElseThrow(
                                                    () -> new IllegalArgumentException(
                                                            "Resumen Diario no encontrado."
                                                    )
                                            );

                            EstadoResumenDiario actual =
                                    resumen.getEstado();

                            if (
                                    actual != EstadoResumenDiario.ERROR_ENVIO
                                            &&
                                            actual != EstadoResumenDiario.ERROR_VALIDACION
                            ) {
                                throw new IllegalStateException(
                                        "El resumen no se encuentra en un estado seguro para reintentar sendSummary."
                                );
                            }

                            if(resumen.getIntentosEnvio()!=0 || resumen.getTicketSunat()!=null)
                                throw new IllegalStateException("No se acredita fallo anterior a red; requiere conciliación, no reenvío.");
                            if (actual == EstadoResumenDiario.ERROR_VALIDACION) {
                                resumen.setEstado(
                                        EstadoResumenDiario.CREADO
                                );

                                resumen.setMensajeSunat(
                                        "Reprocesamiento manual solicitado."
                                );

                                resumenRepository.save(
                                        resumen
                                );
                            }

                            return actual;
                        }
                );

        if (
                estado == EstadoResumenDiario.ERROR_ENVIO
                        ||
                        estado == EstadoResumenDiario.ERROR_VALIDACION
        ) {
            prepararYEnviar(
                    ruc,
                    resumenId
            );
        }

        return obtener(
                ruc,
                resumenId
        );
    }

    /**
     * Devuelve los documentos de un resumen fallido/rechazado a
     * PENDIENTE_RESUMEN para crear un nuevo RC después de corregir la causa.
     *
     * NO se permite en estados ambiguos o aceptados.
     */
    public ResumenDiarioResponse liberarDocumentos(
            String ruc,
            Long resumenId
    ) {

        enTransaccion(
                () -> {

                    resumenRepository.bloquearPorIdYRuc(resumenId, ruc).orElseThrow(
                            () -> new IllegalArgumentException("Resumen Diario no encontrado."));
                    ResumenDiario resumen =
                            resumenRepository
                                    .buscarCompleto(
                                            resumenId,
                                            ruc
                                    )
                                    .orElseThrow(
                                            () -> new IllegalArgumentException(
                                                    "Resumen Diario no encontrado."
                                            )
                                    );

                    Set<EstadoResumenDiario> permitidos =
                            EnumSet.of(
                                    EstadoResumenDiario.ERROR_VALIDACION,
                                    EstadoResumenDiario.ERROR_ENVIO,
                                    EstadoResumenDiario.RECHAZADO
                            );

                    if (!permitidos.contains(resumen.getEstado())) {
                        throw new IllegalStateException(
                                "No es seguro liberar los documentos desde el estado "
                                        + resumen.getEstado()
                                        + "."
                        );
                    }

                    List<Long> ids =
                            resumen
                                    .getDetalles()
                                    .stream()
                                    .map(
                                            ResumenDiarioDetalle::getComprobanteId
                                    )
                                    .toList();

                    if(resumen.getEstado()==EstadoResumenDiario.ERROR_ENVIO && resumen.getIntentosEnvio()!=0)
                        throw new IllegalStateException("ERROR_ENVIO histórico con intento registrado: no se acredita liberación segura; requiere conciliación.");
                    int liberados = comprobanteRepository
                            .liberarDocumentosDelResumen(
                                    ruc,
                                    resumenId,
                                    ids
                            );
                    if (liberados != ids.size()) {
                        throw new IllegalStateException("Los documentos del RC cambiaron de estado o propietario; requiere revisión.");
                    }

                    resumen.setEstado(
                            EstadoResumenDiario.LIBERADO
                    );

                    resumen.setFinalizadoEn(
                            LocalDateTime.now(clock)
                    );

                    resumen.setMensajeSunat(
                            agregarMensaje(
                                    resumen.getMensajeSunat(),
                                    "Documentos liberados manualmente para generar un nuevo Resumen Diario."
                            )
                    );

                    resumenRepository.save(
                            resumen
                    );

                    return null;
                }
        );

        return obtener(
                ruc,
                resumenId
        );
    }

    // =========================================================
    // BAJA DE BOLETA YA INFORMADA Y NO OTORGADA
    // =========================================================

    /**
     * Prepara una boleta ya informada para que el siguiente RC la
     * declare con ConditionCode=3 (Anulado).
     *
     * SUNAT reserva este estado para documentos INFORMADOS que NO
     * fueron otorgados al adquirente/usuario. Si el documento fue
     * otorgado, la anulación/corrección debe sustentarse mediante NC.
     */
    public BajaBoletaResponse solicitarBajaBoletaNoOtorgada(
            String ruc,
            Long comprobanteId
    ) {

        validarRuc(ruc);

        if (comprobanteId == null) {
            throw new IllegalArgumentException("ID de comprobante requerido.");
        }

        Comprobante comprobante =
                comprobanteRepository
                        .findByIdAndRucEmisor(comprobanteId, ruc)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Comprobante no encontrado."
                                )
                        );

        if (!"03".equals(comprobante.getTipoComprobante())) {
            throw new IllegalArgumentException(
                    "Este flujo de baja mediante Resumen Diario sólo aplica a Boleta 03."
            );
        }

        if (comprobante.getEstadoSfs() != EstadoComprobante.ACEPTADO
                && comprobante.getEstadoSfs() != EstadoComprobante.ACEPTADO_CON_OBSERVACION) {
            throw new IllegalStateException(
                    "La boleta debe haber sido informada y aceptada antes de usar ConditionCode=3."
            );
        }

        sunatPlazoService.validarResumenDiario(
                comprobante.getFechaEmision().toLocalDate()
        );

        int actualizado =
                comprobanteRepository.prepararBajaBoletaEnResumen(
                        comprobanteId,
                        ruc
                );

        if (actualizado != 1) {
            throw new IllegalStateException(
                    "La boleta cambió de estado mientras se preparaba la baja. Vuelva a consultar."
            );
        }

        return new BajaBoletaResponse(
                comprobanteId,
                EstadoComprobante.PENDIENTE_RESUMEN.name(),
                "Boleta preparada para baja. El siguiente Resumen Diario la enviará con ConditionCode=3."
        );
    }


    // =========================================================
    // CONSULTAS API
    // =========================================================

    public ResumenDiarioResponse obtener(
            String ruc,
            Long resumenId
    ) {

        ResumenDiario resumen =
                resumenRepository
                        .findByIdAndRucEmisor(
                                resumenId,
                                ruc
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Resumen Diario no encontrado."
                                )
                        );

        return convertir(
                resumen
        );
    }

    public List<ResumenDiarioResponse> listar(
            String ruc,
            LocalDate fecha
    ) {

        List<ResumenDiario> res =
                fecha == null
                        ? resumenRepository
                        .findTop100ByRucEmisorOrderByCreatedAtDesc(
                                ruc
                        )
                        : resumenRepository
                        .findByRucEmisorAndFechaDocumentosOrderByCorrelativoDesc(
                                ruc,
                                fecha
                        );

        return res
                .stream()
                .map(this::convertir)
                .toList();
    }

    // =========================================================
    // RECUPERACIÓN PARA SCHEDULER
    // =========================================================

    public void recuperarResumenesCreados(
            int limite
    ) {

        List<ResumenDiario> creados =
                resumenRepository
                        .buscarCreadosPendientes(
                                PageRequest.of(
                                        0,
                                        Math.max(1, limite)
                                )
                        );

        for (ResumenDiario resumen : creados) {

            try {
                prepararYEnviar(
                        resumen.getRucEmisor(),
                        resumen.getId()
                );

            } catch (Exception e) {
                log.error(
                        "No se pudo recuperar Resumen Diario {}: {}",
                        resumen.getId(),
                        mensajeSeguro(e)
                );
            }
        }
    }

    public void consultarTicketsPendientes(
            int limite
    ) {

        List<ResumenDiario> pendientes =
                resumenRepository
                        .buscarPendientesConsulta(
                                EnumSet.of(
                                        EstadoResumenDiario.TICKET_GENERADO,
                                        EstadoResumenDiario.PROCESANDO,
                                        EstadoResumenDiario.ERROR_CONSULTA
                                ),
                                PageRequest.of(
                                        0,
                                        Math.max(1, limite)
                                )
                        );

        for (ResumenDiario resumen : pendientes) {

            try {
                consultarTicketInterno(
                        resumen.getRucEmisor(),
                        resumen.getId()
                );

            } catch (Exception e) {
                log.error(
                        "Error consultando ticket del Resumen Diario {}: {}",
                        resumen.getId(),
                        mensajeSeguro(e)
                );
            }
        }
    }

    public int marcarEnviosVencidosComoInciertos(
            int minutos
    ) {

        LocalDateTime corte =
                LocalDateTime
                        .now(clock)
                        .minusMinutes(
                                Math.max(1, minutos)
                        );

        return resumenRepository
                .marcarEnviosVencidosComoInciertos(
                        corte
                );
    }

    // =========================================================
    // ESTADOS / ERRORES
    // =========================================================

    private void registrarConsultaEnProceso(
            String ruc,
            Long resumenId,
            String ticket,
            String statusCode
    ) {

        enTransaccion(
                () -> {

                    ResumenDiario resumen =
                            resumenRepository
                                    .bloquearPorIdYRuc(
                                            resumenId,
                                            ruc
                                    )
                                    .orElseThrow();

                    if (!consultable(resumen) || !ticketCorresponde(resumen, ticket)) {
                        return null;
                    }
                    resumen.setEstado(
                            EstadoResumenDiario.PROCESANDO
                    );

                    resumen.setCodigoRespuestaSunat(
                            statusCode
                    );

                    resumen.setMensajeSunat(
                            "SUNAT continúa procesando el ticket."
                    );

                    resumen.setUltimaConsultaEn(
                            LocalDateTime.now(clock)
                    );

                    resumen.setIntentosConsulta(
                            resumen.getIntentosConsulta() + 1
                    );

                    resumenRepository.save(
                            resumen
                    );

                    return null;
                }
        );
    }

    private void marcarRechazoSinCdr(
            String ruc,
            Long resumenId,
            String ticket,
            String statusCode
    ) {

        enTransaccion(
                () -> {

                    ResumenDiario resumen =
                            resumenRepository
                                    .bloquearPorIdYRuc(
                                            resumenId,
                                            ruc
                                    )
                                    .orElseThrow();

                    if (!consultable(resumen) || !ticketCorresponde(resumen, ticket)) {
                        return null;
                    }
                    resumen.setEstado(
                            EstadoResumenDiario.RECHAZADO
                    );

                    resumen.setCodigoRespuestaSunat(
                            statusCode
                    );

                    resumen.setMensajeSunat(
                            "SUNAT finalizó el ticket con errores y no devolvió CDR."
                    );

                    resumen.setUltimaConsultaEn(
                            LocalDateTime.now(clock)
                    );

                    resumen.setIntentosConsulta(
                            resumen.getIntentosConsulta() + 1
                    );

                    resumen.setFinalizadoEn(
                            LocalDateTime.now(clock)
                    );

                    resumenRepository.save(
                            resumen
                    );

                    return null;
                }
        );
    }

    private void marcarErrorConsulta(
            String ruc,
            Long resumenId,
            String ticket,
            String mensaje
    ) {

        enTransaccion(
                () -> {

                    ResumenDiario resumen =
                            resumenRepository
                                    .bloquearPorIdYRuc(
                                            resumenId,
                                            ruc
                                    )
                                    .orElseThrow();

                    if (!consultable(resumen) || !ticketCorresponde(resumen, ticket)) {
                        return null;
                    }
                    resumen.setEstado(
                            EstadoResumenDiario.ERROR_CONSULTA
                    );

                    resumen.setMensajeSunat(
                            "No se pudo consultar getStatus: "
                                    + mensaje
                    );

                    resumen.setUltimaConsultaEn(
                            LocalDateTime.now(clock)
                    );

                    resumen.setIntentosConsulta(
                            resumen.getIntentosConsulta() + 1
                    );

                    resumenRepository.save(
                            resumen
                    );

                    return null;
                }
        );
    }

    private void marcarErrorPreparacion(
            String ruc,
            Long resumenId,
            Exception e
    ) {

        try {
            ResumenDiario actualizado=enTransaccion(
                    () -> {

                        ResumenDiario resumen =
                                resumenRepository
                                        .bloquearPorIdYRuc(
                                                resumenId,
                                                ruc
                                        )
                                        .orElseThrow();

                        if (!preparable(resumen)) {
                            return null;
                        }
                        resumen.setEstado(
                                EstadoResumenDiario.ERROR_VALIDACION
                        );

                        resumen.setMensajeSunat(
                                "No se pudo construir/firmar/validar el Resumen Diario: "
                                        + mensajeSeguro(e)
                        );

                        resumenRepository.save(
                                resumen
                        );

                        return resumen;
                    }
            );
            if(actualizado!=null)org.example.util.ObservabilidadFiscal.evento(ruc,resumenId,actualizado.getIdentificador(),actualizado.getTicketSunat(),
                    actualizado.getEstado().name(),e.getClass().getSimpleName());

        } catch (Exception guardarError) {
            log.error(
                    "No se pudo registrar ERROR_VALIDACION del resumen {}",
                    resumenId,
                    guardarError
            );
        }
    }

    private void marcarErrorEnvioIncierto(
            String ruc,
            Long resumenId,
            Exception e
    ) {

        actualizarErrorEnvio(
                ruc,
                resumenId,
                EstadoResumenDiario.ERROR_ENVIO_INCIERTO,
                "Resultado incierto durante sendSummary. NO se reintentará automáticamente: "
                        + mensajeSeguro(e)
        );
    }

    private void actualizarErrorEnvio(
            String ruc,
            Long resumenId,
            EstadoResumenDiario estado,
            String mensaje
    ) {

        try {
            ResumenDiario actualizado=enTransaccion(
                    () -> {

                        ResumenDiario resumen =
                                resumenRepository
                                        .bloquearPorIdYRuc(
                                                resumenId,
                                                ruc
                                        )
                                        .orElseThrow();

                        if (resumen.getEstado() != EstadoResumenDiario.ENVIANDO) {
                            return null;
                        }
                        resumen.setEstado(
                                estado
                        );

                        resumen.setMensajeSunat(
                                mensaje
                        );

                        resumenRepository.save(
                                resumen
                        );

                        return resumen;
                    }
            );
            if(actualizado!=null)org.example.util.ObservabilidadFiscal.evento(ruc,resumenId,actualizado.getIdentificador(),actualizado.getTicketSunat(),
                    actualizado.getEstado().name(),"SENDSUMMARY_SIN_RESULTADO_CONFIRMADO");

        } catch (Exception guardarError) {
            log.error(
                    "No se pudo registrar error de envío del resumen {}",
                    resumenId,
                    guardarError
            );
        }
    }

    // =========================================================
    // EMPRESA
    // =========================================================

    private boolean preparable(ResumenDiario resumen) {
        return resumen.getTicketSunat() == null && resumen.getIntentosEnvio()==0
                && (resumen.getEstado() == EstadoResumenDiario.CREADO
                || resumen.getEstado() == EstadoResumenDiario.ERROR_ENVIO);
    }

    private boolean consultable(ResumenDiario resumen) {
        return resumen.getEstado() == EstadoResumenDiario.TICKET_GENERADO
                || resumen.getEstado() == EstadoResumenDiario.PROCESANDO
                || resumen.getEstado() == EstadoResumenDiario.ERROR_CONSULTA;
    }

    private boolean ticketCorresponde(ResumenDiario resumen, String ticket) {
        boolean corresponde = ticket.equals(resumen.getTicketSunat())
                && !resumenRepository.existsByRucEmisorAndTicketSunatAndIdNot(
                        resumen.getRucEmisor(), ticket, resumen.getId());
        if (!corresponde) {
            log.warn("RC_TICKET_CORRELATION_CONFLICT resumenId={} ruc={}", resumen.getId(), resumen.getRucEmisor());
        }
        return corresponde;
    }

    private String datoTecnicoLog(String valor) {
        if (valor == null) return "<ausente>";
        return valor.substring(0, Math.min(valor.length(), 120)).replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", "_");
    }

    private Empresa empresaListaParaSunat(
            String ruc
    ) {

        Empresa empresa =
                empresaRepository
                        .findByRuc(
                                ruc
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Empresa emisora no encontrada."
                                )
                        );

        if (
                empresa.getPfxBytes() == null
                        ||
                        empresa.getPfxBytes().length == 0
        ) {
            throw new IllegalArgumentException(
                    "La empresa no tiene certificado PFX configurado."
            );
        }

        if (
                empresa.getPfxPassword() == null
                        ||
                        empresa.getPfxPassword().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La empresa no tiene contraseña PFX configurada."
            );
        }

        if (
                empresa.getUsuarioSol() == null
                        ||
                        empresa.getUsuarioSol().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La empresa no tiene usuario SOL configurado."
            );
        }

        if (
                empresa.getClaveSol() == null
                        ||
                        empresa.getClaveSol().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "La empresa no tiene clave SOL configurada."
            );
        }

        if (
                empresa.getCertificadoVigenteHasta() != null
                        &&
                        empresa
                                .getCertificadoVigenteHasta()
                                .isBefore(
                                        LocalDate.now(clock)
                                )
        ) {
            throw new IllegalArgumentException(
                    "El certificado digital de la empresa está vencido."
            );
        }

        return empresa;
    }

    // =========================================================
    // VALIDACIONES
    // =========================================================

    private void validarRucYFecha(
            String ruc,
            LocalDate fecha
    ) {

        validarRuc(
                ruc
        );

        if (fecha == null) {
            throw new IllegalArgumentException(
                    "La fecha de emisión de los documentos es obligatoria."
            );
        }

        sunatPlazoService.validarResumenDiario(fecha);

        if (fecha.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException(
                    "No se puede generar un Resumen Diario para una fecha futura."
            );
        }
    }

    private void validarRuc(
            String ruc
    ) {
        if (ruc == null || !ruc.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException(
                    "RUC inválido."
            );
        }
    }

    private void validarComprobanteParaResumen(
            Comprobante c,
            LocalDate fecha,
            String ruc
    ) {

        if (!ruc.equals(c.getRucEmisor())) {
            throw new SecurityException(
                    "Se intentó incluir un comprobante de otra empresa en el Resumen Diario."
            );
        }

        if (
                c.getFechaEmision() == null
                        ||
                        !fecha.equals(
                                c.getFechaEmision().toLocalDate()
                        )
        ) {
            throw new IllegalArgumentException(
                    "El comprobante "
                            + c.getId()
                            + " no pertenece a la fecha del Resumen Diario."
            );
        }

        boolean boleta =
                "03".equals(
                        c.getTipoComprobante()
                );

        boolean ncBoleta =
                "07".equals(
                        c.getTipoComprobante()
                )
                        &&
                        "03".equals(
                                c.getTipoComprobanteAfectado()
                        );

        if (!boleta && !ncBoleta) {
            throw new IllegalArgumentException(
                    "Documento no permitido en Resumen Diario: "
                            + c.getTipoComprobante()
            );
        }

        if (
                c.getMoneda() != null
                        &&
                        !"PEN".equalsIgnoreCase(
                                c.getMoneda()
                        )
        ) {
            throw new IllegalArgumentException(
                    "Esta versión sólo consolida boletas expresadas en PEN. Comprobante ID: "
                            + c.getId()
            );
        }

        if (
                c.getTipoDocCliente() == null
                        ||
                        c.getNumDocCliente() == null
        ) {
            throw new IllegalArgumentException(
                    "El comprobante no tiene identificación normalizada del cliente. ID: "
                            + c.getId()
            );
        }
    }

    // =========================================================
    // CARGA COMPLETA
    // =========================================================

    private ResumenDiario cargarCompleto(
            String ruc,
            Long id
    ) {

        return enTransaccion(
                () -> {

                    ResumenDiario resumen =
                            resumenRepository
                                    .buscarCompleto(
                                            id,
                                            ruc
                                    )
                                    .orElseThrow(
                                            () -> new IllegalArgumentException(
                                                    "Resumen Diario no encontrado."
                                            )
                                    );

                    /* Forzamos inicialización antes de cerrar la transacción. */
                    resumen.getDetalles().size();

                    return resumen;
                }
        );
    }

    // =========================================================
    // RESPONSE
    // =========================================================

    private ResumenDiarioResponse convertir(
            ResumenDiario r
    ) {

        return new ResumenDiarioResponse(

                r.getId(),

                r.getIdentificador(),

                r.getNombreArchivoXml(),

                r.getFechaDocumentos(),

                r.getFechaGeneracion(),

                r.getCorrelativo(),

                r.getCantidadDocumentos(),

                r.getEstado().name(),

                r.getTicketSunat(),

                r.getCodigoRespuestaSunat(),

                r.getMensajeSunat(),

                r.getIntentosEnvio(),

                r.getIntentosConsulta(),

                r.getEnviadoEn(),

                r.getUltimaConsultaEn(),

                r.getFinalizadoEn(),

                r.getCreatedAt(),

                r.getUpdatedAt()
        );
    }

    // =========================================================
    // ZIP SUNAT
    // =========================================================

    private byte[] comprimir(
            String nombreXml,
            byte[] xml
    ) {

        try {

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();

            try (
                    ZipOutputStream zos =
                            new ZipOutputStream(
                                    baos
                            )
            ) {

                ZipEntry entry =
                        new ZipEntry(
                                nombreXml
                        );

                zos.putNextEntry(
                        entry
                );

                zos.write(
                        xml
                );

                zos.closeEntry();
            }

            return baos.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo comprimir el Resumen Diario: "
                            + mensajeSeguro(e),
                    e
            );
        }
    }

    // =========================================================
    // TRANSACCIONES
    // =========================================================

    private <T> T enTransaccion(
            Supplier<T> trabajo
    ) {

        TransactionTemplate template =
                new TransactionTemplate(
                        transactionManager
                );

        return template.execute(
                status -> trabajo.get()
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private BigDecimal ceroSiNull(
            BigDecimal valor
    ) {
        return valor == null
                ? BigDecimal.ZERO
                : valor;
    }

    private String agregarMensaje(
            String actual,
            String adicional
    ) {

        if (actual == null || actual.isBlank()) {
            return adicional;
        }

        return actual
                + " | "
                + adicional;
    }

    private String mensajeSeguro(
            Throwable e
    ) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.getClass().getSimpleName();
    }
}
