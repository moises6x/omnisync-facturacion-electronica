package org.example.repository;

import org.example.model.RespuestaSunatRecibida;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RespuestaSunatRecibidaRepository extends JpaRepository<RespuestaSunatRecibida, Long> {
    List<RespuestaSunatRecibida> findByRucEmisorAndFlujoAndDocumentoIdOrderByIdAsc(
            String rucEmisor, RespuestaSunatRecibida.Flujo flujo, Long documentoId);
}
