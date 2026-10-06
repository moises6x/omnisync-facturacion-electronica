# Procedimiento de esquema para producción — pendiente de ejecución autorizada

**No acredita que el esquema desplegado sea reproducible.** No se conectó a producción, ni se ejecutaron migraciones o la aplicación completa. El create-drop existente se limitó a bd_facturacion_test; posteriormente se importó el dump de fixtures únicamente en bd_facturacion_restore_test, creada y habilitada manualmente por el usuario. Flyway sigue desactivado; `prod` mantiene `ddl-auto=validate`. Las entidades y el esquema generado/restaurado de tests no prueban el estado de producción.

## Restore efectivamente ensayado — 01/10/2026, baseline 612

**RESTORE VALIDADO** en `bd_facturacion_restore_test`, comprobada vacía antes de importar. El usuario resolvió los permisos fuera del proceso; el agente no concedió grants ni buscó otra cuenta. Importación con cliente MySQL sin defaults, host local, cuenta de test y catálogo explícito. Sin DROP, correcciones ni reimportación; sólo CREATE/INSERT contenidos en el backup de fixtures.

- Backup original importado: 24.464 bytes, SHA-256 `8ef76d9e63eab96ed0da987d1e2c4dd3d3302d10cf1382bb2017e635542c0544`, verificado inmediatamente antes de importar.
- Manifiesto original: SHA-256 `40d8b5aa8be20d2d660108dbdfe64bde913c61fe2bf4584ba040e1599e9e0a29`. Pareja original conservada en `/tmp/opencode/fase3-restore-evidence/` antes de que la suite regenere los artefactos bajo target. La validación nunca compara contra un manifiesto generado a partir del propio destino.
- Coinciden todas las filas y campos de las once tablas: 14 filas, incluidos tickets NULL/150, estados, secuencias, IDs, timestamps y valores históricos sintéticos. Once campos blob no nulos, 3.057 bytes agregados: igualdad byte a byte contra origen, tamaños y SHA-256 registrados. Hashes de recepción cotejados además mediante SHA2 MySQL.
- Metadata original/destino idéntica: 158 columnas (tipos/enums/nulos/defaults/collation/extra), 66 entradas índice-columna (incluidos PK/UNIQUE existentes), tres entradas FK con columnas referenciadas. Engine InnoDB e identidades AUTO_INCREMENT por encima del máximo conservado. Relaciones físicas y lógicas de CPE, RC, RA, respuestas e idempotencia verificadas sin huérfanos/cruces RUC; tickets archivados coinciden con sus cabeceras.
- `SchemaValidator` Hibernate 6.6.39.Final y cotejo adicional de modelos/PK/FK/índices/nulabilidad/tipos/longitudes satisfactorios sobre el destino. Proceso Java independiente, sin Spring, SessionFactory, repositorios, bootstrap, schedulers o cliente SUNAT. JDBC read-only; ninguna escritura a la base durante la validación. SchemaValidator no ejecuta DDL, aunque Hibernate registra internamente la obtención de conexión bajo su interfaz de schema management.
- Utilidad externa `/tmp/opencode/fase3-restore-evidence/RestoreFase3Validator.java`, ejecución `/tmp/opencode/fase3-restore-validate.py`; evidencia `import-result.json`, `validation-result.json`, `manifest-original.json`, `manifest-restored.json`, `validation.log` en ese directorio. JSON completo cotejado también desde Python. Primer cotejo de metadata falló sólo por wrappers Long/Int de Jackson: corregido comparando JSON serializado contra JSON serializado, sin omitir o alterar campos/valores/constraints.

Este resultado cierra sólo la restaurabilidad del backup fiscal de fixtures ensayado; no es backup integral de toda la aplicación, firma SUNAT, conciliación productiva ni migración de una instalación anterior. No se añadió UNIQUE ni cambió la lógica fiscal/configuración/test. Los intentos denegados que se describen más abajo son **antecedentes**, no el estado vigente del restore.

