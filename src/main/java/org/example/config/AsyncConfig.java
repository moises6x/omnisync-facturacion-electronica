
package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * =============================================================
 * ASYNC CONFIG
 * =============================================================
 *
 * Pool utilizado para trabajos SUNAT que no deben bloquear
 * la petición HTTP principal.
 *
 * El tamaño es deliberadamente pequeño porque:
 *
 * - SUNAT no debe recibir ráfagas innecesarias.
 * - cada empresa utiliza credenciales propias.
 * - necesitamos controlar la presión sobre la cola.
 *
 * Los valores se vuelven configurables para poder utilizar
 * una capacidad distinta en BETA y PRODUCCIÓN sin recompilar.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${sunat.async.core-pool-size:1}")
    private int corePoolSize;

    @Value("${sunat.async.max-pool-size:2}")
    private int maxPoolSize;

    @Value("${sunat.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${sunat.async.await-termination-seconds:30}")
    private int awaitTerminationSeconds;

    @Bean(name = "sunatExecutor")
    public Executor sunatExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(
                Math.max(1, corePoolSize)
        );

        executor.setMaxPoolSize(
                Math.max(
                        Math.max(1, corePoolSize),
                        maxPoolSize
                )
        );

        executor.setQueueCapacity(
                Math.max(10, queueCapacity)
        );

        executor.setThreadNamePrefix(
                "sunat-"
        );

        /*
         * Al apagar el servidor intentamos terminar primero
         * los trabajos que ya fueron aceptados por el executor.
         */
        executor.setWaitForTasksToCompleteOnShutdown(
                true
        );

        executor.setAwaitTerminationSeconds(
                Math.max(5, awaitTerminationSeconds)
        );

        executor.initialize();

        return executor;
    }
}