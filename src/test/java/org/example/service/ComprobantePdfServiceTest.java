package org.example.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.example.model.Comprobante;
import org.example.model.DetalleComprobante;
import org.example.model.Empresa;
import org.example.model.EstadoComprobante;
import org.example.repository.ComprobanteRepository;
import org.example.repository.EmpresaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComprobantePdfServiceTest {

    @Mock
    private ComprobanteRepository comprobanteRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    private ComprobantePdfService service;

    @BeforeEach
    void setUp() {
        service = new ComprobantePdfService(
                comprobanteRepository,
                empresaRepository
        );
    }

    @Test
    void otraEmpresaNoDebePoderDescargarPdfPorId() {

        when(
                comprobanteRepository.findByIdAndRucEmisor(
                        10L,
                        "20600000001"
                )
        ).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.generar(
                        "20600000001",
                        10L
                )
        );

        verifyNoInteractions(empresaRepository);
    }

    @Test
    void debeGenerarPdfValidoParaComprobantePropio() {

        Comprobante c = comprobante();
        Empresa e = empresa();

        configurarMocks(c, e);

        byte[] pdf = service.generar(
                "20100130204",
                10L
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
    }

    @Test
    void debePaginarSinPerderItemsCuandoHayMuchosDetalles()
            throws Exception {

        Comprobante c = comprobante();

        /*
         * Quitamos el único detalle del comprobante base y creamos
         * 80 productos con descripciones únicas. Con la implementación
         * anterior, el "break" cortaba silenciosamente la lista.
         */
        c.getDetalles().clear();

        for (int i = 1; i <= 80; i++) {

            DetalleComprobante d = new DetalleComprobante();
            d.setItemOrden(i);
            d.setDescripcion(
                    String.format(
                            "ITEM TEST %03d",
                            i
                    )
            );
            d.setUnidadMedida("NIU");
            d.setCantidad(BigDecimal.ONE);
            d.setPrecioUnitario(new BigDecimal("1.18"));
            d.setMontoTotalItem(new BigDecimal("1.18"));

            c.addDetalle(d);
        }

        Empresa e = empresa();
        configurarMocks(c, e);

        byte[] pdf = service.generar(
                "20100130204",
                10L
        );

        try (PDDocument documento = Loader.loadPDF(pdf)) {

            assertTrue(
                    documento.getNumberOfPages() > 1,
                    "Una factura con 80 items debe generar varias paginas."
            );

            String texto =
                    new PDFTextStripper().getText(documento);

            /*
             * Verificamos TODOS los items, no solamente el primero y último.
             * Si uno se pierde por paginación, esta prueba falla.
             */
            for (int i = 1; i <= 80; i++) {
                String esperado =
                        String.format(
                                "ITEM TEST %03d",
                                i
                        );

                assertTrue(
                        texto.contains(esperado),
                        "Falta el item en el PDF: " + esperado
                );
            }

            assertTrue(texto.contains("TOTAL:"));
            assertTrue(texto.contains("Estado SUNAT:"));
        }
    }

    private void configurarMocks(
            Comprobante c,
            Empresa e
    ) {

        when(
                comprobanteRepository.findByIdAndRucEmisor(
                        10L,
                        "20100130204"
                )
        ).thenReturn(Optional.of(c));

        when(
                empresaRepository.findByRuc(
                        "20100130204"
                )
        ).thenReturn(Optional.of(e));
    }

    private Empresa empresa() {

        Empresa e = new Empresa();
        e.setRuc("20100130204");
        e.setRazonSocial("EMPRESA TEST");
        e.setDireccionFiscal("LIMA");
        return e;
    }

    private Comprobante comprobante() {

        Comprobante c = new Comprobante();
        c.setId(10L);
        c.setRucEmisor("20100130204");
        c.setTipoComprobante("03");
        c.setSerie("B001");
        c.setNumero(1);
        c.setFechaEmision(
                LocalDateTime.of(
                        2026,
                        9,
                        19,
                        10,
                        0
                )
        );
        c.setFormaPago("Contado");
        c.setTipoDocCliente("1");
        c.setNumDocCliente("48411579");
        c.setRznSocialCliente("CLIENTE TEST");
        c.setDireccionCliente("LIMA");
        c.setOpGravada(new BigDecimal("100.00"));
        c.setIgv(new BigDecimal("18.00"));
        c.setMontoTotal(new BigDecimal("118.00"));
        c.setEstadoSfs(EstadoComprobante.ACEPTADO);
        c.setHashCpe("ABC123");
        c.setMensajeSunat("ACEPTADO");

        DetalleComprobante d = new DetalleComprobante();
        d.setItemOrden(1);
        d.setDescripcion("SERVICIO TEST");
        d.setUnidadMedida("NIU");
        d.setCantidad(BigDecimal.ONE);
        d.setPrecioUnitario(new BigDecimal("118.00"));
        d.setMontoTotalItem(new BigDecimal("118.00"));

        c.addDetalle(d);
        return c;
    }
}
