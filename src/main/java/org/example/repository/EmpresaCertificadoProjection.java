package org.example.repository;

import java.time.LocalDate;

public interface EmpresaCertificadoProjection {
    String getRuc();
    String getRazonSocial();
    LocalDate getCertificadoVigenteHasta();
}
