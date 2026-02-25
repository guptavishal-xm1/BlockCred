package com.blockcred.infra;

import com.blockcred.domain.JobStatus;
import com.blockcred.domain.JobType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnchorJobRepository extends JpaRepository<AnchorJobEntity, Long> {
    List<AnchorJobEntity> findByStatusInAndNextRunAtBeforeOrderByNextRunAtAsc(List<JobStatus> statuses, Instant before, Pageable pageable);
    boolean existsByCredentialIdAndJobTypeAndStatusIn(String credentialId, JobType jobType, List<JobStatus> statuses);
    Optional<AnchorJobEntity> findFirstByCredentialIdAndJobTypeOrderByCreatedAtDesc(String credentialId, JobType jobType);
    Optional<AnchorJobEntity> findTopByCredentialIdOrderByCreatedAtDesc(String credentialId);
    List<AnchorJobEntity> findByStatusOrderByUpdatedAtDesc(JobStatus status, Pageable pageable);
    List<AnchorJobEntity> findByStatusInOrderByUpdatedAtDesc(List<JobStatus> statuses, Pageable pageable);
    List<AnchorJobEntity> findByStatusAndLastAttemptAtBefore(JobStatus status, Instant before, Pageable pageable);
    long countByStatus(JobStatus status);

    @Modifying
    @Transactional
    @Query("update AnchorJobEntity j set j.status = :claimedStatus, j.lastAttemptAt = :lastAttemptAt where j.id = :id and j.status = :expectedStatus")
    int claimJob(
            @Param("id") Long id,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("claimedStatus") JobStatus claimedStatus,
            @Param("lastAttemptAt") Instant lastAttemptAt
    );
}
