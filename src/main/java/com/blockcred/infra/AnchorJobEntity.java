package com.blockcred.infra;

import com.blockcred.domain.JobStatus;
import com.blockcred.domain.JobType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "anchor_jobs", indexes = {
        @Index(name = "idx_anchor_jobs_cred", columnList = "credentialId"),
        @Index(name = "idx_anchor_jobs_status_next", columnList = "status,nextRunAt")
})
public class AnchorJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String credentialId;

    @Column(nullable = false)
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column
    private Instant lastAttemptAt;

    @Column
    private String lastError;

    @Column
    private Instant lastManualTriggerAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public JobType getJobType() { return jobType; }
    public void setJobType(JobType jobType) { this.jobType = jobType; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getLastManualTriggerAt() { return lastManualTriggerAt; }
    public void setLastManualTriggerAt(Instant lastManualTriggerAt) { this.lastManualTriggerAt = lastManualTriggerAt; }
}
