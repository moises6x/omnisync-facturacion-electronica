# FASE 4 — correcciones y revisión final local (02/10/2026)

## A. Hallazgos corregidos

Fuente: último informe independiente F4 de la conversación. Las conclusiones iniciales eran estáticas; esta continuación agrega regresiones ejecutadas. No se declara preparación productiva ni homologación.

| Hallazgo | Corrección mínima y evidencia |
| --- | --- |
| F4-H01 | `SunatDirectSoapClient.ejecutarSoap`: streaming de longitud fija, sin cuerpo buffered replayable; no depender de `sun.net.http.retryPost`. Servidor local recibe el cuerpo completo y cierra o demora respuesta; contador acredita un solo POST por invocación. |
| F4-H02 | `setInstanceFollowRedirects(false)`. Tests 301/302/303/307/308 con servidores A/B: B recibe cero peticiones/cero bytes; error técnico sin SOAP en logs. |
| Fault RC/RA | Después de adquirir/intentar envío, Fault conocido por forma/desconocido y resultado no concluyente son inciertos, nunca rechazo tributario ni permiso de reenvío. Antes de red, fallo local es recuperable. RC/RA exigen cero intentos persistidos para preparar/reintentar; históricos ERROR_ENVIO con intento no se liberan/reenvían por inferencia. |
| F4-H03 | Liberación RC bloquea cabecera por ID/RUC, recoteja estado, verifica asociación con esa cabecera y ausencia de otro propietario activo, y exige conteo completo. MySQL: liberar↔reintentar, liberar↔liberar, liberar↔adquirir nuevo lote, y asociación histórica con otro RC activo. |
| F4-H04 + descripción | Cantidad conserva DECIMAL(12,4) con validación explícita; unitarios22,10; IGV/total de detalle14,2; descripción500. Round-trip con DTO válido, cálculo, flush/clear/reload y firma real ficticia; límites/overflow/metadata. No se modifican documentos anteriores. |
| F4-H05 | Locks de usuario por email/ID/empresa en login y mutaciones; refresh toma usuario antes de token. Sin @Version/DDL. MySQL: login↔desactivar, login fallido↔password temporal, password↔logoutAll, password temporal↔login. Se preservan política existente de intentos y revocación. |
| F4-H06 | Mutadores de configuración SUNAT/API key, suspensión/reactivación y configuración tributaria comparten lock de empresa. MySQL: configuración↔suspensión, configuración↔rotación y dos configuraciones. |
| F4-H07 | Bootstrap automático requiere contraseña inicial por `SECURITY_BOOTSTRAP_INITIAL_PASSWORD`, sin default. Ninguna impresión de contraseña; stdout/stderr/logs capturados con credencial ficticia. Bootstrap HTTP manual no se abre por omisión. |
| F4-H08 | GET_LOCK de MySQL por catálogo; misma conexión para todas las operaciones de importación, intercambio y limpieza. Otro nodo no adquiere ni limpia tablas. Descargas usan nombre UUID para no pisar archivos de otro proceso. MySQL aislado real y dos importadores sintéticos. |
| F4-H09 | Cabecera/estructura mínima, filas válidas, cero/duplicados/advertencias LOAD DATA y conteos cotejados antes de activar. Descenso respecto al padrón anterior requiere revisión si supera umbral técnico (fracción mínima0,95, configurable; no norma SUNAT). ZIP con directorio central válido, CRC leído y un único archivo de datos. RENAME múltiple conserva anterior y archiva versiones previas, sin purga. |
| F4-H10 | Sin perfil fiscal default; EnvironmentPostProcessor registrado antes de beans. Exactamente un perfil beta/prod/test, marcador de destino coherente, beta exige URL aprobada idéntica a datasource efectivo, endpoint exacto, prod=validate, Flyway apagado. Tests negativos de omisión/combinación/datasource sustituido/endpoint/forwarded headers. |
| F4-H11 | Lock compartido de empresa/suscripción, identidad de renovación por clave/payload y UNIQUE por empresa. Scheduler selecciona IDs y vuelve a leer estado/período bajo lock; avisos usan lectura actual para no depender de snapshot anterior. Dos renovaciones, repetición/payload diferente, renovación↔scheduler en ambos órdenes y dos schedulers. AtomicBoolean local no es la garantía de exclusión. |
| F4-M01/M02 | Endpoint RA de reintento restringido a fallo local sin intento; misma RA, no recreación. Asociación por RUC+RA+ticket, duplicado por empresa impedido en flujo, ticket consultado recotejado bajo lock final en CDR/98/99/error. Respuesta tardía de ticket cambiado no altera RA/documentos. Sin nueva igualdad normativa de referencias. |
| F4-M03 | Diagnóstico acotado en setters CPE/RC/RA y bulk RC; XML/ZIP intactos. MySQL aceptado/rechazado con texto multibyte>65.535 bytes no bloquea transición; evidencia XML íntegra. |
| F4-M04 | Evento seguro con nodo UUID, RUC, ID, identificación/tipo/serie/número o RC/RA, ticket, estado/código y timestamp de logging. Auditoría post-commit no propaga error ni registra mensaje/stack potencialmente secreto. XML debug del worker no imprime contenido. |
| F4-M06 (parcial) | RC exige conteo completo también en confirmación/baja; transición parcial revierte y conserva ZIP de recepción para conciliación. No nuevas FK ni backfill de referencias históricas. |
| F4-M07 | Reproducido antes de corregir: tenant con backlog permanente, primera página RA persistente y RC con fallo local previo a getStatus. Rotación de tenants y avance de página RA; fallo local RC registra ERROR_CONSULTA y permite orden existente updatedAt/id. No se cambian asociaciones ni orden documental dentro de un RC. |
| F4-M08 | Delegación se considera completada sólo después de obtener futuro y registrar callback; error síncrono limpia flag/archivo. También se limpia ante rechazo del executor. Test permite segunda actualización local controlada. |
| F4-M05 (mitigación) | JSON1MiB, incluido chunked; máximo técnico CPE/NC500 items y120 cuotas. Forwarded headers `none`, beta/prod rechazan estrategia no verificada. Rate limit por nodo documentado; sin Redis ni infraestructura inventada. |

