# FASE 5 — preparación técnica beta sin llamadas SUNAT

Estado vigente: **LISTO PARA EJECUCIÓN BETA**.

## Primera integración real autorizada — ejecución física pendiente

Autorización posterior del operador: **máximo una factura ficticia y un sendBill beta**, sin retries, sendSummary, getStatus, NC, boletas, RC/RA ni producción. El cierre local anterior sigue acreditado; todavía no hay resultado de integración SUNAT. El agente no emitió comprobantes ni consumió correlativos ni realizó tráfico SUNAT para preparar esta continuación.

Se autorizó ampliar sólo `src/test/java/org/example/config/Fase5BetaPrecheckIntellij.java`. Nuevo argumento del mismo helper: `--primera-factura-beta`. No nuevo cliente/service beta ni cambio en src/main. Contexto selectivo con services/repositorios reales, transacciones Spring y listener productivo AFTER_COMMIT/@Async; **sin Main, component scan de beans, @EnableScheduling ni beans de padrón/recovery/RC/RA/monitor/bootstrap**. Un único worker; espera de su finalización, sin polling ni reenvío. No repite SchemaValidator, provisión ni fixtures fiscales.

Datos de solicitud nueva:01/F501, contado/0101/PEN, un ítem ficticio base10,00, IGV normal18%, total11,80. Receptor documental6/20100066603 y nombre ficticio del escenario ya existente en `Fase5BetaPreparacionTest`; no afirma identidad ficticia/inexistente del RUC ni aceptación SUNAT. Fuente beta: `reglamentos/manual_programador (1).pdf`, págs.6–7 y12–17; endpoint heredado de `application-beta.properties`, no reemplazado. Las credenciales persistidas se cotejan internamente con el escenario documental preparado; nunca se sustituyen ni se muestran.

### Operación única desde IntelliJ

1. Detener Main y cualquier otro proceso que pueda emitir/recuperar documentos en beta. No basta con declarar una variable si siguen ejecutándose. Mantener provisión privada actual, perfilbeta, marcadorBETA y ambas URLs exactas autorizadas.
2. Recompilar el soporte y usar la misma clase con `--primera-factura-beta`. Sólo para este lanzamiento, añadir `FASE5_AUTORIZAR_UN_SEND_BILL=true` y `FASE5_MAIN_DETENIDO=true` después de confirmar físicamente la parada. No compartir secretos ni importar el overlay al Main por accidente. `FASE5_EVIDENCIA_PATH` debe seguir siendo un directorio seguro externo, sin enlaces; se creará una subcarpeta exclusiva `primera-factura-beta`.
3. Antes de ejecutar, configurar un **logpoint sin suspensión** en `src/main/java/org/example/service/SunatDirectSoapClient.java:542`, después de asignar `httpCode`: expresión `"HTTP_STATUS_TECNICO=" + httpCode`. Capturar sólo ese entero para el informe técnico. No activar logging SOAP/HTTP ni renderizadores/variables/objetos de secretos. El cliente compartido no expone el HTTP exitoso exacto por su API; el helper sólo puede acreditar2xx en éxito y el exacto disponible para SOAP Fault. El logpoint no cambia el cliente, los timeouts ni el transporte.
4. Ejecutar **una sola vez**, sin otros requests. El control vuelve a verificar ambiente/BD, empresa/certificado, XML persistido, firma/digest/XSD y bytes/nombre del ZIP real del worker inmediatamente antes de invocar el cliente. No hay segunda llamada aun si falla. Directorio exclusivo, lock MySQL y ausencia exigida de comprobantes/idempotencias previas impiden reejecución del soporte; no borrar markers ni filas para repetir.
5. Esperar resultado y terminación. Compartir sólo indicadores saneados, HTTP entero, ResponseCode y descripción previamente saneada, nunca objetos, tokens, PFX, claves, ciphertexts ni XML/SOAP completo. La descripción/notas se conservan por el worker en BD/CDR, no se imprimen automáticamente porque son texto externo. Retirar `FASE5_AUTORIZAR_UN_SEND_BILL` después de este lanzamiento, mantener Main detenido y esperar autorización posterior.

Evidencia: XML/ZIP enviados exactos y metadatos antes del intento; marcador durable del intento; CDR recibido/procesado si existe y resultado tras reload en la subcarpeta externa. La recepción se conserva mediante el service productivo REQUIRES_NEW antes del parser. SOAP Fault/transporte/protocolo conserva la política productiva conservadora; resultado incierto no se reenvía. Un fallo del archivo técnico después de red no sustituye la respuesta/exception del cliente ni altera deliberadamente el estado fiscal.

