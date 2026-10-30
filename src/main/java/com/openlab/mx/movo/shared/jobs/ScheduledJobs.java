package com.openlab.mx.movo.shared.jobs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Jobs del dominio anterior (marketplace de experiencias): neutralizados en
 * el Corte D de T8. La lógica de viajes Movo tendrá sus propios jobs cuando
 * el retiro sea efectivo; no arrastramos dependencias al marketplace.
 */
@Slf4j
@Component
public class ScheduledJobs {

    /** Sin-op: el ciclo antiguo de experiencias queda fuera del arranque. */
    @Scheduled(fixedDelayString = "${jobs.interval-ms:60000}")
    public void completionJob() {
        // no-op
    }

    /** Sin-op: el ciclo antiguo de experiencias queda fuera del arranque. */
    @Scheduled(fixedDelayString = "${jobs.interval-ms:60000}")
    public void minCapacityJob() {
        // no-op
    }
}