## B. No corregidos integralmente y motivo

- **F4-M05:** rate limit sigue por JVM, no contador compartido. La fase permite mitigación operativa mediante ingress confiable/restricción de acceso; no se añadió dependencia distribuida.
- **F4-M06:** referencias fiscales escalares no se convirtieron en FK; los conteos de updates y aislamiento sí se endurecieron. Históricos ambiguos requieren conciliación, no reparación automática ni nuevas asociaciones.
- **F4-B01:** selección del primer alias privado no se rediseñó. Usar PFX controlado de un único alias privado y verificarlo en el procedimiento del entorno.
- **F4-B02:** referencias obsoletas actuales se aclararon; se conserva el historial, no se reescriben sus totales/veredictos como si fueran vigentes.
- Ningún bloqueo H01–H11 queda sin la corrección local descrita. Los límites de prueba/operación siguientes no son una certificación general del entorno productivo.

## C. Inventario del delta de esta continuación

El diff contra HEAD incluye trabajo previo no comprometido; no usarlo como inventario exclusivo F4. Archivos intervenidos aquí:

### Producción/configuración

Rutas bajo `src/main/java/org/example/`:

```text
service/SunatDirectSoapClient.java
service/ResumenDiarioService.java
service/ComunicacionBajaService.java
service/EnvioSunatService.java
service/CalculoTributarioService.java
service/EmpresaConfiguracionService.java
service/PadronRucImportService.java
service/PadronRucActualizacionService.java
service/ResumenDiarioScheduler.java
service/ComunicacionBajaScheduler.java
repository/ComprobanteRepository.java
repository/EmpresaRepository.java
repository/ComunicacionBajaRepository.java
model/DetalleComprobante.java
model/Comprobante.java
model/ResumenDiario.java
model/ComunicacionBaja.java
util/ImportesXml.java
util/DiagnosticoSunat.java (nuevo)
util/ObservabilidadFiscal.java (nuevo)
auth/repository/UsuarioRepository.java
auth/repository/RefreshTokenRepository.java
auth/service/AuthService.java
auth/service/SuperAdminBootstrapService.java
auth/controller/UsuarioService.java
auth/security/JsonSizeFilter.java (nuevo)
admin/service/SuperAdminUsuarioService.java
admin/service/SuperAdminEmpresaService.java
admin/service/ConfiguracionTributariaEmpresaService.java
admin/service/SuscripcionAutomaticaService.java
admin/model/PagoEmpresa.java
admin/repository/PagoEmpresaRepository.java
admin/repository/SuscripcionEmpresaRepository.java
admin/repository/AnuncioEmpresaRepository.java
admin/controller/SuperAdminEmpresaController.java
controller/ComunicacionBajaController.java
config/ConfiguracionFiscalEnvironmentPostProcessor.java (nuevo)
dto/ComprobanteRequest.java
dto/NotaCreditoRequest.java
```

