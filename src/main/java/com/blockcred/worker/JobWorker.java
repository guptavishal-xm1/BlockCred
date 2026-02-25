package com.blockcred.worker;

import com.blockcred.service.JobService;
import com.blockcred.service.WorkerHeartbeatService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {
    private final JobService jobService;
    private final WorkerHeartbeatService heartbeatService;

    public JobWorker(JobService jobService, WorkerHeartbeatService heartbeatService) {
        this.jobService = jobService;
        this.heartbeatService = heartbeatService;
    }

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        heartbeatService.markRun();
        jobService.processDueJobs();
    }
}
