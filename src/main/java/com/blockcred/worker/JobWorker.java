package com.blockcred.worker;

import com.blockcred.service.JobService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {
    private final JobService jobService;

    public JobWorker(JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        jobService.processDueJobs();
    }
}
