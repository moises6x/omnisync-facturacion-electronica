package org.example.service;

import lombok.RequiredArgsConstructor;

import org.example.dto.NotaVentaRequest;
import org.example.model.NotaVenta;
import org.example.repository.NotaVentaRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotaVentaServiceImpl
        implements NotaVentaService {

    private final
    NotaVentaRepository notaVentaRepository;

    private final
    CorrelativoService correlativoService;

    @Override
    @Transactional
    public NotaVenta procesarNotaVenta(

            String rucEmisor,

            NotaVentaRequest request

    ) {

        String serieInterna =
                "T" + request.getTiendaId();

        int correlativo =
                correlativoService.siguiente(

                        rucEmisor,

                        "NV",

                        serieInterna
                );

        String codigoTicket =
                String.format(

                        "NV-T%d-%08d",

                        request.getTiendaId(),

                        correlativo
                );

        NotaVenta nota =
                NotaVenta.builder()

                        .tiendaId(
                                request.getTiendaId()
                        )

                        .rucEmisor(
                                rucEmisor
                        )

                        .codigoTicket(
                                codigoTicket
                        )

                        .montoTotal(
                                request
                                        .getMontoTotal()
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP
                                        )
                        )

                        .observacion(
                                request.getObservacion()
                                        != null
                                        ? request.getObservacion()
                                        : "Venta rápida sin comprobante SUNAT"
                        )

                        .fechaEmision(
                                LocalDateTime.now()
                        )

                        .build();

        return notaVentaRepository
                .save(
                        nota
                );
    }
}