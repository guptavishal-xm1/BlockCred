package com.blockcred.ops;

import com.blockcred.service.JobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
public class OpsController {
    private final JobService jobService;
    private final long cooldownSeconds;

    public OpsController(JobService jobService, @Value("${blockcred.worker.cooldown-seconds:60}") long cooldownSeconds) {
        this.jobService = jobService;
        this.cooldownSeconds = cooldownSeconds;
    }

    @PostMapping("/reconcile/{credentialId}")
    public Map<String, String> reconcile(
            @PathVariable String credentialId,
            @RequestHeader(value = "X-Admin", required = false) String adminHeader
    ) {
        if (!"true".equalsIgnoreCase(adminHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }

        String result = jobService.reconcile(credentialId, "admin", Duration.ofSeconds(cooldownSeconds));
        return Map.of("result", result);
    }
}
