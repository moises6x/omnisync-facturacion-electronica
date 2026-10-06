# Informe FASE 3 — correcciones y límites de cierre

> Referencia histórica: este informe conserva el cierre técnico local de FASE 3 del 01/10/2026 y su baseline 612. La auditoría FASE 4 fue autorizada posteriormente y detectó bloqueadores independientes; sus correcciones y nuevas pruebas se documentan en `FASE4_INFORME.md`/`FASE4_ESQUEMA.md`. Las frases “no se inicia FASE 4 aquí”, “sin cambios funcionales” y los totales de esta fase describen sus continuaciones históricas, no el estado actual del árbol de trabajo. No reabren ni amplían el cierre limitado del restore, rollback binario o conciliación productiva.

## A. Resultado

**FASE 3 TÉCNICAMENTE CERRADA, en el alcance técnico local autorizado.** Actualizado el 01/10/2026: baseline mantenido **612/612**, nuevos **0**, suite completa repetida sin fallos, errores ni omitidas. **RESTORE VALIDADO** en bd_facturacion_restore_test tras habilitación manual del usuario: filas/blobs/hashes/tickets/estados/secuencias/relaciones/metadata iguales al manifiesto original y SchemaValidator aislado satisfactorio, también después de la suite. RC → ticket → CDR no se rediseña. Rollback por falta de artefacto anterior e históricos productivos siguen pendientes documentados de entorno, no defectos actuales demostrados ni bloqueadores de este cierre local según el criterio acordado. No se declara aptitud productiva ni se cambia el veredicto de FASE 2 `NO APTO TODAVÍA`; FASE 4 requiere autorización independiente y no se inicia aquí.

FASE2-017 opción 1 y la discrepancia de fechas de nuevos RC están implementadas/probadas. Quedan pendientes: esquema desplegado no inspeccionado/ensayado; cuestiones documentales/externas FASE2-018/019; trazabilidad exacta del registro original de FASE 2, todavía no recuperado; decisiones operativas de capacidad/preservación y conciliación de históricos aplicables.

## B. Matriz de trazabilidad

`ID PENDIENTE DE COTEJO` (denominado “ID por cotejar” en las filas históricas siguientes) no constituye un nuevo ID de auditoría ni un cierre de FASE2-005–010/012–016. Identifica el bloque demostrado conservado en el contexto; no se inventa su numeración. Los estados “corregido/probado” se limitan al comportamiento indicado, no a todos los requisitos posibles del flujo. Los totales 313–471 de la matriz son ejecuciones históricas previas a esta continuación.