Suite posterior completa `*Test,*Testt`: **612/612**, nuevos0, fallos/errores/omitidos0/0/0; XML65 clases incluidas6Testt, log `/tmp/opencode/fase3-restore-suite-final.log`. Después de la suite, destino recotejado contra pareja original preservada y SchemaValidator repetido: PASS. Sin reimportar ni purgar restore_test.

## 1. Condiciones previas y aislamiento

1. Obtener aprobación del operador para acceso de **sólo metadatos** sobre la instancia/base concreta. Usar credenciales mediante el mecanismo seguro del operador, sin leerlas/copiar valores ni dejarlos en logs/documentación.
2. Confirmar MySQL y versión, base, esquema, charset/collation, modo SQL y zona horaria efectiva de JVM/Hibernate/JDBC/MySQL. Los updates de recuperación usan timestamps persistidos: comprobar equivalencia temporal y límites en esa instalación; un test local no la garantiza.
3. Tener backup y restauración probada en copia desechable. No usar `bd_facturacion` para tests ni ejecutar `create-drop`/`update` sobre una base no desechable.
4. No usar arranque `beta`, `@SpringBootTest` ni plugin Spring Boot como herramienta de inspección. Existen efectos de bootstrap, schedulers y `PadronRucActualizacionService.@PostConstruct`; desactivar actualización no basta. Cualquier verificación de contexto requiere diseño aprobado sin acceso directo/indirecto a `padron_reducido_ruc/`. Preferir contexto JPA mínimo sin esos servicios.

## 2. Captura explícita de metadatos (propuesta, no ejecutada)

Ejecutar sólo por el operador autorizado. No volcar datos fiscales, blobs, usuarios, claves o toda la base. Esta lista permite examinar tablas relevantes a estos cambios, sin importadores/padrón:

```sql
SELECT VERSION(), DATABASE(), @@sql_mode, @@session.time_zone;

SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE,
       IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('comprobante', 'resumen_diario', 'resumen_diario_detalle',
      'comunicacion_baja', 'comunicacion_baja_detalle', 'refresh_token',
      'solicitud_emision', 'secuencia_correlativo', 'secuencia_resumen_diario',
      'secuencia_comunicacion_baja', 'respuesta_sunat_recibida')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('comprobante', 'resumen_diario', 'resumen_diario_detalle',
      'comunicacion_baja', 'comunicacion_baja_detalle', 'refresh_token',
      'solicitud_emision', 'secuencia_correlativo', 'secuencia_resumen_diario',
      'secuencia_comunicacion_baja', 'respuesta_sunat_recibida')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
```

Completar restricciones/FK/engine mediante `SHOW CREATE TABLE` únicamente para cada tabla anterior revisada. No asumir que una restricción existe porque una entidad la declara. No ampliar este procedimiento a búsquedas de archivos/credenciales/datos del padrón.

## 3. Comparación con artefacto y contrato actual

Registrar la identidad del binario/fuentes, versiones de JDK/Hibernate/driver y metadatos capturados, sin secretos. Comparar con un esquema esperado generado **sólo en una base desechable autorizada**, sin sustituir el esquema real por el de tests.

Checklist prioritario (no inventario exhaustivo de toda la aplicación):

| Tabla / objeto | Comprobación | Motivo/impacto si falta |
| --- | --- | --- |
| `comprobante` | RUC/tipo/serie/número únicos; `estado_sfs`, `updated_at`, referencia de NC, bytes XML/CDR y hash con tipos/capacidad esperados | Duplicados multiempresa, recuperación inválida o pérdida de evidencia |
| `resumen_diario`, detalles | Unicidad empresa/identificador y empresa/fecha/correlativo; vínculo/número de línea; timestamps y blobs | Bloqueo de cabecera y evidencias/estados requieren persistencia compatible |
| `comunicacion_baja`, detalles | Unicidad empresa/identificador, detalle/comprobante; estados/timestamps/blobs; vínculo cabecera/detalles | Finalización RA atómica y recuperación sin reenvío |
| `refresh_token` | Hash único de 64 caracteres, usuario/FK, familia, expiración/revocación/reemplazo | Exclusión por token y rollback deben operar en filas InnoDB transaccionales |
| Secuencias e idempotencia | Claves por empresa, tipos y unicidades de entidades/repositorios | No cambiar la independencia de correlativos/clave idempotente entre empresas |
| `respuesta_sunat_recibida` | Nueva tabla/PK/índice/columnas de `FASE3_CAMBIO_ESQUEMA_RESPUESTAS.md` | El binario nuevo no arranca con validate si falta; nunca usar update como migración |

