package org.example.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

import org.example.dto.ActualizacionPadronResponse;
import org.example.dto.ImportacionPadronResponse;
import org.example.dto.ProgramacionPadronRequest;
import org.example.dto.ProgramacionPadronResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import java.util.Locale;
import java.util.Properties;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PadronRucActualizacionService {

    // =========================================================
    // DEPENDENCIA
    // =========================================================

    private final PadronRucImportService importService;


    // =========================================================
    // CONFIGURACIÓN BASE
    // =========================================================

    @Value("${sunat.padron-ruc.path:./padron_reducido_ruc}")
    private String rutaConfigurada;


    @Value("${sunat.padron-ruc.actualizacion.enabled:true}")
    private boolean habilitadoPorDefecto;


    @Value("${sunat.padron-ruc.actualizacion.page-url:https://www.sunat.gob.pe/descargaPRR/mrc137_padron_reducido.html}")
    private String paginaSunat;


    @Value("${sunat.padron-ruc.actualizacion.zone:America/Lima}")
    private String zonaHorariaPorDefecto;


    @Value("${sunat.padron-ruc.actualizacion.default-mode:SEMANAL}")
    private String modoPorDefecto;


    @Value("${sunat.padron-ruc.actualizacion.default-day:DOMINGO}")
    private String diaPorDefecto;


    @Value("${sunat.padron-ruc.actualizacion.default-time:02:00}")
    private String horaPorDefecto;


    @Value("${sunat.padron-ruc.actualizacion.version-inicial:}")
    private String versionInicial;


    // =========================================================
    // FORMATO FECHA SUNAT
    // =========================================================

    private static final DateTimeFormatter FORMATO_SUNAT =
            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy"
            );


    // =========================================================
    // REGEX SUNAT
    // =========================================================

    private static final Pattern PATRON_FECHA =
            Pattern.compile(
                    "Actualizado\\s+al\\s+(\\d{2}/\\d{2}/\\d{4})",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern PATRON_ZIP =
            Pattern.compile(
                    "href\\s*=\\s*[\"']([^\"']*padron_reducido_ruc\\.zip[^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
            );


    // =========================================================
    // HTTP CLIENT
    // =========================================================

    private final HttpClient httpClient =
            HttpClient
                    .newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(30)
                    )
                    .build();


    // =========================================================
    // EJECUTOR DE ACTUALIZACIÓN
    // =========================================================
    //
    // Aquí se ejecuta:
    //
    // consultar SUNAT
    // descargar
    // iniciar importación
    //
    // NO bloquea peticiones HTTP.
    // =========================================================

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    Thread.ofVirtual()
                            .name("padron-ruc-update-")
                            .factory()
            );


    // =========================================================
    // PROGRAMADOR DINÁMICO
    // =========================================================
    //
    // Este hilo solamente espera hasta la fecha/hora.
    //
    // Cuando llega el momento:
    //
    // NO hace la importación aquí.
    //
    // Solamente dispara el proceso en segundo plano.
    // =========================================================

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual()
                            .name("padron-ruc-scheduler-")
                            .factory()
            );


    private volatile ScheduledFuture<?> tareaProgramada;


    // =========================================================
    // PROGRAMACIÓN ACTUAL
    // =========================================================

    private volatile ProgramacionConfig programacion;


    private volatile LocalDateTime proximaEjecucionReal;


    // =========================================================
    // ESTADO ACTUALIZACIÓN
    // =========================================================

    private volatile boolean actualizacionEnCurso;


    private volatile LocalDate versionLocal;

    private volatile LocalDate versionSunat;


    private volatile LocalDateTime ultimaVerificacion;

    private volatile LocalDateTime ultimaDescarga;


    private volatile String mensaje =
            "Actualización automática todavía no verificada.";


    // =========================================================
    // INICIALIZAR
    // =========================================================

    @PostConstruct
    public synchronized void inicializar() {

        // -----------------------------------------------------
        // Leer versión actual
        // -----------------------------------------------------

        versionLocal =
                leerVersionLocal();


        if (
                versionLocal == null
                        &&
                        versionInicial != null
                        &&
                        !versionInicial.isBlank()
        ) {

            versionLocal =
                    LocalDate.parse(
                            versionInicial.trim()
                    );


            guardarVersionLocal(
                    versionLocal
            );
        }


        // -----------------------------------------------------
        // Leer programación persistente
        // -----------------------------------------------------

        programacion =
                leerProgramacion();


        // -----------------------------------------------------
        // Crear próxima ejecución
        // -----------------------------------------------------

        reprogramar();
    }


    // =========================================================
    // OBTENER PROGRAMACIÓN
    // =========================================================

    public synchronized ProgramacionPadronResponse programacion() {

        return respuestaProgramacion(
                "Programación actual."
        );
    }


    // =========================================================
    // MODIFICAR PROGRAMACIÓN
    // =========================================================

    public synchronized ProgramacionPadronResponse actualizarProgramacion(
            ProgramacionPadronRequest request
    ) {

        if (
                request == null
        ) {

            throw new IllegalArgumentException(
                    "La programación es obligatoria."
            );
        }


        // -----------------------------------------------------
        // MODO
        // -----------------------------------------------------

        ModoProgramacion modo =
                parseModo(
                        request.modo()
                );


        // -----------------------------------------------------
        // ZONA HORARIA
        // -----------------------------------------------------

        String zona =
                request.zonaHoraria();


        if (
                zona == null
                        ||
                        zona.isBlank()
        ) {

            zona =
                    zonaHorariaPorDefecto;
        }


        /*
         * Valida que exista.
         *
         * Ejemplo válido:
         *
         * America/Lima
         */
        ZoneId.of(
                zona
        );


        DayOfWeek dia =
                null;


        LocalTime hora =
                null;


        LocalDateTime fechaHoraUnica =
                null;


        // =====================================================
        // PROGRAMACIÓN SEMANAL
        // =====================================================

        if (
                modo == ModoProgramacion.SEMANAL
        ) {

            dia =
                    parseDiaSemana(
                            request.diaSemana()
                    );


            hora =
                    request.hora();


            if (
                    hora == null
            ) {

                throw new IllegalArgumentException(
                        "La hora es obligatoria para una programación SEMANAL."
                );
            }
        }


        // =====================================================
        // PROGRAMACIÓN UNA VEZ
        // =====================================================

        if (
                modo == ModoProgramacion.UNA_VEZ
        ) {

            fechaHoraUnica =
                    request.fechaHoraUnica();


            if (
                    fechaHoraUnica == null
            ) {

                throw new IllegalArgumentException(
                        "fechaHoraUnica es obligatoria para UNA_VEZ."
                );
            }


            ZonedDateTime fechaProgramada =
                    fechaHoraUnica.atZone(
                            ZoneId.of(
                                    zona
                            )
                    );


            if (
                    !fechaProgramada.isAfter(
                            ZonedDateTime.now(
                                    ZoneId.of(
                                            zona
                                    )
                            )
                    )
            ) {

                throw new IllegalArgumentException(
                        "La fecha y hora programadas deben estar en el futuro."
                );
            }
        }


        // =====================================================
        // GUARDAR NUEVA PROGRAMACIÓN
        // =====================================================

        programacion =
                new ProgramacionConfig(

                        request.activo(),

                        modo,

                        dia,

                        hora,

                        zona,

                        fechaHoraUnica
                );


        guardarProgramacion(
                programacion
        );


        // -----------------------------------------------------
        // Compatibilidad con archivo anterior
        // -----------------------------------------------------

        guardarEstadoAutomaticoLegacy(
                request.activo()
        );


        // =====================================================
        // CANCELAR Y CREAR NUEVA TAREA
        // =====================================================

        reprogramar();


        mensaje =
                "Programación de actualización modificada correctamente.";


        return respuestaProgramacion(
                mensaje
        );
    }


    // =========================================================
    // ACTIVAR
    // =========================================================

    public synchronized ActualizacionPadronResponse activar() {

        programacion =
                new ProgramacionConfig(

                        true,

                        programacion.modo(),

                        programacion.diaSemana(),

                        programacion.hora(),

                        programacion.zonaHoraria(),

                        programacion.fechaHoraUnica()
                );


        guardarProgramacion(
                programacion
        );


        guardarEstadoAutomaticoLegacy(
                true
        );


        reprogramar();


        mensaje =
                "Actualización automática activada.";


        return estado();
    }


    // =========================================================
    // DESACTIVAR
    // =========================================================

    public synchronized ActualizacionPadronResponse desactivar() {

        programacion =
                new ProgramacionConfig(

                        false,

                        programacion.modo(),

                        programacion.diaSemana(),

                        programacion.hora(),

                        programacion.zonaHoraria(),

                        programacion.fechaHoraUnica()
                );


        guardarProgramacion(
                programacion
        );


        guardarEstadoAutomaticoLegacy(
                false
        );


        cancelarTareaProgramada();


        proximaEjecucionReal =
                null;


        mensaje =
                "Actualización automática desactivada. "
                        + "El padrón actualmente cargado permanece funcionando.";


        return estado();
    }


    // =========================================================
    // CREAR / RECREAR PROGRAMACIÓN
    // =========================================================

    private synchronized void reprogramar() {

        cancelarTareaProgramada();


        proximaEjecucionReal =
                null;


        if (
                programacion == null
                        ||
                        !programacion.activo()
        ) {

            return;
        }


        ZonedDateTime proxima =
                calcularProximaEjecucion(
                        programacion
                );


        if (
                proxima == null
        ) {

            return;
        }


        ZoneId zona =
                ZoneId.of(
                        programacion.zonaHoraria()
                );


        ZonedDateTime ahora =
                ZonedDateTime.now(
                        zona
                );


        long delayMillis =
                Math.max(

                        0L,

                        Duration
                                .between(
                                        ahora,
                                        proxima
                                )
                                .toMillis()
                );


        proximaEjecucionReal =
                proxima.toLocalDateTime();


        tareaProgramada =
                scheduler.schedule(

                        this::ejecutarProgramacion,

                        delayMillis,

                        TimeUnit.MILLISECONDS
                );
    }


    // =========================================================
    // LLEGÓ LA FECHA/HORA
    // =========================================================

    private synchronized void ejecutarProgramacion() {

        proximaEjecucionReal =
                null;


        /*
         * Si en ese momento ya existe una importación
         * manual o automática:
         *
         * NO ejecutamos dos importaciones simultáneas.
         *
         * Reintentamos 15 minutos después.
         */
        if (
                actualizacionEnCurso
                        ||
                        importService.estaProcesando()
        ) {

            mensaje =
                    "La actualización programada fue pospuesta "
                            + "15 minutos porque existe otra importación en proceso.";


            proximaEjecucionReal =
                    LocalDateTime.now()
                            .plusMinutes(
                                    15
                            );


            tareaProgramada =
                    scheduler.schedule(

                            this::ejecutarProgramacion,

                            15,

                            TimeUnit.MINUTES
                    );


            return;
        }


        ModoProgramacion modoActual =
                programacion.modo();


        // =====================================================
        // UNA SOLA VEZ
        // =====================================================

        if (
                modoActual == ModoProgramacion.UNA_VEZ
        ) {

            /*
             * La desactivamos antes de iniciar.
             *
             * Así no se vuelve a ejecutar después
             * de reiniciar la aplicación.
             */
            programacion =
                    new ProgramacionConfig(

                            false,

                            programacion.modo(),

                            programacion.diaSemana(),

                            programacion.hora(),

                            programacion.zonaHoraria(),

                            programacion.fechaHoraUnica()
                    );


            guardarProgramacion(
                    programacion
            );


            guardarEstadoAutomaticoLegacy(
                    false
            );
        }


        // =====================================================
        // DISPARAR ACTUALIZACIÓN
        // =====================================================

        iniciarProcesoActualizacion(
                "Actualización programada iniciada."
        );


        // =====================================================
        // SI ES SEMANAL, PROGRAMAR LA PRÓXIMA SEMANA
        // =====================================================

        if (
                modoActual == ModoProgramacion.SEMANAL
        ) {

            reprogramar();
        }
    }


    // =========================================================
    // CALCULAR PRÓXIMA EJECUCIÓN
    // =========================================================

    private ZonedDateTime calcularProximaEjecucion(
            ProgramacionConfig config
    ) {

        ZoneId zone =
                ZoneId.of(
                        config.zonaHoraria()
                );


        ZonedDateTime ahora =
                ZonedDateTime.now(
                        zone
                );


        // =====================================================
        // SEMANAL
        // =====================================================

        if (
                config.modo()
                        == ModoProgramacion.SEMANAL
        ) {

            ZonedDateTime proxima =
                    ahora

                            .with(
                                    TemporalAdjusters.nextOrSame(
                                            config.diaSemana()
                                    )
                            )

                            .withHour(
                                    config.hora().getHour()
                            )

                            .withMinute(
                                    config.hora().getMinute()
                            )

                            .withSecond(
                                    config.hora().getSecond()
                            )

                            .withNano(
                                    0
                            );


            if (
                    !proxima.isAfter(
                            ahora
                    )
            ) {

                proxima =
                        proxima.plusWeeks(
                                1
                        );
            }


            return proxima;
        }


        // =====================================================
        // UNA VEZ
        // =====================================================

        ZonedDateTime unica =
                config
                        .fechaHoraUnica()
                        .atZone(
                                zone
                        );


        if (
                !unica.isAfter(
                        ahora
                )
        ) {

            return null;
        }


        return unica;
    }


    // =========================================================
    // ESTADO GENERAL
    // =========================================================

    public ActualizacionPadronResponse estado() {

        boolean disponible =
                versionSunat != null
                        &&
                        (
                                versionLocal == null
                                        ||
                                        versionSunat.isAfter(
                                                versionLocal
                                        )
                        );


        boolean activa =
                programacion != null
                        &&
                        programacion.activo();


        return new ActualizacionPadronResponse(

                activa,

                versionLocal,

                versionSunat,

                disponible,

                actualizacionEnCurso,

                importService
                        .estado()
                        .estado(),

                ultimaVerificacion,

                ultimaDescarga,

                proximaEjecucionReal,

                mensaje
        );
    }


    // =========================================================
    // VERIFICAR SUNAT MANUALMENTE
    // =========================================================

    public synchronized ActualizacionPadronResponse verificar() {

        try {

            InfoSunat info =
                    consultarSunat();


            versionSunat =
                    info.fecha();


            ultimaVerificacion =
                    LocalDateTime.now();


            if (
                    versionLocal == null
            ) {

                mensaje =
                        "SUNAT tiene la versión "
                                + versionSunat
                                + ". No existe una versión local registrada.";

            } else if (
                    versionSunat.isAfter(
                            versionLocal
                    )
            ) {

                mensaje =
                        "Existe una actualización disponible. "
                                + "Local: "
                                + versionLocal
                                + " | SUNAT: "
                                + versionSunat;

            } else {

                mensaje =
                        "El padrón local ya está actualizado. "
                                + "Versión: "
                                + versionLocal;
            }


        } catch (Exception e) {

            mensaje =
                    "No se pudo verificar SUNAT: "
                            + mensajeSeguro(
                            e
                    );
        }


        return estado();
    }


    // =========================================================
    // EJECUCIÓN MANUAL
    // =========================================================

    public synchronized ActualizacionPadronResponse ejecutar() {

        if (
                actualizacionEnCurso
        ) {

            mensaje =
                    "Ya existe una actualización en proceso.";

            return estado();
        }


        if (
                importService.estaProcesando()
        ) {

            mensaje =
                    "No se puede actualizar porque existe "
                            + "una importación del padrón en proceso.";

            return estado();
        }


        iniciarProcesoActualizacion(
                "Actualización manual iniciada."
        );


        return estado();
    }


    // =========================================================
    // INICIAR PROCESO EN SEGUNDO PLANO
    // =========================================================

    private synchronized void iniciarProcesoActualizacion(
            String mensajeInicio
    ) {

        if (
                actualizacionEnCurso
        ) {

            return;
        }


        actualizacionEnCurso =
                true;


        mensaje =
                mensajeInicio;


        /*
         * IMPORTANTE:
         *
         * El proceso pesado NO ocurre en el
         * hilo que atiende las peticiones HTTP.
         */
        try { executor.submit(
                this::procesarActualizacion
        ); } catch(RuntimeException e) { actualizacionEnCurso=false; throw e; }
    }


    // =========================================================
    // PROCESO DE ACTUALIZACIÓN
    // =========================================================

    private void procesarActualizacion() {

        boolean importacionDelegada =
                false;


        Path archivoDescargado =
                null;


        try {

            // =================================================
            // CONSULTAR SUNAT
            // =================================================

            InfoSunat info =
                    consultarSunat();


            versionSunat =
                    info.fecha();


            ultimaVerificacion =
                    LocalDateTime.now();


            // =================================================
            // ¿HAY ACTUALIZACIÓN?
            // =================================================

            if (
                    versionLocal != null
                            &&
                            !versionSunat.isAfter(
                                    versionLocal
                            )
            ) {

                mensaje =
                        "No existe una versión nueva. "
                                + "Local: "
                                + versionLocal
                                + " | SUNAT: "
                                + versionSunat;


                return;
            }


            // =================================================
            // DESCARGAR
            // =================================================

            mensaje =
                    "Descargando padrón RUC SUNAT versión "
                            + versionSunat
                            + "...";


            archivoDescargado =
                    descargarZip(
                            info
                    );


            ultimaDescarga =
                    LocalDateTime.now();


            mensaje =
                    "Descarga completada. "
                            + "Iniciando importación de la versión "
                            + versionSunat
                            + "...";


            final Path zipFinal =
                    archivoDescargado;


            final LocalDate versionAImportar =
                    versionSunat;


            // =================================================
            // ENTREGAR AL IMPORTADOR
            // =================================================

            importService
                    .iniciarDesdeArchivo(
                            zipFinal
                    )
                    .whenComplete(
                            (
                                    ImportacionPadronResponse resultado,
                                    Throwable error
                            ) -> {

                                try {

                                    if (
                                            error != null
                                    ) {

                                        mensaje =
                                                "La importación automática falló: "
                                                        + mensajeSeguro(
                                                        error
                                                );

                                        return;
                                    }


                                    if (
                                            resultado != null
                                                    &&
                                                    "COMPLETADO"
                                                            .equalsIgnoreCase(
                                                                    resultado.estado()
                                                            )
                                    ) {

                                        guardarVersionLocal(
                                                versionAImportar
                                        );


                                        versionLocal =
                                                versionAImportar;


                                        mensaje =
                                                "Padrón RUC actualizado correctamente "
                                                        + "a la versión "
                                                        + versionAImportar
                                                        + ". Registros finales: "
                                                        + resultado.registrosFinales();


                                    } else {

                                        String detalle =
                                                resultado != null
                                                        ? resultado.mensaje()
                                                        : "Sin detalle";


                                        mensaje =
                                                "El archivo se descargó, "
                                                        + "pero la importación no se completó: "
                                                        + detalle;
                                    }


                                } finally {

                                    eliminarArchivoSeguro(
                                            zipFinal
                                    );


                                    actualizacionEnCurso =
                                            false;
                                }
                            }
                    );


            importacionDelegada = true; // Sólo después de registrar correctamente el callback.
        } catch (Exception e) {

            mensaje =
                    "No se pudo completar la actualización: "
                            + mensajeSeguro(
                            e
                    );


        } finally {

            if (
                    !importacionDelegada
            ) {

                if (
                        archivoDescargado != null
                ) {

                    eliminarArchivoSeguro(
                            archivoDescargado
                    );
                }


                actualizacionEnCurso =
                        false;
            }
        }
    }


    // =========================================================
    // CONSULTAR SUNAT
    // =========================================================

    private InfoSunat consultarSunat()
            throws Exception {

        URI pagina =
                URI.create(
                        paginaSunat
                );


        HttpRequest request =
                HttpRequest
                        .newBuilder(
                                pagina
                        )
                        .GET()
                        .timeout(
                                Duration.ofSeconds(60)
                        )
                        .header(
                                "User-Agent",
                                "OmniSync-PadronRUC-Updater/1.0"
                        )
                        .header(
                                "Accept",
                                "text/html"
                        )
                        .build();


        HttpResponse<String> response =
                httpClient.send(

                        request,

                        HttpResponse.BodyHandlers.ofString()
                );


        if (
                response.statusCode()
                        != 200
        ) {

            throw new IllegalStateException(
                    "SUNAT respondió HTTP "
                            + response.statusCode()
            );
        }


        String html =
                response.body();


        Matcher fechaMatcher =
                PATRON_FECHA.matcher(
                        html
                );


        if (
                !fechaMatcher.find()
        ) {

            throw new IllegalStateException(
                    "No se pudo encontrar la fecha "
                            + "de actualización en SUNAT."
            );
        }


        LocalDate fecha =
                LocalDate.parse(

                        fechaMatcher.group(1),

                        FORMATO_SUNAT
                );


        Matcher zipMatcher =
                PATRON_ZIP.matcher(
                        html
                );


        if (
                !zipMatcher.find()
        ) {

            throw new IllegalStateException(
                    "No se pudo localizar el ZIP "
                            + "del padrón RUC en SUNAT."
            );
        }


        String href =
                zipMatcher
                        .group(1)
                        .replace(
                                "&amp;",
                                "&"
                        );


        URI urlZip =
                pagina.resolve(
                        href
                );


        return new InfoSunat(
                fecha,
                urlZip
        );
    }


    // =========================================================
    // DESCARGAR ZIP
    // =========================================================

    private Path descargarZip(
            InfoSunat info
    ) throws Exception {

        Path carpeta =
                directorioBase()
                        .resolve(
                                "actualizaciones"
                        );


        Files.createDirectories(
                carpeta
        );


        String nombre =
                "padron_reducido_ruc_"
                        + info.fecha()
                        + "_" + java.util.UUID.randomUUID()
                        + ".zip";


        Path destino =
                carpeta.resolve(
                        nombre
                );


        Files.deleteIfExists(
                destino
        );


        HttpRequest request =
                HttpRequest
                        .newBuilder(
                                info.urlZip()
                        )
                        .GET()
                        .timeout(
                                Duration.ofHours(2)
                        )
                        .header(
                                "User-Agent",
                                "OmniSync-PadronRUC-Updater/1.0"
                        )
                        .header(
                                "Accept",
                                "application/zip,application/octet-stream,*/*"
                        )
                        .build();


        HttpResponse<Path> response =
                httpClient.send(

                        request,

                        HttpResponse.BodyHandlers.ofFile(
                                destino
                        )
                );


        if (
                response.statusCode()
                        != 200
        ) {

            Files.deleteIfExists(
                    destino
            );


            throw new IllegalStateException(
                    "La descarga SUNAT respondió HTTP "
                            + response.statusCode()
            );
        }


        validarZip(
                destino
        );


        return destino;
    }


    // =========================================================
    // VALIDAR ZIP
    // =========================================================

    private void validarZip(
            Path archivo
    ) throws IOException {

        if (
                !Files.exists(
                        archivo
                )
        ) {

            throw new IllegalStateException(
                    "El ZIP descargado no existe."
            );
        }


        if (
                Files.size(
                        archivo
                ) < 1024
        ) {

            throw new IllegalStateException(
                    "El archivo descargado es demasiado pequeño."
            );
        }


        try (

                InputStream input =
                        Files.newInputStream(
                                archivo
                        )

        ) {

            int primero =
                    input.read();


            int segundo =
                    input.read();


            if (
                    primero != 'P'
                            ||
                            segundo != 'K'
            ) {

                throw new IllegalStateException(
                        "El archivo descargado no parece "
                                + "ser un ZIP válido."
                );
            }
        }
    }


    // =========================================================
    // LEER PROGRAMACIÓN
    // =========================================================

    private ProgramacionConfig leerProgramacion() {

        Path archivo =
                archivoProgramacion();


        if (
                !Files.exists(
                        archivo
                )
        ) {

            ProgramacionConfig inicial =
                    new ProgramacionConfig(

                            leerEstadoAutomaticoLegacy(),

                            parseModo(
                                    modoPorDefecto
                            ),

                            parseDiaSemana(
                                    diaPorDefecto
                            ),

                            LocalTime.parse(
                                    horaPorDefecto
                            ),

                            zonaHorariaPorDefecto,

                            null
                    );


            guardarProgramacion(
                    inicial
            );


            return inicial;
        }


        try {

            Properties propiedades =
                    new Properties();


            try (

                    Reader reader =
                            Files.newBufferedReader(

                                    archivo,

                                    StandardCharsets.UTF_8
                            )

            ) {

                propiedades.load(
                        reader
                );
            }


            boolean activo =
                    Boolean.parseBoolean(
                            propiedades.getProperty(
                                    "activo",
                                    Boolean.toString(
                                            habilitadoPorDefecto
                                    )
                            )
                    );


            ModoProgramacion modo =
                    parseModo(
                            propiedades.getProperty(
                                    "modo",
                                    modoPorDefecto
                            )
                    );


            String zona =
                    propiedades.getProperty(
                            "zonaHoraria",
                            zonaHorariaPorDefecto
                    );


            ZoneId.of(
                    zona
            );


            DayOfWeek dia =
                    null;


            LocalTime hora =
                    null;


            LocalDateTime fechaUnica =
                    null;


            if (
                    modo == ModoProgramacion.SEMANAL
            ) {

                dia =
                        parseDiaSemana(
                                propiedades.getProperty(
                                        "diaSemana",
                                        diaPorDefecto
                                )
                        );


                hora =
                        LocalTime.parse(
                                propiedades.getProperty(
                                        "hora",
                                        horaPorDefecto
                                )
                        );
            }


            if (
                    modo == ModoProgramacion.UNA_VEZ
            ) {

                String fecha =
                        propiedades.getProperty(
                                "fechaHoraUnica",
                                ""
                        );


                if (
                        !fecha.isBlank()
                ) {

                    fechaUnica =
                            LocalDateTime.parse(
                                    fecha
                            );
                }
            }


            return new ProgramacionConfig(

                    activo,

                    modo,

                    dia,

                    hora,

                    zona,

                    fechaUnica
            );


        } catch (Exception e) {

            throw new IllegalStateException(
                    "No se pudo leer la programación "
                            + "del padrón RUC.",
                    e
            );
        }
    }


    // =========================================================
    // GUARDAR PROGRAMACIÓN
    // =========================================================

    private void guardarProgramacion(
            ProgramacionConfig config
    ) {

        try {

            Path archivo =
                    archivoProgramacion();


            Files.createDirectories(
                    archivo.getParent()
            );


            Properties propiedades =
                    new Properties();


            propiedades.setProperty(
                    "activo",
                    Boolean.toString(
                            config.activo()
                    )
            );


            propiedades.setProperty(
                    "modo",
                    config.modo().name()
            );


            propiedades.setProperty(
                    "zonaHoraria",
                    config.zonaHoraria()
            );


            propiedades.setProperty(
                    "diaSemana",
                    config.diaSemana() != null
                            ? config.diaSemana().name()
                            : ""
            );


            propiedades.setProperty(
                    "hora",
                    config.hora() != null
                            ? config.hora().toString()
                            : ""
            );


            propiedades.setProperty(
                    "fechaHoraUnica",
                    config.fechaHoraUnica() != null
                            ? config.fechaHoraUnica().toString()
                            : ""
            );


            Path temporal =
                    archivo.resolveSibling(
                            archivo.getFileName()
                                    + ".tmp"
                    );


            try (

                    Writer writer =
                            Files.newBufferedWriter(

                                    temporal,

                                    StandardCharsets.UTF_8
                            )

            ) {

                propiedades.store(
                        writer,
                        "Programacion dinamica Padron RUC SUNAT"
                );
            }


            moverAtomico(
                    temporal,
                    archivo
            );


        } catch (IOException e) {

            throw new IllegalStateException(
                    "No se pudo guardar la programación "
                            + "del padrón RUC.",
                    e
            );
        }
    }


    // =========================================================
    // RESPUESTA PROGRAMACIÓN
    // =========================================================

    private ProgramacionPadronResponse respuestaProgramacion(
            String detalle
    ) {

        String dia =
                programacion.diaSemana() != null
                        ? nombreDiaEspanol(
                        programacion.diaSemana()
                )
                        : null;


        return new ProgramacionPadronResponse(

                programacion.activo(),

                programacion.modo().name(),

                dia,

                programacion.hora(),

                programacion.zonaHoraria(),

                programacion.fechaHoraUnica(),

                proximaEjecucionReal,

                detalle
        );
    }


    // =========================================================
    // VERSION LOCAL
    // =========================================================

    private LocalDate leerVersionLocal() {

        Path archivo =
                archivoVersion();


        if (
                !Files.exists(
                        archivo
                )
        ) {

            return null;
        }


        try {

            String contenido =
                    Files.readString(

                                    archivo,

                                    StandardCharsets.UTF_8
                            )
                            .trim();


            if (
                    contenido.isBlank()
            ) {

                return null;
            }


            return LocalDate.parse(
                    contenido
            );


        } catch (Exception e) {

            return null;
        }
    }


    private void guardarVersionLocal(
            LocalDate version
    ) {

        try {

            Path archivo =
                    archivoVersion();


            Files.createDirectories(
                    archivo.getParent()
            );


            escribirAtomico(

                    archivo,

                    version.toString()
            );


        } catch (IOException e) {

            throw new IllegalStateException(
                    "No se pudo guardar la versión local "
                            + "del padrón RUC.",
                    e
            );
        }
    }


    // =========================================================
    // COMPATIBILIDAD ACTIVACIÓN ANTERIOR
    // =========================================================

    private boolean leerEstadoAutomaticoLegacy() {

        Path archivo =
                archivoEstadoAutomatico();


        if (
                !Files.exists(
                        archivo
                )
        ) {

            return habilitadoPorDefecto;
        }


        try {

            return Boolean.parseBoolean(

                    Files.readString(

                                    archivo,

                                    StandardCharsets.UTF_8
                            )
                            .trim()
            );


        } catch (IOException e) {

            return habilitadoPorDefecto;
        }
    }


    private void guardarEstadoAutomaticoLegacy(
            boolean activo
    ) {

        try {

            Path archivo =
                    archivoEstadoAutomatico();


            Files.createDirectories(
                    archivo.getParent()
            );


            escribirAtomico(

                    archivo,

                    Boolean.toString(
                            activo
                    )
            );


        } catch (IOException e) {

            throw new IllegalStateException(
                    "No se pudo guardar el estado "
                            + "de actualización automática.",
                    e
            );
        }
    }


    // =========================================================
    // ARCHIVOS
    // =========================================================

    private Path archivoProgramacion() {

        return directorioBase()
                .resolve(
                        ".programacion_actualizacion.properties"
                );
    }


    private Path archivoVersion() {

        return directorioBase()
                .resolve(
                        ".ultima_version_importada"
                );
    }


    private Path archivoEstadoAutomatico() {

        return directorioBase()
                .resolve(
                        ".actualizacion_automatica"
                );
    }


    // =========================================================
    // DIRECTORIO BASE
    // =========================================================

    private Path directorioBase() {

        Path ruta =
                Paths.get(
                                rutaConfigurada
                        )
                        .toAbsolutePath()
                        .normalize();


        if (
                Files.exists(
                        ruta
                )
                        &&
                        Files.isRegularFile(
                                ruta
                        )
        ) {

            return ruta.getParent();
        }


        return ruta;
    }


    // =========================================================
    // HELPERS FECHA / DÍA / MODO
    // =========================================================

    private ModoProgramacion parseModo(
            String valor
    ) {

        if (
                valor == null
                        ||
                        valor.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "El modo de programación es obligatorio."
            );
        }


        String normalizado =
                valor
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        return switch (normalizado) {

            case "SEMANAL" ->
                    ModoProgramacion.SEMANAL;


            case "UNA_VEZ",
                 "UNAVEZ",
                 "UNICA",
                 "ÚNICA" ->
                    ModoProgramacion.UNA_VEZ;


            default ->
                    throw new IllegalArgumentException(
                            "Modo no válido. Use SEMANAL o UNA_VEZ."
                    );
        };
    }


    private DayOfWeek parseDiaSemana(
            String valor
    ) {

        if (
                valor == null
                        ||
                        valor.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "El día de la semana es obligatorio."
            );
        }


        String dia =
                valor
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        return switch (dia) {

            case "LUNES", "MONDAY" ->
                    DayOfWeek.MONDAY;

            case "MARTES", "TUESDAY" ->
                    DayOfWeek.TUESDAY;

            case "MIERCOLES",
                 "MIÉRCOLES",
                 "WEDNESDAY" ->
                    DayOfWeek.WEDNESDAY;

            case "JUEVES", "THURSDAY" ->
                    DayOfWeek.THURSDAY;

            case "VIERNES", "FRIDAY" ->
                    DayOfWeek.FRIDAY;

            case "SABADO",
                 "SÁBADO",
                 "SATURDAY" ->
                    DayOfWeek.SATURDAY;

            case "DOMINGO", "SUNDAY" ->
                    DayOfWeek.SUNDAY;

            default ->
                    throw new IllegalArgumentException(
                            "Día de semana no válido."
                    );
        };
    }


    private String nombreDiaEspanol(
            DayOfWeek dia
    ) {

        return switch (dia) {

            case MONDAY ->
                    "LUNES";

            case TUESDAY ->
                    "MARTES";

            case WEDNESDAY ->
                    "MIERCOLES";

            case THURSDAY ->
                    "JUEVES";

            case FRIDAY ->
                    "VIERNES";

            case SATURDAY ->
                    "SABADO";

            case SUNDAY ->
                    "DOMINGO";
        };
    }


    // =========================================================
    // ESCRITURA ATÓMICA
    // =========================================================

    private void escribirAtomico(
            Path destino,
            String contenido
    ) throws IOException {

        Path temporal =
                destino.resolveSibling(
                        destino.getFileName()
                                + ".tmp"
                );


        Files.writeString(

                temporal,

                contenido,

                StandardCharsets.UTF_8
        );


        moverAtomico(
                temporal,
                destino
        );
    }


    private void moverAtomico(
            Path temporal,
            Path destino
    ) throws IOException {

        try {

            Files.move(

                    temporal,

                    destino,

                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );


        } catch (AtomicMoveNotSupportedException e) {

            Files.move(

                    temporal,

                    destino,

                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }


    // =========================================================
    // CANCELAR TAREA FUTURA
    // =========================================================

    private void cancelarTareaProgramada() {

        ScheduledFuture<?> tarea =
                tareaProgramada;


        if (
                tarea != null
                        &&
                        !tarea.isDone()
        ) {

            tarea.cancel(
                    false
            );
        }


        tareaProgramada =
                null;
    }


    // =========================================================
    // ELIMINAR ZIP
    // =========================================================

    private void eliminarArchivoSeguro(
            Path archivo
    ) {

        try {

            Files.deleteIfExists(
                    archivo
            );

        } catch (IOException ignored) {
        }
    }


    // =========================================================
    // ERROR
    // =========================================================

    private String mensajeSeguro(
            Throwable e
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
                detalle.length() > 500
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

        cancelarTareaProgramada();

        scheduler.shutdown();

        executor.shutdown();
    }


    // =========================================================
    // RECORDS / ENUMS INTERNOS
    // =========================================================

    private record InfoSunat(

            LocalDate fecha,

            URI urlZip

    ) {
    }


    private record ProgramacionConfig(

            boolean activo,

            ModoProgramacion modo,

            DayOfWeek diaSemana,

            LocalTime hora,

            String zonaHoraria,

            LocalDateTime fechaHoraUnica

    ) {
    }


    private enum ModoProgramacion {

        SEMANAL,

        UNA_VEZ
    }
}
