
package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.model.Comprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;
import org.example.model.RespuestaSunatRecibida;
import org.example.auth.service.AuditoriaService;

import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * =============================================================
 * ENVÍO SUNAT SERVICE
 * =============================================================
 *
 * Responsable de enviar individualmente a SUNAT:
 *
 * - Facturas 01
 * - Notas de Crédito 07 vinculadas a Factura 01
 *
 * Las boletas y las NC de boleta NO pasan por este servicio;
 * quedarán para el Resumen Diario.
 *
 * El servicio intenta distinguir tres situaciones:
 *
 * 1. CDR aceptada/rechazada -> estado tributario definitivo.
 * 2. SOAP Fault             -> error de validación/protocolo.
 * 3. Red/timeout            -> error de comunicación.
 *
 * IMPORTANTE:
 * un SOAP Fault NO se convierte automáticamente en RECHAZADO,
 * porque rechazo tributario definitivo debe venir respaldado por CDR.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvioSunatService {

    private final ComprobanteRepository comprobanteRepository;

    private final EmpresaRepository empresaRepository;

    private final SunatDirectSoapClient sunatClient;

    private final CdrParserService cdrParserService;

    private final CryptoService cryptoService;

    private final SunatPlazoService sunatPlazoService;
    private final RespuestaSunatRecibidaService respuestaSunatRecibidaService;
    private final SaldoNotaCreditoService saldoNotaCreditoService;

    @Autowired(required = false)
    private AuditoriaService auditoriaService;


    // =========================================================
    // DEBUG XML
    // =========================================================

    @Value("${sunat.debug.xml:false}")
    private boolean debugXml;

    /**
     * En producción exigimos que la CDR identifique el documento
     * procesado. En tests unitarios puede desactivarse para mocks mínimos.
     */
    @Value("${sunat.cdr.require-reference-id:true}")
    private boolean cdrRequireReferenceId;


    // =========================================================
    // ENVIAR COMPROBANTE INDIVIDUAL
    // =========================================================

    public void enviar(
            Long comprobanteId
    ) {

        if (comprobanteId == null) {
            return;
        }

        /*
         * UPDATE atómico:
         *
         * PENDIENTE_ENVIO -> ENVIANDO
         *
         * Si otro worker ya tomó el comprobante, devuelve 0.
         */
        int tomado =
                comprobanteRepository
                        .marcarEnviando(
                                comprobanteId
                        );

        if (tomado == 0) {

            log.debug(
                    "El comprobante {} no está disponible para envío o ya está siendo procesado.",
                    comprobanteId
            );

            return;
        }

        Comprobante comprobante =
                comprobanteRepository
                        .findById(
                                comprobanteId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Comprobante no encontrado: "
                                                        + comprobanteId
                                        )
                        );

        EtapaEnvio etapa = EtapaEnvio.PREPARACION;
        try {

            Empresa empresa = empresaRepository.findByRuc(comprobante.getRucEmisor())
                    .orElseThrow(() -> new IllegalArgumentException("Empresa emisora no encontrada."));

            validarPreparadoParaEnvio(
                    comprobante,
                    empresa
            );

            /*
             * Antes de tocar la red validamos el plazo del documento.
             * Esto evita que un worker de recovery reenvíe ciegamente
             * un CPE que ya quedó fuera del plazo operativo configurado.
             */
            sunatPlazoService.validarEnvioIndividual(
                    comprobante.getFechaEmision()
            );

            byte[] xmlFirmado =
                    comprobante.getXmlFirmadoBytes();

            if (debugXml) {
                log.warn("XML debug solicitado pero no se registra contenido fiscal; comprobanteId={}",comprobanteId);
            }

            String nombreXml =
                    comprobante.getNombreArchivoXml();

            String nombreBase =
                    nombreXml.endsWith(".xml")
                            ?
                            nombreXml.substring(
                                    0,
                                    nombreXml.length() - 4
                            )
                            :
                            nombreXml;

            byte[] zip =
                    comprimir(
                            nombreXml,
                            xmlFirmado
                    );

            String usuarioSolPlano =
                    cryptoService.descifrarUsuarioSol(
                            empresa.getRuc(),
                            empresa.getUsuarioSol()
                    );

            String claveSolPlano =
                    cryptoService.descifrarClaveSol(
                            empresa.getRuc(),
                            empresa.getClaveSol()
                    );

            // Desde este límite no se puede presumir ausencia de recepción en SUNAT.
            etapa = EtapaEnvio.PETICION_INICIADA;
            byte[] cdrZip =
                    sunatClient.enviarZipDirecto(
                            empresa.getRuc(),
                            usuarioSolPlano,
                            claveSolPlano,
                            nombreBase + ".zip",
                            zip
                    );

            etapa = EtapaEnvio.RESPUESTA_RECIBIDA;
            if (
                    cdrZip == null
                            ||
                            cdrZip.length == 0
            ) {

                throw new SunatDirectSoapClient.SunatProtocolException(
                        "SUNAT no devolvió CDR para el comprobante."
                );
            }

            respuestaSunatRecibidaService.conservar(comprobante.getRucEmisor(), RespuestaSunatRecibida.Flujo.CPE,
                    comprobanteId, null, cdrZip);

            procesarCdr(
                    comprobante,
                    cdrZip
            );

        } catch (
                SunatPlazoService.FueraDePlazoSunatException e
        ) {

            comprobante.setEstadoSfs(
                    EstadoComprobante.FUERA_DE_PLAZO
            );

            comprobante.setMensajeSunat(
                    limitar(
                            mensajeSeguro(e),
                            4000
                    )
            );

            log.error(
                    "Comprobante {} fuera de plazo SUNAT: {}",
                    comprobanteId,
                    mensajeSeguro(e)
            );

        } catch (
                SunatDirectSoapClient.SunatSoapFaultException e
        ) {

            /*
             * SUNAT respondió explícitamente con SOAP Fault.
             *
             * No existe una CDR que permita declarar RECHAZADO.
             * Requiere conciliación, no se presume un fallo local seguro para repetir.
             */
            comprobante.setEstadoSfs(
                    EstadoComprobante.ERROR_ENVIO_INCIERTO
            );

            comprobante.setCodigoRespuestaSunat(
                    limitar(
                            e.getFaultCode(),
                            20
                    )
            );

            comprobante.setMensajeSunat(
                    limitar(
                            "SUNAT SOAP Fault: "
                                    + e.getFaultString(),
                            4000
                    )
            );

            log.warn(
                    "SUNAT SOAP Fault en comprobante {}: code={}, mensaje={}",
                    comprobanteId,
                    e.getFaultCode(),
                    e.getFaultString()
            );

        } catch (
                SunatDirectSoapClient.SunatTransportException e
        ) {

            /*
             * Error de red/timeout/TLS.
             *
             * No sabemos con certeza si SUNAT alcanzó a recibir el
             * request antes de cortarse la conexión, por eso NO hacemos
             * reintento automático ciego desde este bloque.
             */
            comprobante.setEstadoSfs(
                    EstadoComprobante.ERROR_ENVIO_INCIERTO
            );

            comprobante.setMensajeSunat(
                    limitar(
                            "Error de comunicación con SUNAT: "
                                    + mensajeSeguro(e),
                            4000
                    )
            );

            log.error("Error de transporte en comprobante {}: etapa={}, tipo={}", comprobanteId, etapa, e.getClass().getSimpleName());

        } catch (
                SunatDirectSoapClient.SunatProtocolException
                |
                IllegalArgumentException
                |
                IllegalStateException e
        ) {

            comprobante.setEstadoSfs(
                    etapa == EtapaEnvio.PREPARACION ? EstadoComprobante.ERROR_VALIDACION : EstadoComprobante.ERROR_ENVIO_INCIERTO
            );

            comprobante.setMensajeSunat(
                    limitar(
                            etapa == EtapaEnvio.PREPARACION ? mensajeSeguro(e) : "Resultado incierto: fallo en etapa " + etapa + "; requiere conciliación.",
                            4000
                    )
            );

            log.error("Error de validación/protocolo en comprobante {}: etapa={}, tipo={}", comprobanteId, etapa, e.getClass().getSimpleName());

        } catch (Exception e) {

            comprobante.setEstadoSfs(
                    etapa == EtapaEnvio.PREPARACION ? EstadoComprobante.ERROR_ENVIO : EstadoComprobante.ERROR_ENVIO_INCIERTO
            );

            comprobante.setMensajeSunat(
                    limitar(
                            "Error en etapa " + etapa + "; requiere revisión técnica.",
                            4000
                    )
            );

            log.error("Error inesperado en comprobante {}: etapa={}, tipo={}", comprobanteId, etapa, e.getClass().getSimpleName());
        }

        Comprobante confirmado;
        try {
            if ("07".equals(comprobante.getTipoComprobante())) {
                confirmado = saldoNotaCreditoService.guardarResultadoIndividual(comprobante);
            } else {
                comprobanteRepository.save(comprobante);
                confirmado = comprobante;
            }
        } catch (RuntimeException e) {
            // No intentar un segundo save ni reenvío: el commit puede ser incierto.
            // Si el rollback deja ENVIANDO, recovery lo convierte en incierto, no pendiente.
            log.error("Persistencia de resultado fallida: comprobanteId={}, etapa={}, tipo={}; requiere conciliación",
                    comprobanteId, etapa, e.getClass().getSimpleName());
            throw new IllegalStateException("No se confirmó la persistencia del resultado SUNAT; no reenviar sin conciliación.");
        }
        // Auditar el resultado confirmado, no el objeto previo al commit ni una respuesta tardía descartada.
        if (confirmado.getCdrZipBytes() != null) {
            if (auditoriaService != null) {
                org.example.util.ObservabilidadFiscal.auditar(auditoriaService, confirmado.getRucEmisor(), "CDR_COMPROBANTE_RECIBIDA",
                        confirmado.getEstadoSfs().name(), confirmado.getTipoComprobante() + " " + confirmado.getSerie() + "-" + confirmado.getNumero());
            }
        }
        org.example.util.ObservabilidadFiscal.evento(confirmado.getRucEmisor(), confirmado.getId(),
                confirmado.getTipoComprobante()+"/"+confirmado.getSerie()+"-"+confirmado.getNumero(), null,
                confirmado.getEstadoSfs().name(), confirmado.getCodigoRespuestaSunat());
    }

    private enum EtapaEnvio { PREPARACION, PETICION_INICIADA, RESPUESTA_RECIBIDA }


    // =========================================================
    // VALIDAR PRECONDICIONES
    // =========================================================

    private void validarPreparadoParaEnvio(

            Comprobante comprobante,

            Empresa empresa
    ) {

        if (
                comprobante.getXmlFirmadoBytes() == null
                        ||
                        comprobante.getXmlFirmadoBytes().length == 0
        ) {

            throw new IllegalStateException(
                    "No existe XML firmado."
            );
        }

        if (
                comprobante.getNombreArchivoXml() == null
                        ||
                        comprobante.getNombreArchivoXml().isBlank()
        ) {

            throw new IllegalStateException(
                    "El comprobante no tiene nombre de archivo XML."
            );
        }

        if (
                empresa == null
                        ||
                        !empresa.tieneConfiguracionSunat()
        ) {

            throw new IllegalStateException(
                    "La empresa no tiene completa su configuración SUNAT."
            );
        }

        if (
                !"01".equals(
                        comprobante.getTipoComprobante()
                )
                        &&
                        !(
                                "07".equals(
                                        comprobante.getTipoComprobante()
                                )
                                        &&
                                        "01".equals(
                                                comprobante.getTipoComprobanteAfectado()
                                        )
                        )
        ) {

            throw new IllegalStateException(
                    "Este comprobante no corresponde al flujo sendBill individual."
            );
        }
    }


    // =========================================================
    // PROCESAR CDR
    // =========================================================

    private void procesarCdr(

            Comprobante comprobante,

            byte[] cdrZip
    ) {

        CdrParserService.ResultadoCdr cdr =
                cdrParserService.procesar(
                        cdrZip
                );

        validarReferenciaCdr(
                comprobante,
                cdr
        );

        comprobante.setCdrZipBytes(
                cdrZip
        );

        comprobante.setCdrXmlBytes(
                cdr.xml()
        );

        comprobante.setCodigoRespuestaSunat(
                limitar(
                        cdr.responseCode(),
                        20
                )
        );

        String mensaje =
                cdr.description() == null
                        ?
                        ""
                        :
                        cdr.description();

        if (
                cdr.observaciones() != null
                        &&
                        !cdr.observaciones().isEmpty()
        ) {

            mensaje +=
                    " | Observaciones: "
                            +
                            String.join(
                                    " | ",
                                    cdr.observaciones()
                            );
        }

        comprobante.setMensajeSunat(
                limitar(
                        mensaje,
                        4000
                )
        );

        if (cdr.aceptado()) {

            if (
                    cdr.observaciones() == null
                            ||
                            cdr.observaciones().isEmpty()
            ) {

                comprobante.setEstadoSfs(
                        EstadoComprobante.ACEPTADO
                );

            } else {

                comprobante.setEstadoSfs(
                        EstadoComprobante.ACEPTADO_CON_OBSERVACION
                );
            }

        } else {

            /*
             * Aquí sí existe CDR y responseCode distinto de 0.
             * Por tanto podemos registrar rechazo tributario.
             */
            comprobante.setEstadoSfs(
                    EstadoComprobante.RECHAZADO
            );
        }
    }


    // =========================================================
    // VALIDAR IDENTIDAD DE LA CDR
    // =========================================================

    private void validarReferenciaCdr(
            Comprobante comprobante,
            CdrParserService.ResultadoCdr cdr
    ) {

        String referencia =
                cdr.referenceId();

        if (referencia == null || referencia.isBlank()) {

            if (cdrRequireReferenceId) {
                throw new SunatDirectSoapClient.SunatProtocolException(
                        "La CDR no contiene ReferenceID del comprobante procesado."
                );
            }

            return;
        }

        String esperado =
                comprobante.getSerie()
                        + "-"
                        + comprobante.getNumero();

        if (!mismaReferencia(esperado, referencia)) {
            throw new SunatDirectSoapClient.SunatProtocolException(
                    "La CDR recibida pertenece a otro comprobante. Esperado="
                            + esperado
                            + ", recibido="
                            + referencia
                            + "."
            );
        }
    }

    /**
     * SUNAT puede representar el correlativo con o sin ceros a la
     * izquierda. Comparamos la serie literalmente y el correlativo
     * como número entero.
     */
    private boolean mismaReferencia(
            String esperado,
            String recibido
    ) {

        try {
            String[] a = esperado.trim().split("-", 2);
            String[] b = recibido.trim().split("-", 2);

            if (a.length != 2 || b.length != 2) {
                return esperado.trim().equalsIgnoreCase(recibido.trim());
            }

            return a[0].equalsIgnoreCase(b[0])
                    && Integer.parseInt(a[1]) == Integer.parseInt(b[1]);

        } catch (Exception e) {
            return esperado.trim().equalsIgnoreCase(recibido.trim());
        }
    }


    // =========================================================
    // CREAR ZIP SUNAT
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

                zos.putNextEntry(
                        new ZipEntry(
                                nombreXml
                        )
                );

                zos.write(
                        xml
                );

                zos.closeEntry();
            }

            return baos.toByteArray();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error creando ZIP SUNAT: "
                            + mensajeSeguro(e),
                    e
            );
        }
    }


    // =========================================================
    // UTILIDADES
    // =========================================================

    private String mensajeSeguro(
            Throwable e
    ) {

        if (
                e == null
                        ||
                        e.getMessage() == null
                        ||
                        e.getMessage().isBlank()
        ) {

            return e == null
                    ? "Error desconocido"
                    : e.getClass().getSimpleName();
        }

        return e.getMessage();
    }


    private String limitar(

            String texto,

            int maximo
    ) {

        if (texto == null) {
            return null;
        }

        if (texto.length() <= maximo) {
            return texto;
        }

        return texto.substring(
                0,
                maximo
        );
    }
}
