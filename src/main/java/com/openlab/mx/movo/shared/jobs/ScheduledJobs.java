package com.openlab.mx.movo.shared.jobs;

import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.payment.service.EscrowReleaseService;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.reservation.service.CancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;import java.time.LocalDate;
import java.time.ZoneId;import java.time.LocalTime;

/**
 * Jobs de la Etapa B (spec §11): finalización de instancias + liberación
 * de escrow vencido, y cancelación por cupo mínimo. Corren cada minuto y
 * nunca propagan excepciones (un job roto no puede frenar a los demás).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobs {

    private final ExperienceInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final CancellationService cancellationService;
    private final EscrowReleaseService escrowReleaseService;

    /** Instancias terminadas → COMPLETED y liberación de escrow vencido (idempotente). */
    @Scheduled(fixedDelayString = "${jobs.interval-ms:60000}")
    public void completionJob() {
        try {
            var terminadas = instanceRepository.findFinished(
                    InstanceStatus.SCHEDULED, LocalDate.now(ZoneId.systemDefault()), LocalTime.now(ZoneId.systemDefault()));
            for (ExperienceInstance instance : terminadas) {
                if (instance.getStatus() != InstanceStatus.SCHEDULED) {
                    continue;
                }
                instance.setStatus(InstanceStatus.COMPLETED);
                instanceRepository.save(instance);
                escrowReleaseService.releaseIfDue(instance.getId());
            }
        } catch (Exception e) {
            log.error("completionJob falló: {}", e.getMessage());
        }
    }

    /** Instancias iniciadas con menos asistentes que el mínimo → Caso 3. */
    @Scheduled(fixedDelayString = "${jobs.interval-ms:60000}")
    public void minCapacityJob() {
        try {
            var iniciadas = instanceRepository.findStarted(
                    InstanceStatus.SCHEDULED, LocalDate.now(ZoneId.systemDefault()), LocalTime.now(ZoneId.systemDefault()));
            for (ExperienceInstance instance : iniciadas) {
                long asistentes = reservationRepository.countAttendees(instance.getId());
                if (asistentes < instance.getExperience().getMinCapacity()) {
                    cancellationService.cancelByMinCapacity(instance.getId());
                }
            }
        } catch (Exception e) {
            log.error("minCapacityJob falló: {}", e.getMessage());
        }
    }
}
