package org.example.admin.service;

import org.example.admin.dto.ConfigurarTributacionEmpresaRequest;
import org.example.config.ReglasTributariasProperties;
import org.example.model.Empresa;
import org.example.repository.EmpresaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracionTributariaEmpresaServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;

    private ConfiguracionTributariaEmpresaService service;

    private Empresa empresa;

    @BeforeEach
    void setUp() {

        ReglasTributariasProperties reglas =
                new ReglasTributariasProperties();

        reglas.setIgvPorcentajesPermitidos(
                new LinkedHashSet<>(
                        Set.of(
                                new BigDecimal("18.00"),
                                new BigDecimal("10.50")
                        )
                )
        );

        service =
                new ConfiguracionTributariaEmpresaService(
                        empresaRepository,
                        reglas
                );

        empresa =
                Empresa.builder()
                        .id(1L)
                        .ruc("20100130204")
                        .razonSocial("EMPRESA TEST")
                        .igvPorcentaje(new BigDecimal("18.00"))
                        .build();
    }

    @Test
    void debeConfigurarTasaPermitidaPorEmpresa() {

        when(
                empresaRepository.bloquearPorId(1L)
        ).thenReturn(
                Optional.of(empresa)
        );

        when(
                empresaRepository.saveAndFlush(empresa)
        ).thenReturn(
                empresa
        );

        var respuesta =
                service.actualizar(
                        1L,
                        new ConfigurarTributacionEmpresaRequest(
                                new BigDecimal("10.50"),
                                "Empresa validada para tasa especial."
                        )
                );

        assertEquals(
                0,
                empresa.getIgvPorcentaje().compareTo(
                        new BigDecimal("10.50")
                )
        );

        assertEquals(
                0,
                respuesta.igvPorcentaje().compareTo(
                        new BigDecimal("10.50")
                )
        );

        verify(
                empresaRepository
        ).saveAndFlush(
                empresa
        );
    }

    @Test
    void debeRechazarTasaNoHabilitada() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.actualizar(
                                1L,
                                new ConfigurarTributacionEmpresaRequest(
                                        new BigDecimal("17.00"),
                                        "Intento inválido"
                                )
                        )
        );

        verify(
                empresaRepository,
                never()
        ).saveAndFlush(
                empresa
        );
    }

    @Test
    void debeRechazarEmpresaInexistente() {

        when(
                empresaRepository.bloquearPorId(999L)
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.actualizar(
                                999L,
                                new ConfigurarTributacionEmpresaRequest(
                                        new BigDecimal("18.00"),
                                        "Configuración inicial"
                                )
                        )
        );
    }
}
