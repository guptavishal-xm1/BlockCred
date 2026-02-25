package com.blockcred.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "system_controls")
public class SystemControlEntity {
    @Id
    @Column(nullable = false, length = 120)
    private String controlKey;

    @Column(nullable = false, length = 2000)
    private String controlValue;

    @Column
    private String updatedBy;

    @Column
    private String details;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getControlKey() { return controlKey; }
    public void setControlKey(String controlKey) { this.controlKey = controlKey; }
    public String getControlValue() { return controlValue; }
    public void setControlValue(String controlValue) { this.controlValue = controlValue; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getUpdatedAt() { return updatedAt; }
}
