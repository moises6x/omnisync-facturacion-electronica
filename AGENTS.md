# Guía del proyecto de facturación electrónica

## Alcance y fuentes

Esta guía se basa en `pom.xml`, fuentes de `src/main/java`, configuración de `src/main/resources`, tests de `src/test` y `.gitignore`. El código y la configuración efectiva prevalecen sobre comentarios que hayan quedado desactualizados. No asumir módulos, infraestructura ni funcionalidades únicamente por el nombre de una dependencia o de un archivo.

## Carpetas restringidas

> NO leer, listar, buscar, indexar, modificar ni usar como contexto `padron_reducido_ruc/`.

- Excluir completamente esa carpeta antes de cualquier exploración, búsqueda, indexación o herramienta automática. Preferir rutas permitidas explícitas, como `src/main/java`, `src/test` o archivos concretos; no realizar recorridos indiscriminados desde la raíz.
- No seguir enlaces simbólicos ni ejecutar programas que permitan acceder indirectamente a su contenido. La exclusión de `.gitignore` no sustituye esta regla.
- No iniciar importaciones, descargas, actualizaciones ni endpoints administrativos del padrón real. El código externo puede revisarse. Sólo con autorización expresa pueden ensayarse archivos sintéticos fuera del directorio restringido y MySQL desechable aislado; esto nunca autoriza leer sus datos masivos.
- **Advertencia de arranque:** `PadronRucActualizacionService.inicializar()` tiene `@PostConstruct` y lee versión/programación persistidas; también puede guardar la versión inicial. `sunat.padron-ruc.actualizacion.enabled=false` no evita por sí solo estos accesos iniciales. No arrancar el contexto completo con `sunat.padron-ruc.path` apuntando a la carpeta restringida. Cualquier verificación que requiera ese contexto debe acordar primero un aislamiento que impida dichos accesos.

## Memoria del proyecto

- Si existe `MEMORY.md`, debe leerse al inicio de cada sesión.
- `MEMORY.md` contiene el estado actual, decisiones técnicas importantes, riesgos, restricciones y próximos pasos.
- Solo debe actualizarse cuando cambie realmente el estado del proyecto o se tome una decisión técnica importante; no registrar cada lectura, comando o cambio trivial.
- Nunca almacenar contraseñas, tokens, API keys, claves SOL, secretos, certificados privados ni otras credenciales dentro de `MEMORY.md`.
- No crear ni actualizar memoria cuando el alcance autorizado se limite a otro archivo.

## Stack tecnológico verificado

- Backend Java **21**, Maven, artefacto `com.facturacion:facturacion-service:1.0-SNAPSHOT`.
- Spring Boot **3.4.13**: Web/MVC, JSON con Jackson, Validation/Jakarta, Data JPA/Hibernate, Security y Actuator.
- MySQL mediante `mysql-connector-j`; también se usa JDBC directo (`JdbcTemplate`/`DataSource`) para importación del padrón.
- Lombok para constructores, getters/setters, builders y logging.
- JWT/JOSE de Spring Security, con implementaciones Nimbus y firma HMAC HS256.
- Apache Santuario `xmlsec` **3.0.6** para firma XML; esquemas SUNAT/UBL locales.
- PDFBox **3.0.8** para PDF y ZXing **3.5.4** para QR.
- Flyway Core y soporte MySQL declarados, pero **Flyway está desactivado**.
- Tests con JUnit Jupiter, Mockito, Spring Test y herramientas de Spring Security Test declaradas en el POM.
- Ejecución asíncrona con `ThreadPoolTaskExecutor`; servicios del padrón usan ejecutores con hilos virtuales de Java 21. Scheduling de Spring habilitado.
- No se identificó un frontend ni una configuración de contenedores/CI en las rutas examinadas. Los orígenes CORS locales no prueban qué tecnología usa un cliente externo.

## Estructura y módulos principales

El proyecto tiene un único `pom.xml`, sin módulos Maven declarados. El paquete Java real es `org.example`, no `com.facturacion`.