| ID | ANTES | CAMBIO | TEST | RESULTADO | ESTADO FINAL |
| --- | --- | --- | --- | --- | --- |
| FASE2-001 | CDR leído por nombres globales/primer XML | Raíz/namespace/rutas directas, unicidad/texto/código y único XML | CdrParserServiceTest/Testt y consumidores | +44; completa 313 | Corregido/probado en perfil estructural interpretado |
| FASE2-002 | Valor/precio unitario recortado al serializar | Hasta n(12,10), precio calculado a 10; importes a 2; rechazo sin truncar entrada | PrecisionMonetariaXmlTest | +43; completa 356 | Corregido/probado |
| FASE2-003/004 | Deuda/cuotas no cotejadas según XML monetario real | Validación común tras redondeo; límites deuda/cuotas/total y suma | CreditoXmlTest | +16; completa 372 | Corregidos/probados |
| RC, ID por cotejar | XML/hash escritos antes de propiedad; respuestas tardías reabrían terminales | Bloqueo por ID/RUC; ENVIANDO y evidencia juntos; guardas bajo bloqueo | ResumenDiarioServiceTest/Testt, ResumenDiarioRepositoryTest, MultiempresaRepositoryTest | +13; completa 385 | Carrera demostrada corregida/probada |
| RA, ID por cotejar | Finalización/anulaciones/auditoría sin commit fiscal común | Anulaciones+CDR/estado atómicos; auditoría posterior; propiedad de envío | ComunicacionBajaRepositoryTest y CdrTest | +8; completa 393 | Atomicidad demostrada corregida/probada |
| Refresh, ID por cotejar | Token podía consumirse simultáneamente | Bloqueo por hash en refresh/logout; replay conserva política de revocación | RefreshTokenRepositoryTest | +5; completa 398 | Exclusividad corregida/probada |
| Recuperación, ID por cotejar | CPE/RA ENVIANDO tras caída sin clasificación; RA CREADO sin recovery | Vencidos a incierto sin reenvío; RA recupera CREADO sin ticket; revalidación de plazo | Repositorios CPE/RA y schedulers/RC | +7; completa 405 | Casos enumerados corregidos/probados |
| Fechas RC, ID por cotejar | Fecha antigua vencida/fallida bloqueaba posteriores | Ventana del plazo configurado y cursor posterior a fecha fallida/agotada | ResumenDiarioSchedulerTest, ComprobanteRepositoryTest | +4; completa 409 | Corregido/probado |
| FASE2-011 | ZIP inválido podía perderse al fallar parser/finalización | Archivo bruto append-only en commit independiente antes del parser | RespuestaSunatRecibidaRepositoryTest, consumidores, rollback RA | +8; completa 417 | Implementado/probado; tabla productiva pendiente, alcance ZIP |
| Receptor/2022, ID por cotejar | Nombre corto o con controles incompatibles | Mínimo 3, sin controles; conserva límite API 200 | ValidacionTributariaServiceTest | +10; completa 427 | Corregido/probado |
| SOAP/estados, ID por cotejar | Lectura global de campos/Fault; estado desconocido finalizable | Contexto Envelope/Body/operación/status; sólo 0/98/99; Base64 estricto | SunatDirectSoapClientContractTest | +17; completa 444 | Corregido/probado para contrato documentado |
| Límites, ID por cotejar | HTTP/ZIP/expansión sin cotas | Límites operativos antes de lectura/acumulación; cuenta entradas ignoradas | CdrLimitesTest, contrato SOAP | +13; completa 457 | Corregido/probado |
| Firma/XSD positivos, ID por cotejar | Cobertura positiva firmada pendiente | CA/emisor ficticios efímeros; firma/verificación/XSD para 6 flujos; PKIX y alteración | FlujosFirmadosXsdTest | +14; completa 471 | Cobertura local agregada; no homologación |
| Esquema productivo, ID PENDIENTE DE COTEJO | Sin procedimiento verificado contra esquema real | DDL ENUM/documentación y SchemaValidator local | RespuestaSunatEsquemaRepositoryTest | +3; específicas 9; completa 528 | Verificación local; esquema desplegado pendiente |
| FASE2-017 | Cada NC comparada sólo contra el original | Saldo derivado por total/bases/IGV; originales y NC bloqueados; terminales protegidos | SaldoNotaCreditoServiceTest, SaldoNotaCreditoRepositoryTest, EnvioSunatServiceTest | +46; específicas 127; completa 517 | Política autorizada implementada/probada; no conciliación productiva |
| Fechas ID/archivo RC, ID PENDIENTE DE COTEJO | ID por fecha documental pero IssueDate por generación | Nuevos RC por generación; ReferenceDate documental; secuencias compatibles con ambas unicidades | ResumenDiarioFechaRepositoryTest y regresiones RC | +8; específicas 87; completa 525 | Corregido/probado sólo para nuevos RC |
| FASE2-018/019 | Conflictos/documentación/exigencias externas pendientes | No se resolvieron por inferencia | XSD/firma locales no cierran estos hallazgos | Sin cierre integral | PENDIENTE DE CONFIRMACIÓN DOCUMENTAL |
| Refuerzo operativo RC/ticket (ámbito tratado bajo FASE2-018; definición original no recuperada) | Ticket persistido por RC/RUC, sin unicidad operativa ni recotejo al aplicar respuesta; auditoría antes del commit | Asignación serializada por empresa; recotejo ID/RUC/ticket bajo lock; referencias sólo evidencia/warning; auditoría posterior | ResumenDiarioTicketRepositoryTest, CdrParserServiceTest y regresiones | +24; específicas 130; completa 552 | Reforzado/probado técnicamente; RB/RC sigue pendiente documental |

## C. Cambios funcionales principales

### Continuación limitada a RESTORE — baseline612 mantenido

- Importación única en destino autorizado/vacío, SHA256 del dump original comprobado antes de importar: `8ef76d9e63eab96ed0da987d1e2c4dd3d3302d10cf1382bb2017e635542c0544`,24.464 bytes. Manifiesto fuente congelado previamente, SHA256 `40d8b5aa8be20d2d660108dbdfe64bde913c61fe2bf4584ba040e1599e9e0a29`. No usar manifiesto derivado del propio destino para probar igualdad.
- Cotejo completo:11 tablas,14 filas,11 camposblob no nulos/3.057 bytes,158 columnas,66 entradas índice-columna,3 FK; todos los campos/bytes idénticos, SHA2 de recepción y vínculos lógicos porRUC correctos. Tickets150 yNULL, estados y secuencias conservados, identidades verificadas. Scope sólo tablas fiscales de fixtures, no backup integral ni históricos productivos.
- SchemaValidator y cotejo de modelos aislados mediante Java/JDBC read-only, sin Spring/aplicación completa/schedulers/bootstrap/SUNAT/padrón. Evidencia `/tmp/opencode/fase3-restore-evidence/`: `import-result.json`, `validation-result.json`, manifiestos original/restaurado, `validation.log`, `suite-result.json`. Procedimiento detallado en `FASE3_PROCEDIMIENTO_ESQUEMA.md`.
- Completa `*Test,*Testt` **612/612**,0/0/0,65 clases/6Testt; log `/tmp/opencode/fase3-restore-suite-final.log`. Después de la suite se repitió cotejo contra origen congelado y SchemaValidator: PASS. Artefactos target regenerados por la suite no reemplazan la pareja original del restore.
- Sin cambios en código/config/tests, grants del agente, otras cuentas, commit/push/deploy. Sólo restore y actualización de cuatrodocs. Los ensayos anteriores denegados conservados más abajo son antecedentes, ya no bloqueo del restore vigente.

### Ensayo operativo desechable posterior — baseline593, sin cambios funcionales

