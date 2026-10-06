package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.ConsultaRucResponse;
import org.example.model.PadronRuc;
import org.example.repository.PadronRucRepository;
import org.example.util.RucUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PadronRucService {

    private final PadronRucRepository padronRucRepository;

    @Transactional(readOnly = true)
    public ConsultaRucResponse consultar(String ruc) {

        if (ruc == null || ruc.isBlank()) {
            throw new IllegalArgumentException("El RUC es obligatorio.");
        }

        String rucNormalizado = ruc.trim();

        if (!RucUtils.esValido(rucNormalizado)) {
            throw new IllegalArgumentException(
                    "El RUC ingresado no tiene un formato o dígito verificador válido."
            );
        }

        return padronRucRepository
                .findById(rucNormalizado)
                .map(this::convertir)
                .orElseGet(() -> new ConsultaRucResponse(
                        false, rucNormalizado, null, null, null, null, null, false, false
                ));
    }

    private ConsultaRucResponse convertir(PadronRuc p) {

        String estado = normalizarDato(p.getEstado());
        String condicion = normalizarDato(p.getCondicion());

        return new ConsultaRucResponse(
                true,
                p.getRuc(),
                normalizarDato(p.getRazonSocial()),
                estado,
                condicion,
                normalizarDato(p.getUbigeo()),
                construirDireccion(p),
                "ACTIVO".equalsIgnoreCase(estado),
                "HABIDO".equalsIgnoreCase(condicion)
        );
    }

    private String construirDireccion(PadronRuc p) {

        List<String> partes = new ArrayList<>();

        agregar(partes, p.getTipoVia());
        agregar(partes, p.getNombreVia());
        agregarConEtiqueta(partes, "NRO.", p.getNumero());
        agregarConEtiqueta(partes, "INT.", p.getInterior());
        agregarConEtiqueta(partes, "LT.", p.getLote());
        agregarConEtiqueta(partes, "DPTO.", p.getDepartamento());
        agregarConEtiqueta(partes, "MZ.", p.getManzana());
        agregarConEtiqueta(partes, "KM.", p.getKilometro());

        String tipoZona = normalizarDato(p.getTipoZona());
        String codigoZona = normalizarDato(p.getCodigoZona());

        if (tipoZona != null || codigoZona != null) {
            String zona = ((tipoZona != null ? tipoZona : "") + " "
                    + (codigoZona != null ? codigoZona : "")).trim();
            if (!zona.isBlank()) {
                partes.add(zona);
            }
        }

        if (partes.isEmpty()) {
            return null;
        }

        return String.join(" ", partes).replaceAll("\s+", " ").trim();
    }

    private void agregar(List<String> partes, String valor) {
        String limpio = normalizarDato(valor);
        if (limpio != null) {
            partes.add(limpio);
        }
    }

    private void agregarConEtiqueta(List<String> partes, String etiqueta, String valor) {
        String limpio = normalizarDato(valor);
        if (limpio != null) {
            partes.add(etiqueta + " " + limpio);
        }
    }

    private String normalizarDato(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        if (limpio.isBlank() || "-".equals(limpio)) return null;
        return limpio;
    }
}