Verificación del soporte: `test-compile -DskipTests`, BUILD SUCCESS, sin ejecución de tests/aplicación/BD beta/SUNAT. Log externo `/tmp/opencode/fase5-primera-factura-compile-final.log`. Esto acredita compilación, **no** validación física del nuevo contexto ni envío/CDR/estado. Suite730 histórica no repetida. No commitGit/push/deploy. Nunca implica producción lista ni homologación.

## Corrección vigente — cambio inicial y login acreditados físicamente

Fuente: resultado saneado comunicado por el operador de IntelliJ al ejecutar `org.example.config.Fase5BetaPrecheckIntellij --cambiar-password-acceso`, exit0. No es una nueva ejecución del agente.

- EMPRESA_ADMIN_BETA: persistido y verificado tras commit/reload.
- Empresa asociada:20100066603; rolEMPRESA_ADMIN; estadoACTIVO; hash presente/verificado sin mostrar.
- CAMBIO_PASSWORD_INICIAL:YA_COMPLETADO.
- LOGIN_INTERNO: validado mediante AuthService/JWT, sin mostrar token.
- ACCESO_ENSAYO:CONFIGURADO; BLOQUEADOR_RESTANTE:NINGUNO_EN_ACCESO.
- LLAMADAS_SUNAT:0; estado físico comunicado:LISTO PARA EJECUCION BETA.

Esto cierra el bloqueo de acceso del precheck beta local. No acredita producción/homologación/aceptación SUNAT ni autoriza ninguna llamada SUNAT. No se repite ninguna operación física ni prueba para actualizar este estado. No secretos en este informe, ni producción/sendBill/sendSummary/getStatus/commitGit/push/deploy.

Las secciones siguientes conservan el histórico de preparación; las referencias a acceso/cambio inicial pendientes quedan sustituidas por este resultado. No reejecutar provisión ni cambio inicial por esas instrucciones históricas.

## Confirmación operativa posterior — esquema existente, precheck físico pendiente

El operador confirmó que Main inició en beta con marcador BETA, las dos URLs exactas de bd_facturacion_beta_ensayo, bootstrap OFF, Hikari conectado, EntityManagerFactory y Tomcat inicializados. La base **ya contiene el esquema generado por ese arranque**; no repetir provisión ni recreación. Estos datos provienen del operador, no de una consulta JDBC del agente, y no sustituyen SchemaValidator ni commit/reload del soporte.

La ejecución pendiente sigue siendo **la misma** `org.example.config.Fase5BetaPrecheckIntellij --preparar`. Ante un esquema completo existente, esa herramienta valida sin reaplicar CREATE; ante diferencias se detiene. No se creó otra herramienta ni se cambiaron código/tests en esta continuación. El operador eligió ejecutarla desde IntelliJ y aún no entregó indicadores físicos. OpenCode no puede lanzar su configuración de IDE ni consumir sus variables privadas; no se intentó leer/copiar sus valores. Detener Main antes del soporte para impedir concurrencia con automatizaciones del proceso normal.

Última suite verificada:730/730,0 fallos/errores/omitidos, no repetida en esta espera sin cambios de código. Empresa/credenciales/certificado/reload y siete fixtures **sobre beta** no se declaran demostrados hasta recibir el resultado saneado de la herramienta. No llamadas SUNAT, no nueva autorización de red ni declaración de producción lista.

## Actualización vigente — cuenta compartida local y precheck desde IntelliJ

El operador confirmó **127.0.0.1:3306**, `bd_facturacion_beta_ensayo` inicialmente creada/vacía (ahora con esquema por su arranque beta posterior), y autorizó **la misma cuenta de desarrollo que utiliza la aplicación**. Ya no se exige crear/validar `facturacion_beta_ensayo` ni grants exclusivos. No elegir una cuenta alternativa automáticamente: la herramienta consume la cuenta explícita del proceso del IDE y comprueba el catálogo/puerto antes de escribir. La cuenta puede tener permisos más amplios; este soporte nunca usa otros catálogos ni administra usuarios/grants.