- SchemaValidator y metadata de11 tablas/158 columnas/66 entradas índice-columna/3 FK físicas concordantes (incluidas columnas referidas). Tickets150. Contexto JPA mínimo sin servicios con efectos. Tablas iniciales ausentes: reprodujo schema actual, no migración incremental antigua.
- UNIQUE prototipo real sólo en tablaTEMPORARY con NULL, scopeRUC, collation case-insensitive y rollback del índice. Sigue **HARDENING RECOMENDADO** no aplicado a resumen_diario ni a entidades.
- SELECT de históricos probados en11 anomalías sintéticas +longitud151 derivada. No datos históricos representativos disponibles. **CONCILIACIÓN HISTÓRICA PRODUCTIVA: PENDIENTE DE ENSAYO PRODUCTIVO.** Gravedades/bloqueos y consultas en test/procedimiento, no correcciones.
- Backup real mysqldump de11 tablas/14 filas ficticias con blobs y manifest. No contiene cuentas/secretos/padrón, no abarca todo el esquema de la app. XML/hashCPE sintéticos no acreditan firma. Fuente coherente por relaciones/hash; restauración independiente **NO realizada por DATABASE_PERMISSION_DENIED** en cuenta test. No se elevó permiso ni se buscó otra cuenta.
- **ROLLBACK DE BINARIO: PENDIENTE DE ENSAYO PRODUCTIVO POR AUSENCIA DE ARTEFACTO ANTERIOR IDENTIFICADO.** No usar HEAD/código actual como sustituto; no ejecutar aplicación ni construir falso anterior.
- A–F: fallos controlados/rollback real/redlocal/recreación de servicio; no apagadoSO literal. Archivo sin commit puede recuperarse por ticket; archivo confirmado permite diagnóstico y conciliación offline controlada mediante el finalizador privado existente, una sola transición/auditoría y sin nueva llamadaSOAP. No endpoint ni replayauto, no cambios de correlación/ResponseCode/locks/firma/plazos.
- Capacidad y controles externos mínimos definidos, no desplegados. Política **CONSERVAR + DIMENSIONAR + MONITORIZAR**. RB/RC/autenticidad criptográfica SUNAT pendientes separados, no bloqueantes por sí solos.
- Nuevos archivos: `src/test/java/org/example/repository/Fase3EnsayoEsquemaRepositoryTest.java` (3), `Fase3HistoricosRepositoryTest.java` (12), `Fase3BackupRepositoryTest.java` (1); +3 en `ResumenDiarioTicketRepositoryTest.java`. Sólo tests y cuatrodocs autorizados. Detalle reproducible, matrixcapacidad, alertas, gravedades y límites en `FASE3_PROCEDIMIENTO_ESQUEMA.md`.

### Continuación de cierre técnico — 01/10/2026, baseline 552/552

| Cambio autorizado | Estado | Resultado y límite |
| --- | --- | --- |
| Longitud ticket | **IMPLEMENTADO / PENDIENTE DE ESQUEMA** | RC, RA y archivo bruto comparten límite 150 sin truncar; >150 se rechaza por validación/contrato técnico, no por regla normativa inferida. Respuestas >150 de sendSummary son protocolo incierto; consultas >150 no salen a red. DTOs de respuesta/repositorios no recortan ni añaden entrada pública de ticket arbitrario. |
| Body HTTP/errores | **IMPLEMENTADO** | No incorpora body a excepciones; faultstring no se propaga y código Fault queda clase/sufijo numérico seguro. Causas técnicas conservan sólo tipo, sin texto/cadena remota ni stderr SAX. HTTP no satisfactorio conserva código y contexto mínimo, sin XML/SOAP recibido. |
| Fallos sendBill | **IMPLEMENTADO** | Etapas preparación, petición iniciada, respuesta recibida. Fallos posteriores, incluidos SOAP Fault/protocolo/parser/archivo, mantienen resultado incierto, no fallo local seguro ni rechazo tributario. No modifica ResponseCode de un CDR válido ni política de saldo NC. |
| Persistencia posterior | **IMPLEMENTADO** | Si falla confirmar resultado, error técnico saneado, sin auditoría de éxito ni segundo save/reenvío. No se afirma que el objeto aceptado en memoria esté confirmado en DB. Rollback MySQL probado conserva archivo bruto, deja ENVIANDO y recovery lo convierte a incierto sin encolar. |
| Lote monopolizado | **IMPLEMENTADO** | Regresión demostrada antes del cambio: cuatro conflictos preceden válidos, página 1 elige conflicto. Query excluye otro dueño del ticket dentro del mismo RUC (incluso terminal), conserva orden y límite. Prueba posterior avanza a otro tenant con igual texto y RC válido posterior. Ninguna asociación, estado conflictivo, lock o finalización se modifica. |
| UNIQUE RUC/ticket | **PENDIENTE DE ESQUEMA** | Sólo hardening recomendado/documentado; sin anotación física nueva ni DDL aplicado. SQL de detección, propuesta y rollback en procedimiento. Metadata test acredita ausencia actual; contrato futuro se comprueba con fixtures, no enforcement imaginario. |
| Autenticidad CDR | **PENDIENTE DOCUMENTAL/EXTERNO** | Sin verificación criptográfica obligatoria, CA SUNAT inventada, cambios de truststore/algoritmos, OCSP/CRL ni rechazo por firma no verificada. |

Compatibilidad: mensajes SOAP Fault ya no contienen texto remoto y su código técnico no conserva un prefijo arbitrario; errores individuales posteriores al envío cambian de ERROR_VALIDACION/ERROR_ENVIO a ERROR_ENVIO_INCIERTO. Consumidores deben tratar esos casos como conciliación, no reenvío seguro. Históricos no se reescriben. Si falla el commit final no se intenta marcar incierto mediante otro save potencialmente inseguro; verificar estado duradero y usar recuperación existente.

