
package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.config.ReglasTributariasProperties;
import org.example.dto.ComprobanteRequest;
import org.example.dto.NotaCreditoRequest;
import org.example.util.ImportesXml;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * =============================================================
 * CALCULO TRIBUTARIO SERVICE
 * =============================================================
 *
 * El backend es la fuente de verdad de los cálculos tributarios.
 *
 * El frontend puede enviar totales y porcentajes únicamente para
 * que podamos compararlos y detectar manipulaciones o errores,
 * pero NO decide la tasa de IGV utilizada para una operación gravada.
 *
 * Versión actualmente soportada:
 *
 * 10 = Gravado
 * 20 = Exonerado
 * 30 = Inafecto
 */
@Service
@RequiredArgsConstructor
public class CalculoTributarioService {

    private static final BigDecimal CIEN =
            new BigDecimal("100");

    /**
     * Diferencia máxima admitida entre el valor informado
     * por el cliente y el calculado por el backend.
     */
    private static final BigDecimal TOLERANCIA_MONETARIA =
            new BigDecimal("0.01");

    private static final BigDecimal TOLERANCIA_PORCENTAJE =
            new BigDecimal("0.0001");

    private static final Set<String> AFECTACIONES_SOPORTADAS =
            Set.of(
                    "10",
                    "20",
                    "30"
            );

    private final ReglasTributariasProperties reglas;


    // =========================================================
    // FACTURA / BOLETA
    // =========================================================

    public Totales calcular(
            ComprobanteRequest request
    ) {

        return calcular(
                request,
                reglas.getIgvPorcentaje()
        );
    }


    /**
     * Versión multiempresa: la tasa ya fue resuelta por el backend
     * para el tenant emisor.
     */
    public Totales calcular(
            ComprobanteRequest request,
            BigDecimal porcentajeIgvBackend
    ) {

        porcentajeIgvBackend =
                normalizarPorcentajeBackend(
                        porcentajeIgvBackend
                );

        if (
                request == null
                        ||
                        request.getItems() == null
                        ||
                        request.getItems().isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Debe existir al menos un item para calcular el comprobante."
            );
        }

        List<ItemCalculado> items =
                new ArrayList<>();

        BigDecimal gravada = BigDecimal.ZERO;
        BigDecimal exonerada = BigDecimal.ZERO;
        BigDecimal inafecta = BigDecimal.ZERO;
        BigDecimal igvTotal = BigDecimal.ZERO;

        int orden = 1;

        for (
                ComprobanteRequest.DetalleItemRequest item :
                request.getItems()
        ) {

            ItemCalculado calculado =
                    calcularItem(
                            orden++,
                            item.getCantidad(),
                            item.getUnidadMedida(),
                            item.getDescripcion(),
                            item.getValorUnitario(),
                            item.getPrecioUnitario(),
                            item.getIgvItem(),
                            item.getTipoAfectacionIgv(),
                            item.getPorcentajeIgv(),
                            porcentajeIgvBackend
                    );

            items.add(
                    calculado
            );

            switch (
                    calculado.tipoAfectacionIgv()
            ) {

                case "10" ->
                        gravada =
                                gravada.add(
                                        calculado.baseImponible()
                                );

                case "20" ->
                        exonerada =
                                exonerada.add(
                                        calculado.baseImponible()
                                );

                case "30" ->
                        inafecta =
                                inafecta.add(
                                        calculado.baseImponible()
                                );

                default ->
                        throw new IllegalArgumentException(
                                "Tipo de afectación IGV no soportado."
                        );
            }

            igvTotal =
                    igvTotal.add(
                            calculado.igv()
                    );
        }

        gravada = moneda(gravada);
        exonerada = moneda(exonerada);
        inafecta = moneda(inafecta);
        igvTotal = moneda(igvTotal);

        BigDecimal total =
                moneda(
                        gravada
                                .add(exonerada)
                                .add(inafecta)
                                .add(igvTotal)
                );