**Corrección de visibilidad:** APP_ADMIN_KEY, APP_JWT_SECRET_BASE64, APP_MASTER_KEY_BASE64 y SPRING_DATASOURCE_PASSWORD están provisionadas en IntelliJ según el operador. No son visibles desde OpenCode; eso **no** implica que falten. No se leyó la configuración del IDE ni se copiaron valores. La contraseña existente corresponde ahora a la misma cuenta autorizada, no a una cuenta nueva. Otros campos del IDE no se han inspeccionado ni se dan por ausentes.

El operador eligió ejecutar **un precheck aislado en IntelliJ**, no arrancar Main ni transferir secretos al agente. Soporte preparado: `src/test/java/org/example/config/Fase5BetaPrecheckIntellij.java`. Está en fuentes de test, no en el JAR principal; no es una versión beta de la aplicación. Reutiliza entidades, repositorios, services, cifrado, firmador, validadores y fixtures actuales.

### Ejecución manual autorizada SIN SUNAT

1. Crear una configuración **Application** llamada `Precheck BETA SIN RED`.
2. Main class: `org.example.config.Fase5BetaPrecheckIntellij`.
3. Usar JDK21 y el classpath del módulo del proyecto **incluyendo clases/recursos de test**. Directorio de trabajo: raíz de Facturacion.
4. Argumentos: `--preparar`. No Spring Boot/Main, argumentos fiscales ni debugger de secretos.
5. Reutilizar mediante la provisión segura del IDE las variables existentes. El agente no accede a sus valores ni modifica `.idea`/`.run`. No versionar una configuración con secretos.
6. Proveer los campos siguientes; este bloque no contiene secretos:

```text
SPRING_PROFILES_ACTIVE=beta
APP_DEPLOYMENT_ENVIRONMENT=BETA
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/bd_facturacion_beta_ensayo
APP_BETA_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/bd_facturacion_beta_ensayo
SECURITY_BOOTSTRAP_ENABLED=false
SECURITY_BOOTSTRAP_AUTO_CREATE=false
```

`SPRING_DATASOURCE_USERNAME`: el mismo usuario lógico ya utilizado por la aplicación, provisto explícitamente en el IDE; no se imprime ni se asume su nombre por un default. `SPRING_DATASOURCE_PASSWORD`, `APP_ADMIN_KEY`, `APP_JWT_SECRET_BASE64`, `APP_MASTER_KEY_BASE64`: mantener su provisión existente, sin regeneración ni traslado a documentación.

`APP_CORS_ALLOWED_ORIGINS`: si se necesita añadirla, origen local real del cliente. El código común ya declara localhost4200/5173; `http://localhost:5173` es un ejemplo local, no prueba de frontend existente. No wildcard ni dominio productivo.

Añadir para este soporte:

- `FASE5_PADRON_METADATA_PATH`: ruta absoluta **nueva/vacía**, externa al proyecto, sin enlaces/reparse points. Se valida sin seguir enlaces y no se instancia el servicio del padrón. No se borra metadata existente.
- `FASE5_EVIDENCE_PATH`: otra ruta absoluta externa sin enlaces, no solapada con metadata. Guarda sólo XML/ZIP/hash ficticios y un resultado saneado en subdirectorio nuevo por ejecución; no PFX/password ni claves privadas.
- Opcional `FASE5_USUARIO_PASSWORD`: secreto temporal suministrado por operador para crear un EMPRESA_ADMIN de ensayo mediante el servicio/política/BCrypt12 existentes. No se genera ni publica. Si falta, se prepara empresa/certificado pero se informa acceso pendiente. No SUPER_ADMIN. Si se crea, conserva el cambio obligatorio al primer login; no se elimina esa protección para declarar listo.

Este soporte carga explícitamente application común+beta+overlay de precheck. **No definir en esta configuración Application** SPRING_APPLICATION_JSON, SPRING_CONFIG_LOCATION/IMPORT/NAME/ADDITIONAL_LOCATION ni perfiles adicionales: se rechazan porque no serían cotejados por este cargador aislado. Esto no modifica cómo Boot carga la aplicación normal. Para Main - BETA futuro, el overlay deberá aplicarse expresamente como configuración externa; no arrancarlo en esta etapa.

### Operaciones y guardas de la herramienta

