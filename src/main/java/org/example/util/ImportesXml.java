package org.example.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Representación decimal compartida por cálculo, validación y XML; no usa float/double. */
public final class ImportesXml {
    private ImportesXml() { }

    public static BigDecimal monetario(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** SUNAT, Excel 26.08.2026, reglas 2369/2367: positivo, n(12,10). */
    public static BigDecimal unitario(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El valor/precio unitario debe ser positivo.");
        }
        BigDecimal normalizado = valor.stripTrailingZeros();
        if (normalizado.scale() > 10 || normalizado.precision() - normalizado.scale() > 12) {
            throw new IllegalArgumentException("El valor/precio unitario admite hasta 12 enteros y 10 decimales.");
        }
        return normalizado;
    }

    /** Contrato de almacenamiento existente de cantidad: DECIMAL(12,4), sin recorte. */
    public static void validarCantidad(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Cantidad inválida.");
        BigDecimal n = valor.stripTrailingZeros();
        if (n.scale() > 4 || n.precision() - n.scale() > 8)
            throw new IllegalArgumentException("Cantidad admite hasta 8 enteros y 4 decimales significativos.");
    }

    public static BigDecimal monetarioPersistible(BigDecimal valor) {
        BigDecimal n = monetario(valor);
        if (n.abs().compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new IllegalArgumentException("Importe excede la capacidad DECIMAL(14,2).");
        return n;
    }

    /** Reglas 3265/3266/3319: comparar los importes efectivamente serializados. */
    public static void validarCredito(BigDecimal total, BigDecimal deuda, List<BigDecimal> cuotas) {
        BigDecimal totalXml = monetario(total);
        BigDecimal deudaXml = positivoMonetario(deuda, "montoPendientePago");
        if (deudaXml.compareTo(totalXml) > 0) {
            throw new IllegalArgumentException("3265: montoPendientePago no puede superar el total del comprobante.");
        }
        if (cuotas == null || cuotas.isEmpty()) {
            throw new IllegalArgumentException("Una venta a crédito requiere cuotas.");
        }
        BigDecimal sumaXml = BigDecimal.ZERO;
        for (BigDecimal cuota : cuotas) {
            BigDecimal cuotaXml = positivoMonetario(cuota, "monto de cuota");
            if (cuotaXml.compareTo(totalXml) > 0) {
                throw new IllegalArgumentException("3266: una cuota no puede superar el total del comprobante.");
            }
            sumaXml = sumaXml.add(cuotaXml);
        }
        if (sumaXml.compareTo(deudaXml) != 0) {
            throw new IllegalArgumentException("3319: la suma de las cuotas no coincide con montoPendientePago en el XML.");
        }
    }

    private static BigDecimal positivoMonetario(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() <= 0 || monetario(valor).signum() <= 0) {
            throw new IllegalArgumentException(campo + " debe ser mayor a cero, también después del redondeo XML.");
        }
        return monetario(valor);
    }
}
