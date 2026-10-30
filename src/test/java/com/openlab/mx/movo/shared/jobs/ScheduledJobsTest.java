package com.openlab.mx.movo.shared.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ScheduledJobsTest {

    @Test
    void jobsNoPropaganExcepciones() {
        ScheduledJobs jobs = new ScheduledJobs();
        assertDoesNotThrow(jobs::completionJob);
        assertDoesNotThrow(jobs::minCapacityJob);
    }
}