Recursos: `src/main/resources/application.properties`, `src/main/resources/META-INF/spring.factories` (nuevo). No se cambiaron POM, dependencias, algoritmos de firma, prod=validate ni Flyway.

### Pruebas/documentos

Rutas bajo `src/test/java/org/example/`:

```text
service/SunatDirectSoapClientContractTest.java
service/ResumenDiarioServiceTest.java
service/ResumenDiarioServiceTestt.java
service/ResumenDiarioSchedulerTest.java
service/ComunicacionBajaSchedulerTest.java
service/PadronRucActualizacionServiceTest.java (nuevo)
service/ObservabilidadFiscalTest.java (nuevo)
repository/ResumenDiarioTicketRepositoryTest.java
repository/ComunicacionBajaRepositoryTest.java
repository/Fase4DecimalRepositoryTest.java (nuevo)
repository/Fase4SeguridadConcurrenciaRepositoryTest.java (nuevo)
repository/Fase4SuscripcionRepositoryTest.java (nuevo)
repository/Fase4PadronRepositoryTest.java (nuevo)
auth/service/SuperAdminBootstrapServiceTest.java (nuevo)
admin/service/ConfiguracionTributariaEmpresaServiceTest.java
config/ConfiguracionFiscalTest.java (nuevo)
security/JsonSizeFilterTest.java (nuevo)
```

Documentos: `AGENTS.md`, `MEMORY.md`, aclaración histórica en `FASE3_INFORME.md`, nuevos `FASE4_INFORME.md` y `FASE4_ESQUEMA.md`.

Utilidades/evidencias externas: `/tmp/opencode/run-fase4-tests.py`, `/tmp/opencode/fase4-isolated-mysql.ps1`, logs `fase4-*.log`; datadir/logs del MySQL sintético en `%TEMP%/opencode-fase4-mysql-isolated-2/`. Instancia iniciada sólo en loopback:33307 y apagada al concluir; conservar archivos de ensayo, no sobrescribirlos al reiniciar. Ningún cambio global en el servidor habitual.

## D. Esquema

DDL propuesto, impacto, compatibilidad y rollback preservador en `FASE4_ESQUEMA.md`. Ampliaciones `detalle_comprobante`, dos campos nullable64 e índice por empresa en `pago_empresa`. Tests usan create-drop exclusivamente en bd_facturacion_test; padrón usa LOAD DATA/RENAME real sólo en su servidor desechable. No se aplicó ALTER a producción ni se ensayó una migración incremental del esquema productivo. No tocar restore_test ni sustituir su pareja original por los artefactos target regenerados.

## E. Tests nuevos

**80 nuevos casos:** 52 en nueve clases nuevas y28 en clases existentes. Nuevas: Bootstrap2, Configuración12, Decimal10, Seguridad7, Suscripción7, Padrón9, JSON3, Observabilidad1, Actualización1. Incrementos existentes: SOAP5, RC11 (27→38), RA10 y schedulers2. Conteo definitivo por suite completa:612+80=692. No se eliminaron tests; fixtures Mockito fueron adaptados a locks/conteos y una expectativa antigua de propagar auditoría se sustituyó por afirmar que no falla el resultado confirmado, conservando assertions fiscales y de no reenvío.

## F. Resultado de pruebas

| ANTES | NUEVOS | TOTAL | PASAN | FALLAN | ERRORES | OMITIDOS |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 612 | 80 | 692 | 692 | 0 | 0 | 0 |

Comando final: `python3 /tmp/opencode/run-fase4-tests.py '*Test,*Testt' fase4-full-cierre`. Exit0, BUILD SUCCESS; log `/tmp/opencode/fase4-full-cierre.log`. XML comprobados:74 clases, incluidas las6 Testt, sumas692/0/0/0. No sumar selecciones específicas solapadas como casos nuevos.

Específicas: transporte/RC103; padrón/suscripción/RA35; progreso corregido57; guardas finales94. Fallos intermedios conservados en logs: expectativa anterior de auditoría, stubbing antiguo, compilación de fixture, fixture CREADO con intento incorrecto, LOCAL INFILE apagado en servidor habitual, y regresiones de starvation previas al fix. Nada se ocultó mediante skips, cambio de assertions fiscales o relajación de validaciones. MySQL aislado resolvió infraestructura del padrón, no una modificación global del servidor compartido.

