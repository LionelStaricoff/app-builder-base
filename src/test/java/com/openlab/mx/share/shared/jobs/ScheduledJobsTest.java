package com.openlab.mx.share.shared.jobs;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.payment.service.EscrowReleaseService;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.CancellationService;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledJobsTest {

    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CancellationService cancellationService;
    @Mock
    private EscrowReleaseService escrowReleaseService;

    @InjectMocks
    private ScheduledJobs scheduledJobs;

    private ExperienceInstance instancia(Long id, int minCapacity) {
        Experience experience = Experience.builder()
                .id(1L)
                .creator(User.builder().id(1L).username("creador").build())
                .name("Tour")
                .price(BigDecimal.valueOf(190))
                .minCapacity(minCapacity)
                .maxCapacity(8)
                .build();
        return ExperienceInstance.builder()
                .id(id)
                .experience(experience)
                .date(LocalDate.now().minusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED)
                .build();
    }

    @Test
    void testCompletionJobMarcaCompleted() {
        ExperienceInstance instancia = instancia(10L, 1);
        when(instanceRepository.findFinished(any(InstanceStatus.class), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(instancia))
                .thenReturn(List.of(instancia));

        scheduledJobs.completionJob();
        assertEquals(InstanceStatus.COMPLETED, instancia.getStatus());
        verify(instanceRepository).save(instancia);
        verify(escrowReleaseService).releaseIfDue(10L);

        scheduledJobs.completionJob();
        assertEquals(InstanceStatus.COMPLETED, instancia.getStatus(), "2ª corrida: no-op");
        verify(instanceRepository, times(1)).save(instancia);
        verify(escrowReleaseService, times(1)).releaseIfDue(10L);
    }

    @Test
    void testMinCapacityJobCancelaYReembolsa() {
        ExperienceInstance pocos = instancia(10L, 3);
        ExperienceInstance lleno = instancia(11L, 2);
        when(instanceRepository.findStarted(any(InstanceStatus.class), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(pocos, lleno));
        when(reservationRepository.countAttendees(10L)).thenReturn(1L);
        when(reservationRepository.countAttendees(11L)).thenReturn(3L);

        scheduledJobs.minCapacityJob();

        verify(cancellationService).cancelByMinCapacity(10L);
        verify(cancellationService, never()).cancelByMinCapacity(eq(11L));
    }

    @Test
    void testJobsNoPropaganExcepciones() {
        when(instanceRepository.findFinished(any(InstanceStatus.class), any(LocalDate.class), any(LocalTime.class)))
                .thenThrow(new RuntimeException("repositorio caído"));
        when(instanceRepository.findStarted(any(InstanceStatus.class), any(LocalDate.class), any(LocalTime.class)))
                .thenThrow(new RuntimeException("repositorio caído"));

        assertDoesNotThrow(() -> scheduledJobs.completionJob());
        assertDoesNotThrow(() -> scheduledJobs.minCapacityJob());
        verify(cancellationService, never()).cancelByMinCapacity(anyLong());
    }
}
