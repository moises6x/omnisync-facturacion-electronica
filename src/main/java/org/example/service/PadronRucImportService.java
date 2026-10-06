package org.example.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

import org.example.dto.ImportacionPadronResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.io.BufferedInputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.sql.Connection;
import java.sql.Statement;

import java.time.LocalDateTime;

import java.util.Comparator;
import java.util.Locale;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class PadronRucImportService {

    // =========================================================
    // DEPENDENCIAS
    // =========================================================

    private final JdbcTemplate jdbcTemplate;

    private final DataSource dataSource;

    // GET_LOCK pertenece a esta sesión: todas las operaciones usan la MISMA conexión.
    private final ThreadLocal<Connection> sesion = new ThreadLocal<>();
    private JdbcTemplate jdbc() {
        Connection c=sesion.get();
        if(c==null) throw new IllegalStateException("Importación sin exclusión distribuida.");
        return new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true));
    }
    @Value("${sunat.padron-ruc.min-fraccion-anterior:0.95}")
    private double minFraccionAnterior=0.95;
    private volatile String etapa="SIN_INICIAR";


    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    @Value("${sunat.padron-ruc.path:./padron_reducido_ruc}")
    private String rutaConfigurada;


    @Value("${sunat.padron-ruc.charset:windows-1252}")
    private String charsetConfigurado;


    // =========================================================
    // EJECUTOR
    // =========================================================

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    Thread.ofVirtual()
                            .name("padron-ruc-import-")
                            .factory()
            );


    // =========================================================
    // ESTADO
    // =========================================================

    private volatile EstadoImportacion estado =
            EstadoImportacion.SIN_INICIAR;


    private volatile String archivoActual;


    private volatile long lineasLeidas;

    private volatile long registrosValidos;

    private volatile long registrosInvalidos;

    private volatile long registrosFinales;


    private volatile LocalDateTime iniciadoEn;

    private volatile LocalDateTime finalizadoEn;


    private volatile String mensaje =
            "La importación todavía no se ha iniciado.";


    // =========================================================
    // IMPORTACIÓN MANUAL
    // =========================================================
    //
    // Este método mantiene funcionando:
    //
    // POST /api/v1/admin/padron-ruc/importar
    //
    // =========================================================

    public synchronized ImportacionPadronResponse iniciar() {

        Path origen =
                resolverOrigen();


        iniciarAsincrono(
                origen
        );


        return estado();
    }


    // =========================================================
    // IMPORTACIÓN DESDE ARCHIVO ESPECÍFICO
    // =========================================================
    //
    // Lo utilizará la actualización automática.
    //
    // Puede recibir directamente:
    //
    // padron_reducido_ruc_2026-09-20.zip
    //
    // =========================================================

    public synchronized CompletableFuture<ImportacionPadronResponse>
    iniciarDesdeArchivo(
            Path origen
    ) {

        Path archivo =
                origen
                        .toAbsolutePath()
                        .normalize();


        if (
                !Files.exists(archivo)
                        ||
                        !Files.isRegularFile(archivo)
        ) {

            throw new IllegalArgumentException(
                    "No existe el archivo del padrón RUC: "
                            + archivo
            );
        }


        return iniciarAsincrono(
                archivo
        );
    }


    // =========================================================
    // INICIO INTERNO
    // =========================================================

    private CompletableFuture<ImportacionPadronResponse>
    iniciarAsincrono(
            Path origen
    ) {

        if (
                estado
                        == EstadoImportacion.PROCESANDO
        ) {

            throw new IllegalArgumentException(
                    "Ya existe una importación del padrón RUC en proceso."
            );
        }


        // =====================================================
        // REINICIAR ESTADO
        // =====================================================

        estado =
                EstadoImportacion.PROCESANDO;


        archivoActual =
                origen
                        .toAbsolutePath()
                        .toString();


        lineasLeidas =
                0L;


        registrosValidos =
                0L;


        registrosInvalidos =
                0L;


        registrosFinales =
                0L;


        iniciadoEn =
                LocalDateTime.now();


        finalizadoEn =
                null;


        mensaje =
                "Importación rápida iniciada. Preparando padrón SUNAT.";


        // =====================================================
        // EJECUTAR EN SEGUNDO PLANO
        // =====================================================

        try { return CompletableFuture.supplyAsync(
                () -> {

                    importar(
                            origen
                    );

                    return estado();

                },
                executor
        ); } catch(RuntimeException e) { estado=EstadoImportacion.ERROR; throw e; }
    }


    // =========================================================
    // ¿ESTÁ IMPORTANDO?
    // =========================================================

    public boolean estaProcesando() {

        return estado
                == EstadoImportacion.PROCESANDO;
    }


    // =========================================================
    // ESTADO
    // =========================================================

    public ImportacionPadronResponse estado() {

        return new ImportacionPadronResponse(

                estado.name(),

                archivoActual,

                lineasLeidas,

                registrosValidos,

                registrosInvalidos,

                registrosFinales,

                iniciadoEn,

                finalizadoEn,

                "["+etapa+"] "+mensaje
        );
    }


    // =========================================================
    // IMPORTACIÓN PRINCIPAL
    // =========================================================

    private void importar(
            Path origen
    ) {

        Path archivoCarga =
                null;


        boolean archivoTemporal =
                false;

        Connection conexion=null;
        boolean adquirido=false;


        try {
            conexion=dataSource.getConnection();
            String lock="facturacion:padron:"+conexion.getCatalog();
            try(var stmt=conexion.prepareStatement("SELECT GET_LOCK(?,0)")){
                stmt.setString(1,lock); try(var rs=stmt.executeQuery()){
                    if(!rs.next() || rs.getInt(1)!=1) throw new IllegalStateException("Otra instancia controla la importación del padrón.");
                }
            }
            adquirido=true; sesion.set(conexion); etapa="DESCARGADO";

            // =================================================
            // VALIDAR MYSQL
            // =================================================

            mensaje =
                    "Verificando configuración LOAD DATA LOCAL INFILE...";


            validarLocalInfile();


            // =================================================
            // TABLAS TEMPORALES
            // =================================================

            mensaje =
                    "Preparando tablas temporales de importación...";


            prepararTablas();


            // =================================================
            // PREPARAR TXT
            // =================================================

            archivoCarga =
                    prepararArchivoParaCarga(
                            origen
                    );


            archivoTemporal =
                    !archivoCarga.equals(
                            origen
                    );

            long filasEsperadas=validarArchivo(archivoCarga);


            // =================================================
            // CARGA RÁPIDA
            // =================================================

            mensaje =
                    "MySQL está cargando el padrón SUNAT. "
                            + "Los contadores pueden permanecer en 0 "
                            + "hasta finalizar esta etapa.";


            cargarConLoadData(
                    archivoCarga
            );
            etapa="CARGADO";


            // =================================================
            // TOTAL RAW
            // =================================================

            lineasLeidas =
                    contar(
                            "padron_ruc_raw"
                    );


            // =================================================
            // VALIDAR Y LIMPIAR
            // =================================================

            mensaje =
                    "Validando y preparando los registros del padrón...";


            cargarRegistrosValidos();


            registrosValidos =
                    contar(
                            "padron_ruc_carga"
                    );


            registrosInvalidos =
                    Math.max(
                            0L,
                            lineasLeidas
                                    -
                                    registrosValidos
                    );

            long anteriores=contar("padron_ruc");
            if(registrosValidos==0 || lineasLeidas!=filasEsperadas || registrosValidos!=lineasLeidas)
                throw new IllegalStateException("Carga vacía, parcial, inválida o duplicada; no se activa.");
            if(minFraccionAnterior<=0 || minFraccionAnterior>1 || registrosValidos<anteriores*minFraccionAnterior)
                throw new IllegalStateException("Descenso de volumen supera umbral técnico; requiere revisión antes de activar.");
            etapa="VALIDADO";


            // =================================================
            // ELIMINAR RAW
            // =================================================

            jdbc().execute(
                    "DROP TABLE IF EXISTS padron_ruc_raw"
            );


            // =================================================
            // ACTIVAR NUEVO PADRÓN
            // =================================================

            mensaje =
                    "Activando el nuevo padrón RUC...";


            intercambiarTablas();


            // =================================================
            // TOTAL FINAL
            // =================================================

            registrosFinales =
                    contar(
                            "padron_ruc"
                    );
            if(registrosFinales!=registrosValidos) throw new IllegalStateException("Activación sin conteo acreditado; preservar anterior y revisar.");
            etapa="ACTIVADO";


            // =================================================
            // COMPLETADO
            // =================================================

            estado =
                    EstadoImportacion.COMPLETADO;


            finalizadoEn =
                    LocalDateTime.now();


            mensaje =
                    "Padrón RUC importado correctamente mediante carga rápida MySQL.";


        } catch (Exception e) {

            // =================================================
            // ERROR
            // =================================================

            estado =
                    EstadoImportacion.ERROR;


            finalizadoEn =
                    LocalDateTime.now();


            mensaje =
                    "No se pudo completar la importación: "
                            + mensajeSeguro(
                            e
                    );
            mensaje="Etapa "+etapa+": "+mensaje; etapa="ERROR";


            /*
             * MUY IMPORTANTE:
             *
             * No eliminamos padron_ruc.
             *
             * Si algo falla, sigue disponible
             * el padrón anterior.
             */

            try { if(adquirido) {

                jdbc().execute(
                        "DROP TABLE IF EXISTS padron_ruc_carga"
                );
            }
            } catch (Exception ignored) {
            }


            try { if(adquirido) {

                jdbc().execute(
                        "DROP TABLE IF EXISTS padron_ruc_raw"
                );
            }
            } catch (Exception ignored) {
            }

        } finally {
            // Cerrar la conexión libera GET_LOCK incluso tras error/reinicio; jamás reconectar para limpiar.
            sesion.remove();
            if(conexion!=null) try { if(adquirido) try(var s=conexion.createStatement()){s.execute("SELECT RELEASE_ALL_LOCKS()");} }
                catch(Exception ignored) { }
            if(conexion!=null) try { conexion.close(); } catch(Exception ignored) { }

            // =================================================
            // TXT TEMPORAL GENERADO DESDE ZIP
            // =================================================

            if (
                    archivoTemporal
                            &&
                            archivoCarga != null
            ) {

                try {

                    Files.deleteIfExists(
                            archivoCarga
                    );

                } catch (IOException ignored) {
                }
            }
        }
    }


    // =========================================================
    // VALIDAR LOCAL INFILE
    // =========================================================

    private void validarLocalInfile() {

        Number habilitado =
                jdbc().queryForObject(

                        "SELECT @@GLOBAL.local_infile",

                        Number.class
                );


        if (
                habilitado == null
                        ||
                        habilitado.intValue() != 1
        ) {

            throw new IllegalStateException(

                    "MySQL tiene LOCAL INFILE desactivado. "
                            + "Debe habilitarse antes de importar el padrón."
            );
        }
    }


    // =========================================================
    // TABLAS TEMPORALES
    // =========================================================

    private void prepararTablas() {

        jdbc().execute(
                "DROP TABLE IF EXISTS padron_ruc_carga"
        );


        jdbc().execute(
                "DROP TABLE IF EXISTS padron_ruc_raw"
        );


        jdbc().execute(
                "CREATE TABLE padron_ruc_carga LIKE padron_ruc"
        );


        jdbc().execute(
                """
                CREATE TABLE padron_ruc_raw (

                    ruc VARCHAR(64),

                    razon_social VARCHAR(500),

                    estado VARCHAR(100),

                    condicion VARCHAR(100),

                    ubigeo VARCHAR(50),

                    tipo_via VARCHAR(100),

                    nombre_via VARCHAR(500),

                    codigo_zona VARCHAR(100),

                    tipo_zona VARCHAR(100),

                    numero VARCHAR(100),

                    interior VARCHAR(100),

                    lote VARCHAR(100),

                    departamento VARCHAR(100),

                    manzana VARCHAR(100),

                    kilometro VARCHAR(100),

                    extra VARCHAR(100)

                )
                ENGINE=InnoDB
                DEFAULT CHARSET=utf8mb4
                COLLATE=utf8mb4_unicode_ci
                """
        );
    }


    // =========================================================
    // LOAD DATA
    // =========================================================

    private void cargarConLoadData(
            Path archivo
    ) throws Exception {

        String rutaSql =
                archivo
                        .toAbsolutePath()
                        .normalize()
                        .toString()
                        .replace(
                                "\\",
                                "/"
                        )
                        .replace(
                                "'",
                                "''"
                        );


        String charsetMysql =
                resolverCharsetMysql();


        String sql =
                """
                LOAD DATA LOCAL INFILE '%s'
                INTO TABLE padron_ruc_raw
                CHARACTER SET %s
                FIELDS TERMINATED BY '|'
                LINES TERMINATED BY '\\n'
                IGNORE 1 LINES
                (
                    ruc,
                    razon_social,
                    estado,
                    condicion,
                    ubigeo,
                    tipo_via,
                    nombre_via,
                    codigo_zona,
                    tipo_zona,
                    numero,
                    interior,
                    lote,
                    departamento,
                    manzana,
                    kilometro,
                    extra
                )
                """
                        .formatted(
                                rutaSql,
                                charsetMysql
                        );


        try (

                Connection connection =
                        new org.springframework.jdbc.datasource.SingleConnectionDataSource(sesion.get(),true).getConnection();

                Statement statement =
                        connection.createStatement()

        ) {

            statement.executeUpdate(
                    sql
            );
            if(statement.getWarnings()!=null) throw new IllegalStateException("LOAD DATA produjo advertencias; carga no acreditada.");
        }
    }


    // =========================================================
    // VALIDAR REGISTROS
    // =========================================================

    private void cargarRegistrosValidos() {

        jdbc().update(
                """
                INSERT IGNORE INTO padron_ruc_carga (

                    ruc,
                    razon_social,
                    estado,
                    condicion,
                    ubigeo,
                    tipo_via,
                    nombre_via,
                    codigo_zona,
                    tipo_zona,
                    numero,
                    interior,
                    lote,
                    departamento,
                    manzana,
                    kilometro

                )

                SELECT

                    TRIM(ruc),

                    LEFT(
                        TRIM(razon_social),
                        250
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(estado), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(condicion), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(ubigeo), 10),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(tipo_via), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(nombre_via), 200),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(codigo_zona), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(tipo_zona), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(numero), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(interior), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(lote), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(departamento), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(manzana), 40),
                            ''
                        ),
                        '-'
                    ),

                    NULLIF(
                        NULLIF(
                            LEFT(TRIM(kilometro), 40),
                            ''
                        ),
                        '-'
                    )

                FROM padron_ruc_raw

                WHERE

                    TRIM(ruc)
                        REGEXP '^[0-9]{11}$'

                    AND razon_social IS NOT NULL

                    AND TRIM(razon_social) <> ''

                    AND TRIM(razon_social) <> '-'
                """
        );
    }


    // =========================================================
    // INTERCAMBIAR TABLAS
    // =========================================================

    private void intercambiarTablas() {
        boolean anterior=jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='padron_ruc_anterior'",Long.class)>0;
        // Retener todas las versiones: no borrar la única anterior ni siquiera en una segunda actualización.
        String conservar=anterior?"padron_ruc_anterior TO padron_ruc_hist_"+java.util.UUID.randomUUID().toString().replace("-","")+", ":"";
        jdbc().execute(conservar.isEmpty()?
                """
                RENAME TABLE

                    padron_ruc
                    TO padron_ruc_anterior,

                    padron_ruc_carga
                    TO padron_ruc
                """ : "RENAME TABLE "+conservar+"padron_ruc TO padron_ruc_anterior, padron_ruc_carga TO padron_ruc"
        );
    }


    // =========================================================
    // CONTAR
    // =========================================================

    private long contar(
            String tabla
    ) {

        Long total =
                jdbc().queryForObject(

                        "SELECT COUNT(*) FROM "
                                + tabla,

                        Long.class
                );


        return total != null
                ? total
                : 0L;
    }


    // =========================================================
    // PREPARAR ARCHIVO
    // =========================================================

    private Path prepararArchivoParaCarga(
            Path origen
    ) throws IOException {

        String nombre =
                origen
                        .getFileName()
                        .toString()
                        .toLowerCase(
                                Locale.ROOT
                        );


        if (
                !nombre.endsWith(
                        ".zip"
                )
        ) {

            return origen;
        }


        Path temporal =
                Files.createTempFile(

                        origen.getParent(),

                        "padron_ruc_mysql_",

                        ".txt"
                );

        boolean encontrado=false;
        // ZIP central directory íntegro; no aceptar archivos truncados que sólo tengan prefijo PK.
        try(var central=new java.util.zip.ZipFile(origen.toFile())){
            if(central.size()==0) { Files.deleteIfExists(temporal); throw new IOException("ZIP sin entradas."); }
        }catch(IOException e){ Files.deleteIfExists(temporal); throw e; }


        try (

                ZipInputStream zip =
                        new ZipInputStream(

                                new BufferedInputStream(

                                        Files.newInputStream(
                                                origen
                                        )
                                )
                        )

        ) {

            ZipEntry entry;


            while (
                    (entry = zip.getNextEntry())
                            != null
            ) {

                if (
                        entry.isDirectory()
                ) {

                    continue;
                }


                String entryName =
                        entry
                                .getName()
                                .toLowerCase(
                                        Locale.ROOT
                                );


                if (
                        entryName.endsWith(".txt")
                                ||
                                entryName.endsWith(".csv")
                                ||
                                entryName.endsWith(".dat")
                ) {
                    if(encontrado) throw new IOException("ZIP ambiguo: múltiples archivos de padrón.");

                    Files.copy(

                            zip,

                            temporal,

                            StandardCopyOption.REPLACE_EXISTING
                    );


                    encontrado=true;
                }
            }
        }catch(IOException e){ Files.deleteIfExists(temporal); throw e; }

        if(encontrado)return temporal;


        Files.deleteIfExists(
                temporal
        );


        throw new IllegalArgumentException(
                "El ZIP no contiene un archivo TXT, CSV o DAT del padrón RUC."
        );
    }

    private long validarArchivo(Path archivo) throws IOException {
        try(var lector=Files.newBufferedReader(archivo,java.nio.charset.Charset.forName(charsetConfigurado))){
            String cabecera=lector.readLine();
            if(cabecera==null || !cabecera.toLowerCase(Locale.ROOT).startsWith("ruc|"))
                throw new IllegalStateException("Estructura mínima de cabecera inválida.");
            long filas=0; String linea;
            while((linea=lector.readLine())!=null){
                String[] campos=linea.split("\\|",-1);
                if(campos.length!=15 && !(campos.length==16 && campos[15].isEmpty()))
                    throw new IllegalStateException("Estructura de fila incompleta o desconocida.");
                if(!campos[0].trim().matches("[0-9]{11}") || campos[1].isBlank() || campos[1].trim().equals("-"))
                    throw new IllegalStateException("Fila sin identificador o denominación mínima.");
                filas++;
            }
            if(filas==0) throw new IllegalStateException("Padrón vacío; no se activa.");
            return filas;
        }
    }


    // =========================================================
    // RESOLVER ARCHIVO MANUAL
    // =========================================================

    private Path resolverOrigen() {

        Path ruta =
                Paths.get(
                                rutaConfigurada
                        )
                        .toAbsolutePath()
                        .normalize();


        if (
                !Files.exists(
                        ruta
                )
        ) {

            throw new IllegalArgumentException(

                    "No existe la ruta configurada del padrón RUC: "
                            + ruta
            );
        }


        if (
                Files.isRegularFile(
                        ruta
                )
        ) {

            return ruta;
        }


        if (
                !Files.isDirectory(
                        ruta
                )
        ) {

            throw new IllegalArgumentException(
                    "La ruta del padrón RUC no es válida."
            );
        }


        try {

            // =================================================
            // TXT / CSV / DAT
            // =================================================

            try (

                    var stream =
                            Files.list(
                                    ruta
                            )

            ) {

                var archivo =
                        stream
                                .filter(
                                        Files::isRegularFile
                                )
                                .filter(
                                        p -> {

                                            String nombre =
                                                    p
                                                            .getFileName()
                                                            .toString()
                                                            .toLowerCase(
                                                                    Locale.ROOT
                                                            );


                                            return nombre.endsWith(".txt")
                                                    ||
                                                    nombre.endsWith(".csv")
                                                    ||
                                                    nombre.endsWith(".dat");
                                        }
                                )
                                .max(
                                        Comparator.comparingLong(
                                                this::tamanoSeguro
                                        )
                                );


                if (
                        archivo.isPresent()
                ) {

                    return archivo.get();
                }
            }


            // =================================================
            // ZIP
            // =================================================

            try (

                    var stream =
                            Files.list(
                                    ruta
                            )

            ) {

                var zip =
                        stream
                                .filter(
                                        Files::isRegularFile
                                )
                                .filter(
                                        p ->
                                                p
                                                        .getFileName()
                                                        .toString()
                                                        .toLowerCase(
                                                                Locale.ROOT
                                                        )
                                                        .endsWith(".zip")
                                )
                                .max(
                                        Comparator.comparingLong(
                                                this::tamanoSeguro
                                        )
                                );


                if (
                        zip.isPresent()
                ) {

                    return zip.get();
                }
            }


            throw new IllegalArgumentException(

                    "No se encontró TXT, CSV, DAT o ZIP en: "
                            + ruta
            );


        } catch (IOException e) {

            throw new IllegalArgumentException(

                    "No se pudo revisar la carpeta del padrón RUC.",

                    e
            );
        }
    }


    // =========================================================
    // CHARSET MYSQL
    // =========================================================

    private String resolverCharsetMysql() {

        String charset =
                charsetConfigurado
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        return switch (charset) {

            case "windows-1252",
                 "windows1252",
                 "cp1252",
                 "iso-8859-1",
                 "iso8859-1",
                 "latin1" -> "latin1";


            case "utf-8",
                 "utf8",
                 "utf8mb4" -> "utf8mb4";


            default ->
                    throw new IllegalArgumentException(

                            "Charset no soportado para el padrón RUC: "
                                    + charsetConfigurado
                    );
        };
    }


    // =========================================================
    // TAMAÑO
    // =========================================================

    private long tamanoSeguro(
            Path path
    ) {

        try {

            return Files.size(
                    path
            );

        } catch (IOException e) {

            return -1L;
        }
    }


    // =========================================================
    // MENSAJE ERROR
    // =========================================================

    private String mensajeSeguro(
            Exception e
    ) {

        String detalle =
                e.getMessage();


        if (
                detalle == null
                        ||
                        detalle.isBlank()
        ) {

            return e
                    .getClass()
                    .getSimpleName();
        }


        if (
                detalle.length()
                        > 500
        ) {

            return detalle.substring(
                    0,
                    500
            );
        }


        return detalle;
    }


    // =========================================================
    // CERRAR
    // =========================================================

    @PreDestroy
    public void cerrar() {

        executor.shutdown();
    }


    // =========================================================
    // ESTADOS
    // =========================================================

    private enum EstadoImportacion {

        SIN_INICIAR,

        PROCESANDO,

        COMPLETADO,

        ERROR
    }
}
