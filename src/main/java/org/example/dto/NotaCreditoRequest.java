package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class NotaCreditoRequest {

    @NotNull
    private Long tiendaId;

    @NotBlank
    @Pattern(
            regexp = "^[FB][A-Z0-9]{3}$",
            message = "Serie inválida"
    )
    private String serie;

    @NotBlank
    @Pattern(
            regexp = "^(01|03)$",
            message = "El comprobante afectado debe ser 01 o 03"
    )
    private String tipoComprobanteAfectado;

    @NotBlank
    private String numComprobanteAfectado;

    @NotBlank
    @Pattern(
            regexp = "^[0-9]{2}$",
            message = "Código motivo SUNAT inválido"
    )
    private String codMotivoSunat;

    @NotBlank
    private String descripcionMotivo;

    // =========================================================
    // CLIENTE
    // =========================================================
    /*
     * Debe conservar los mismos datos del comprobante original.
     *
     * Tipos soportados:
     * 0 = cliente sin identificación normalizado internamente
     * 1 = DNI
     * 4 = Carné de Extranjería
     * 6 = RUC
     * 7 = Pasaporte
     */

    @NotBlank
    @Pattern(
            regexp = "^(0|1|4|6|7)$",
            message = "Tipo de documento inválido"
    )
    private String tipoDocCliente;

    @NotBlank
    @Size(max = 15)
    private String numDocCliente;

    @NotBlank
    @Size(max = 200)
    private String rznSocialCliente;

    // =========================================================
    // TOTALES
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

    @NotEmpty
    @Size(max=500, message="Máximo técnico de 500 items por solicitud")
    @Valid
    private List<DetalleItemRequest> items;

    @Data
    public static class DetalleItemRequest {

        @NotNull
        @Positive
        private BigDecimal cantidad;

        @NotBlank
        private String unidadMedida;

        @NotBlank
        private String descripcion;

        @NotNull
        @Positive
        private BigDecimal valorUnitario;

        @Positive
        private BigDecimal precioUnitario;

        @PositiveOrZero
        private BigDecimal igvItem;

        @NotBlank
        @Pattern(regexp = "^(10|20|30)$")
        private String tipoAfectacionIgv;

        @PositiveOrZero
        private BigDecimal porcentajeIgv;
    }
}