Para `respuesta_sunat_recibida.flujo`, el mapeo esperado del artefacto actual (Spring Boot 3.4.13 / Hibernate 6.6.39.Final / dialecto MySQL) es `ENUM('CPE','RA','RC')`, no VARCHAR(3). DDL aditivo propuesto en `FASE3_CAMBIO_ESQUEMA_RESPUESTAS.md`. Cotejar DDL generado, valores enum, longitudes, precisión temporal y validación con la versión/configuración exactas. Los índices/constraints deben comprobarse explícitamente: `ddl-auto=validate` no es auditoría completa de unicidad, datos ni rendimiento.

## 4. Plan de cambios y rollback antes de ejecutarlos

### Actualización técnica del cierre — 01/10/2026

- **IMPLEMENTADO:** tickets de hasta 150 sin truncamiento, saneamiento de errores SOAP/HTTP, diferenciación pre/post sendBill y selección RC que excluye asociaciones ambiguas dentro del RUC. No modifica el protocolo de correlación ni las guardas/locks de aplicación.
- **PENDIENTE DE ESQUEMA:** ampliar `respuesta_sunat_recibida.ticket_sunat` y `comunicacion_baja.ticket_sunat` de 100 a 150 si la metadata lo confirma. El valor RC ya era 150. No reducir ni truncar en rollback. Ver SQL propuesto en `FASE3_CAMBIO_ESQUEMA_RESPUESTAS.md`.
- **PENDIENTE DE ESQUEMA (hardening recomendado, NO implementado):** UNIQUE por RUC/ticket sólo en `resumen_diario`. No hay anotación UNIQUE nueva, ni migración aplicada, ni ejecución de su DDL en los tests.

#### SELECT previos al UNIQUE — no ejecutados sobre históricos

```sql
-- Comparación real de la collation existente: NULL queda fuera; vacíos NO se excluyen.
SELECT ruc_emisor, ticket_sunat, COUNT(*) AS cantidad,
       MIN(id) AS primer_id, MAX(id) AS ultimo_id
FROM resumen_diario
WHERE ruc_emisor = :ruc AND ticket_sunat IS NOT NULL
GROUP BY ruc_emisor, ticket_sunat HAVING COUNT(*) > 1;

SELECT id, estado, CHAR_LENGTH(ticket_sunat) AS longitud,
       HEX(ticket_sunat) AS representacion
FROM resumen_diario
WHERE ruc_emisor = :ruc AND ticket_sunat IS NOT NULL
  AND (TRIM(ticket_sunat) = '' OR ticket_sunat <> TRIM(ticket_sunat)
       OR CHAR_LENGTH(ticket_sunat) > 150);

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario'
  AND COLUMN_NAME IN ('ruc_emisor','ticket_sunat');

SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='resumen_diario'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
```

La comparación SQL puede ignorar espacios finales según la collation: usar además `OCTET_LENGTH(ticket_sunat) <> OCTET_LENGTH(TRIM(ticket_sunat))` para detectarlos. El HEX sólo se entrega al operador autorizado, no se vuelca en logs públicos. Revisar variantes de mayúsculas/espacios y collation; no normalizarlas automáticamente.

#### DDL y rollback propuestos — NO ejecutar sin nueva autorización

```sql
ALTER TABLE resumen_diario ADD CONSTRAINT uk_resumen_empresa_ticket
    UNIQUE (ruc_emisor, ticket_sunat);

-- Retirada opcional, separadamente autorizada; preferible conservar al revertir binario.
ALTER TABLE resumen_diario DROP INDEX uk_resumen_empresa_ticket;
```