## G. Riesgos altos restantes

Ninguno de los H01–H11 permanece como defecto demostrado dentro de los escenarios locales ensayados. No se verificaron configuración, grants, TLS, proxy, esquema, artefactos anteriores ni topología de una instalación desplegada. No convertir esta conclusión limitada en “producción lista”.

## H. Riesgos medios/límites restantes

- Rate limiting por nodo; ingress y cuotas globales requieren infraestructura verificada antes de exponer varios nodos.
- Referencias escalares/históricos externos aún requieren cotejo; writers SQL ajenos al flujo no quedan impedidos por todas las guardas de aplicación.
- Padrón: GET_LOCK no coordina primarios independientes; archivos locales de versión/programación no constituyen metadata distribuida authoritative ni evitan por sí solos cargar secuencialmente una versión vieja. Mantener un único actualizador automático designado hasta acreditar coordinación de versiones; otros nodos pueden consultar el padrón. Importadores concurrentes sí están serializados por BD.
- El umbral de volumen/estructura detecta cargas claramente incompletas; no prueba autenticidad ni completitud absoluta de la fuente, especialmente primera instalación. Requiere aprobación del archivo/versión y capacidad de archivos/tablas retenidas.
- Reinicios ensayados mediante fallo controlado/rollback/conexión cerrada y recreación de servicios; no apagado literal de equipo ni prueba de clúster desplegado.
- Persisten las ventanas fiscales de recepción antes de commit y pérdida de ticket ya documentadas; conservar evidencia y no reintentar inciertos.

## I. Mitigaciones operativas necesarias

1. Entorno controlado con BD/cuentas propias, esquema nuevo cotejado y backup; no heredar datasource productivo. `SPRING_PROFILES_ACTIVE` explícito, `APP_DEPLOYMENT_ENVIRONMENT` coherente y URL beta aprobada cuando corresponda. La igualdad de URLs no prueba identidad/grants físicos: cotejarlos aparte.
2. Bootstrap mediante gestor de secretos/operador autorizado, nunca línea de comando/log/documentación con valores; retirar provisión tras crear y cambiar contraseña. No ejecutar bootstrap como parte de una prueba fiscal.
3. `forward-headers-strategy=none`; backend inaccesible directamente desde Internet si se añade ingress. Rate limit global en ingress verificado o un solo nodo público mientras no exista esa garantía; no confiar cabeceras de clientes directos.
4. Un mismo primario/catálogo para locks de los nodos, escritor automático único para metadata/versiones del padrón y archivos controlados. Monitorear errores, conteos, retención, espacio y posesión del lock. No borrar anteriores automáticamente.
5. Adaptar cliente administrativo a X-Idempotency-Key; separar pagos legítimos y reintentos. Validar PFX de alias único controlado y no usar credenciales/certificados reales en pruebas locales.
6. Monitorear eventos FISCAL/AUDITORIA_POST_COMMIT_FALLIDA, pendientes/errores/inciertos y asociaciones ambiguas; conciliación expresa, no reenvío ciego. Logging no sustituye persistencia fiscal ni ledger de auditoría.

## J. Documentación actualizada

Guía vigente: perfiles, bootstrap, rate limit/proxy, transporte/recuperación, tipos persistidos, firma positiva ya existente y garantías DB frente a optimizaciones locales. Memoria: resultado F4 y próximos pasos separados del historial. FASE3_INFORME conserva cierre histórico612 y restore; nuevas propuestas/resultados en documentos F4.

## K. Bloqueadores para FASE 5

No queda bloqueo funcional demostrado de los H01–H11 en este cierre local. FASE5 no está autorizada ni iniciada: su futuro entorno debe acreditar las condiciones de I antes de exponerse o usar servicios externos. Si no puede aislar cuentas/datasource, aplicar/cotejar esquema, limitar ingress multinodo o designar actualización del padrón, **ese entorno** queda bloqueado. Rollback binario e históricos productivos siguen pendientes de FASE3 sin reabrir su cierre limitado.

## L. Estado final

**FASE 4 SIN BLOQUEADORES**

Limitado a código y escenarios locales descritos, con mitigaciones operativas explícitas; no producción lista, homologación, SUNAT real ni autorización para iniciar FASE5. Sin commit, push, despliegue, cambios en reglamentos ni acceso al directorio restringido.