**PENDIENTE DE ENSAYO PRODUCTIVO:** metadatos y ampliaciones reales, revisión de históricos/escritores, capacidad/alertas/retención, restauración y rollback de binarios identificados, arranque aislado y conciliación desde archivo bruto sin pedir de nuevo a SUNAT. La ventana recepción-en-memoria/primer commit persiste. Query de lote ya no emite por sí misma warnings de conflictos excluidos: monitorizar mediante SELECT contextual; no purgar ni olvidar conciliación.

**PENDIENTE DOCUMENTAL/EXTERNO:** RB/RC, igualdad normativa ReferenceID, plazos consolidados, perfil criptográfico integral, catálogos/condiciones externas y anclas de confianza SUNAT. Se separan del cierre del código autorizado; no implican modificaciones actuales por inferencia.

Archivos de esta continuación (delta no equivale al diff acumulado contra HEAD):
- Producción: `src/main/java/org/example/util/TicketSunat.java`; `model/{ResumenDiario,ComunicacionBaja,RespuestaSunatRecibida}.java`; `service/{RespuestaSunatRecibidaService,SunatDirectSoapClient,EnvioSunatService}.java`; `repository/ResumenDiarioRepository.java`.
- Tests: `src/test/java/org/example/service/{TicketSunatValidacionTest,SunatDirectSoapClientContractTest,EnvioSunatServiceTest,EnvioSunatServiceTestt}.java`; `repository/{RespuestaSunatRecibidaRepositoryTest,RespuestaSunatEsquemaRepositoryTest,ResumenDiarioTicketRepositoryTest,EnvioSunatPersistenciaRepositoryTest,TicketSunatMetadataTest}.java`.
- Estado/esquema: este informe, `FASE3_CAMBIO_ESQUEMA_RESPUESTAS.md`, `FASE3_PROCEDIMIENTO_ESQUEMA.md`, `MEMORY.md`. No DTO modificado: se revisaron salidas sin límites truncantes y ausencia de entrada pública de ticket arbitrario.

- CDR/SOAP: contexto explícito, campos únicos de texto simple, observaciones desde raíz CDR, único XML. Sin nueva normalización ni comparación fiscal obligatoria de referencias RC–CDR; diferencias textuales sólo warning técnico.
- Cálculo/XML/validación: `ImportesXml` centraliza representación y comprobaciones comunes; conserva BigDecimal/tasa histórica y redondeo monetario de dos decimales. No debilita plazos ni valida elegibilidad legal.
- Concurrencia: transacciones cortas para cabeceras RC/RA y consumo refresh. HTTP/SOAP fuera de transacciones largas. Evidencia de preparación perdedora no sustituye la ganadora; respuestas tardías no sustituyen terminales.
- Archivo bruto: nueva entidad/repositorio/servicio `RespuestaSunatRecibida`, con RUC/flujo/documento/ticket/Clock/ZIP/SHA-256. No cambia contratos HTTP ni agrega endpoints/permisos. No es un CDR validado.
- Recuperación: `ERROR_ENVIO_INCIERTO` no se reenvía automáticamente. El scheduler RA conserva su condición de activación; no fue habilitado por esta fase.
- NC: `SaldoNotaCreditoService` deriva consumo y reserva por total/base gravada/exonerada/inafecta/IGV de NC existentes. Motivo 03 excluido; monetarios 01/04/05/06/07/08/09; no habilita motivos ni límites por ítem. Aceptadas consumen; pendientes, inciertas, errores y bajas sin restitución demostrada conservan ocupación. 01/06 bloqueados con afectaciones activas; datos ambiguos/sobreafectados bloquean emisión monetaria hasta conciliación.
- Única exclusión por rechazo: NC individual de factura RECHAZADO, terminal no reenviable, ZIP parseable con XML/código/referencia propia concordantes. Se admiten únicamente las representaciones serie-número y serie-número de ocho dígitos ya emitidas; no se añaden normalizaciones RC–CDR. Un rechazo sin esa evidencia conserva reserva. RC rechazado y su liberación a PENDIENTE_RESUMEN no cancelan NC ni liberan saldo.
- NC: emisión e idempotencia permanecen en la misma transacción. `bloquearOriginalNotaCredito` usa RUC/tipo/serie/número (unicidad equivale al ID del original) y PESSIMISTIC_WRITE; historial en proyección escalar SQL FOR UPDATE ordenada por ID, no SUM/snapshot ni entidades antiguas del persistence context. Confirmación/rechazo individual bloquea original antes de NC en un commit corto posterior a SOAP; RC/RA aceptados bloquean originales ordenados antes de actualizar NC. Auditoría individual ocurre después de persistir el resultado confirmado, no antes del commit.
- Nuevos RC: misma fecha capturada con Clock para ID, archivo e IssueDate; ReferenceDate conserva fecha documental. Marcas máximas en las filas de secuencia de ambas fechas, adquiridas en orden cronológico, respetan RUC/ID y RUC/fecha_documentos/correlativo sin cambiar constraints. Piso histórico evita colisiones legacy; huecos permitidos. No renombra, regenera ni cambia evidencias de RC existentes.
- RC/ticket: se mantiene entidad/campo/scheduler y el envío ya implementados. `registrarTicket` privado adquiere empresa por RUC y luego cabecera por ID/RUC, sólo después de SOAP; lectura escalar SQL FOR UPDATE comprueba otros dueños del ticket dentro del RUC incluso con snapshot previo. No reasigna un ticket existente. Duplicación devuelta por sendSummary conserva el segundo envío como incierto sin reenvío automático, no como rechazo fiscal.
- getStatus utiliza exclusivamente el ticket persistido del RC; ese valor capturado acompaña CDR, 98, 99 sin CDR y errores. Bajo el lock existente se comprueban estado consultable, ticket sin cambios y ausencia de otro dueño dentro del RUC. Los duplicados históricos se detienen con warning, sin elegir el primer RC, purgar ni cambiar su estado fiscal. SOAP permanece fuera de transacciones/locks del flujo.
- `ResultadoCdr.documentReferenceId` expone el ID que ya se exigía, con constructores internos compatibles. Ambos IDs son evidencia adicional: no RB→RC, igualdad inferida ni sustitución de ResponseCode. Bytes originales siguen en archivo bruto append-only y ZIP/XML del CDR final; no nuevas columnas. Warning `CDR_REFERENCE_MISMATCH` posterior a finalización, con valores técnicos acotados/sin controles, nunca XML/ZIP ni credenciales. Auditoría RC posterior a la transacción fiscal y sólo para la transición efectiva.