| Ruta bajo `src/main/java/org/example/` | Responsabilidad |
| --- | --- |
| `Main.java` | Arranque Spring Boot y `@EnableScheduling`; excluye la autoconfiguración del usuario de seguridad predeterminado. |
| `controller/`, `dto/` | API de comprobantes, notas de venta, empresas, correlativos, resúmenes diarios, bajas, consulta pública y consulta/administración del padrón. |
| `service/` | Orquestación fiscal, cálculos, validaciones, idempotencia, correlativos, XML, firma, envío SUNAT, CDR, PDF, criptografía y workers. |
| `model/`, `repository/` | Entidades JPA, estados, secuencias y acceso a datos; incluye consultas JPQL y SQL nativo MySQL. |
| `auth/` | Login, refresh, contraseñas, usuarios, permisos, auditoría, JWT, filtros y contexto de seguridad. |
| `admin/` | Administración de empresas/propietarios, configuración tributaria, anuncios, pagos y suscripciones. |
| `config/` | Executor SUNAT, zona horaria/Clock, propiedades tributarias y health de certificados. |
| `event/` | Evento `ComprobanteCreadoEvent`. |
| `exception/`, `util/` | Respuestas centralizadas de error y validación/utilidades de RUC. |

Otros recursos:

- `src/main/resources/application*.properties`: configuración común y perfiles `beta`/`prod`.
- `src/main/resources/sunat-xsd/2.1/`: esquemas UBL, incluidos Invoice y CreditNote.
- `src/main/resources/sunat-xsd/2.0/`: esquemas SUNAT para SummaryDocuments y VoidedDocuments, además de recursos XSL/XML. Su presencia no implica que todos los XSL se ejecuten.
- `src/test/java/org/example/`: pruebas de servicios, seguridad, validaciones, administración y repositorios.
- `src/test/resources/application-test.properties`: configuración de la base exclusiva de pruebas.
- `target/`: salida generada; no editarla ni tratar sus clases/reportes antiguos como fuente de verdad.

## Arquitectura y responsabilidades

1. **Controladores:** contratos HTTP, validación `@Valid`, permisos `@PreAuthorize`, resolución de empresa autenticada mediante `SecurityContextService` y delegación a servicios. La emisión devuelve HTTP 202: no equivale a aceptación SUNAT.
2. **DTO:** solicitudes/respuestas y restricciones Jakarta. Se observan tanto clases Lombok como records; conservar el estilo del área modificada.
3. **Servicios:** reglas de negocio, cálculos independientes del cliente, límites transaccionales, generación/firma/validación del XML y selección del flujo SUNAT.
4. **Entidades/repositorios:** persistencia, claves únicas, consultas por empresa, bloqueos y cambios atómicos de estado. El aislamiento no puede delegarse solamente al controlador.
5. **Integración y workers:** cliente SOAP, interpretación de CDR, eventos después del commit, recuperación de pendientes y consulta de tickets.
6. **Errores:** `ApiExceptionHandler` maneja validación, autenticación, permisos y errores internos; el filtro de autenticación tiene sus propias respuestas JSON. Mantener los contratos existentes y no exponer stack traces al cliente.

`FacturacionServiceImpl` y `NotaVentaServiceImpl` implementan sus respectivas interfaces. No todos los servicios tienen interfaz, y `UsuarioService` está actualmente en `auth/controller/`; no reorganizar paquetes sin necesidad y autorización de alcance.

## Configuración y perfiles existentes

Los siguientes son **perfiles Spring**, no perfiles Maven; el POM no declara `<profiles>`.

| Configuración | Comportamiento verificado |
| --- | --- |
| Común | Puerto `${PORT:8082}`, sin perfil fiscal predeterminado (`SPRING_PROFILES_ACTIVE` explícito), zona `America/Lima`, `open-in-view=false`, SQL no mostrado, cierre ordenado y Flyway desactivado. |
| `beta` | `ddl-auto=update`, `sunat.environment=BETA`, endpoint beta, bootstrap habilitado/automático por defecto mediante variables, RSA mínimo 1024 bits, resumen diario automático desactivado. |
| `prod` | `ddl-auto=validate`, `sunat.environment=PRODUCCION`, datasource/CORS por variables obligatorias, bootstrap manual desactivado y creación automática por defecto desactivada, truststore requerido, RSA mínimo 2048 bits, resumen diario automático activado. |
| `test` | Solo en recursos de test: MySQL `bd_facturacion_test`, `ddl-auto=create-drop`, Flyway y automatizaciones de resumen/baja/certificados/padrón desactivadas; secretos de prueba y bootstrap desactivado. |