- Validar perfil/marcador/endpoint heredado, dos URLs exactas, secretos sólo por presencia/formato/coherencia y flags seguros. No adoptar cuentas TEST/root ni defaults si falta el usuario explícito.
- Abrir JDBC sólo al destino aprobado y comprobar DATABASE()/puerto. No CREATE DATABASE/USER, GRANT, Flyway ni escrituras en otros catálogos.
- Regenerar DDL en memoria desde todas las entidades actuales y cotejarlo exactamente con `target/fase5-precheck/schema-create-propuesto.sql`. Si no coincide, detenerse. Si falta el archivo, ejecutar antes la prueba local Fase5BetaPreparacionTest, sin usar el datasource beta para la suite.
- Aplicar CREATE/índices/FK sólo si el catálogo está vacío. DDL MySQL autocommit: un fallo puede dejar esquema parcial; **no rollback/purga/reparación automáticos**. Si ya hay tablas, exige exactamente las21 esperadas y valida sin reaplicar DDL.
- SchemaValidator aislado y cotejo adicional integral de tipos/precisiones/nulos/PK/índices/UNIQUE/FK. No interpretar una prueba sobre bd_facturacion_test como validación beta.
- Rechazar datos fiscales/secuencias preexistentes y empresas/usuarios ajenos al ensayo. Crear empresa/suscripción por service actual y cifrar configuración/certificado por EmpresaConfiguracionService en TX real. RUC sólo documental autorizado. No emisión, evento ComprobanteCreado ni consumo de secuencias.
- Certificado nuevo sólo si no hay configuración: X.509 autofirmado RSA2048, OU del emisor, alias privado único, password aleatorio. Generación en temporal externo validado, password por entorno keytool, limpieza sólo de sus propios archivos. PFX/password quedan cifrados en Empresa; no se sobrescriben configuraciones parciales ni certificados existentes.
- Tras commit, nueva transacción/reload: descifrado en memoria, Username interno sin red, certificado/vigencia/alias/autofirma, siete fixtures XML/firma/digest/XSD/ZIP. Fixtures de fechas/números fijos, marcados NO EMITIDOS: **no utilizarlos como comprobantes para enviar**.
- No arranque completo, schedulers, HTTP, autenticación SUNAT ni llamada a métodos de envío/consulta. Logging de bibliotecas silenciado en ese proceso para no filtrar errores SQL/entorno; stdout sólo indicadores fijos. Ante fallo devuelve etapa/tipo, no mensaje/cause/stack ni secretos.

La herramienta **no fue ejecutada con el datasource beta desde OpenCode**. No se afirma que se aplicó esquema, se persistió empresa/certificado o pasó SchemaValidator beta. El operador deberá compartir sólo el resultado saneado/etapa, no variables ni capturas con secretos. `PRECHECK_LOCAL_VALIDADO_SIN_ENVIO` acredita únicamente la ejecución local exitosa; acceso inicial/configuración de Main/condiciones de egress pueden seguir pendientes y no autoriza SUNAT.

### Pruebas actuales

Antes709; nuevos21; **total730/730**, fallos/errores/omitidos0/0/0. Específicas38/38: `python3 /tmp/opencode/run-fase4-tests.py 'Fase5BetaPreparacionTest,Fase5BetaPrecheckIntellijTest' fase5-intellij-precheck-specific-final`. Suite final `python3 /tmp/opencode/run-fase4-tests.py '*Test,*Testt' fase5-intellij-precheck-full-final`, exit0/BUILD SUCCESS, XML76 clases incluidas6Testt; sumas `/tmp/opencode/fase5-intellij-suite-result.json`. Los19 guards iniciales pasaron y se añadieron2 regresiones de overrides/configuración externa antes del cierre final. Los17 casos anteriores conservaron su comportamiento; sólo se expusieron helpers de fixtures en fuentes de test para reutilización, sin cambios de código principal. La suite730 acredita compilación/regresiones/guards, **no ejecución del helper JDBC contra beta**. MySQL sintético retenido reiniciado sólo para la suite autorizada, apagado al concluir y archivos conservados; sin cambio global del MySQL habitual. DDL offline conserva el SHA-256 anterior.

Las secciones siguientes describen la preparación anterior. Las exigencias de cuenta exclusiva/destino indefinido y la clasificación general de variables como faltantes **quedan sustituidas por esta actualización**.

FASE 3 conserva su cierre técnico y FASE 4 su cierre limitado. Esta continuación autoriza preparación, no `sendBill`, `sendSummary`, `getStatus`, autenticación SUNAT ni ejecución fiscal. No aplicación completa, producción, Flyway, commit/push/deploy. No acceso al directorio restringido ni modificación de reglamentos.