Archivos principales bajo `src/main/java/org/example/`: servicios `CdrParserService`, `CalculoTributarioService`, `UblXmlBuilderService`, `ValidacionTributariaService`, `FacturacionServiceImpl`, `SaldoNotaCreditoService`, `ResumenDiarioService/Scheduler`, `SecuenciaResumenDiarioService`, `ComunicacionBajaService/Scheduler`, `EnvioSunatService/RecoveryScheduler`, `SunatPlazoService`, `SunatDirectSoapClient`, `RespuestaSunatRecibidaService`; repositorios asociados; `auth/service/AuthService`, `auth/repository/RefreshTokenRepository`; utilidades `ImportesXml`, `LecturaLimitada`; modelo `RespuestaSunatRecibida`. `SecuenciaResumenDiario` sólo cambia comentario, no mapeo. No se reescribió arquitectura ni reorganizaron paquetes.

## D. Pruebas y contabilidad

| Concepto | Cantidad |
| --- | ---: |
| Baseline completo informado de FASE 1 | 269 |
| Baseline no repositorio ejecutado inicialmente | 238 |
| Casos nuevos acumulados FASE 3 | 343 |
| Referencia anterior a continuación NC/fechas/esquema | 471 |
| Nuevos de continuación NC/fechas/esquema | 57 |
| Baseline anterior al refuerzo RC/ticket | 528 |
| Nuevos del refuerzo RC/ticket | 24 |
| Baseline de esta continuación técnica | 552 |
| Nuevos de esta continuación técnica | 41 |
| Baseline del ensayo operativo | 593 |
| Nuevos del ensayo operativo | 19 |
| Total última suite completa | 612 |
| Satisfactorios | 612 |
| Fallos / errores / omitidos finales | 0 / 0 / 0 |

Los 202 iniciales se desglosan como `44+43+16+13+8+5+7+4+8+10+17+13+14`. La continuación NC/fechas/esquema agrega `46+8+3=57`: 32 casos de política NC, 12 MySQL NC, 2 de worker NC, 8 MySQL fechas RC y 3 de esquema. El refuerzo RC/ticket agrega 23 MySQL y 1 parser: `528+24=552`. Esta continuación agrega **41**: longitud 20; body/transportes locales 5; sendBill/rollback 10; lote 1; metadata UNIQUE propuesta 5. Total `552+41=593`, acumulados `269+324=593`. No se eliminaron tests ni se desactivaron/relajaron assertions; expectativas de estado se actualizaron sólo según el nuevo contrato incierto autorizado. Últimos XML de Surefire comprobados: **62 clases**, incluyendo **6 Testt**, suma **593/0/0/0**.

| Bloque actual | ANTES | NUEVOS | TOTAL | PASAN | FALLAN | ERRORES | OMITIDOS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| FASE2-017 | 471 | 46 | 517 | 517 | 0 | 0 | 0 |
| Nuevos RC / fechas | 517 | 8 | 525 | 525 | 0 | 0 | 0 |
| Esquema local | 525 | 3 | 528 | 528 | 0 | 0 | 0 |
| Refuerzo RC/ticket | 528 | 24 | 552 | 552 | 0 | 0 | 0 |
| Continuación técnica autorizada | 552 | 41 | 593 | 593 | 0 | 0 | 0 |
| Ensayo operativo desechable | 593 | 19 | 612 | 612 | 0 | 0 | 0 |

Selecciones específicas iniciales por bloque: 93, 78, 94, 52, 20, 21, 63, 36, 80, 68, 67, 98 y 70 satisfactorias. Continuación: NC 127, RC 87, esquema 9; cada bloque pasó específicas y suite completa antes del siguiente.

