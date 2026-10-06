
package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.model.Comprobante;
import org.example.util.RucUtils;
import org.example.util.ImportesXml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.Locale;
import java.util.Set;

/**
 * =============================================================
 * VALIDACIÓN TRIBUTARIA SERVICE
 * =============================================================
 *
 * Reúne reglas de negocio tributarias que deben cumplirse ANTES
 * de construir, firmar o enviar un XML a SUNAT.
 *
 * El objetivo es bloquear en nuestro backend solicitudes que ya
 * sabemos que esta versión del sistema no soporta correctamente.
 *
 * IMPORTANTE:
 * no intentamos aceptar todos los códigos que existen en los
 * catálogos SUNAT. Sólo habilitamos los que esta implementación
 * puede manejar de forma coherente.
 */
@Service
@RequiredArgsConstructor
public class ValidacionTributariaService {

    private static final BigDecimal TOLERANCIA =
            new BigDecimal("0.01");

    private final ReglasTributariasProperties reglas;

    /**
     * Clock tributario. En Spring se inyecta el Clock America/Lima
     * definido en TimeZoneConfig. El valor por defecto conserva la
     * compatibilidad de los tests que construyen este servicio a mano.
     */
    private Clock clock = Clock.system(ZoneId.of("America/Lima"));

    @Autowired(required = false)
    void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    private static final Set<String> DOCUMENTOS_BOLETA_SOPORTADOS =
            Set.of(
                    "0",
                    "1",
                    "4",
                    "6",
                    "7"
            );

    /**
     * Catálogo SUNAT 09 contiene más códigos, pero esta versión
     * habilita únicamente los motivos cuyo flujo actual podemos
     * construir y validar sin agregar todavía lógica especial.
     *
     * 01 = Anulación de la operación
     * 03 = Corrección por error en descripción
     * 04 = Descuento global
     * 05 = Descuento por ítem
     * 06 = Devolución total
     * 07 = Devolución por ítem
     * 08 = Bonificación
     * 09 = Disminución en el valor
     *
     * No habilitamos todavía:
     *
     * 02 = error de RUC: requiere tratamiento específico del receptor.
     * 10 = otros conceptos: no debe entrar en Resumen Diario de boletas.
     * 11 = ajustes de exportación: este backend sólo soporta 0101.
     * 12 = IVAP: no está implementado.
     * 13 = corrección de crédito/cuotas: requiere XML específico.
     */
    private static final Set<String> MOTIVOS_NC_IMPLEMENTADOS =
            Set.of(
                    "01",
                    "03",
                    "04",
                    "05",
                    "06",
                    "07",
                    "08",
                    "09"
            );


    // =========================================================
    // FACTURA / BOLETA
    // =========================================================