## A. Base beta

- Destino exclusivo: `bd_facturacion_beta_ensayo`, desechable.
- Destino vigente confirmado por operador: **127.0.0.1:3306**, base creada/vacía. Cuenta actual de la aplicación autorizada; nombre/secretos se consumen sólo en el proceso del IDE.
- No conexión beta desde OpenCode: identidad física/acceso/esquema **NO VERIFICADOS por el agente**. La cuenta exclusiva ya no es requisito. No se creó ninguna fila beta desde esta preparación.
- Se autorizó aparte la suite en sus dos `bd_facturacion_test` desechables. No son la base beta.

### Acción administrativa pendiente; SQL propuesto, NO ejecutado

**HISTÓRICO, NO EJECUTAR POR ESTA CONTINUACIÓN:** el operador retiró el requisito de cuenta exclusiva. Los SQL siguientes conservan la propuesta anterior; no bloquean el precheck con la cuenta existente.

Confirmar servidor/puerto, usuario lógico y host de conexión antes de completar las plantillas. La creación de cuenta y la provisión de contraseña corresponden al operador por canal seguro; no generar ni publicar ese secreto.

```sql
-- Sólo si el operador confirma que la base todavía no existe.
CREATE DATABASE `bd_facturacion_beta_ensayo` CHARACTER SET utf8mb4;

-- Sólo si no existe la cuenta exclusiva aprobada.
-- El marcador NO es una contraseña utilizable ni un valor predeterminado.
CREATE USER '<USUARIO_LOGICO_BETA>'@'<HOST_CLIENTE>'
  IDENTIFIED BY '<SECRETO_PROVISTO_FUERA_DE_DOCUMENTACION>';
```

Privilegios de operación para los flujos limitados: `SELECT, INSERT, UPDATE`. Sin privilegios globales, GRANT OPTION, FILE, CREATE USER ni acceso a otras bases. Si otro flujo precisa DELETE, aprobarlo separadamente; no se necesita para las escrituras previstas del precheck.

Para ejecutar el DDL de creación de tablas/índices/FK: `CREATE, ALTER, INDEX, REFERENCES`, sólo sobre este catálogo y durante la preparación. Puede ejecutarlo una cuenta DDL separada del operador. No se propone DROP.

El operador debe comprobar `@@GLOBAL.partial_revokes`: los nombres de catálogo contienen guiones bajos, que pueden interpretarse como comodines en GRANT. Plantillas para **partial_revokes=OFF**:

```sql
GRANT SELECT, INSERT, UPDATE
  ON `bd\_facturacion\_beta\_ensayo`.*
  TO '<USUARIO_LOGICO_BETA>'@'<HOST_CLIENTE>';
GRANT CREATE, ALTER, INDEX, REFERENCES
  ON `bd\_facturacion\_beta\_ensayo`.*
  TO '<USUARIO_DDL_BETA>'@'<HOST_CLIENTE>';
```

Para **partial_revokes=ON**, usar el catálogo literal sin esos escapes:

```sql
GRANT SELECT, INSERT, UPDATE
  ON `bd_facturacion_beta_ensayo`.*
  TO '<USUARIO_LOGICO_BETA>'@'<HOST_CLIENTE>';
GRANT CREATE, ALTER, INDEX, REFERENCES
  ON `bd_facturacion_beta_ensayo`.*
  TO '<USUARIO_DDL_BETA>'@'<HOST_CLIENTE>';
```

No ejecutar ambas variantes. Verificar después alcance exacto/grants efectivos y retirar la habilitación DDL temporal de esa cuenta, si se utiliza. No se ejecutó ningún SQL administrativo ni se eligió otra cuenta automáticamente.

## B. Esquema

Generado offline desde **todas las entidades actuales**, mismo código/dialecto Hibernate, sin datasource, con `database.action=none`: `target/fase5-precheck/schema-create-propuesto.sql`, 21 tablas e índices/FK. SHA-256 de esta ejecución: `cdc506f751c64d1bf057ff5f069b107cea7027fc4591a9dc76201b602dd7b7cf`.

Incluye cantidad12,4; unitarios22,10; detalle monetario14,2; descripción500; hashes64/UNIQUE empresa+idempotencia; archivo de recepción y tickets150. No inventa UNIQUE de tickets ni baseline Flyway. La tabla vacía del modelo del padrón no supone carga/importación.