Refuerzo RC/ticket: primer bloque 113/113; específicas finales 130/130. Los 23 casos MySQL nuevos cubren RC distintos de un tenant, dos tenants con igual identificador RC, idempotencia/recreación del servicio, dos workers con respuestas simultáneas (una transición y dos recepciones preservadas), 0/98/99/rechazo/observaciones, referencias diferentes sin veto fiscal, CDR corrupto/ReferenceID ausente-vacío-duplicado con archivo bruto, respuestas tardías de ticket cambiado (CDR/98/99/error), duplicados históricos, reasignación prohibida, tenant/locks, asociación duplicada concurrente con snapshot previo y rollback tras escribir cabecera/documentos con recuperación posterior. También prueban sendSummary→persistencia→getStatus y ticket duplicado sin reenvío. El nuevo caso parser expone ambas referencias distintas y XML intacto; assertions existentes se conservan.

Continuación técnica: específicas del límite **83/83**, HTTP **63/63**, lote/regresiones **64/64**, sendBill/rollback **110/110**, metadata/transporte/saldo MySQL **62/62**, causas saneadas/parser/consumidores **136/136**. No sumar estas selecciones solapadas como casos nuevos. Nuevos: `TicketSunatValidacionTest` 4; archivo 10; metadata de respuesta 2 (longitud + ausencia UNIQUE); contrato SOAP 10 (longitud 5 + privacidad 3 + conexión/timeout 2); `EnvioSunatServiceTest` 9; rollback MySQL 1; lote MySQL 1; contrato metadata UNIQUE con fixtures 4.

Verificación local no equivale a cobertura exhaustiva de reglas Excel, pruebas SUNAT extremo a extremo, todas las combinaciones de impuestos/motivos, revocación OCSP/CRL ni operación multinodo de toda la aplicación.

Ensayo operativo actual: **593+3 esquema+12 históricos+1 backup+3 crash=612**. XML finales comprobados65 clases incluidas6Testt,612/0/0/0. Específicas metadata/backup19, última detalle16 (3+12+1), crash109 yoffline27, solapadas. Primera completa610 pasó antes de ampliar detección de secuenciasRC/RA; final612 pasó con todas las nuevas assertions. No tests eliminados/assertions desactivadas ni omisiones para ocultar restore: restore es operación externa pendiente, no un test declarado satisfactorio o skipped.

## E. Ejecución reproducible y fallos intermedios

- **Comando final vigente:** `python3 /tmp/opencode/run-fase3-tests.py '*Test,*Testt' fase3-ensayo-operativo-full-final`, exit0,BUILD SUCCESS,612/0/0/0. Log `/tmp/opencode/fase3-ensayo-operativo-full-final.log`; anteriores comandos593/610 conservados como históricos.
- Nuevo ejecutor externo descubre sólo mysqldump en binarios locales y usa stdinPowerShell para evitar dos fallos de interopEncodedCommand previos a Maven; no cambió configuración del proyecto. Metadata test inicial marcó sinónimoINTEGER/INT; corregido mediante equivalencia exacta, sin relajar longitudes/enums/nulos/índices. Específicas y finales verdes.
- Backup final `target/fase3-ensayo/backup-test.sql`,24464bytes, SHA256 `8ef76d9e63eab96ed0da987d1e2c4dd3d3302d10cf1382bb2017e635542c0544`; manifest y SHA en el mismo directorio. SHA comprobado externamente después de la suite. Script `/tmp/opencode/fase3-restore.py` verifica hash/routing y conecta sólo restore_test: exit1 por permiso, antes de importar. No interpretar backup exportado como restaurabilidad demostrada.