Impacto: protege otros escritores del mismo RUC; no impide NULL múltiples ni la misma cadena en empresas distintas. No garantiza inmutabilidad del ticket y no impone unicidad RC/RA cruzada. Revisar InnoDB, longitud de clave/charset, duración de locks y coste de construir índice con volumen real. Los duplicados bloquean la creación: conciliar antes, nunca borrar/reescribir evidencia para hacer pasar el DDL. No usar `IF NOT EXISTS` como sustituto de inspección. Si se retira, quedan las guardas del servicio, pero se pierde hardening para otros escritores.

Tests preparados: `RespuestaSunatEsquemaRepositoryTest` acredita **ausencia actual** de unicidad ticket y conserva índice no único/nullability; `TicketSunatMetadataTest` distingue metadata compuesta completa, global indebida, no única y con prefijos usando fixtures. No prueban enforcement de una constraint no aplicada. Tras autorización futura: metadata debe mostrar `NON_UNIQUE=0`, posiciones 1 RUC/2 ticket y `SUB_PART IS NULL`; ensayar dos NULL, mismo ticket en distintos RUC y duplicado en un RUC en copia desechable.

### Límites operativos del binario nuevo

- **IMPLEMENTADO:** fallos de protocolo/parseo/archivo posteriores al intento sendBill y SOAP Fault quedan `ERROR_ENVIO_INCIERTO`, no rechazo con CDR ni fallo local seguro. Mantiene reservas NC. Si falla persistir resultado, no se hace un segundo save/envío: se propaga error técnico saneado; el estado duradero debe conciliarse. Rollback real probado deja ENVIANDO y recovery lo convierte en incierto sin encolarlo.
- **IMPLEMENTADO:** lote RC excluye tickets con cualquier otro dueño del mismo RUC, incluso terminal. Orden `updatedAt,id` y límite conservados. No modifica esos RC; su conflicto necesita conciliación externa y monitorización, no desaparece fiscalmente.
- **PENDIENTE DE ENSAYO PRODUCTIVO:** comparación de schema completo, aplicación/ensayo autorizado de ampliaciones, análisis/conciliación histórica, planes de capacidad/alertas/retención, restauración, rollback del binario identificado y recuperación desde archivo sin nueva respuesta SUNAT. Pruebas locales no autorizan despliegue.
- **PENDIENTE DOCUMENTAL/EXTERNO:** autenticidad criptográfica obligatoria del CDR, anclas SUNAT y RB/RC; sin cambios de algoritmo, truststore ni rechazo fiscal por firma no verificada.

- La única entidad nueva de esta FASE 3 es `respuesta_sunat_recibida`; su propuesta aditiva y rollback conservando datos se documentan por separado. Los bloqueos y guardas no añaden columnas/versiones a tablas existentes.
- FASE2-017 deriva total/bases/IGV de las NC vinculadas; no añade tabla de saldos ni contadores. Comprobar `uk_comprobante_emisor` e `idx_comprobante_afectado`, referencias/motivos/moneda/importes históricos y la garantía terminal del rechazo individual. NC sin referencia determinable bloquean nuevas NC monetarias de esa empresa hasta conciliación; no hay backfill ni liberación masiva.
- Nuevos RC usan fecha de generación en ID/archivo/IssueDate y fecha documental en ReferenceDate. Se mantienen `uk_resumen_diario_identificador` y `uk_resumen_diario_empresa_fecha_correlativo`, sin cambiar esquema. Las secuencias existentes conservan marcas máximas de las dos fechas, bloqueadas en orden; el piso histórico evita colisiones con IDs previos. Se permiten huecos, no se exige correlativo contiguo. No renombrar/regenerar RC existentes: cualquier legacy incompatible con 2346 requiere revisión operativa, no una reparación silenciosa.
- Para cualquier diferencia detectada en otras tablas, presentar tabla/columna/índice/restricción, evidencia, razón, impacto sobre datos/locks/consumidores, procedimiento y rollback **antes** de modificarla. No corregir duplicados/borrar evidencias/inventar valores por un arranque fallido.
- No reducir longitudes/precision, renombrar enums, copiar cifrados entre empresas, eliminar constraints ni purgar respuestas como atajo.
- No ejecutar Flyway `baseline`, `migrate` o `repair`, ni activar Flyway por esta guía. La adopción futura requiere un plan independiente basado en el esquema real, copia de ensayo, aprobación y trazabilidad.
- Ensayar cambios revisados en copia desechable, registrar metadatos antes/después y pruebas de rollback. El rollback de cambios aditivos conserva la tabla/evidencias y revierte sólo el binario mediante el procedimiento operativo autorizado; no `DROP TABLE` automático.