    public void validar(
            ComprobanteRequest request,
            CalculoTributarioService.Totales totales
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Solicitud de comprobante requerida."
            );
        }

        if (totales == null) {

            throw new IllegalArgumentException(
                    "No existen totales calculados por el backend."
            );
        }

        normalizarComprobante(
                request
        );

        // =====================================================
        // TIPO DE OPERACIÓN
        // =====================================================

        if (
                !reglas
                        .getTiposOperacionSoportados()
                        .contains(
                                request.getTipoOperacion()
                        )
        ) {

            throw new IllegalArgumentException(
                    "Tipo de operación SUNAT no soportado por esta versión: "
                            + request.getTipoOperacion()
            );
        }

        // =====================================================
        // TIPO DE COMPROBANTE
        // =====================================================

        switch (
                request.getTipoComprobante()
        ) {

            case "01" ->
                    validarFactura(
                            request
                    );

            case "03" ->
                    validarBoleta(
                            request,
                            totales
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Esta versión sólo emite Factura 01 o Boleta 03 desde este endpoint."
                    );
        }

        validarCredito(
                request,
                totales.total()
        );

        validarNombreReceptor(request.getRznSocialCliente());
    }


    private void normalizarComprobante(
            ComprobanteRequest request
    ) {

        request.setTipoOperacion(
                limpiar(
                        request.getTipoOperacion()
                )
        );

        request.setTipoComprobante(
                limpiar(
                        request.getTipoComprobante()
                )
        );

        request.setSerie(
                mayuscula(
                        request.getSerie()
                )
        );

        if (request.getFormaPago() != null) {

            String forma =
                    request
                            .getFormaPago()
                            .trim();

            if (
                    "credito".equalsIgnoreCase(
                            forma
                    )
            ) {

                request.setFormaPago(
                        "Credito"
                );

            } else if (
                    "contado".equalsIgnoreCase(
                            forma
                    )
            ) {

                request.setFormaPago(
                        "Contado"
                );
            }
        }
    }


    private void validarFactura(
            ComprobanteRequest request
    ) {

        if (
                request.getSerie() == null
                        ||
                        !request
                                .getSerie()
                                .startsWith(
                                        "F"
                                )
        ) {

            throw new IllegalArgumentException(
                    "Las facturas deben utilizar serie que empiece con F."
            );
        }

        if (
                esBlanco(
                        request.getTipoDocCliente()
                )
                        ||
                        esBlanco(
                                request.getNumDocCliente()
                        )
                        ||
                        esBlanco(
                                request.getRznSocialCliente()
                        )
        ) {

            throw new IllegalArgumentException(
                    "Una factura requiere los datos del cliente con RUC."
            );
        }

        request.setTipoDocCliente(
                request
                        .getTipoDocCliente()
                        .trim()
        );

        request.setNumDocCliente(
                request
                        .getNumDocCliente()
                        .trim()
        );

        request.setRznSocialCliente(
                request
                        .getRznSocialCliente()
                        .trim()
        );

        if (
                !"6".equals(
                        request.getTipoDocCliente()
                )
        ) {

            throw new IllegalArgumentException(
                    "Una factura requiere cliente identificado con RUC."
            );
        }

        if (
                !RucUtils.esValido(
                        request.getNumDocCliente()
                )
        ) {

            throw new IllegalArgumentException(
                    "RUC del cliente inválido."
            );
        }
    }


    private void validarBoleta(
            ComprobanteRequest request,
            CalculoTributarioService.Totales totales
    ) {

        if (
                request.getSerie() == null
                        ||
                        !request
                                .getSerie()
                                .startsWith(
                                        "B"
                                )
        ) {

            throw new IllegalArgumentException(
                    "Las boletas deben utilizar serie que empiece con B."
            );
        }

        boolean sinTipo =
                esBlanco(
                        request.getTipoDocCliente()
                );

        boolean sinNumero =
                esBlanco(
                        request.getNumDocCliente()
                );

        boolean sinNombre =
                esBlanco(
                        request.getRznSocialCliente()
                );

        boolean sinIdentificacion =
                sinTipo
                        &&
                        sinNumero
                        &&
                        sinNombre;

        // =====================================================
        // BOLETA SIN IDENTIFICACIÓN
        // =====================================================

        if (sinIdentificacion) {

            validarUmbralBoletaAnonima(
                    totales.total()
            );

            /*
             * Estos valores son internos para mantener columnas
             * NOT NULL en la base de datos.
             *
             * El constructor del XML individual ya reconoce
             * exactamente este caso.
             *
             * El Resumen Diario posteriormente convertirá este
             * cliente a los guiones exigidos en sus campos.
             */
            request.setTipoDocCliente(
                    "0"
            );

            request.setNumDocCliente(
                    "-"
            );

            request.setRznSocialCliente(
                    "CONSUMIDOR FINAL"
            );

            return;
        }

        /*
         * Si se empezó a identificar al cliente,
         * los tres datos deben venir completos.
         */
        if (
                sinTipo
                        ||
                        sinNumero
                        ||
                        sinNombre
        ) {

            throw new IllegalArgumentException(
                    "Si identifica al cliente debe enviar tipo de documento, número de documento y nombre."
            );
        }

        String tipo =
                request
                        .getTipoDocCliente()
                        .trim();

        String numero =
                request
                        .getNumDocCliente()
                        .trim();

        request.setTipoDocCliente(
                tipo
        );

        request.setNumDocCliente(
                numero
        );

        request.setRznSocialCliente(
                request
                        .getRznSocialCliente()
                        .trim()
        );

        validarDocumentoBoleta(
                tipo,
                numero,
                totales.total()
        );
    }


    private void validarDocumentoBoleta(

            String tipo,

            String numero,

            BigDecimal total
    ) {

        if (
                !DOCUMENTOS_BOLETA_SOPORTADOS
                        .contains(
                                tipo
                        )
        ) {

            throw new IllegalArgumentException(
                    "Tipo de documento no soportado para boleta. Use 1=DNI, 4=Carné de Extranjería, 6=RUC o 7=Pasaporte."
            );
        }

        switch (tipo) {

            case "1" -> {

                if (
                        !numero.matches(
                                "^[0-9]{8}$"
                        )
                ) {

                    throw new IllegalArgumentException(
                            "DNI inválido. Debe tener 8 dígitos."
                    );
                }
            }

            case "4" -> {

                if (
                        !numero.matches(
                                "^[A-Za-z0-9]{1,12}$"
                        )
                ) {

                    throw new IllegalArgumentException(
                            "Carné de Extranjería inválido."
                    );
                }
            }

            case "6" -> {

                if (
                        !RucUtils.esValido(
                                numero
                        )
                ) {

                    throw new IllegalArgumentException(
                            "RUC del cliente inválido."
                    );
                }
            }

            case "7" -> {

                if (
                        !numero.matches(
                                "^[A-Za-z0-9]{1,12}$"
                        )
                ) {

                    throw new IllegalArgumentException(
                            "Pasaporte inválido."
                    );
                }
            }

            case "0" -> {

                if (
                        !"-".equals(
                                numero
                        )
                ) {

                    throw new IllegalArgumentException(
                            "El tipo 0 sólo se admite con el valor interno '-' para una boleta sin identificación."
                    );
                }

                validarUmbralBoletaAnonima(
                        total
                );
            }

            default ->
                    throw new IllegalArgumentException(
                            "Tipo de documento de cliente no soportado."
                    );
        }
    }


    private void validarUmbralBoletaAnonima(
            BigDecimal total
    ) {

        if (
                total.compareTo(
                        reglas.getBoletaIdentificacionUmbral()
                ) > 0
        ) {

            throw new IllegalArgumentException(
                    "Por el monto de la boleta debe identificarse al cliente."
            );
        }
    }


    // =========================================================
    // VENTA A CRÉDITO
    // =========================================================

    private void validarCredito(
            ComprobanteRequest request,
            BigDecimal total
    ) {

        if (
                !"Credito".equalsIgnoreCase(
                        request.getFormaPago()
                )
        ) {

            return;
        }

        if (
                request.getMontoPendientePago() == null
                        ||
                        request
                                .getMontoPendientePago()
                                .compareTo(
                                        BigDecimal.ZERO
                                ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Una venta a crédito requiere montoPendientePago mayor a cero."
            );
        }

        if (
                request.getCuotas() == null
                        ||
                        request.getCuotas().isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Una venta a crédito requiere cuotas."
            );
        }

        LocalDate fechaEmision =
                LocalDate.now(clock);

        for (ComprobanteRequest.CuotaRequest cuota : request.getCuotas()) {

            if (cuota == null) {
                throw new IllegalArgumentException(
                        "La venta a crédito contiene una cuota vacía."
                );
            }

            if (cuota.getFechaVencimiento() == null) {
                throw new IllegalArgumentException(
                        "Cada cuota requiere fecha de vencimiento."
                );
            }

            /*
             * Regla SUNAT: la fecha de vencimiento de una cuota debe
             * ser posterior a la fecha de emisión. Como este backend
             * fija la fecha de emisión en el servidor, comparamos con
             * el día tributario actual (America/Lima).
             */
            if (!cuota.getFechaVencimiento().isAfter(fechaEmision)) {
                throw new IllegalArgumentException(
                        "La fecha de vencimiento de cada cuota debe ser posterior a la fecha de emisión."
                );
            }

            if (cuota.getMonto() == null
                    || cuota.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Cada cuota debe tener monto mayor a cero."
                );
            }
        }

        ImportesXml.validarCredito(total, request.getMontoPendientePago(),
                request
                        .getCuotas()
                        .stream()
                        .map(
                                ComprobanteRequest.CuotaRequest::getMonto
                        )
                        .toList());
    }


    // =========================================================
    // NOTA DE CRÉDITO - VALIDACIÓN PREVIA
    // =========================================================

    public void validar(
            NotaCreditoRequest request
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Solicitud de Nota de Crédito requerida."
            );
        }

        normalizarNotaCredito(
                request
        );

        if (
                !MOTIVOS_NC_IMPLEMENTADOS
                        .contains(
                                request.getCodMotivoSunat()
                        )
        ) {

            throw new IllegalArgumentException(
                    "El motivo de Nota de Crédito "
                            + request.getCodMotivoSunat()
                            + " existe en catálogos SUNAT, pero todavía no está implementado de forma segura en esta versión."
            );
        }

        if (
                "01".equals(
                        request.getTipoComprobanteAfectado()
                )
                        &&
                        !request
                                .getSerie()
                                .startsWith(
                                        "F"
                                )
        ) {

            throw new IllegalArgumentException(
                    "Una Nota de Crédito asociada a Factura debe utilizar serie que empiece con F."
            );
        }

        if (
                "03".equals(
                        request.getTipoComprobanteAfectado()
                )
                        &&
                        !request
                                .getSerie()
                                .startsWith(
                                        "B"
                                )
        ) {

            throw new IllegalArgumentException(
                    "Una Nota de Crédito asociada a Boleta debe utilizar serie que empiece con B."
            );
        }

        /*
         * El código 10 no se acepta en esta versión de todas formas,
         * pero dejamos esta regla explícita para documentar que una NC
         * de boleta con ese motivo no debe entrar al Resumen Diario.
         */
        if (
                "03".equals(
                        request.getTipoComprobanteAfectado()
                )
                        &&
                        "10".equals(
                                request.getCodMotivoSunat()
                        )
        ) {

            throw new IllegalArgumentException(
                    "La Nota de Crédito tipo 10 vinculada a boleta no está soportada por el flujo de Resumen Diario de esta versión."
            );
        }

        validarDocumentoNotaCredito(
                request
        );
    }


    private void normalizarNotaCredito(
            NotaCreditoRequest request
    ) {

        request.setSerie(
                mayuscula(
                        request.getSerie()
                )
        );

        request.setTipoComprobanteAfectado(
                limpiar(
                        request.getTipoComprobanteAfectado()
                )
        );

        request.setNumComprobanteAfectado(
                mayuscula(
                        request.getNumComprobanteAfectado()
                )
        );

        request.setCodMotivoSunat(
                limpiar(
                        request.getCodMotivoSunat()
                )
        );

        if (request.getDescripcionMotivo() != null) {

            request.setDescripcionMotivo(
                    request
                            .getDescripcionMotivo()
                            .trim()
            );
        }

        if (request.getTipoDocCliente() != null) {

            request.setTipoDocCliente(
                    request
                            .getTipoDocCliente()
                            .trim()
            );
        }

        if (request.getNumDocCliente() != null) {

            request.setNumDocCliente(
                    request
                            .getNumDocCliente()
                            .trim()
            );
        }

        if (request.getRznSocialCliente() != null) {

            request.setRznSocialCliente(
                    request
                            .getRznSocialCliente()
                            .trim()
            );
        }
    }


    /** Excel 26.08.2026, regla 2022; mantener máximo 200 de la API, no ampliarlo aquí. */
    private void validarNombreReceptor(String nombre) {
        if (nombre == null || nombre.codePointCount(0, nombre.length()) < 3) {
            throw new IllegalArgumentException("2022: nombre o razón social del receptor requiere al menos 3 caracteres.");
        }
        if (nombre.length() > 200) {
            throw new IllegalArgumentException("Nombre o razón social excede el máximo de 200 caracteres soportado por la aplicación.");
        }
        if (nombre.codePoints().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException("2022: nombre o razón social no admite tabulaciones, saltos de línea ni controles XML.");
        }
    }

    private void validarDocumentoNotaCredito(
            NotaCreditoRequest request
    ) {

        String tipo =
                request.getTipoDocCliente();

        String numero =
                request.getNumDocCliente();

        String nombre =
                request.getRznSocialCliente();

        validarNombreReceptor(nombre);

        if (
                esBlanco(tipo)
                        ||
                        esBlanco(numero)
                        ||
                        esBlanco(nombre)
        ) {

            throw new IllegalArgumentException(
                    "La Nota de Crédito debe conservar los datos del cliente del comprobante original."
            );
        }

        if (
                !DOCUMENTOS_BOLETA_SOPORTADOS
                        .contains(
                                tipo
                        )
        ) {

            throw new IllegalArgumentException(
                    "Tipo de documento del cliente no soportado."
            );
        }

        switch (tipo) {

            case "1" -> {

                if (
                        !numero.matches(
                                "^[0-9]{8}$"
                        )
                ) {

                    throw new IllegalArgumentException(
                            "DNI inválido."
                    );
                }
            }

            case "4" -> {

                if (
                        !numero.matches(
                                "^[A-Za-z0-9]{1,12}$"
                        )
                ) {

                    throw new IllegalArgumentException(
                            "Carné de Extranjería inválido."
                    );
                }
            }

            case "6" -> {

                if (
                        !RucUtils.esValido(
                                numero
                        )
                ) {

                    throw new IllegalArgumentException(
                            "RUC del cliente inválido."
                    );
                }
            }

            case "7" -> {

                if (
                        !numero.matches(
                                "^[A-Za-z0-9]{1,12}$"
                        )
                ) {

                    throw new IllegalArgumentException(
                            "Pasaporte inválido."
                    );
                }
            }

            case "0" -> {

                if (
                        !"-".equals(
                                numero
                        )
                ) {

                    throw new IllegalArgumentException(
                            "Cliente sin identificación inválido."
                    );
                }
            }

            default ->
                    throw new IllegalArgumentException(
                            "Tipo de documento del cliente no soportado."
                    );
        }
    }


    // =========================================================
    // NOTA DE CRÉDITO - CONTRA EL DOCUMENTO ORIGINAL
    // =========================================================

    /**
     * Debe llamarse DESPUÉS de recuperar el comprobante original
     * desde la base de datos y DESPUÉS de calcular los importes de
     * la Nota de Crédito con CalculoTributarioService.
     */
    public void validarContraComprobanteOriginal(

            NotaCreditoRequest request,

            Comprobante origen,

            CalculoTributarioService.Totales totales
    ) {

        if (origen == null) {

            throw new IllegalArgumentException(
                    "Comprobante original requerido."
            );
        }

        if (totales == null) {

            throw new IllegalArgumentException(
                    "Totales calculados de la Nota de Crédito requeridos."
            );
        }

        if (
                !iguales(
                        request.getTipoComprobanteAfectado(),
                        origen.getTipoComprobante()
                )
        ) {

            throw new IllegalArgumentException(
                    "El tipo de comprobante afectado no coincide con el documento original."
            );
        }

        /*
         * Como esta versión NO implementa motivo 02 (error en RUC),
         * el adquirente debe seguir siendo exactamente el mismo.
         */
        if (
                !iguales(
                        request.getTipoDocCliente(),
                        origen.getTipoDocCliente()
                )
                        ||
                        !iguales(
                                request.getNumDocCliente(),
                                origen.getNumDocCliente()
                        )
        ) {

            throw new IllegalArgumentException(
                    "El documento del cliente de la Nota de Crédito no coincide con el comprobante original."
            );
        }

        if (
                !textoEquivalente(
                        request.getRznSocialCliente(),
                        origen.getRznSocialCliente()
                )
        ) {

            throw new IllegalArgumentException(
                    "El nombre o razón social de la Nota de Crédito no coincide con el comprobante original."
            );
        }

        if (
                totales.total().compareTo(
                        BigDecimal.ZERO
                ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "El importe de la Nota de Crédito debe ser mayor a cero."
            );
        }

        if (
                totales.total().compareTo(
                        origen.getMontoTotal()
                ) > 0
        ) {

            throw new IllegalArgumentException(
                    "La Nota de Crédito no puede superar el importe total del comprobante original en esta versión."
            );
        }

        String motivo =
                request.getCodMotivoSunat();

        switch (motivo) {

            /*
             * Anulación completa y devolución total:
             * el valor total de la NC debe coincidir con el original.
             */
            case "01", "06" ->
                    validarMismosImportesPrincipales(
                            origen,
                            totales,
                            "El motivo "
                                    + motivo
                                    + " requiere afectar el total completo del comprobante original."
                    );

            /*
             * Corrección de descripción:
             * no debe cambiar los importes tributarios.
             */
            case "03" ->
                    validarMismosImportesPrincipales(
                            origen,
                            totales,
                            "La corrección de descripción no debe modificar los importes del comprobante original."
                    );

            /*
             * Los demás motivos implementados pueden afectar
             * parcial o totalmente importes, pero nunca exceder
             * el original; esa regla ya fue comprobada arriba.
             */
            case "04", "05", "07", "08", "09" -> {
                // Sin validación adicional por ahora.
            }

            default ->
                    throw new IllegalArgumentException(
                            "Motivo de Nota de Crédito no implementado."
                    );
        }
    }


    private void validarMismosImportesPrincipales(

            Comprobante origen,

            CalculoTributarioService.Totales totales,

            String mensaje
    ) {

        if (
                diferenciaMayorATolerancia(
                        origen.getOpGravada(),
                        totales.opGravada()
                )
                        ||
                        diferenciaMayorATolerancia(
                                origen.getOpExonerada(),
                                totales.opExonerada()
                        )
                        ||
                        diferenciaMayorATolerancia(
                                origen.getOpInafecta(),
                                totales.opInafecta()
                        )
                        ||
                        diferenciaMayorATolerancia(
                                origen.getIgv(),
                                totales.igv()
                        )
                        ||
                        diferenciaMayorATolerancia(
                                origen.getMontoTotal(),
                                totales.total()
                        )
        ) {

            throw new IllegalArgumentException(
                    mensaje
            );
        }
    }


    // =========================================================
    // UTILIDADES
    // =========================================================

    private boolean diferenciaMayorATolerancia(

            BigDecimal a,

            BigDecimal b
    ) {

        if (
                a == null
                        ||
                        b == null
        ) {

            return true;
        }

        return a
                .subtract(b)
                .abs()
                .compareTo(
                        TOLERANCIA
                ) > 0;
    }


    private boolean iguales(

            String a,

            String b
    ) {

        if (
                a == null
                        ||
                        b == null
        ) {

            return a == b;
        }

        return a
                .trim()
                .equalsIgnoreCase(
                        b.trim()
                );
    }


    private boolean textoEquivalente(

            String a,

            String b
    ) {

        if (
                a == null
                        ||
                        b == null
        ) {

            return a == b;
        }

        String na =
                a
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        String nb =
                b
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        return na.equals(nb);
    }


    private String limpiar(
            String valor
    ) {

        return valor == null
                ? null
                : valor.trim();
    }


    private String mayuscula(
            String valor
    ) {

        return valor == null
                ? null
                : valor
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }


    private boolean esBlanco(
            String valor
    ) {

        return valor == null
                ||
                valor.isBlank();
    }
}