- Datasource común: MySQL local, base `bd_facturacion`, usuario predeterminado `facturacion_app`; contraseña mediante `SPRING_DATASOURCE_PASSWORD`. **No usar esta base para tests.**
- `ConfiguracionFiscalEnvironmentPostProcessor` falla antes de crear beans/datasource si falta un perfil fiscal explícito o se combinan beta/prod/test. Beta/prod exigen `APP_DEPLOYMENT_ENVIRONMENT` coherente y endpoint exacto del ambiente. Beta exige además `APP_BETA_DATASOURCE_URL` aprobado, idéntico al datasource efectivo; no reutilizar una URL/cuenta productiva bajo esa aprobación. Test sólo admite `bd_facturacion_test`. No sustituye verificación de permisos/identidad del servidor real.
- Bootstrap automático exige `SECURITY_BOOTSTRAP_INITIAL_PASSWORD` suministrada por operador/gestor de secretos, sin default ni stdout/logs. Cambiarla obligatoriamente al primer login y retirar la provisión de la variable después del bootstrap. Bootstrap HTTP manual sigue sólo bajo habilitación expresa.
- `server.forward-headers-strategy=none`; beta/prod rechazan otra estrategia hasta verificar proxy confiable. Rate limiting sigue **por nodo**, no distribuido. JSON tiene límite técnico de 1 MiB también chunked; CPE/NC máximo 500 items y crédito 120 cuotas. No son requisitos SUNAT.
- Variables de seguridad verificadas: `APP_MASTER_KEY_BASE64`, `APP_ADMIN_KEY`, `APP_JWT_SECRET_BASE64`, `APP_JWT_ISSUER`, `APP_CORS_ALLOWED_ORIGINS`. Documentar nombres, nunca valores reales.
- Producción exige `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SUNAT_CERT_TRUSTSTORE_PATH` y `SUNAT_CERT_TRUSTSTORE_PASSWORD`; tipo de truststore por `SUNAT_CERT_TRUSTSTORE_TYPE`, predeterminado `PKCS12`.
- `sunat.debug.xml=false` en beta y producción; el worker ya no imprime XML incluso si se activa el flag. No introducir logs de XML/SOAP ni secretos.
- El resumen diario tiene cierre a las 23:50 y recuperación de fechas anteriores cada hora, en `America/Lima`; máximo configurado de 500 líneas por RC.
- El padrón tiene actualización habilitada en configuración común, programación semanal y ruta restringida. Esto exige especial cuidado con cualquier arranque completo.
- `ComunicacionBajaScheduler` requiere explícitamente `sunat.comunicacion-baja.auto.enabled=true`; esa activación no aparece en los perfiles principales examinados. No asumir automatización RA en producción.

## Seguridad y aislamiento multiempresa

