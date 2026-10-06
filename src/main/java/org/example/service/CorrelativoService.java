package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.SecuenciaCorrelativo;
import org.example.repository.SecuenciaCorrelativoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorrelativoService {

    private final SecuenciaCorrelativoRepository repository;

    @Transactional(
            propagation = Propagation.MANDATORY
    )
    public int siguiente(
            String ruc,
            String tipo,
            String serie
    ) {

        repository.inicializarSiNoExiste(
                ruc,
                tipo,
                serie
        );

        SecuenciaCorrelativo secuencia = repository
                .obtenerSecuenciaBloqueada(
                        ruc,
                        tipo,
                        serie
                )
                .orElseThrow();

        int siguiente = secuencia.getUltimoNumero() + 1;

        if (siguiente > 99_999_999) {
            throw new IllegalStateException(
                    "La serie alcanzó el máximo de 8 dígitos de correlativo."
            );
        }

        secuencia.setUltimoNumero(siguiente);
        repository.save(secuencia);

        return siguiente;
    }

    /**
     * Configuración inicial para migrar una serie existente.
     *
     * Ejemplo:
     * el cliente ya emitió hasta F001-400.
     * Configuras 400 y el siguiente generado será 401.
     *
     * Por seguridad NO permite modificar una secuencia que ya comenzó a usarse
     * dentro de este sistema.
     */
    @Transactional
    public int configurarInicial(
            String ruc,
            String tipo,
            String serie,
            int ultimoNumero
    ) {

        validarSerie(tipo, serie);

        repository.inicializarSiNoExiste(
                ruc,
                tipo,
                serie
        );

        SecuenciaCorrelativo secuencia = repository
                .obtenerSecuenciaBloqueada(
                        ruc,
                        tipo,
                        serie
                )
                .orElseThrow();

        if (secuencia.getUltimoNumero() != null
                && secuencia.getUltimoNumero() != 0) {

            throw new IllegalArgumentException(
                    "La serie ya fue utilizada en este sistema y no puede reinicializarse."
            );
        }

        secuencia.setUltimoNumero(ultimoNumero);
        repository.saveAndFlush(secuencia);

        return ultimoNumero + 1;
    }

    private void validarSerie(String tipo, String serie) {

        if ("01".equals(tipo) && !serie.startsWith("F")) {
            throw new IllegalArgumentException(
                    "Una factura debe usar serie que empiece con F."
            );
        }

        if ("03".equals(tipo) && !serie.startsWith("B")) {
            throw new IllegalArgumentException(
                    "Una boleta debe usar serie que empiece con B."
            );
        }

        if ("07".equals(tipo)
                && !(serie.startsWith("F") || serie.startsWith("B"))) {
            throw new IllegalArgumentException(
                    "La Nota de Crédito debe usar serie que empiece con F o B."
            );
        }
    }
}