**No ejecutar el CREATE sobre un esquema existente.** Primero comprobar catálogo/servidor/grants, metadata y vaciedad. Si ya tiene tablas, comparar y proponer únicamente diferencias aditivas, conservando evidencias. Para F4 existente, DDL/impacto/rollback en `FASE4_ESQUEMA.md`; para F3, `FASE3_CAMBIO_ESQUEMA_RESPUESTAS.md`.

SchemaValidator aislado sobre **beta: PENDIENTE**, no sustituido por pruebas sobre otra BD. Debe abarcar todas las entidades y cotejar además PK/FK/índices/nulos/precisiones/longitudes, sin beans de servicios ni SessionFactory de aplicación. Revisar versión MySQL/charset/collation antes de aprobar el SQL offline.

## C. Perfil beta

Overlay de soporte: `src/test/resources/fase5-beta-precheck.properties`. **No se carga automáticamente** ni se empaqueta como configuración principal. Se podrá suministrar externamente al mismo JAR sólo tras aprobar el precheck y la etapa de arranque correspondiente.

El entorno deberá proveer `SPRING_PROFILES_ACTIVE=beta` y `APP_DEPLOYMENT_ENVIRONMENT=BETA`. El endpoint se hereda exclusivamente de `application-beta.properties`, no se duplica. El processor compartido exige perfil fiscal exclusivo, marcador/endpoint correctos y datasource idéntico a `APP_BETA_DATASOURCE_URL`.

El overlay propone `ddl-auto=validate` para ese lanzamiento beta posterior a preparar esquema. No cambia el archivo beta original, el perfil prod ni el ddl-auto productivo. No se activó ningún perfil de aplicación real.

## D. Variables internas

La comprobación anterior sólo cubrió el entorno de OpenCode/Windows, **no IntelliJ**. Estado corregido:

| Variable | OpenCode | IntelliJ según operador / acción beta |
| --- | --- | --- |
| SPRING_DATASOURCE_URL | NO VISIBLE | Fijar URL beta exacta |
| SPRING_DATASOURCE_USERNAME | NO VISIBLE | Misma cuenta actual, explícita; no inspeccionada |
| SPRING_DATASOURCE_PASSWORD | NO VISIBLE | CONFIGURADO en IDE según operador; misma cuenta autorizada |
| APP_BETA_DATASOURCE_URL | NO VISIBLE | Fijar URL beta exacta, idéntica |
| APP_MASTER_KEY_BASE64 | NO VISIBLE | CONFIGURADO en IDE según operador |
| APP_ADMIN_KEY | NO VISIBLE | CONFIGURADO en IDE según operador |
| APP_JWT_SECRET_BASE64 | NO VISIBLE | CONFIGURADO en IDE según operador |
| APP_CORS_ALLOWED_ORIGINS | NO VISIBLE | No inspeccionado; origen local si se añade |

No se generaron, reemplazaron, copiaron ni persistieron secretos de lanzamiento. Las claves sintéticas de pruebas no son provisión beta.

## E. Bootstrap

Overlay: ambos flags false. No SUPER_ADMIN, bootstrap automático ni bootstrap HTTP ejecutados.

La nueva base necesita una primera empresa y un usuario de ensayo. Los endpoints administrativos existentes requieren SUPER_ADMIN; con base vacía/bootstrap OFF no hay una sesión administrativa inicial. **Acción controlada pendiente:** provisionar empresa/suscripción y un EMPRESA_ADMIN exclusivo mediante invocación transaccional aislada de los services existentes (`SuperAdminEmpresaService.crear`, `SuperAdminUsuarioService.crear`), usando contraseña de usuario provista por operador y la política/encoder existentes. No añadir un endpoint, saltar controles HTTP ni fabricar SUPER_ADMIN. Debe acordarse el procedimiento antes de escribir filas.

## F. Padrón

Overlay: actualización false; `sunat.padron-ruc.path=${FASE5_PADRON_METADATA_PATH}` sin default; versión inicial vacía. El operador debe suministrar un directorio nuevo, absoluto, externo y sin enlaces/reparse points. No reutilizar metadata existente: una programación persistida activa prevalecería sobre el flag por defecto.