- Spring Security stateless, sin login por formulario, HTTP Basic ni cookies de sesión; CSRF desactivado. CORS usa orígenes explícitos y `allowCredentials=false`.
- Dos mecanismos: `Authorization: Bearer ...` o `X-Empresa-RUC` + `X-Empresa-Key`. El filtro **rechaza mezclarlos**.
- JWT HS256 valida issuer/expiración; se vuelve a consultar el usuario y se comprueban estado, bloqueo y versión del token. No confiar únicamente en los claims para autorizar.
- Roles observados: `SUPER_ADMIN`, `EMPRESA_DUENO`, `EMPRESA_ADMIN`, `TRABAJADOR`; el cliente API recibe `ROLE_API_CLIENT` y un conjunto explícito de permisos. Mantener la matriz de `SecurityAuthorities`, incluidos los límites del trabajador.
- Usuarios/API de empresas suspendidas no pueden operar normalmente. Existen excepciones explícitas para sesión, cambio de contraseña, cierre de sesiones y anuncios; no ampliarlas accidentalmente.
- Contraseñas con BCrypt coste 12; API keys y refresh tokens almacenados como hashes SHA-256. Hay rate limiting y bloqueo por intentos fallidos.
- SOL, PFX y contraseña PFX se cifran mediante AES-256-GCM. La clave maestra exige 32 bytes Base64 decodificados; JWT exige al menos 32. El AAD incluye **RUC y campo**, por lo que copiar secretos cifrados entre empresas/campos no es válido.
- `Empresa` tiene ID, RUC único y `tenantId` UUID único. Las operaciones fiscales se aíslan principalmente por `rucEmisor`; no se observó un filtro global que sustituya las condiciones por empresa de cada consulta.
- En operaciones privadas, obtener RUC/empresa del principal autenticado, no aceptar un tenant arbitrario desde el body. Usar búsquedas como `findByIdAndRucEmisor` y mantener el RUC en updates, listados, dashboard, PDF y documentos afectados.
- Los workers internos usan también `findById`; eso **no** autoriza a usar consultas globales en endpoints privados. El worker debe resolver las credenciales de la empresa emisora del documento persistido.
- Preservar unicidad de comprobante por RUC/tipo/serie/número e independencia por empresa de correlativos, idempotencia y RC. La misma serie/número o clave idempotente puede existir en distintas empresas.
- `POST /api/public/cpe/consulta` es una excepción pública deliberada: coteja documento, fecha e importe, y receptor si se proporciona; devuelve información limitada. No convertirla en un listado o descarga de datos privados.
- La lista pública de `SecurityConfig` y la del filtro deben mantenerse coherentes. No abrir endpoints, modificar CORS o ampliar permisos sin revisar ambos y añadir verificaciones.

## Base de datos y Flyway

- Base compartida MySQL con aislamiento lógico por empresa. Hay SQL específico de MySQL (`INSERT IGNORE`, consultas nativas, tipos `LONGBLOB`); no sustituir estas pruebas por H2 suponiendo equivalencia.
- Correlativos e idempotencia usan inicialización idempotente y `PESSIMISTIC_WRITE`. `IdempotencyService` requiere una transacción existente (`Propagation.MANDATORY`). Mantener estos límites y bloqueos para evitar duplicados concurrentes.
- La idempotencia guarda SHA-256 del JSON serializado por `ObjectMapper`: una clave reutilizada con otro payload se rechaza. Cambios de DTO/serialización pueden afectar esa identidad; revisar compatibilidad antes de cambiarlos.
- XML firmado, hash y CDR se conservan en entidades. No regenerar silenciosamente documentos ya emitidos ni reemplazar sus evidencias.
- **Estado actual de Flyway:** dependencias declaradas, `spring.flyway.enabled=false` tanto en configuración común como de tests; no se encontraron scripts de migración en `src`. La configuración común indica que el baseline/migraciones de la base preexistente están pendientes.
- No habilitar Flyway, ejecutar `migrate`/`repair`/`baseline`, inventar un baseline ni cambiar `ddl-auto` sin autorización y un plan verificado sobre el esquema real.
- `beta` puede alterar tablas al arrancar por `update`; `prod` únicamente valida; `test` crea y elimina tablas por `create-drop`. La selección de perfil es una medida de seguridad, no solo una preferencia.
- El importador del padrón usa JDBC/LOAD DATA y `GET_LOCK` con una sola conexión durante carga/intercambio/limpieza. Retiene anterior e históricas sin purga. No ejecutarlo sobre datos reales para validar cambios; los ensayos F4 sólo usan archivos sintéticos y `bd_facturacion_test` del MySQL aislado de loopback:33307 con LOCAL INFILE habilitado exclusivamente allí.
- Renovar suscripción exige `X-Idempotency-Key`, hash/payload por empresa e índice compuesto en `pago_empresa`. Ver DDL/compatibilidad/rollback en `FASE4_ESQUEMA.md`; ninguna ampliación se aplicó a producción.
- Cantidad CPE conserva DECIMAL(12,4) con rechazo explícito de precisión/magnitud no representable. Unitarios son DECIMAL(22,10), IGV/total de detalle DECIMAL(14,2) y descripción VARCHAR(500); no regenerar documentos existentes.