## 5. Antecedentes del criterio de cierre y ensayo inicial

## Ensayo desechable autorizado — 01/10/2026 (baseline 593)

### Alcance y resultado verificable

- Sólo `bd_facturacion_test` y `bd_facturacion_restore_test` autorizadas. Sin cambios Java de producción, perfiles, ddl-auto, Flyway ni arranque completo. Nuevas pruebas JPA mínimas verifican ausencia de workers/schedulers/cliente SOAP/servicio de padrón, y ausencia de tareas registradas. No acceden a su carpeta restringida.
- Inspección directa previa a cualquier create-drop: instancia local reportó VERSION()=26.7.0, modo estricto y max_allowed_packet=64MiB. Ninguna de las once tablas fiscales estaba presente. **DATOS HISTÓRICOS REALES DISPONIBLES EN TEST: ninguno representativo.** No trasladar este resultado a producción.
- `Fase3EnsayoEsquemaRepositoryTest` valida once modelos con SchemaValidator sin acción DDL y compara tipos/enums/nulos, engine InnoDB, columnas, PK, índices/UNIQUE y FK contra metadata actual. Sólo normaliza el sinónimo MySQL INTEGER=INT, no ignora longitudes ni constraints. Se generó esquema de prueba mediante create-drop ya configurado, con tickets VARCHAR(150) en RC, RA y respuesta; no fue necesario ALTER de una tabla existente. La reproducción inicial de esquema no prueba una migración incremental desde una instalación antigua.
- UNIQUE real ensayado **sólo en una tabla TEMPORARY de fixtures**, con charset/collation del ticket RC: dos NULL, mismo texto en RUC diferentes, error1062/23000 por duplicado de un RUC, comparación case-insensitive y retirada del índice en la tabla temporal. No se añade a `resumen_diario`, no se modifica JPA; queda **HARDENING RECOMENDADO**. Collation observada `utf8mb4_unicode_ci`; antes de una aprobación externa revisar variantes/espacios y la collation efectiva de esa instalación.
- Primer intento: creación de `bd_facturacion_restore_test` y conexión denegadas a la cuenta de pruebas (`DATABASE_PERMISSION_DENIED`); restore quedó entonces pendiente. Bloqueo posteriormente resuelto manualmente por el usuario, con importación/cotejo/validator satisfactorios según la sección vigente de restore. No se buscó otra cuenta ni elevaron permisos por el agente.
- No hay JAR local anterior en raíz (sólo *.jar), target (incluidos descendientes) ni `out/artifacts` (ruta inexistente). No se buscó en dependencias/credenciales/directorios restringidos ni se fabricó mediante HEAD/package/checkout. **ROLLBACK DE BINARIO: PENDIENTE DE ENSAYO PRODUCTIVO POR AUSENCIA DE ARTEFACTO ANTERIOR IDENTIFICADO.**

### Backup real de fixtures y restore preparado

`Fase3BackupRepositoryTest` exige cero filas previas en las once tablas antes de insertar datos sintéticos, commit y export. No exporta empresas/usuarios/tokens/credenciales/padrón. Usa mysqldump local con single-transaction, hex-blob, sin defaults/GTID/tablespaces y sin DROP/CREATE DATABASE/USE. Salidas generadas:

- `target/fase3-ensayo/backup-test.sql`: schema y datos de once tablas.
- `target/fase3-ensayo/manifest-source.json`: filas completas canonicalizadas (blobs HEX), tipos/nulos/collation, índices y relaciones FK; sin secretos.
- `target/fase3-ensayo/backup-sha256.txt`: integridad del dump concreto. Se regenera en cada ensayo; registrar hash/tamaño de la última ejecución, no reutilizar un hash histórico para un dump nuevo.

Fixture: comprobante2, detalle_comprobante1, RC1/detalle1, RA1/detalle1, respuesta3, cada secuencia1 e idempotencia1: total14. Los campos XML/hashCPE contienen bytes/valores **sintéticos**, no se demuestra firma SUNAT con ellos; ZIP CDR de prueba y SHA256 de recepción sí se preservan como bytes. Export cubre esas once tablas, **no un backup integral de todas las tablas de la aplicación**. SQL lógico HEX puede ocupar aproximadamente2× el contenido binario, antes de compresión.

La prueba termina invalidando su contexto y dejando al create-drop existente eliminar fixtures/tablas de esa base desechable; no corrige históricos. El dump/manifest quedan para el ensayo separado. El primer script externo `/tmp/opencode/fase3-restore.py` comprobó SHA256 y routing y recibió denegación antes de importar; ese intento no fue satisfactorio. El restore posterior sí se completó, conservando previamente la pareja original fuera de target como se documenta arriba; no se habilitó ningún servicio.

Acción faltante en el ensayo inicial, **ya realizada**: operador creó/habilitó sólo bd_facturacion_restore_test; agente importó en destino vacío y comparó filas/HEX/metadata/conteos/tamaños/SHA/tickets/estados/secuencias/relaciones, con SchemaValidator aislado satisfactorio. No se cambió URL de aplicación ni se conectó restore a servicios o a una tercera base.

### Detectores históricos: SELECT sobre fixtures, no conciliación productiva

`Fase3HistoricosRepositoryTest` ejecuta once casos de detección sobre anomalías sintéticas transaccionales (incluye secuencias CPE, RC por ambas fechas y RA) y comprueba un hallazgo esperado por caso; no hace correcciones. Una fixture derivada SELECT de151 caracteres prueba el detector de longitud porque el esquema nuevo admite sólo150; no se ensanchan columnas para introducirla. Consultas concretas versionadas en ese test y SELECT previos en este procedimiento.

| Anomalía | Gravedad | Bloqueo si existiera en una instalación operativa |
| --- | --- | --- |
| Ticket duplicado mismo RUC | ALTA | Bloquea los RC afectados; no elegir dueño automáticamente. Lote ya excluye conflictos para no bloquear otros. |
| Ticket >150 | ALTA | Bloquea archivo/correlación del registro; conciliar sin truncar. Con esquema150 no puede persistirse legítimamente. |
| Ticket ausente en RC consultable/terminal | ALTA | Bloquea seguimiento o acreditación de ese RC. |
| Terminal aceptado sin código/CDR/finalización | ALTA | Bloquea confianza en ese resultado; no regenerar evidencias. |
| Respuesta huérfana | MEDIA (CRÍTICA si cruce tenant demostrado) | Conservar; no aplicar fiscalmente hasta esclarecer dueño. |
| EN_RESUMEN sin vínculo activo coherente | ALTA | Bloquea recuperación del documento. |
| Secuencia bajo máximo existente | ALTA | Bloquea nueva numeración de esa empresa/serie. |
| Documento ligado a varios RC activos | CRÍTICA | Bloquea procesamiento de asociaciones afectadas. Historial terminal múltiple no es automáticamente corrupción. |
| SHA/bytes inconsistentes | ALTA | Bloquea confianza en esa evidencia, preservar y cotejar backup. |
| ID/archivo legacy incompatible | MEDIA, ALTA si aún debe enviarse | Terminal íntegro puede coexistir; revisar pendiente sin renombrar/regenerar. |