- Windows: JDK 21.0.12.1, Maven 3.9.16 del IntelliJ. Java/Maven Linux no disponibles. Ejecutor externo `/tmp/opencode/run-fase3-tests.py`; credenciales únicamente del entorno Windows de test, sin imprimir valores.
- Base fijada a `bd_facturacion_test`, exclusiva/desechable confirmada/autorizada. Flyway desactivado y ruta del padrón aislada; tests JPA/directos/mocks, no arranque completo. La suite incluye `*Test,*Testt`.
- Comando final: `python3 /tmp/opencode/run-fase3-tests.py '*Test,*Testt' fase3-cierre-tecnico-full-final`. Resultado: exit 0, BUILD SUCCESS, **593/0/0/0**. Referencias históricas: `fase3-rc-ticket-full`, 552/552, y `fase3-cierre-full`, 528/528. El ejecutor externo fija la base test y ruta aislada del padrón, desactiva Flyway/bootstrap y consume únicamente variables de test del entorno Windows.
- Historial: autenticación MySQL inicial falló hasta que el usuario configuró variables; error de quoting inicial antes de tests; dos fixtures Mockito nuevos RC fallaron y se corrigieron; un fixture de binding temporal JDBC/Hibernate falló y se corrigió usando el mismo binding; una compilación SOAP quedó con argumento Document en vez de Element y se corrigió. Ninguno continúa como regresión abierta.
- Reinicios eliminaron logs/snapshots externos antiguos; no presentarlos como archivos hoy disponibles. Una ejecución de respuestas se interrumpió sin resultado y fue repetida. Reportes finales están en `target/surefire-reports/`, sólo leídos, nunca editados.
- Logs históricos de bloques anteriores: `/tmp/opencode/fase3-receptor-{specific,full}.log`, `fase3-soap-{specific-retry,full}.log`, `fase3-limites-{specific,full}.log`, `fase3-firma-{specific-final,full}.log`; también respuestas. No asumir que persisten después de reinicios. `git diff --check` no está limpio contra HEAD por CRLF/EOF en el árbol con trabajo previo; no se realizó reformateo masivo ni se confunde ese diff con delta exclusivo de FASE 3.
- Logs de esta continuación: `/tmp/opencode/fase3-nc-specific-green.log`, `fase3-nc-full.log`, `fase3-rc-fechas-specific-retry.log`, `fase3-rc-fechas-full.log`, `fase3-esquema-specific.log`, `fase3-cierre-full.log`. Fallos intermedios corregidos: referencia mal sustituida en fixture CDR nuevo; compilación por captura de variable reasignada al mover auditoría; dos mocks NC sin retorno de confirmación; dos verificaciones RC conservaban la firma antigua de dos argumentos. No se borraron tests ni se relajaron assertions. Diff-check acotado con CRLF permitido sólo señaló EOF vacío preexistente en SecuenciaResumenDiarioService; no limpieza ajena.
- Logs del refuerzo: `/tmp/opencode/fase3-rc-ticket-bloque1.log`, `fase3-rc-ticket-specific.log`, `fase3-rc-ticket-specific-final.log`, `fase3-rc-ticket-full.log`. Todas esas ejecuciones finalizaron satisfactoriamente; diff-check acotado de los archivos de este refuerzo, con CRLF permitido, limpio.
- Logs actuales: `/tmp/opencode/fase3-cierre-longitud.log`, `fase3-cierre-http-verificado.log`, `fase3-cierre-lote.log`, `fase3-cierre-sendbill.log`, `fase3-cierre-metadata-transporte.log`, `fase3-cierre-causas.log`, `fase3-cierre-tecnico-full-final.log`. Intermedios: prueba de lote falló intencionalmente antes del fix, seleccionando ID conflictivo 1 en vez de válido 5; HTTP HTML perdió código 503 antes de sanear su clasificación y se corrigió; primera completa `fase3-cierre-tecnico-full` ejecutó 593 con 30 fallos porque se quitó la causa exigida por tests de parser. Se conservó causa técnica saneada sólo con tipo y sin cadena original: tests originales intactos, específicas 136/136 y completa 593/593. Sin regresiones abiertas en la selección ejecutada.

## F. Fuentes y alcance normativo

Sólo informe/contexto de FASE 2, código vigente y documentos suministrados en `reglamentos/`. Regla 2022 cotejada en Excel 26.08.2026: Factura2_0 fila 106, Boleta2_0 fila 98 y NotaCredito2_0 fila 81. Precisión/deuda/cuotas según reglas conservadas 2369/2367/3271 y 3265/3266/3319. Perfil CDR según manual v2.1 pp.23/35/38/44; estados getStatus pp.20–21.

XSD locales comparados byte a byte con paquete oficial en FASE 2; validación positiva usa dichos XSD, sin editarlos. Firma local prueba el algoritmo y las validaciones existentes, no un perfil criptográfico SUNAT integral certificado.

Persisten incertidumbres de referencias/identificadores RB–RC/fechas, reglas diferidas al 01/01/2027, catálogos/condiciones externas y gaps del paquete oficial. No se inventaron requisitos ni se ampliaron plazos para enviar documentos.

Precisión documental de la continuación: Excel 26.08.2026 `Resumen Diario1_1` filas 9/11, reglas 2220/2346, exige ID coherente con archivo e IssueDate igual a su fecha; implementado para nuevos RC. `NotaCredito2_0` filas 111/113 (3286) y 114–122 (3503) son comparaciones individuales condicionadas, no prueba de acumulación entre NC. Regla 3496 en filas 169/170 corresponde a código de producto, no saldo. `Control de Cambios` fila 562 posterga sólo los ajustes allí enumerados al 01/01/2027.

El refuerzo de correlación RUC/RC/ticket es una política operativa SaaS autorizada expresamente por el usuario, no una nueva regla SUNAT inferida. No adapta reglas 2027, firma, plazos, NC ni XSD. FASE2-018: correlación operativa reforzada técnicamente; RB/RC y formatos/equivalencias normativas de referencias **PENDIENTE DE CONFIRMACIÓN DOCUMENTAL**. **DEFINICIÓN ORIGINAL NO RECUPERADA**; no cierre integral ni reconstrucción del ID original.

## G. Persistencia y límites operativos

Esta continuación añade sólo ampliaciones propuestas de longitud (RA y respuestas de 100 a 150), sin nuevas columnas/constraints aplicadas. Revisar metadata integral antes del nuevo binario: SchemaValidator no sustituye auditoría de longitudes/índices/datos. Rollback conserva amplitud y evidencias; no disminuir a 100 con datos más largos. UNIQUE RUC/ticket sigue hardening no implementado, no obligación global.

La única entidad nueva de esta fase es `respuesta_sunat_recibida`; NC/RC no añaden tablas/columnas/constraints. Tabla, DDL con `ENUM('CPE','RA','RC')`, índice, impacto y rollback están en `FASE3_CAMBIO_ESQUEMA_RESPUESTAS.md`; procedimiento general en `FASE3_PROCEDIMIENTO_ESQUEMA.md`. Metadatos de MySQL test confirmaron tipos/nullability/PK/índice; Hibernate 6.6.39.Final generó DDL sólo en memoria y SchemaValidator validó ese mapeo contra la tabla test. **No prueba validación integral del esquema desplegado ni aplica SQL a producción.** Con `prod/validate`, el binario nuevo no puede arrancar si falta el esquema requerido. Flyway no fue habilitado, ddl-auto no cambió y no se inventó baseline.