## Integración con SUNAT e invariantes fiscales

- Factura **01** y nota de crédito **07 de factura**: XML UBL 2.1 firmado/validado, persistencia en `PENDIENTE_ENVIO`, evento de comprobante y envío individual `sendBill`.
- Boleta **03** y nota de crédito **07 de boleta**: `PENDIENTE_RESUMEN`, incorporación al RC y `sendSummary`; no enviarlas por el worker individual.
- `ComprobanteEnvioListener` usa `@Async("sunatExecutor")` y `@TransactionalEventListener(AFTER_COMMIT)`. **El envío individual no debe ocurrir antes del commit.**
- `SunatDirectSoapClient` implementa SOAP con credenciales SOL y ZIP/XML: `sendBill` devuelve ZIP CDR; `sendSummary` devuelve ticket; `getStatus` consulta ese ticket. Los resúmenes usan SummaryDocuments UBL 2.0 y las bajas RA VoidedDocuments UBL 2.0.
- Endpoints configurados: beta `https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService`; producción `https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService`. El cliente valida HTTPS y host conforme al ambiente. No saltarse esa validación para una prueba contra SUNAT real.
- Firma RSA-SHA256, validación de vigencia/clave privada/RSA y, en producción, validación de cadena con truststore/PKIX. Mantener protecciones XML contra DOCTYPE/entidades externas.
- `UblValidatorService` valida XML y XSD configurados; la existencia de XSD no demuestra cumplimiento integral de todas las reglas normativas SUNAT.
- Un SOAP Fault no es una CDR de rechazo tributario. Preservar las diferencias entre rechazo con CDR, validación, transporte y resultado incierto.
- No reintentar ciegamente `ERROR_ENVIO_INCIERTO`: SUNAT pudo recibir el documento. La recuperación individual automática solo toma `PENDIENTE_ENVIO`. RC/RA no convierten SOAP Fault posterior al intento en fallo seguro; la recuperación sin ticket exige cero intentos persistidos y fallo local, no una lista inventada de códigos SUNAT. ERROR_ENVIO histórico con intento requiere conciliación. `getStatus` de ticket existente es repetible y se recoteja bajo lock final. El transporte usa streaming de longitud fija y redirects desactivados, sin depender de retryPost externo.
- `SunatPlazoService` controla días calendario inclusivos: por defecto 3 para envío individual y 7 para resumen/baja. Son parámetros del código actual, no una afirmación de vigencia legal; no ampliarlos para forzar envíos.
- El backend recalcula importes con `BigDecimal`, redondeo monetario a dos decimales `HALF_UP` y cotejo con valores del cliente. Afectaciones admitidas por el cálculo: 10, 20 y 30; operación configurada: 0101.
- IGV por empresa, predeterminado 18.00%; tasas permitidas configuradas: 18.00 y 10.50. La tasa especial requiere validación administrativa; el servicio no determina elegibilidad legal.
- La tasa usada queda registrada históricamente en el comprobante. Las NC deben resolver la tasa del original, conservar su vínculo y validar cliente, estado e importes; cambiar la tasa actual de una empresa no debe recalcular documentos anteriores.
- Emisión y XML comparten una misma fecha calculada con `Clock` en `America/Lima`. No sustituirla por UTC o por llamadas independientes que puedan cruzar de día.
- Las notas de venta (`NV`) son ventas internas sin comprobante SUNAT; tienen su correlativo y no deben integrarse accidentalmente al flujo fiscal.
- RA corresponde en este código a factura/NC de factura; la baja de boleta usa RC con estado 3. No confundir una baja de numeración con una nota de crédito.

## Convenciones observadas

