# Propuesta aditiva: conservar respuestas SUNAT recibidas

Estado: código y pruebas locales, **no aplicada a producción**. Flyway sigue desactivado. No se propone baseline ni una migración automática. La validación usa únicamente `bd_facturacion_test`, exclusiva/desechable autorizada con `create-drop`.

## Tabla y propósito

`respuesta_sunat_recibida` guarda cada ZIP recibido antes del parser en una transacción independiente. Los bytes pueden ser inválidos: no es una tabla de CDR validados ni acredita aceptación SUNAT. No sustituye XML firmado, hash o CDR de comprobantes/RC/RA existentes. No incorpora endpoints de descarga ni permisos nuevos.

| Columna / objeto | Definición propuesta | Motivo |
| --- | --- | --- |
| `id` | BIGINT autoincremental, PK | Identificación de cada recepción; no deduplicar ni sobrescribir intentos |
| `ruc_emisor` | VARCHAR(11), NOT NULL | Aislamiento por empresa |
| `flujo` | ENUM('CPE','RA','RC'), NOT NULL, esperado con Hibernate 6.6.39.Final/MySQL | Tipo de documento/flujo, no resultado fiscal; valores STRING en Java |
| `documento_id` | BIGINT, NOT NULL | Vínculo lógico al documento del flujo indicado |
| `ticket_sunat` | VARCHAR(150), NULL | Contexto de getStatus; alineado con RC/RA; sendBill no usa ticket |
| `recibido_en` | DATETIME(6), NOT NULL | Clock central, America/Lima |
| `sha256` | VARCHAR(64), NOT NULL | Integridad de los bytes archivados, no autenticidad SUNAT |
| `respuesta_zip_bytes` | LONGBLOB, NOT NULL | Respuesta bruta exacta, incluso ZIP/CDR inválido |
| `idx_respuesta_sunat_documento` | (`ruc_emisor`,`flujo`,`documento_id`,`recibido_en`) | Búsqueda interna contextual sin listados globales de empresa |

No se añade FK a tres tablas distintas sobre una misma columna. Los consumidores internos toman RUC/ID del documento persistido; cualquier futura consulta privada deberá conservar todas las condiciones de empresa/flujo/documento. No hay columna/flag que declare el ZIP validado.

Spring Boot 3.4.13 administra Hibernate 6.6.39.Final. El mapeo actual `@Enumerated(EnumType.STRING)` usa el enum nativo del dialecto MySQL, con valores ordenados alfabéticamente: `CPE,RA,RC`. `length=3` no obliga a generar VARCHAR. No se cambia la entidad para forzar un tipo diferente. Cotejar siempre versión/dialecto/configuración efectivos y esquema real; no afirmar compatibilidad productiva a partir de las anotaciones. Ver `FASE3_PROCEDIMIENTO_ESQUEMA.md`.

### DDL aditivo propuesto (NO ejecutado en producción)

```sql
CREATE TABLE respuesta_sunat_recibida (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ruc_emisor VARCHAR(11) NOT NULL,
    flujo ENUM('CPE','RA','RC') NOT NULL,
    documento_id BIGINT NOT NULL,
    ticket_sunat VARCHAR(150) NULL,
    recibido_en DATETIME(6) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    respuesta_zip_bytes LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_respuesta_sunat_documento
        (ruc_emisor, flujo, documento_id, recibido_en)
) ENGINE=InnoDB;
```

Revisar charset/collation efectivos antes de aprobar SQL final. No usar `IF NOT EXISTS` para ocultar una tabla incompatible. No es un baseline ni el esquema completo de una instalación nueva. No hay UNIQUE sobre hash/documento: las recepciones repetidas se conservan, y el ID de documento es una referencia lógica dependiente del flujo.

### Verificación local autorizada histórica — 01/10/2026

`RespuestaSunatEsquemaRepositoryTest` agrega 3 casos: metadatos de MySQL confirman ENUM/tipos/nullability/PK/índice; el artefacto genera el DDL de esta entidad sólo en un StringWriter (acción de BD `none`); SchemaValidator valida este mapeo contra la tabla creada por el contexto JPA test. La prueba comprueba expresamente `DATABASE() = bd_facturacion_test` y Hibernate 6.6.39.Final. Selección con archivo de respuestas: **9/9**; suite completa final **528/528**, cero fallos/errores/omitidas.

No se cambia ddl-auto ni Flyway; se conserva el create-drop autorizado de la base desechable. No se ejecuta el DDL propuesto en producción ni se arranca la aplicación completa. Esta validación de una entidad/tabla no acredita compatibilidad integral del esquema desplegado.

## Impacto y límites

- **IMPLEMENTADO / PENDIENTE DE ESQUEMA:** límite técnico compartido de 150 caracteres sin truncamiento en RC, RA y archivo bruto. SOAP rechaza tickets recibidos >150 como protocolo incierto para sendSummary; getStatus rechaza una entrada >150 antes de red. Servicio de archivo y validación de entidades rechazan >150 sin persistir versiones recortadas. DTO de respuesta y repositorios no transforman el valor.
- Si la tabla de respuestas ya existe con VARCHAR(100), proponer ampliación a VARCHAR(150); lo mismo para `comunicacion_baja.ticket_sunat`. `resumen_diario` ya declara 150. Revisar metadatos antes de cualquier ejecución. Las ampliaciones y la creación inicial siguen no aplicadas a producción.