        /*
         * El frontend NO define estos valores.
         * Sólo comprobamos que lo que informó coincida
         * con el cálculo independiente del backend.
         */
        verificarMoneda(
                "opGravada",
                request.getOpGravada(),
                gravada
        );

        verificarMoneda(
                "opExonerada",
                request.getOpExonerada(),
                exonerada
        );

        verificarMoneda(
                "opInafecta",
                request.getOpInafecta(),
                inafecta
        );

        verificarMoneda(
                "IGV",
                request.getIgv(),
                igvTotal
        );

        verificarMoneda(
                "total",
                request.getTotal(),
                total
        );

        return new Totales(
                gravada,
                exonerada,
                inafecta,
                igvTotal,
                total,
                List.copyOf(items)
        );
    }


    // =========================================================
    // NOTA DE CRÉDITO
    // =========================================================

    public Totales calcular(
            NotaCreditoRequest request
    ) {

        return calcular(
                request,
                reglas.getIgvPorcentaje()
        );
    }


    /**
     * La Nota de Crédito recibe la tasa histórica del comprobante
     * original para no depender de la tasa actual de la empresa.
     */
    public Totales calcular(
            NotaCreditoRequest request,
            BigDecimal porcentajeIgvBackend
    ) {

        porcentajeIgvBackend =
                normalizarPorcentajeBackend(
                        porcentajeIgvBackend
                );

        if (
                request == null
                        ||
                        request.getItems() == null
                        ||
                        request.getItems().isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Debe existir al menos un item para calcular la Nota de Crédito."
            );
        }

        List<ItemCalculado> items =
                new ArrayList<>();

        BigDecimal gravada = BigDecimal.ZERO;
        BigDecimal exonerada = BigDecimal.ZERO;
        BigDecimal inafecta = BigDecimal.ZERO;
        BigDecimal igvTotal = BigDecimal.ZERO;

        int orden = 1;

        for (
                NotaCreditoRequest.DetalleItemRequest item :
                request.getItems()
        ) {

            ItemCalculado calculado =
                    calcularItem(
                            orden++,
                            item.getCantidad(),
                            item.getUnidadMedida(),
                            item.getDescripcion(),
                            item.getValorUnitario(),
                            item.getPrecioUnitario(),
                            item.getIgvItem(),
                            item.getTipoAfectacionIgv(),
                            item.getPorcentajeIgv(),
                            porcentajeIgvBackend
                    );

            items.add(
                    calculado
            );

            switch (
                    calculado.tipoAfectacionIgv()
            ) {

                case "10" ->
                        gravada =
                                gravada.add(
                                        calculado.baseImponible()
                                );

                case "20" ->
                        exonerada =
                                exonerada.add(
                                        calculado.baseImponible()
                                );

                case "30" ->
                        inafecta =
                                inafecta.add(
                                        calculado.baseImponible()
                                );

                default ->
                        throw new IllegalArgumentException(
                                "Tipo de afectación IGV no soportado."
                        );
            }

            igvTotal =
                    igvTotal.add(
                            calculado.igv()
                    );
        }

        gravada = moneda(gravada);
        exonerada = moneda(exonerada);
        inafecta = moneda(inafecta);
        igvTotal = moneda(igvTotal);

        BigDecimal total =
                moneda(
                        gravada
                                .add(exonerada)
                                .add(inafecta)
                                .add(igvTotal)
                );

        verificarMoneda(
                "opGravada",
                request.getOpGravada(),
                gravada
        );

        verificarMoneda(
                "opExonerada",
                request.getOpExonerada(),
                exonerada
        );

        verificarMoneda(
                "opInafecta",
                request.getOpInafecta(),
                inafecta
        );

        verificarMoneda(
                "IGV",
                request.getIgv(),
                igvTotal
        );

        verificarMoneda(
                "total",
                request.getTotal(),
                total
        );

        return new Totales(
                gravada,
                exonerada,
                inafecta,
                igvTotal,
                total,
                List.copyOf(items)
        );
    }


    // =========================================================
    // CALCULAR ITEM
    // =========================================================

    private ItemCalculado calcularItem(

            int orden,

            BigDecimal cantidad,

            String unidad,

            String descripcion,

            BigDecimal valorUnitario,

            BigDecimal precioUnitarioEnviado,

            BigDecimal igvItemEnviado,

            String afectacion,

            BigDecimal porcentajeEnviado,

            BigDecimal porcentajeIgvBackend

    ) {

        validarDatosItem(
                orden,
                cantidad,
                unidad,
                descripcion,
                valorUnitario,
                afectacion
        );

        /*
         * Para operaciones gravadas la tasa la decide
         * EXCLUSIVAMENTE el backend.
         *
         * Si el frontend envía porcentaje, sólo se usa
         * como comprobación de integridad.
         */
        BigDecimal porcentaje =
                porcentajeAplicable(
                        orden,
                        afectacion,
                        porcentajeEnviado,
                        porcentajeIgvBackend
                );

        BigDecimal base =
                moneda(
                        cantidad.multiply(
                                valorUnitario
                        )
                );

        BigDecimal igv =
                moneda(
                        base
                                .multiply(porcentaje)
                                .divide(
                                        CIEN,
                                        10,
                                        RoundingMode.HALF_UP
                                )
                );

        /*
         * Precio unitario de venta:
         *
         * - gravado: valor + IGV
         * - exonerado/inafecto: igual al valor unitario
         */
        BigDecimal precioUnitario =
                valorUnitario
                        .add(
                                valorUnitario
                                        .multiply(porcentaje)
                                        .divide(
                                                CIEN,
                                                10,
                                                RoundingMode.HALF_UP
                                        )
                        )
                        .setScale(
                                10,
                                RoundingMode.HALF_UP
                        );

        precioUnitario = ImportesXml.unitario(precioUnitario);

        BigDecimal totalItem =
                moneda(
                        base.add(igv)
                );

        if (precioUnitarioEnviado != null) {

            verificarMoneda(
                    "precioUnitario del item " + orden,
                    precioUnitarioEnviado,
                    precioUnitario
            );
        }

        if (igvItemEnviado != null) {

            verificarMoneda(
                    "igvItem del item " + orden,
                    igvItemEnviado,
                    igv
            );
        }

        return new ItemCalculado(
                orden,
                cantidad,
                unidad.trim(),
                descripcion.trim(),
                valorUnitario,
                precioUnitario,
                base,
                igv,
                totalItem,
                afectacion,
                porcentaje
        );
    }


    // =========================================================
    // TASA IGV
    // =========================================================

    private BigDecimal porcentajeAplicable(

            int orden,

            String afectacion,

            BigDecimal porcentajeEnviado,

            BigDecimal porcentajeBackend
    ) {

        if (!AFECTACIONES_SOPORTADAS.contains(afectacion)) {

            throw new IllegalArgumentException(
                    "Tipo de afectación IGV no soportado en item "
                            + orden
                            + ": "
                            + afectacion
            );
        }

        if ("10".equals(afectacion)) {

            if (
                    porcentajeBackend == null
                            ||
                            porcentajeBackend.compareTo(
                                    BigDecimal.ZERO
                            ) <= 0
            ) {

                throw new IllegalStateException(
                        "La tasa IGV del backend no está configurada correctamente."
                );
            }

            if (porcentajeEnviado != null) {

                BigDecimal diferencia =
                        porcentajeEnviado
                                .subtract(porcentajeBackend)
                                .abs();

                if (
                        diferencia.compareTo(
                                TOLERANCIA_PORCENTAJE
                        ) > 0
                ) {

                    throw new IllegalArgumentException(
                            "Porcentaje IGV incorrecto en item "
                                    + orden
                                    + ". El frontend informó "
                                    + porcentajeEnviado
                                    + " pero el backend tiene configurado "
                                    + porcentajeBackend
                                    + "."
                    );
                }
            }

            return porcentajeBackend;
        }

        /*
         * Exonerado e inafecto no generan IGV.
         */
        if (
                porcentajeEnviado != null
                        &&
                        porcentajeEnviado.compareTo(
                                BigDecimal.ZERO
                        ) != 0
        ) {

            throw new IllegalArgumentException(
                    "Operaciones exoneradas o inafectas deben tener porcentaje IGV 0."
            );
        }

        return BigDecimal.ZERO;
    }


    /**
     * Normaliza la tasa que el backend resolvió para una empresa.
     * Nunca se toma del request como fuente de verdad.
     */
    private BigDecimal normalizarPorcentajeBackend(
            BigDecimal porcentaje
    ) {

        BigDecimal efectivo =
                porcentaje != null
                        ? porcentaje
                        : reglas.getIgvPorcentaje();

        if (
                efectivo == null
                        ||
                        efectivo.compareTo(BigDecimal.ZERO) <= 0
        ) {

            throw new IllegalStateException(
                    "La tasa IGV del backend no está configurada correctamente."
            );
        }

        return efectivo.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }


    // =========================================================
    // VALIDACIONES INTERNAS
    // =========================================================

    private void validarDatosItem(

            int orden,

            BigDecimal cantidad,

            String unidad,

            String descripcion,

            BigDecimal valorUnitario,

            String afectacion
    ) {

        if (
                cantidad == null
                        ||
                        cantidad.compareTo(
                                BigDecimal.ZERO
                        ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Cantidad inválida en item " + orden + "."
            );
        }

        if (
                valorUnitario == null
                        ||
                        valorUnitario.compareTo(
                                BigDecimal.ZERO
                        ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Valor unitario inválido en item " + orden + "."
            );
        }

        ImportesXml.unitario(valorUnitario);
        ImportesXml.validarCantidad(cantidad);

        if (
                unidad == null
                        ||
                        unidad.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Unidad de medida requerida en item " + orden + "."
            );
        }

        if (
                descripcion == null
                        ||
                        descripcion.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Descripción requerida en item " + orden + "."
            );
        }

        if (
                afectacion == null
                        ||
                        !AFECTACIONES_SOPORTADAS.contains(afectacion)
        ) {

            throw new IllegalArgumentException(
                    "Tipo de afectación IGV inválido en item " + orden + "."
            );
        }
    }


    // =========================================================
    // COMPARACIÓN DE IMPORTES
    // =========================================================

    private void verificarMoneda(

            String campo,

            BigDecimal enviado,

            BigDecimal calculado

    ) {

        if (enviado == null) {
            return;
        }

        BigDecimal diferencia =
                enviado
                        .subtract(calculado)
                        .abs();

        if (
                diferencia.compareTo(
                        TOLERANCIA_MONETARIA
                ) > 0
        ) {

            throw new IllegalArgumentException(
                    campo
                            + " incorrecto. Enviado: "
                            + enviado
                            + " / Calculado por backend: "
                            + calculado
            );
        }
    }


    private BigDecimal moneda(
            BigDecimal valor
    ) {

        return ImportesXml.monetarioPersistible(valor);
    }


    // =========================================================
    // RESULTADOS INMUTABLES
    // =========================================================

    public record Totales(

            BigDecimal opGravada,

            BigDecimal opExonerada,

            BigDecimal opInafecta,

            BigDecimal igv,

            BigDecimal total,

            List<ItemCalculado> items

    ) {
    }


    public record ItemCalculado(

            int orden,

            BigDecimal cantidad,

            String unidadMedida,

            String descripcion,

            BigDecimal valorUnitario,

            BigDecimal precioUnitario,

            BigDecimal baseImponible,

            BigDecimal igv,

            BigDecimal total,

            String tipoAfectacionIgv,

            BigDecimal porcentajeIgv

    ) {
    }
}
