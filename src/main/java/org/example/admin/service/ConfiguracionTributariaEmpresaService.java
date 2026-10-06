package org.example.admin.service;

import lombok.RequiredArgsConstructor;

import org.example.admin.dto.ConfiguracionTributariaEmpresaResponse;
import org.example.admin.dto.ConfigurarTributacionEmpresaRequest;
import org.example.config.ReglasTributariasProperties;
import org.example.model.Empresa;
import org.example.repository.EmpresaRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Configuración tributaria por tenant.
 *
 * Este servicio NO determina si una empresa califica legalmente para una
 * tasa especial. Sólo permite registrar una tasa soportada por esta versión
 * después de que esa condición haya sido verificada administrativamente.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionTributariaEmpresaService {

    private final EmpresaRepository empresaRepository;
    private final ReglasTributariasProperties reglas;

    @Transactional(readOnly = true)
    public ConfiguracionTributariaEmpresaResponse obtener(
            Long empresaId
    ) {

        Empresa empresa = buscarEmpresa(empresaId);

        return convertir(empresa);
    }

    @Transactional
    public ConfiguracionTributariaEmpresaResponse actualizar(
            Long empresaId,
            ConfigurarTributacionEmpresaRequest request
    ) {

        if (request == null || request.igvPorcentaje() == null) {
            throw new IllegalArgumentException(
                    "La tasa IGV es obligatoria."
            );
        }

        String motivo =
                request.motivo() != null
                        ? request.motivo().trim()
                        : null;

        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe indicar el motivo de la configuración tributaria."
            );
        }

        BigDecimal porcentaje =
                request.igvPorcentaje()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (!estaPermitido(porcentaje)) {
            throw new IllegalArgumentException(
                    "La tasa IGV "
                            + porcentaje.toPlainString()
                            + "% no está habilitada por esta versión del sistema."
            );
        }

        Empresa empresa = empresaRepository.bloquearPorId(empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada."));

        empresa.setIgvPorcentaje(
                porcentaje
        );

        empresa.setIgvConfiguradoEn(
                LocalDateTime.now()
        );

        empresa.setIgvConfiguracionMotivo(
                motivo
        );

        Empresa guardada =
                empresaRepository.saveAndFlush(
                        empresa
                );

        return convertir(guardada);
    }

    private boolean estaPermitido(
            BigDecimal porcentaje
    ) {

        if (
                reglas.getIgvPorcentajesPermitidos() == null
                        || reglas.getIgvPorcentajesPermitidos().isEmpty()
        ) {
            return false;
        }

        return reglas.getIgvPorcentajesPermitidos()
                .stream()
                .filter(valor -> valor != null)
                .anyMatch(
                        valor ->
                                valor.compareTo(porcentaje) == 0
                );
    }

    private Empresa buscarEmpresa(
            Long empresaId
    ) {

        if (empresaId == null) {
            throw new IllegalArgumentException(
                    "Empresa requerida."
            );
        }

        return empresaRepository
                .findById(empresaId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Empresa no encontrada."
                                )
                );
    }

    private ConfiguracionTributariaEmpresaResponse convertir(
            Empresa empresa
    ) {

        return new ConfiguracionTributariaEmpresaResponse(
                empresa.getId(),
                empresa.getRuc(),
                empresa.getRazonSocial(),
                empresa.igvPorcentajeEfectivo(),
                empresa.getIgvConfiguradoEn(),
                empresa.getIgvConfiguracionMotivo()
        );
    }
}
