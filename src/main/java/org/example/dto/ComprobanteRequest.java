package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ComprobanteRequest {

    @NotNull(message = "El ID de la tienda es obligatorio")
    private Long tiendaId;

    /**
     * Catálogo SUNAT 51.
     * 0101 = Venta interna.
     */
    @NotBlank(message = "El tipo de operación es obligatorio")
    @Pattern(
            regexp = "^[0-9]{4}$",
            message = "Tipo de operación SUNAT inválido"
    )
    private String tipoOperacion = "0101";

    /**
     * 01 = Factura
     * 03 = Boleta
     */
    @NotBlank(message = "El tipo de comprobante es obligatorio")
    @Pattern(
            regexp = "^(01|03)$",
            message = "Tipo de comprobante inválido. Use 01 o 03"
    )
    private String tipoComprobante;

    @NotBlank(message = "La serie es obligatoria")
    @Pattern(
            regexp = "^[FB][A-Z0-9]{3}$",
            message = "Serie inválida. Ejemplo F001 o B001"
    )
    private String serie;

    // El frontend NO genera el correlativo. Lo genera el backend.

    // =========================================================
    // CLIENTE
    // =========================================================
    /*
     * Para Factura 01 estos datos son obligatorios y el backend
     * exige RUC.
     *
     * Para Boleta 03:
     * - pueden omitirse completamente cuando corresponda una
     *   boleta sin identificación del adquirente;
     * - si se informa un documento, el backend valida su tipo.
     *
     * Tipos soportados actualmente:
     * 0 = uso interno para boleta sin identificación / otros
     * 1 = DNI
     * 4 = Carné de Extranjería
     * 6 = RUC
     * 7 = Pasaporte
     *
     * Las reglas condicionales se aplican en
     * ValidacionTributariaService.
     */

    @Size(max = 1, message = "Tipo de documento demasiado largo")
    private String tipoDocCliente;

    @Size(max = 15, message = "Número de documento demasiado largo")
    private String numDocCliente;

    @Size(max = 200, message = "Nombre o razón social demasiado largo")
    private String rznSocialCliente;

    @Size(max = 250, message = "Dirección del cliente demasiado larga")
    private String direccionCliente;

    // =========================================================
    // FORMA DE PAGO
    // =========================================================

    @NotBlank
    @Pattern(
            regexp = "(?i)^(Contado|Credito)$",
            message = "Forma de pago debe ser Contado o Credito"
    )
    private String formaPago = "Contado";

    private BigDecimal montoPendientePago;

    @Valid
    @Size(max=120, message="Máximo técnico de 120 cuotas por solicitud")
    private List<CuotaRequest> cuotas;

    // =========================================================
    // TOTALES ENVIADOS
    // =========================================================

    @NotNull
    @PositiveOrZero
    private BigDecimal opGravada;

    @NotNull
    @PositiveOrZero
    private BigDecimal opExonerada;

    @NotNull
    @PositiveOrZero
    private BigDecimal opInafecta;

    @NotNull
    @PositiveOrZero
    private BigDecimal igv;

    @NotNull
    @Positive
    private BigDecimal total;

    // =========================================================
    // ITEMS
    // =========================================================

    @NotEmpty(message = "Debe existir al menos un item")
    @Size(max=500, message="Máximo técnico de 500 items por solicitud")
    @Valid
    private List<DetalleItemRequest> items;

    @Data
    public static class DetalleItemRequest {

        @NotNull
        @Positive
        private BigDecimal cantidad;

        @NotBlank
        @Size(max = 5)
        private String unidadMedida;

        @NotBlank
        @Size(max = 500)
        private String descripcion;

        /** Precio SIN IGV. */
        @NotNull
        @Positive
        private BigDecimal valorUnitario;

        /** Si viene, el backend lo compara con el cálculo propio. */
        @Positive
        private BigDecimal precioUnitario;

        @PositiveOrZero
        private BigDecimal igvItem;

        /**
         * 10 = Gravado
         * 20 = Exonerado
         * 30 = Inafecto
         */
        @NotBlank
        @Pattern(
                regexp = "^(10|20|30)$",
                message = "Tipo de afectación IGV inválido"
        )
        private String tipoAfectacionIgv;

        /** Para gravados normalmente 18. Si no viene, usa configuración. */
        @PositiveOrZero
        private BigDecimal porcentajeIgv;
    }

    @Data
    public static class CuotaRequest {

        @NotNull
        @Positive
        private BigDecimal monto;

        @NotNull
        private LocalDate fechaVencimiento;
    }
}