- Clases en PascalCase, métodos/campos camelCase; predominan nombres de dominio y mensajes en español, combinados con términos técnicos en inglés.
- Inyección principalmente por constructor con campos `final` y `@RequiredArgsConstructor`; hay excepciones existentes. No introducir inyección por campo como estilo general ni refactorizar las excepciones fuera del alcance.
- Sufijos observados: `Controller`, `Service`, `ServiceImpl`, `Repository`, `Request`, `Response`, `Test`; también existen clases de prueba `Testt`.
- Entidades con anotaciones Jakarta, nombres SQL snake_case, builders Lombok y estados enum persistidos como texto. Cambiar nombres de enum o columnas afecta datos/SQL.
- Secciones y comentarios explicativos extensos; formato desigual entre archivos. Conservar el formato local y evitar reformateos masivos sin una configuración de formatter verificada.
- Usar el `Clock`/`ZoneId` central para código fiscal nuevo y clocks fijos en tests de fechas. Parte del código existente aún usa `LocalDateTime.now()`.
- No usar `double`/`float` para importes ni alterar tolerancias/redondeos sin tests específicos.
- Mantener DTO, estados, cabeceras, permisos y códigos HTTP compatibles con sus consumidores. No renombrar paquetes para alinearlos con el groupId Maven por una tarea no relacionada.

## Compilación y verificación de tests

### Herramientas y comandos

Ejecutar desde la raíz con **JDK 21** y Maven instalado. No hay scripts `mvnw`/`mvnw.cmd` disponibles en la raíz examinada. No se verificó una versión mínima concreta de Maven.

Los comandos siguientes corresponden al ciclo Maven y al plugin Spring Boot declarados en el POM; no son scripts propios ni resultados de ejecución:

```bash
mvn --version
mvn compile
mvn -DskipTests package
mvn test
```

- `compile` compila fuentes; `package` produce el JAR ejecutable Spring Boot. `-DskipTests` no valida comportamiento y normalmente sigue compilando fuentes de test.
- `mvn test` ejecuta la selección habitual de Surefire, pero no basta para cubrir las clases terminadas en `Testt`: no hay configuración de inclusión personalizada en el POM.
- No se declaró Maven Failsafe ni un perfil separado de integración; los tests de repositorios pertenecen también a la fase `test`.

### Pruebas sin base de datos

Las pruebas de servicios/seguridad/validación usan instancias directas, mocks y/o `ReflectionTestUtils`; los tests de repositorios son la parte que requiere MySQL. Para incluir también `Testt` y excluir repositorios:

```bash
mvn -Dtest='*Test,*Testt,!*RepositoryTest' test
```

Ejemplo de selección por funcionalidad existente:

```bash
mvn -Dtest=CalculoTributarioServiceTest,CalculoTributarioMultiempresaTest,FacturacionServiceTest,NotaCreditoReglasServiceTest test
mvn -Dtest=SunatDirectSoapClientContractTest test
```

El test de contrato SOAP levanta servidores HTTP efímeros en `127.0.0.1`; no necesita SUNAT ni Internet, pero sí permiso para abrir puertos locales. `FlujosFirmadosXsdTest` ya acredita firma positiva/XSD/tamper para 01/03/07-01/07-03/RC/RA y PKIX con certificados ficticios; esto no acredita homologación ni aceptación SUNAT. `Fase4DecimalRepositoryTest` agrega firma real después del round-trip MySQL.

### Repositorios y suite completa

Antes de ejecutarlas:

1. Confirmar que existe un MySQL de pruebas y la base **exclusiva y desechable** `bd_facturacion_test`. No usar `bd_facturacion` ni producción.
2. Configurar `TEST_DB_USERNAME` y `TEST_DB_PASSWORD` mediante un mecanismo seguro; el usuario predeterminado es `facturacion_test` y debe poder crear/eliminar tablas en esa base de pruebas.
3. Revisar que variables externas, propiedades de sistema o configuración del IDE no sobrescriban el datasource/perfil hacia otra base. Los tests usan `@DataJpaTest`, `@ActiveProfiles("test")` y `@AutoConfigureTestDatabase(replace=NONE)`.
4. Tener presente que `create-drop` ejecuta DDL sobre esa base; esto no es una migración Flyway, pero es destructivo para sus tablas.

```bash
mvn -Dtest='*RepositoryTest' test
mvn -Dtest='*Test,*Testt' test
mvn -Dtest='*Test,*Testt' verify
```

`MultiempresaRepositoryTest` verifica aislamiento de búsquedas, historial, dashboard, updates de RC, idempotencia y secuencias. No omitirlo en cambios de persistencia o multiempresa.

