package com.blockcred.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WorkerHeartbeatService {
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();

    public void markRun() {
        lastRunAt.set(Instant.now());
    }

    public Instant lastRunAt() {
        return lastRunAt.get();
    }
}
