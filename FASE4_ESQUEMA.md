# FASE 4 — cambios aditivos de esquema

## Alcance

Propuesta para una instalación existente; **no ejecutada en producción**. Los ensayos sólo usan `bd_facturacion_test` desechable (servidor habitual para pruebas fiscales; MySQL aislado en loopback:33307 para padrón sintético). Flyway permanece desactivado y producción conserva `ddl-auto=validate`.

## Detalles de comprobante: contrato y DDL propuesto

- Cantidad: conservar `DECIMAL(12,4)`; validar hasta 8 enteros/4 decimales significativos, sin truncar. Decisión expresa del usuario. Se admiten ceros finales adicionales representables.
- Valor/precio unitario: contrato existente hasta 12 enteros y 10 decimales; ampliar a `DECIMAL(22,10)`.
- IGV/total de detalle: cálculo monetario a 2 decimales HALF_UP; capacidad igual a cabecera `DECIMAL(14,2)`. Rechazar overflow antes de persistir.
- Descripción: máximo API existente de 500; ampliar mapeo a `VARCHAR(500)`.
- No se introduce descuento nuevo, tasa ni cálculo fiscal nuevo. No regenerar XML/hash/documentos históricos.

```sql
ALTER TABLE detalle_comprobante
  MODIFY COLUMN valor_unitario DECIMAL(22,10) NOT NULL,
  MODIFY COLUMN precio_unitario DECIMAL(22,10) NOT NULL,
  MODIFY COLUMN igv_item DECIMAL(14,2) NOT NULL,
  MODIFY COLUMN monto_total_item DECIMAL(14,2) NOT NULL,
  MODIFY COLUMN descripcion VARCHAR(500) NOT NULL;
```

Revisar primero tipos, charset/collation, nulos, motor y dependencias del esquema real; preservar opciones adicionales que existan. No ejecutar este ejemplo ciegamente. Puede reconstruir/bloquear tabla según MySQL y tamaño: exigir ventana, backup y ensayo de esa versión.

## Renovación: identidad explícita por empresa

El endpoint exige `X-Idempotency-Key` (1–128 caracteres no blancos). Sólo se almacena SHA-256 de la clave y del payload canónico (meses, importe exacto a dos decimales y observación). Identidad por empresa; no deducir identidad por igualdad de importe/meses. Repetir la clave no agrega pago/período y devuelve la información actual de empresa; cambiar payload con esa clave se rechaza. Los pagos históricos permanecen con ambos campos NULL, sin backfill ni cambio del período registrado.

```sql
ALTER TABLE pago_empresa
  ADD COLUMN idempotency_hash VARCHAR(64) NULL,
  ADD COLUMN request_hash VARCHAR(64) NULL,
  ADD CONSTRAINT uk_pago_empresa_operacion UNIQUE (empresa_id,idempotency_hash);
```

Impacto: cambio HTTP autorizado expresamente; adaptar consumidor administrativo antes del cambio de binario. Las nuevas escrituras serializan por lock de empresa; el índice confirma exclusión por empresa. No índice global de tickets SUNAT, ni columna `@Version` para usuarios/empresas.

## Padrón

Sin nueva entidad/columna. El importador conserva `padron_ruc_anterior` y archiva versiones previas en tablas `padron_ruc_hist_<UUID>`, mediante un único RENAME múltiple. No purga automática. Esto aumenta almacenamiento y exige capacidad/monitorización. Exclusión por `GET_LOCK` ligado al catálogo y a la sesión MySQL; toda operación de carga/intercambio/limpieza usa esa misma conexión, sin reconexión silenciosa. Sólo coordina instancias que usan el mismo servidor primario/catálogo; no sustituye un lock entre primarios independientes.

## Rollback y compatibilidad

1. Preferir rollback de binario **conservando ampliaciones y columnas**, no reducción de esquema ni eliminación de evidencias.
2. El binario anterior no protege la nueva idempotencia de renovación ni la escala decimal ampliada: **no es equivalente funcionalmente**. Detener nuevas operaciones antes de cualquier rollback y probar el artefacto anterior genuino en entorno desechable.
3. No reducir decimales/VARCHAR salvo SELECT autorizado que acredite que todos los datos son exactamente representables en el esquema antiguo. El rollback de ampliaciones no está autorizado sobre históricos productivos.
4. No eliminar columnas/índice de pagos sin preservar el registro de identidad de operaciones y sin retirar al mismo tiempo la dependencia del código nuevo. Dejar las columnas es compatible con lectura del modelo viejo, no garantía operativa del binario viejo.
5. Para padrón, conservar versión activa, anterior e históricas. Un intercambio de recuperación exige coordinación de escritores, cotejo del conteo/contenido y autorización operativa; no se ofrece recuperación automática que elija una versión por nombre.

## Verificación

`Fase4DecimalRepositoryTest`: request→cálculo→MySQL→flush/clear/reload→firma real con certificado ficticio, cantidad mínima/máxima, escalas/magnitudes unitarias, descripción500, rechazo de cantidades no representables/overflow, metadata.

`Fase4SuscripcionRepositoryTest`: campos nullable64 e índice compuesto mediante `information_schema`, renovaciones concurrentes, repetición/cambio de payload y scheduler concurrente.

Los contexts JPA generan el esquema de test con `create-drop`; no afirmar que esto ensaya una migración incremental de una instalación productiva ni su rollback binario. Ese ensayo requiere el esquema y el artefacto reales autorizados, fuera del cierre local de esta fase.
