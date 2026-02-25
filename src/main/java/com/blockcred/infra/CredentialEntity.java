package com.blockcred.infra;

import com.blockcred.domain.CredentialLifecycleStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "credentials", indexes = {
        @Index(name = "idx_credentials_credential_id", columnList = "credentialId", unique = true),
        @Index(name = "idx_credentials_hash", columnList = "hash", unique = true)
})
public class CredentialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String credentialId;

    @Column(nullable = false, unique = true, length = 128)
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialLifecycleStatus lifecycleStatus;

    @Column(nullable = false, length = 3000)
    private String canonicalPayloadJson;

    @Column
    private String lastTxHash;

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
    public CredentialLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(CredentialLifecycleStatus lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getCanonicalPayloadJson() { return canonicalPayloadJson; }
    public void setCanonicalPayloadJson(String canonicalPayloadJson) { this.canonicalPayloadJson = canonicalPayloadJson; }
    public String getLastTxHash() { return lastTxHash; }
    public void setLastTxHash(String lastTxHash) { this.lastTxHash = lastTxHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
