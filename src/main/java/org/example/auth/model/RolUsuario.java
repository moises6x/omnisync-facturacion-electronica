package org.example.auth.model;


public enum RolUsuario {

    /**
     * Administrador global de toda la plataforma.
     *
     * No pertenece a una empresa específica.
     */
    SUPER_ADMIN,

    /**
     * Dueño de una empresa.
     *
     * Puede gestionar:
     * - administradores
     * - trabajadores
     * - facturación
     * - configuración sensible de su empresa
     */
    EMPRESA_DUENO,

    /**
     * Administrador interno de una empresa.
     *
     * Puede gestionar facturación y trabajadores,
     * pero no operaciones globales.
     */
    EMPRESA_ADMIN,

    /**
     * Trabajador.
     *
     * Sus permisos son configurables.
     */
    TRABAJADOR
}