**CONCILIACIÓN HISTÓRICA PRODUCTIVA: PENDIENTE DE ENSAYO PRODUCTIVO.** Los fixtures demuestran los detectores; no demuestran ausencia de anomalías en producción ni autorizan sus consultas allí.

Para RC, una secuencia ausente/atrasada es **MEDIA** si identificadores/documentos son íntegros: el servicio actual aplica el piso histórico y marcas por ambas fechas, no debe declararse automáticamente duplicación efectiva. RA/CPE requieren revisar el máximo antes de nueva numeración. No ajustar ninguna secuencia automáticamente mediante este ensayo.

### Recuperación A–F

Ensayos con fallos controlados/mocks, servidor127.0.0.1, transacciones MySQL reales y recreación de servicio; no apagado literal de máquina/DB ni aplicación completa:

- A: conexión recibe sendBill y se cierra/timeout sin respuesta; estado incierto sin reenvío.
- B: sendSummary devuelve ticket, escritura hace rollback antes de commit; XML preparado persiste, ticket no confirmado no se inventa, queda incierto y un servicio recreado no reenvía. Ticket perdido requiere conciliación externa.
- C: CDR getStatus llega pero falla archivo antes de commit; ticket persistido permite reconsulta, una sola finalización efectiva y sin otro sendSummary. CPE sin ticket conserva la limitación de recuperación externa si no alcanzó primer commit.
- D: archivo confirmado y parser falla; los bytes sobreviven y un parser nuevo inspecciona ZIP offline con RUC/ticket/ID. **Diagnóstico offline no aplica estado fiscal**. El test ensaya después conciliación manual controlada invocando por Reflection el finalizador privado existente: recotejo/locks/guardas permanecen, se conserva ZIP/XML y una segunda invocación no finaliza ni audita otra vez; no otra llamada SOAP. No se añade endpoint ni replay automático; usar ese acceso privado en producción requeriría un canal operativo autorizado independiente.
- E: finalización hace rollback real: estados/CDR no quedan confirmados, respuesta bruta independiente sobrevive; recuperación por ticket existente está probada.
- F: servicio recreado/incierto y ENVIANDO vencido: guardas excluyen reenvío, recovery convierte a incierto y no encola; terminal no acepta dos veces.

No existe garantía exactly-once entre SUNAT/MySQL. Persiste ventana de primera recepción en memoria; aceptar/mitigar mediante procedimiento de conciliación, no reenvío ciego. Restauración real y compatibilidad con proceso/binario anterior siguen pendientes.

### Capacidad inicial parametrizable — horizonte, NO plazo legal de purga

`B=R×S×T` bytes, con S en bytes; mes de planificación30 días, año365. Recepciones incluyen consultas repetidas, no sólo documentos.

| Recepciones/día | KiB/recepción | GiB/30d | GiB/365d |
| ---: | ---: | ---: | ---: |
| 100 | 20 | 0.0572 | 0.6962 |
| 100 | 100 | 0.2861 | 3.4809 |
| 100 | 500 | 1.4305 | 17.4046 |
| 1000 | 20 | 0.5722 | 6.9618 |
| 1000 | 100 | 2.8610 | 34.8091 |
| 1000 | 500 | 14.3051 | 174.0456 |
| 10000 | 20 | 5.7220 | 69.6182 |
| 10000 | 100 | 28.6102 | 348.0911 |
| 10000 | 500 | 143.0511 | 1740.4556 |

Estas cifras son únicamente ZIP bruto. Añadir XML firmado, CDR final ZIP/XML, filas, índices, binlogs/réplicas y backups. Margen inicial recomendado **30% sobre estimación de datos+índices**, provisional hasta medir. Fórmula de reserva: `(B+otros)×1.30 + k×factor_backup×(B+otros) + binlogs/réplicas`. Dos dumps completos HEX sin compresión pueden añadir aproximadamente4B: conjunto mínimo orientativo5.3B, sin los otros componentes. Compresión y duración de backup se deben medir, no asumir. LONGBLOB no elimina límites de paquete/heap; límites SOAP16MiB, ZIP8MiB, XML16MiB y expansión32MiB permanecen.