```sql
-- PROPUESTA, NO EJECUTADA; sólo si la metadata confirma VARCHAR(100) NULL.
ALTER TABLE respuesta_sunat_recibida MODIFY COLUMN ticket_sunat VARCHAR(150) NULL;
ALTER TABLE comunicacion_baja MODIFY COLUMN ticket_sunat VARCHAR(150) NULL;
```

Rollback: conservar las ampliaciones al volver al binario compatible anterior. No reducir a 100 si existen tickets más largos, ni truncarlos. Ensayo contra el artefacto anterior: **PENDIENTE DE ENSAYO PRODUCTIVO**.

Verificación del nuevo límite: casos 100/101/150 y rechazo 151 en cliente/validación de entidades/archivo, metadata `VARCHAR(150)` para RC, RA y respuestas. Específicas iniciales del límite **83/83**; suite final de la continuación **593/593**, cero fallos/errores/omitidos (baseline552 +41 nuevos;62 clases,6 Testt). Ampliaciones sólo generadas por el create-drop ya autorizado de tests, no por ejecución manual del ALTER propuesto. Modelo/output DTO/repositorios no truncan: el valor recibido se conserva tal como retorna el contrato SOAP existente (que ya elimina whitespace exterior).

UNIQUE RUC/ticket permanece propuesta separada en `FASE3_PROCEDIMIENTO_ESQUEMA.md`: no se añade a esta tabla de recepciones ni se impide guardar respuestas repetidas.

- Una escritura corta adicional por respuesta no vacía, fuera de HTTP/SOAP y anterior al parser. Si falla el archivo, no se declara aceptación; no se oculta la indisponibilidad de persistencia.
- Aumenta almacenamiento y carga de escritura. No hay purga automática ni eliminación de evidencias. Deben acordarse capacidad y retención operativa; los límites de descarga/descompresión se verifican en su bloque independiente.
- No preserva el body SOAP bruto cuando el cliente falla antes de devolver un ZIP. No resuelve autenticidad criptográfica del CDR ni la ventana entre recepción en memoria y commit si el proceso cae.
- En producción con `ddl-auto=validate`, la tabla faltante impide arrancar el nuevo binario. **No desplegar antes de aplicar y comprobar un cambio autorizado sobre el esquema real.**

## Migración manual propuesta (no ejecutada)

1. Obtener autorización, backup probado y esquema/versiones reales de MySQL. Inspeccionar solamente metadatos de las tablas necesarias mediante un acceso autorizado; no leer credenciales ni datos fiscales como atajo.
2. Comparar la tabla propuesta con el esquema existente; si ya existe, detenerse ante diferencias. No usar `IF NOT EXISTS` para ocultarlas.
3. Revisar tipo de IDs, precisión temporal, charset/collation, tamaño de paquete y permisos con el operador. Probar la creación en una copia desechable y medir el impacto.
4. Preparar/aplicar una creación aditiva revisada equivalente a las columnas/PK/índice anteriores. No alterar ni regenerar evidencias existentes; no activar Flyway ni inventar un baseline.
5. Comprobar metadatos, validación del esquema y pruebas aisladas de recepción/rollback/empresa antes de un despliegue independiente autorizado.

## Rollback propuesto

## Ensayo local posterior autorizado — baseline 593

Se reprodujo el esquema actual sólo en bd_facturacion_test con el create-drop ya configurado. `Fase3EnsayoEsquemaRepositoryTest` compara once tablas fiscales (PK/FK/índices/unique/tipos/enums/nulos/longitudes) y ejecuta SchemaValidator aislado; tickets150 verificados en RC, RA y archivo bruto. No se aplicó ALTER sobre un schema antiguo existente porque la inspección previa encontró las tablas objetivo ausentes. No acredita una migración incremental productiva.

`Fase3BackupRepositoryTest` exporta schema+14 filas ficticias+blobs de esas once tablas mediante mysqldump, manifest canonicalizado y SHA256 del dump. Archivo generado en `target/fase3-ensayo/`; **no contiene empresas/usuarios/tokens/credenciales**. Campos XML/hashCPE son sintéticos y no prueban firma SUNAT. El primer intento fue denegado por permisos. Tras creación/habilitación manual del usuario, **RESTORE VALIDADO** en bd_facturacion_restore_test: importado el dump original SHA256 `8ef76d9e63eab96ed0da987d1e2c4dd3d3302d10cf1382bb2017e635542c0544`, 14 filas/todos los campos/11 blobs iguales al manifiesto original, relaciones/hash/metadata y SchemaValidator aislado satisfactorios. Evidencia conservada en `/tmp/opencode/fase3-restore-evidence/`; detalle en `FASE3_PROCEDIMIENTO_ESQUEMA.md`. Sin grants del agente, otras cuentas, aplicación completa ni modificaciones fiscales.

UNIQUE RUC/ticket sólo se ensayó en una tabla TEMPORARY de fixtures y se retiró allí para probar rollback; sigue sin constraint en resumen_diario y sin modificación de entidades. **HARDENING RECOMENDADO**, no decisión productiva. Ausencia de JAR anterior identificable: **ROLLBACK DE BINARIO: PENDIENTE DE ENSAYO PRODUCTIVO POR AUSENCIA DE ARTEFACTO ANTERIOR IDENTIFICADO**.

Volver al binario anterior mediante el procedimiento operativo autorizado, **conservar la tabla y sus filas**. El código previo puede ignorar la tabla aditiva. No ejecutar `DROP TABLE`, purga ni borrado como rollback automático. Si fuese necesaria retirada posterior, requerirá exportación segura, verificación de integridad y autorización específica de retención/eliminación.