**Cambio de esquema del refuerzo RC/ticket: NO.** La unicidad operativa es por RUC+ticket, garantizada para asociaciones realizadas por este servicio mediante lock de empresa y lectura actual; no se afirma unicidad global del texto de ticket entre empresas. No hay constraint físico que impida escrituras SQL ajenas al servicio. Propuesta no implementada, sujeta a autorización separada: UNIQUE(ruc_emisor,ticket_sunat), comprobando metadatos, collation, semántica de NULL y duplicados/históricos antes de cualquier DDL. No crear dicho constraint automáticamente ni modificar evidencias para hacerlo pasar. La suite usa únicamente el create-drop existente de la base exclusiva/desechable de pruebas autorizada; no DDL manual ni productivo.

Límites técnicos configurables nuevos (defaults):

| Propiedad | Default |
| --- | ---: |
| `sunat.respuesta.max-soap-bytes` | 16777216 |
| `sunat.respuesta.max-zip-bytes` | 8388608 |
| `sunat.cdr.max-xml-bytes` | 16777216 |
| `sunat.cdr.max-expanded-bytes` | 33554432 |
| `sunat.cdr.max-entries` | 64 |

No son tamaños normativos SUNAT. Exceso/configuración inválida no declara rechazo fiscal. Capacidad/retención del archivo append-only requieren decisión operativa; no hay purga automática. No conserva body SOAP bruto cuando falla antes de extraer ZIP ni elimina la ventana entre recepción en memoria y commit. Las respuestas superiores al límite no se acumulan para archivarlas íntegramente.

## H. Decisiones y bloqueadores restantes

1. **FASE2-017 implementado/probado según opción 1 autorizada.** `FASE3_POLITICA_NC_PROPUESTA.md` conserva la propuesta inicial histórica; el código actual, esta actualización y MEMORY.md reflejan la decisión posterior. Falta revisión/conciliación autorizada de históricos reales si existen; no se leyó ni modificó producción. No se añadieron liberaciones por cancelación técnica, timeout, baja o RC rechazado.
2. Esquema real: metadatos/DDL/versiones/ensayo/rollback pendientes de acceso y autorización; el procedimiento escrito no cierra reproducibilidad productiva.
3. FASE2-018/019: **PENDIENTE DE CONFIRMACIÓN DOCUMENTAL** donde falta evidencia oficial suficiente. No se implementa igualdad exacta RC–CDR/ReferenceID, nuevas normalizaciones, perfil criptográfico integral, plazos legales no confirmados ni catálogos externos indisponibles. Su redacción original no fue recuperada; no atribuir cuestiones a cada ID por inferencia.
4. Trazabilidad exacta FASE2-005–010/012–016: cotejar con el registro íntegro original, sin renumerarlo. No se afirma que toda exigencia no conservada esté corregida.
5. Capacidad, archivo/preservación, retención técnica y decisiones legales/contables externas de evidencias pendientes. No hay purga automática ni autorización para implementarla. Legacy RC incompatibles con 2346 requieren revisión, no renombrado silencioso.

## I. Restricciones cumplidas y revisión siguiente

Sin lectura/listado/búsqueda/modificación ni acceso indirecto a `padron_reducido_ruc/`; sin modificar `reglamentos/`; sin credenciales/certificados reales, SUNAT real, emisión/anulación reales, bootstrap de superadministradores, commit, push, despliegue ni migraciones productivas. Sin cambiar dependencias/perfiles/ddl-auto ni descartar trabajo ajeno.

FASE 4 debe auditar independientemente las guardas/transacciones, consultas por RUC, clasificación incierta, evidencia archivada y sus límites, XML firmado final, política NC implementada, nuevas secuencias RC y esquema real. No se inicia mientras existan bloqueadores y sin autorización independiente. Los certificados de tests son efímeros/ficticios y no se incorporan a producción ni al repositorio como claves privadas.

## J. Conclusión

**Veredicto vigente: FASE 3 TÉCNICAMENTE CERRADA en alcance local.** RESTORE VALIDADO, fuente/destino cotejados y SchemaValidator satisfactorio; baseline612/612 mantenido sin omisiones. No queda un bloqueo técnico demostrado dentro de ese alcance. Permanecen exactamente **ROLLBACK DE BINARIO: PENDIENTE DE ENSAYO PRODUCTIVO POR AUSENCIA DE ARTEFACTO ANTERIOR IDENTIFICADO** y **CONCILIACIÓN HISTÓRICA PRODUCTIVA: PENDIENTE DE ENSAYO PRODUCTIVO**. No se declaran ensayados/resueltos; el criterio solicitado permite tratarlos como limitaciones productivas externas, no como defectos actuales. No se atribuye compatibilidad a un binario inexistente ni integridad a datos no inspeccionados. No producción lista ni FASE4.

Resumen histórico: **552+41=593**, ensayo operativo+19=**612=269 original+343 acumulados**. Continuación restore+0, baseline612 mantenido y suite completa repetida. Restore local ya validado; binario anterior e históricos representativos siguen pendientes en entorno autorizado. Ningún otro tema ya resuelto reabierto. No autoriza producción ni inicia/sustituye FASE4.

**FASE 3 TÉCNICAMENTE CERRADA**