### Monitorización externa mínima y retención

No dashboard ni cambios de código productivo. Controles propuestos; **no instalados ni probados en producción**:

| Control | Medición / acción inicial |
| --- | --- |
| Espacio libre | SO/volumen: warning<30%, crítico<15%, revisar días restantes por crecimiento. Umbrales técnicos provisionales. |
| Tamaño/respuestas y crecimiento | information_schema.TABLES para tabla de respuestas (data/index length), conteo/suma OCTET_LENGTH por ventana; muestras diarias. TABLE_ROWS InnoDB es estimación, no conteo de integridad. |
| SOAP/parser/archivo/persistencia | Contar errores saneados por operación/tipo y ventana; alerta inmediata por fallo de archivo/commit. No recolectar bodies/secretos. |
| Tickets demasiado antiguos | Edad desde enviado_en/created_at, no ultima_consulta; threshold T_ticket debe acordarse con operador, no cambiar plazos fiscales. |
| Inciertos | Conteo por RUC/flujo y edad: intervención, nunca auto-reenvío. |
| Conflictos ticket | SELECT GROUP BY RUC/ticket HAVING COUNT>1; fuente explícita porque lote omite conflictos. |
| p95/p99 | OCTET_LENGTH de recepciones por ventana y percentiles discretos con funciones ventana MySQL; sólo tamaños, no blobs. |
| Conexiones | Métricas Hikari/JDBC o MySQL Threads_connected y max_connections; evitar saturación. |
| Executor | Active count/pool/queue de sunatExecutor mediante agente JVM o Actuator ya disponible, sin abrir endpoints/permisos. |
| Último backup | Monitor externo: fecha de éxito, SHA, tamaño, filas/export y alerta si supera intervalo aprobado. |
| Última restauración probada | Registrar fecha/manifest/validator/resultado; inicialmente **SIN RESTAURACIÓN ACREDITADA**. |

Ejemplos SELECT (operador autorizado, sin ejecutar producción): `SUM(OCTET_LENGTH(respuesta_zip_bytes))`, `MAX(TIMESTAMPDIFF(SECOND,COALESCE(enviado_en,created_at),NOW()))` para pendientes, y conteos por estados inciertos. Fijar zona temporal y rango/tenant antes de instalar; no usar cuentas administrativas reales aquí. **CONSERVAR + DIMENSIONAR + MONITORIZAR**: sin purga automática de XML/CDR/respuestas/tickets/hashes/evidencias ni plazo legal inventado.

Consulta de p95 ejecutada sobre las tres recepciones ficticias del backup; las tres tienen igual tamaño, no representa una distribución comercial. Se comprobaron relaciones lógicas de respuestas/detalles RC/RA/idempotencia y SHA contra bytes en origen; posteriormente, integridad equivalente en restore comprobada satisfactoriamente contra el manifiesto original.

Autenticidad criptográfica SUNAT sigue **PENDIENTE DOCUMENTAL/EXTERNO**, separada de estructura/archivo/hash y no bloqueante por sí sola de este cierre técnico.

**Resultado actual: FASE 3 TÉCNICAMENTE CERRADA en alcance técnico local.** Restore de fixtures efectivamente realizado/cotejado y SchemaValidator satisfactorio, baseline612/612 conservado, sin bloqueos técnicos demostrados en ese alcance. **ROLLBACK DE BINARIO: PENDIENTE DE ENSAYO PRODUCTIVO POR AUSENCIA DE ARTEFACTO ANTERIOR IDENTIFICADO** y **CONCILIACIÓN HISTÓRICA PRODUCTIVA: PENDIENTE DE ENSAYO PRODUCTIVO** permanecen explícitamente sin ensayo, como limitaciones productivas externas bajo el criterio de cierre acordado, no defectos actuales de código probados. No demuestra compatibilidad de un binario anterior ni datos desplegados. Ninguna de estas comprobaciones acredita schema/datos/operación de producción ni aplica UNIQUE a resumen_diario. No autoriza despliegue ni inicia FASE4.