### Comprobar resultados

- Revisar código de salida, resumen de tests ejecutados/fallidos/errores/omitidos y reportes nuevos de `target/surefire-reports/`.
- Confirmar que los reportes incluyen las clases seleccionadas, especialmente `Testt`; un build exitoso que omite pruebas no es cobertura completa.
- Distinguir fallos de infraestructura MySQL/permisos/puerto/JDK de regresiones funcionales. No desactivar assertions, borrar tests ni cambiar la base de datos para ocultar fallos.
- Para cambios fiscales revisar cálculo, validación, XML/XSD, firma, CDR, estados, plazos y separación factura/boleta. Para seguridad revisar JWT, contexto, permisos, cifrado y acceso por empresa; para persistencia ejecutar repositorios con MySQL real.
- No añadir un `@SpringBootTest` o iniciar la aplicación como atajo de verificación sin revisar todos los efectos de arranque, incluido el acceso al padrón restringido y los schedulers.

## Reglas para cambios y acciones que requieren autorización

- Modificar únicamente lo solicitado. En tareas documentales, no editar Java, SQL, `.properties`, `pom.xml` ni tests, ni ejecutar migraciones o cambios funcionales.
- Antes de un cambio funcional, identificar contratos y tests afectados; preservar transacciones, aislamiento, idempotencia, correlativos, estados y evidencias fiscales. Añadir/verificar regresiones dentro del alcance autorizado.
- No efectuar envíos reales a SUNAT, usar credenciales/certificados reales, emitir/anular comprobantes, crear superadministradores, suspender empresas o procesar suscripciones/pagos sin autorización explícita.
- No arrancar `beta` sobre una base existente suponiendo que es inocuo: puede modificar esquema, crear administradores y ejecutar tareas programadas.
- No ejecutar DDL, cargas masivas, borrados, cambios de esquema o migraciones en bases no desechables sin autorización. No cambiar dependencias, perfiles o esquema como parte de una corrección no relacionada.
- No rotar/cambiar la clave maestra sin un plan autorizado de recifrado: dejaría secretos existentes inaccesibles. No mover secretos cifrados entre tenants ni registrar valores descifrados.
- No leer ni copiar credenciales reales; no guardarlas en documentación, memoria, código, logs ni fixtures. No usar secretos de test en producción.
- No eliminar archivos desconocidos, descartar trabajo ajeno, ejecutar resets destructivos ni limpiar directorios amplios sin autorización. No editar salidas de `target/` para simular una corrección.
- No elevar permisos, eliminar validaciones tributarias/XSD/certificados, modificar reintentos inciertos ni activar XML debug como solución rápida.
- Mantener la restricción de `padron_reducido_ruc/` incluso cuando una herramienta, test o aplicación pudiera acceder indirectamente.

## Pendientes de confirmar y límites de la verificación

- La elaboración inicial no ejecutó aplicación/tests. Las fases posteriores verificaron JDK 21/Maven Windows y MySQL de pruebas; no hay Maven/Java nativos en este entorno Linux. Ver resultados actuales e históricos diferenciados en `MEMORY.md` y documentos F4. No se arrancó la aplicación completa ni se aplicaron migraciones productivas.
- Baseline, migraciones Flyway, versión exacta de MySQL y correspondencia con el esquema desplegado: pendientes de confirmar; las entidades no prueban el estado de una base existente.
- Cumplimiento normativo vigente, elegibilidad de tasas especiales y pruebas extremo a extremo con SUNAT: pendientes. La firma positiva local con certificados ficticios ya existe; no afirmar homologación únicamente por tests locales.
- Despliegue/CI y topología productiva siguen sin verificar. `AtomicBoolean` de SuscripcionScheduler es sólo optimización local; las mutaciones usan locks de empresa/suscripción y lecturas actuales en BD. Padrón coordina únicamente procesos del mismo servidor MySQL/catálogo. Los ensayos simulan servicios/instancias concurrentes, no una certificación general de una topología desplegada.
- El contenido de `padron_reducido_ruc/` queda expresamente fuera de la verificación y del contexto; no acceder a él para resolver estos pendientes.