`PadronRucActualizacionService.inicializar()` sigue leyendo versión/programación y puede escribir metadata; no se modificó. El test demuestra inicialización en @TempDir nuevo, programación inactiva, sin tarea programada y sin interacción con el importador. Ninguna inicialización de ese servicio real sobre el directorio restringido.

## G. Schedulers

Overlay: RC/RA/padrón/monitor certificados/refresh OFF; cron de suscripciones `-`; executor SUNAT máximo1 y cola1. La creación futura RC/RA será manual.

`EnvioSunatRecoveryScheduler.recuperarPendientes()` no tiene interruptor de habilitación. No se modificó esa lógica compartida. Alternativa mínima del plan: primera ejecución diferida60min, batch1, ventana máxima45min por arranque y parada obligatoria antes del vencimiento. **Diferir no equivale a deshabilitar ni a bloquear egress.** Durante este precheck no se arranca la aplicación ni se crean pendientes fiscales. Para un bloqueo de red total en un proceso de aplicación real, se requiere control externo de egress demostrado; no se implementó infraestructura ni un cambio productivo de transporte.

## H. Empresa beta

Datos preparados en `src/test/resources/fase5-beta-datos.json`: identificación BETA/PRUEBA/NO PRODUCTIVA y dirección/nombres ficticios. **Ninguna empresa creada en beta**. Se reutilizó `EmpresaConfiguracionService.configurarSunat` con repository/principal mock para probar el mecanismo normal, no como evidencia de commit real.

Tienda propuesta sólo como identificador lógico exclusivo en solicitudes de ensayo. El modelo actual no tiene entidad/endpoint Tienda; no se añade tabla ni arquitectura para inventarlos. El identificador utilizado en tests no reserva correlativos.

## I. RUC beta

Referencia documental: **20100066603**, `reglamentos/manual_programador (1).pdf`, página14, sección2.3 Servicio Beta, ejemplo. Dígito verificador válido por `RucUtils`. Fuente local, no padrón, búsqueda web ni RUC productivo del usuario. No atribuirle nombres/datos reales; el ejemplo documental no prueba aceptación ni que ese RUC sea ficticio/inexistente. Se utilizó sólo en datos de soporte y fixtures locales, sin empresa persistida ni tráfico.

## J. Credenciales beta de empresa

Estado persistido beta: **NO CONFIGURADO**.

Se probaron los datos de ensayo solicitados con `EmpresaConfiguracionService`/`CryptoService`: cifrado AES-GCM/AAD por RUC/campo, round-trip y construcción privada del Username por el cliente compartido. Sin invocar sus métodos de red y sin imprimir valores. Ningún hardcode nuevo en código productivo ni credencial de ensayo persistida fuera de beta.

## K. Certificado autofirmado beta

Certificado persistente de la empresa beta: **NO CONFIGURADO**.

Sólo se generó un certificado **efímero de test** en @TempDir: X.509 autofirmado RSA2048, alias privado único, OU documental, uso de firma y vigencia30d. Password aleatorio por entorno del subprocess keytool, no argumento de CLI ni documentación. Cargado/validado con el firmador normal. No exportación de clave privada plana, PFX/secretos en Git/reglamentos ni certificado productivo. No confundir este fixture que JUnit elimina con un certificado provisionado para ejecutar beta.

La generación persistente de FASE5 y su cifrado/commit/reload en Empresa quedan pendientes de BD y clave maestra aprobadas; no se dejó un password persistente en archivo como sustituto.

## L. Firma/XSD/ZIP local

Siete escenarios pasan:01,07-01,03,RC de03,07-03,RC de07 yRA deNC-factura. Datos: un ítem, contado/0101/18%, originales10,00+1,80=11,80; NC motivo04 por1,00+0,18=1,18. Receptor ficticio local; no se acredita admisibilidad comercial por pasar XSD.

Se reutilizan los builders/calculador/firmador/validador compartidos, verificando emisor, firma contra el certificado generado, digest y ZIP de una entrada con XML exacto. No XML completo en stdout; ninguna secuencia, emisión o evento de envío invocado. **Pipeline con persistencia/reload reales en beta: PENDIENTE**.

## M. Observabilidad

XML debug OFF; logger fiscal INFO. Test acredita nodo UUID y correlación empresa/documento/ticket, ausencia de las credenciales/password de test en output capturado. El transporte existente no se instrumentó para imprimir SOAP. Las regresiones existentes de secretos/transporte/auditoría pasan.

