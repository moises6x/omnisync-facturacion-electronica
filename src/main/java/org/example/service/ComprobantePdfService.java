package org.example.service;

import lombok.RequiredArgsConstructor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.example.model.Comprobante;
import org.example.model.DetalleComprobante;
import org.example.model.Empresa;

import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class ComprobantePdfService {

    private final ComprobanteRepository comprobanteRepository;
    private final EmpresaRepository empresaRepository;

    private static final DateTimeFormatter FECHA_FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final DateTimeFormatter FECHA_QR =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // =========================================================
    // MEDIDAS PDF
    // =========================================================

    private static final float MARGEN_IZQUIERDO = 45f;
    private static final float ANCHO_CONTENIDO = 505f;
    private static final float ALTO_FILA_DETALLE = 18f;

    /*
     * En páginas de detalle podemos aprovechar casi toda la hoja.
     * Cuando se llega a este límite se crea automáticamente otra página.
     */
    private static final float Y_MINIMO_DETALLE = 90f;

    /*
     * Para dibujar totales + estado SUNAT + hash sin invadir el QR
     * necesitamos una zona libre suficiente. Si no existe, el resumen
     * pasa automáticamente a una nueva página.
     */
    private static final float Y_MINIMO_RESUMEN = 320f;

    // =========================================================
    // FUENTES
    // =========================================================

    private final PDType1Font fuenteNormal =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private final PDType1Font fuenteBold =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);


    // =========================================================
    // GENERAR PDF
    // =========================================================

    @Transactional(readOnly = true)
    public byte[] generar(
            String rucEmisor,
            Long comprobanteId
    ) {

        Comprobante comprobante =
                comprobanteRepository
                        .findByIdAndRucEmisor(
                                comprobanteId,
                                rucEmisor
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Comprobante no encontrado."
                                )
                        );

        Empresa empresa =
                empresaRepository
                        .findByRuc(rucEmisor)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Empresa no encontrada."
                                )
                        );

        try (
                PDDocument documento = new PDDocument();
                ByteArrayOutputStream salida = new ByteArrayOutputStream()
        ) {

            dibujarDocumento(
                    documento,
                    empresa,
                    comprobante
            );

            documento.save(salida);
            return salida.toByteArray();

        } catch (IOException e) {

            throw new IllegalStateException(
                    "No se pudo generar el PDF del comprobante.",
                    e
            );
        }
    }


    // =========================================================
    // DIBUJAR DOCUMENTO MULTIPÁGINA
    // =========================================================

    private void dibujarDocumento(
            PDDocument documento,
            Empresa empresa,
            Comprobante comprobante
    ) throws IOException {

        PaginaTrabajo pagina = null;

        try {
            // Primera página: cabecera completa + cliente + tabla.
            pagina = crearPrimeraPagina(
                    documento,
                    empresa,
                    comprobante
            );

            List<DetalleComprobante> detalles =
                    comprobante.getDetalles();

            if (detalles != null) {
                for (DetalleComprobante detalle : detalles) {

                    /*
                     * Antes de pintar una nueva fila comprobamos si todavía
                     * existe espacio. Si no existe, cerramos correctamente la
                     * tabla/página y continuamos en una nueva hoja.
                     *
                     * IMPORTANTE: aquí ya NO existe el antiguo "break" que
                     * descartaba silenciosamente los productos restantes.
                     */
                    if (pagina.y < Y_MINIMO_DETALLE) {
                        finalizarPaginaDetalles(
                                pagina.content,
                                pagina.y,
                                true
                        );

                        pagina.close();

                        pagina = crearPaginaContinuacionDetalles(
                                documento,
                                empresa,
                                comprobante
                        );
                    }

                    dibujarDetalle(
                            pagina.content,
                            detalle,
                            pagina.y
                    );

                    pagina.y -= ALTO_FILA_DETALLE;
                }
            }

            // Cerramos visualmente la tabla de la última página de detalle.
            pagina.y = finalizarPaginaDetalles(
                    pagina.content,
                    pagina.y,
                    false
            );

            /*
             * Si los detalles dejaron poco espacio, NO apretamos los totales
             * encima del QR ni de los datos SUNAT. Creamos una página final
             * dedicada al resumen del comprobante.
             */
            if (pagina.y < Y_MINIMO_RESUMEN) {
                pagina.close();

                pagina = crearPaginaResumen(
                        documento,
                        empresa,
                        comprobante
                );
            }

            dibujarResumenFinal(
                    documento,
                    pagina.content,
                    comprobante,
                    pagina.y
            );

        } finally {
            if (pagina != null) {
                pagina.close();
            }
        }
    }


    // =========================================================
    // PRIMERA PÁGINA
    // =========================================================

    private PaginaTrabajo crearPrimeraPagina(
            PDDocument documento,
            Empresa empresa,
            Comprobante comprobante
    ) throws IOException {

        PDPage pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);

        PDPageContentStream content =
                new PDPageContentStream(
                        documento,
                        pagina
                );

        float y = 795f;

        // EMPRESA
        escribir(
                content,
                fuenteBold,
                15,
                MARGEN_IZQUIERDO,
                y,
                textoSeguro(empresa.getRazonSocial())
        );

        y -= 20;

        escribir(
                content,
                fuenteNormal,
                9,
                MARGEN_IZQUIERDO,
                y,
                "RUC: " + textoSeguro(empresa.getRuc())
        );

        y -= 14;

        escribir(
                content,
                fuenteNormal,
                9,
                MARGEN_IZQUIERDO,
                y,
                textoSeguro(empresa.getDireccionFiscal())
        );

        // TIPO DE COMPROBANTE
        y -= 40;

        escribirCentrado(
                content,
                fuenteBold,
                15,
                tituloComprobante(comprobante),
                MARGEN_IZQUIERDO,
                ANCHO_CONTENIDO,
                y
        );

        y -= 23;

        escribirCentrado(
                content,
                fuenteBold,
                14,
                numeroCompleto(comprobante),
                MARGEN_IZQUIERDO,
                ANCHO_CONTENIDO,
                y
        );

        y -= 35;

        linea(
                content,
                MARGEN_IZQUIERDO,
                y,
                MARGEN_IZQUIERDO + ANCHO_CONTENIDO,
                y
        );

        y -= 22;

        // DATOS DEL COMPROBANTE
        escribir(
                content,
                fuenteBold,
                9,
                MARGEN_IZQUIERDO,
                y,
                "Fecha de emision:"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                150,
                y,
                comprobante.getFechaEmision().format(FECHA_FORMATO)
        );

        y -= 17;

        escribir(
                content,
                fuenteBold,
                9,
                MARGEN_IZQUIERDO,
                y,
                "Forma de pago:"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                150,
                y,
                textoSeguro(comprobante.getFormaPago())
        );

        y -= 28;

        // CLIENTE
        escribir(
                content,
                fuenteBold,
                11,
                MARGEN_IZQUIERDO,
                y,
                "CLIENTE"
        );

        y -= 18;

        escribir(
                content,
                fuenteBold,
                9,
                MARGEN_IZQUIERDO,
                y,
                nombreDocumento(comprobante.getTipoDocCliente()) + ":"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                150,
                y,
                textoSeguro(comprobante.getNumDocCliente())
        );

        y -= 17;

        escribir(
                content,
                fuenteBold,
                9,
                MARGEN_IZQUIERDO,
                y,
                "Nombre:"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                150,
                y,
                limitar(
                        textoSeguro(comprobante.getRznSocialCliente()),
                        65
                )
        );

        if (
                comprobante.getDireccionCliente() != null
                        && !comprobante.getDireccionCliente().isBlank()
        ) {
            y -= 17;

            escribir(
                    content,
                    fuenteBold,
                    9,
                    MARGEN_IZQUIERDO,
                    y,
                    "Direccion:"
            );

            escribir(
                    content,
                    fuenteNormal,
                    9,
                    150,
                    y,
                    limitar(
                            comprobante.getDireccionCliente(),
                            65
                    )
            );
        }

        y -= 30;

        y = dibujarCabeceraTabla(
                content,
                y
        );

        return new PaginaTrabajo(
                content,
                y
        );
    }


    // =========================================================
    // PÁGINA DE CONTINUACIÓN DE DETALLES
    // =========================================================

    private PaginaTrabajo crearPaginaContinuacionDetalles(
            PDDocument documento,
            Empresa empresa,
            Comprobante comprobante
    ) throws IOException {

        PDPage pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);

        PDPageContentStream content =
                new PDPageContentStream(
                        documento,
                        pagina
                );

        float y = dibujarCabeceraContinuacion(
                content,
                empresa,
                comprobante,
                "CONTINUACION DE ITEMS"
        );

        y -= 18;

        y = dibujarCabeceraTabla(
                content,
                y
        );

        return new PaginaTrabajo(
                content,
                y
        );
    }


    // =========================================================
    // PÁGINA FINAL DE RESUMEN
    // =========================================================

    private PaginaTrabajo crearPaginaResumen(
            PDDocument documento,
            Empresa empresa,
            Comprobante comprobante
    ) throws IOException {

        PDPage pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);

        PDPageContentStream content =
                new PDPageContentStream(
                        documento,
                        pagina
                );

        float y = dibujarCabeceraContinuacion(
                content,
                empresa,
                comprobante,
                "RESUMEN DEL COMPROBANTE"
        );

        y -= 25;

        return new PaginaTrabajo(
                content,
                y
        );
    }


    // =========================================================
    // CABECERA COMPACTA PARA PÁGINAS 2+
    // =========================================================

    private float dibujarCabeceraContinuacion(
            PDPageContentStream content,
            Empresa empresa,
            Comprobante comprobante,
            String subtitulo
    ) throws IOException {

        float y = 790f;

        escribir(
                content,
                fuenteBold,
                12,
                MARGEN_IZQUIERDO,
                y,
                limitar(
                        textoSeguro(empresa.getRazonSocial()),
                        65
                )
        );

        y -= 17;

        escribir(
                content,
                fuenteNormal,
                8,
                MARGEN_IZQUIERDO,
                y,
                "RUC: " + textoSeguro(empresa.getRuc())
        );

        escribir(
                content,
                fuenteBold,
                10,
                360,
                y,
                numeroCompleto(comprobante)
        );

        y -= 24;

        escribirCentrado(
                content,
                fuenteBold,
                10,
                subtitulo,
                MARGEN_IZQUIERDO,
                ANCHO_CONTENIDO,
                y
        );

        y -= 14;

        linea(
                content,
                MARGEN_IZQUIERDO,
                y,
                MARGEN_IZQUIERDO + ANCHO_CONTENIDO,
                y
        );

        return y;
    }


    // =========================================================
    // TABLA DE ITEMS
    // =========================================================

    private float dibujarCabeceraTabla(
            PDPageContentStream content,
            float y
    ) throws IOException {

        float xCantidad = MARGEN_IZQUIERDO;
        float xDescripcion = 100f;
        float xPrecio = 390f;
        float xTotal = 465f;

        escribir(
                content,
                fuenteBold,
                8,
                xCantidad,
                y,
                "CANT."
        );

        escribir(
                content,
                fuenteBold,
                8,
                xDescripcion,
                y,
                "DESCRIPCION"
        );

        escribir(
                content,
                fuenteBold,
                8,
                xPrecio,
                y,
                "P. UNIT."
        );

        escribir(
                content,
                fuenteBold,
                8,
                xTotal,
                y,
                "TOTAL"
        );

        y -= 8;

        linea(
                content,
                MARGEN_IZQUIERDO,
                y,
                MARGEN_IZQUIERDO + ANCHO_CONTENIDO,
                y
        );

        return y - 17;
    }


    private void dibujarDetalle(
            PDPageContentStream content,
            DetalleComprobante detalle,
            float y
    ) throws IOException {

        escribir(
                content,
                fuenteNormal,
                8,
                MARGEN_IZQUIERDO,
                y,
                numero(detalle.getCantidad())
        );

        escribir(
                content,
                fuenteNormal,
                8,
                100,
                y,
                limitar(
                        textoSeguro(detalle.getDescripcion()),
                        48
                )
        );

        escribir(
                content,
                fuenteNormal,
                8,
                390,
                y,
                dinero(detalle.getPrecioUnitario())
        );

        escribir(
                content,
                fuenteNormal,
                8,
                465,
                y,
                dinero(detalle.getMontoTotalItem())
        );
    }


    /**
     * Cierra visualmente la tabla de detalles.
     *
     * @return nueva coordenada Y disponible.
     */
    private float finalizarPaginaDetalles(
            PDPageContentStream content,
            float y,
            boolean continua
    ) throws IOException {

        float lineaY = Math.max(
                55f,
                y - 4f
        );

        linea(
                content,
                MARGEN_IZQUIERDO,
                lineaY,
                MARGEN_IZQUIERDO + ANCHO_CONTENIDO,
                lineaY
        );

        if (continua && lineaY > 72f) {
            escribir(
                    content,
                    fuenteNormal,
                    7,
                    MARGEN_IZQUIERDO,
                    lineaY - 14,
                    "Continua en la siguiente pagina."
            );
        }

        return lineaY;
    }


    // =========================================================
    // TOTALES + SUNAT + QR (SOLO EN LA ÚLTIMA PÁGINA)
    // =========================================================

    private void dibujarResumenFinal(
            PDDocument documento,
            PDPageContentStream content,
            Comprobante comprobante,
            float y
    ) throws IOException {

        y -= 25;

        float xEtiquetaTotal = 385f;
        float xValorTotal = 480f;

        escribir(
                content,
                fuenteNormal,
                9,
                xEtiquetaTotal,
                y,
                "Op. gravada:"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                xValorTotal,
                y,
                dinero(comprobante.getOpGravada())
        );

        y -= 17;

        escribir(
                content,
                fuenteNormal,
                9,
                xEtiquetaTotal,
                y,
                "IGV:"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                xValorTotal,
                y,
                dinero(comprobante.getIgv())
        );

        y -= 20;

        escribir(
                content,
                fuenteBold,
                11,
                xEtiquetaTotal,
                y,
                "TOTAL:"
        );

        escribir(
                content,
                fuenteBold,
                11,
                xValorTotal,
                y,
                dinero(comprobante.getMontoTotal())
        );

        // SUNAT
        y -= 45;

        linea(
                content,
                MARGEN_IZQUIERDO,
                y,
                MARGEN_IZQUIERDO + ANCHO_CONTENIDO,
                y
        );

        y -= 20;

        escribir(
                content,
                fuenteBold,
                9,
                MARGEN_IZQUIERDO,
                y,
                "Estado SUNAT:"
        );

        escribir(
                content,
                fuenteNormal,
                9,
                150,
                y,
                comprobante.getEstadoSfs() != null
                        ? comprobante.getEstadoSfs().name()
                        : ""
        );

        y -= 17;

        if (
                comprobante.getMensajeSunat() != null
                        && !comprobante.getMensajeSunat().isBlank()
        ) {
            escribir(
                    content,
                    fuenteNormal,
                    8,
                    MARGEN_IZQUIERDO,
                    y,
                    limitar(
                            comprobante.getMensajeSunat(),
                            95
                    )
            );

            y -= 17;
        }

        if (
                comprobante.getHashCpe() != null
                        && !comprobante.getHashCpe().isBlank()
        ) {
            escribir(
                    content,
                    fuenteNormal,
                    7,
                    MARGEN_IZQUIERDO,
                    y,
                    "Hash: " + limitar(
                            comprobante.getHashCpe(),
                            80
                    )
            );
        }

        // QR SUNAT: únicamente en la última página.
        dibujarQrSunat(
                documento,
                content,
                comprobante,
                MARGEN_IZQUIERDO,
                32,
                96
        );

        escribir(
                content,
                fuenteNormal,
                7,
                MARGEN_IZQUIERDO + 115,
                92,
                "Representacion impresa del comprobante electronico."
        );

        escribir(
                content,
                fuenteNormal,
                7,
                MARGEN_IZQUIERDO + 115,
                80,
                "Consulte el documento utilizando los datos del comprobante."
        );
    }


    // =========================================================
    // QR SUNAT
    // =========================================================

    private void dibujarQrSunat(
            PDDocument documento,
            PDPageContentStream content,
            Comprobante comprobante,
            float x,
            float y,
            float size
    ) throws IOException {

        try {
            String data = construirContenidoQrSunat(comprobante);

            Map<EncodeHintType, Object> hints =
                    new EnumMap<>(EncodeHintType.class);

            hints.put(
                    EncodeHintType.ERROR_CORRECTION,
                    ErrorCorrectionLevel.Q
            );
            hints.put(
                    EncodeHintType.CHARACTER_SET,
                    "UTF-8"
            );
            hints.put(
                    EncodeHintType.MARGIN,
                    2
            );

            BitMatrix matrix =
                    new QRCodeWriter().encode(
                            data,
                            BarcodeFormat.QR_CODE,
                            240,
                            240,
                            hints
                    );

            BufferedImage image =
                    MatrixToImageWriter.toBufferedImage(matrix);

            PDImageXObject pdfImage =
                    LosslessFactory.createFromImage(
                            documento,
                            image
                    );

            content.drawImage(
                    pdfImage,
                    x,
                    y,
                    size,
                    size
            );

        } catch (WriterException e) {
            throw new IOException(
                    "No se pudo generar el QR SUNAT.",
                    e
            );
        }
    }


    /**
     * Orden establecido en la especificación técnica SUNAT:
     *
     * RUC | TIPO | SERIE | NUMERO | IGV | TOTAL | FECHA |
     * TIPO DOC ADQUIRENTE | NUM DOC ADQUIRENTE | VALOR RESUMEN
     */
    String construirContenidoQrSunat(
            Comprobante comprobante
    ) {

        return String.join(
                "|",
                textoSeguro(comprobante.getRucEmisor()),
                textoSeguro(comprobante.getTipoComprobante()),
                textoSeguro(comprobante.getSerie()),
                String.format("%08d", comprobante.getNumero()),
                dineroQr(comprobante.getIgv()),
                dineroQr(comprobante.getMontoTotal()),
                comprobante.getFechaEmision()
                        .toLocalDate()
                        .format(FECHA_QR),
                textoSeguro(comprobante.getTipoDocCliente()),
                textoSeguro(comprobante.getNumDocCliente()),
                textoSeguro(comprobante.getHashCpe())
        );
    }


    private String dineroQr(
            BigDecimal value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .setScale(
                        2,
                        java.math.RoundingMode.HALF_UP
                )
                .toPlainString();
    }


    // =========================================================
    // TEXTOS DEL COMPROBANTE
    // =========================================================

    private String tituloComprobante(
            Comprobante comprobante
    ) {

        return switch (comprobante.getTipoComprobante()) {
            case "01" -> "FACTURA ELECTRONICA";
            case "03" -> "BOLETA DE VENTA ELECTRONICA";
            case "07" -> "NOTA DE CREDITO ELECTRONICA";
            default -> "COMPROBANTE ELECTRONICO";
        };
    }


    private String numeroCompleto(
            Comprobante comprobante
    ) {

        return textoSeguro(comprobante.getSerie())
                + "-"
                + String.format(
                "%08d",
                comprobante.getNumero()
        );
    }


    // =========================================================
    // DOCUMENTO CLIENTE
    // =========================================================

    private String nombreDocumento(
            String tipo
    ) {

        if (tipo == null) {
            return "Documento";
        }

        return switch (tipo) {
            case "1" -> "DNI";
            case "4" -> "Carne de Extranjeria";
            case "6" -> "RUC";
            case "7" -> "Pasaporte";
            default -> "Documento";
        };
    }


    // =========================================================
    // TEXTO
    // =========================================================

    private void escribir(
            PDPageContentStream content,
            PDType1Font fuente,
            float tamanio,
            float x,
            float y,
            String texto
    ) throws IOException {

        content.beginText();
        content.setFont(fuente, tamanio);
        content.newLineAtOffset(x, y);
        content.showText(normalizarTexto(texto));
        content.endText();
    }


    private void escribirCentrado(
            PDPageContentStream content,
            PDType1Font fuente,
            float tamanio,
            String texto,
            float xInicio,
            float ancho,
            float y
    ) throws IOException {

        String limpio = normalizarTexto(texto);

        float anchoTexto =
                fuente.getStringWidth(limpio)
                        / 1000
                        * tamanio;

        float x =
                xInicio
                        + (ancho - anchoTexto) / 2;

        escribir(
                content,
                fuente,
                tamanio,
                x,
                y,
                limpio
        );
    }


    private void linea(
            PDPageContentStream content,
            float x1,
            float y1,
            float x2,
            float y2
    ) throws IOException {

        content.setLineWidth(0.4f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }


    // =========================================================
    // FORMATOS
    // =========================================================

    private String dinero(
            BigDecimal monto
    ) {

        if (monto == null) {
            return "S/ 0.00";
        }

        return String.format(
                "S/ %.2f",
                monto
        );
    }


    private String numero(
            BigDecimal numero
    ) {

        if (numero == null) {
            return "0";
        }

        return numero
                .stripTrailingZeros()
                .toPlainString();
    }


    private String textoSeguro(
            String texto
    ) {

        return texto == null
                ? ""
                : texto;
    }


    private String limitar(
            String texto,
            int maximo
    ) {

        if (texto == null) {
            return "";
        }

        if (texto.length() <= maximo) {
            return texto;
        }

        return texto.substring(
                0,
                maximo - 3
        ) + "...";
    }


    private String normalizarTexto(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("\n", " ")
                .replace("\r", " ");
    }


    // =========================================================
    // CONTEXTO INTERNO DE PÁGINA
    // =========================================================

    private static final class PaginaTrabajo {

        private final PDPageContentStream content;
        private float y;
        private boolean closed;

        private PaginaTrabajo(
                PDPageContentStream content,
                float y
        ) {
            this.content = content;
            this.y = y;
        }

        private void close() throws IOException {
            if (!closed) {
                content.close();
                closed = true;
            }
        }
    }
}
