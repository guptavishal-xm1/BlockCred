package com.blockcred.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "audit_digests", indexes = {
        @Index(name = "idx_audit_digests_date", columnList = "digestDate", unique = true),
        @Index(name = "idx_audit_digests_created", columnList = "createdAt")
})
public class AuditDigestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate digestDate;

    @Column(nullable = false)
    private int recordCount;

    @Column(nullable = false, length = 128)
    private String digestHex;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getDigestDate() { return digestDate; }
    public void setDigestDate(LocalDate digestDate) { this.digestDate = digestDate; }
    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
    public String getDigestHex() { return digestHex; }
    public void setDigestHex(String digestHex) { this.digestHex = digestHex; }
    public Instant getCreatedAt() { return createdAt; }
}