No observabilidad de un proceso beta real ni respuesta SUNAT demostrada. No guardar SOAP con WS-Security como evidencia; no capturar cabeceras de autenticación. El ZIP enviado y HTTP exitoso exacto continúan sin archivo persistente, como se distinguió en el plan.

## N. Cambios realizados

- Nuevo `src/test/java/org/example/config/Fase5BetaPreparacionTest.java`.
- Nuevos datos/overlay en los dos recursos de test antes citados.
- Este informe y actualización acotada de `MEMORY.md`.
- Utilidades externas en `/tmp/opencode/` para lectura PDF y lifecycle del servidor sintético de la suite; no pertenecen al artefacto de aplicación.

**Cero cambios en src/main, POM, endpoints, reglas fiscales, contratos, algoritmos, perfiles originales o arquitectura.** El mismo código/JAR sirve a beta/prod. No aplicación beta duplicada. El DDL es salida generada de test, no migración aplicada.

## O. Tests

| ANTES | NUEVOS | TOTAL | PASAN | FALLAN | ERRORES | OMITIDOS |
| --- | --- | --- | --- | --- | --- | --- |
| 692 | 17 | 709 | 709 | 0 | 0 | 0 |

- Específica17/17: `python3 /tmp/opencode/run-fase4-tests.py 'Fase5BetaPreparacionTest' fase5-precheck-local-retry`.
- Completa: `python3 /tmp/opencode/run-fase4-tests.py '*Test,*Testt' fase5-preparacion-full`, exit0/BUILD SUCCESS.
- XML75 clases, incluidas6Testt; parser confirma709/0/0/0 y baseline692 conservado. Evidencia `/tmp/opencode/fase5-suite-result.json`, logs específicos/completo y reportes recién generados en target.
- Primer intento específico no compiló por dos símbolos del fixture; corregidos en el test, sin tocar código principal ni quitar assertions. Los errores de soporte PowerShell/lifecycle no se ocultaron ni cambiaron permisos/configuración global.
- Suite con las bases desechables autorizadas; LOCAL INFILE sólo en el MySQL sintético propio de loopback33307. Se reinició el datadir retenido sin inicializar/sobrescribir; identidad/puerto cotejados; apagado graceful al terminar y archivos conservados. Ninguna cuenta de ese servidor se adoptó para beta. MySQL habitual sin cambio global; no base restore reutilizada.

## P. Bloqueadores y separación de ambientes

1. Ejecutar desde IntelliJ el soporte aislado elegido por operador, con las variables ya provisionadas y URLs beta exactas. No transferir secretos al agente. No se exige otra cuenta MySQL.
2. Verificar conexión e identidad de la base confirmada, cotejo/DDL sólo allí y SchemaValidator aislado integral. El helper no administra cuentas/grants; si la cuenta actual carece de un permiso necesario, detener y reportar, sin elevarlo automáticamente.
3. Provisionar empresa/usuario de ensayo por procedimiento controlado; luego certificado FASE5 y configuración cifrada mediante los mecanismos normales, con commit/reload comprobados.
4. Acordar ruta metadata nueva sin enlaces y comprobar configuración efectiva, schedulers, ventana recovery y bloqueo externo de red si se requiere arrancar un proceso real antes de autorizar llamadas.
5. Repetir el pipeline usando la Empresa recargada de beta. Las pruebas mock/locales no reemplazan este paso.

| BETA de ensayo | PRODUCCIÓN futura |
| --- | --- |
| Mismo código/capas/services/repositories/JAR | Mismo código/JAR validado, sin bifurcación |
| Perfil beta y marcador BETA | Perfil prod y marcador PRODUCCION |
| bd_facturacion_beta_ensayo, empresa exclusiva | Base/empresa productivas, nunca reutilizadas desde beta |
| RUC del ejemplo beta, datos de ensayo | RUC y datos reales autorizados |
| Credenciales de prueba configuradas por empresa | Credenciales SOL productivas apropiadas |
| Autofirmado exclusivo FASE5 | Certificado productivo válido y validación/truststore del perfil |
| Endpoint heredado de application-beta.properties | Endpoint heredado de application-prod.properties |
| Secrets específicos de ensayo | Secrets productivos propios, nunca adoptados de beta |

No se declara producción lista. No nueva autorización implícita de envío aunque se resuelva la provisión.

## Q. Estado

**LISTO PARA EJECUCIÓN BETA**
