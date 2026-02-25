package com.blockcred.infra;

import com.blockcred.domain.JobStatus;
import com.blockcred.domain.JobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnchorJobRepository extends JpaRepository<AnchorJobEntity, Long> {
    List<AnchorJobEntity> findByStatusInAndNextRunAtBefore(List<JobStatus> statuses, Instant before);
    boolean existsByCredentialIdAndJobTypeAndStatusIn(String credentialId, JobType jobType, List<JobStatus> statuses);
    Optional<AnchorJobEntity> findFirstByCredentialIdAndJobTypeOrderByCreatedAtDesc(String credentialId, JobType jobType);
    Optional<AnchorJobEntity> findTopByCredentialIdOrderByCreatedAtDesc(String credentialId);
}
