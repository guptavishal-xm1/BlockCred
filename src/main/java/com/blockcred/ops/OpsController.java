package com.blockcred.ops;

import com.blockcred.api.OpsAuditEntryResponse;
import com.blockcred.api.OpsAnomalyResponse;
import com.blockcred.api.OpsCredentialStateResponse;
import com.blockcred.api.OpsJobSummaryResponse;
import com.blockcred.api.OpsSecurityStatusResponse;
import com.blockcred.api.OpsSummaryResponse;
import com.blockcred.api.ReconcileResultResponse;
import com.blockcred.api.WalletControlRequest;
import com.blockcred.api.WalletControlResponse;
import com.blockcred.api.WalletStatusResponse;
import com.blockcred.domain.JobStatus;
import com.blockcred.domain.ReconcileDecision;
import com.blockcred.service.ApiAccessService;
import com.blockcred.service.CurrentUserService;
import com.blockcred.service.JobService;
import com.blockcred.service.OpsQueryService;
import com.blockcred.service.WalletControlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/ops")
public class OpsController {
    private final JobService jobService;
    private final OpsQueryService opsQueryService;
    private final ApiAccessService apiAccessService;
    private final WalletControlService walletControlService;
    private final CurrentUserService currentUserService;
    private final long cooldownSeconds;

    public OpsController(
            JobService jobService,
            OpsQueryService opsQueryService,
            ApiAccessService apiAccessService,
            CurrentUserService currentUserService,
            WalletControlService walletControlService,
            @Value("${blockcred.worker.cooldown-seconds:60}") long cooldownSeconds
    ) {
        this.jobService = jobService;
        this.opsQueryService = opsQueryService;
        this.apiAccessService = apiAccessService;
        this.currentUserService = currentUserService;
        this.walletControlService = walletControlService;
        this.cooldownSeconds = cooldownSeconds;
    }

    @PostMapping("/reconcile/{credentialId}")
    public ReconcileResultResponse reconcile(
            @PathVariable String credentialId,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken
    ) {
        apiAccessService.requireAdmin(adminToken);
        ReconcileDecision decision = jobService.reconcile(credentialId, currentUserService.actorOr("admin"), Duration.ofSeconds(cooldownSeconds));
        return new ReconcileResultResponse(
                decision.result(),
                decision.credentialId(),
                decision.jobType(),
                decision.jobStatus(),
                decision.checkedAt(),
                decision.message(),
                decision.recommendedAction(),
                decision.cooldownRemainingSeconds()
        );
    }

    @GetMapping("/credentials/{credentialId}/state")
    public OpsCredentialStateResponse credentialState(
            @PathVariable String credentialId,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken
    ) {
        apiAccessService.requireAdmin(adminToken);
        return opsQueryService.credentialState(credentialId);
    }

    @GetMapping("/jobs")
    public List<OpsJobSummaryResponse> jobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken
    ) {
        apiAccessService.requireAdmin(adminToken);
        return opsQueryService.jobs(status, limit);
    }

    @GetMapping("/audit")
    public List<OpsAuditEntryResponse> audit(
            @RequestParam(required = false) String credentialId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken
    ) {
        apiAccessService.requireAdmin(adminToken);
        return opsQueryService.audit(credentialId, limit);
    }

    @GetMapping("/summary")
    public OpsSummaryResponse summary(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        apiAccessService.requireAdmin(adminToken);
        return opsQueryService.summary();
    }

    @GetMapping("/anomalies")
    public List<OpsAnomalyResponse> anomalies(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken
    ) {
        apiAccessService.requireAdmin(adminToken);
        return opsQueryService.anomalies(limit);
    }

    @GetMapping("/security/status")
    public OpsSecurityStatusResponse securityStatus(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        apiAccessService.requireAdmin(adminToken);
        return opsQueryService.securityStatus();
    }

    @GetMapping("/wallet/status")
    public WalletStatusResponse walletStatus(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        apiAccessService.requireAdmin(adminToken);
        return walletControlService.status();
    }

    @PostMapping("/wallet/disable")
    public WalletControlResponse walletDisable(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestBody(required = false) WalletControlRequest request
    ) {
        apiAccessService.requireAdmin(adminToken);
        String reason = request == null ? null : request.reason();
        return walletControlService.disableWallet(currentUserService.actorOr("admin"), reason);
    }

    @PostMapping("/wallet/enable")
    public WalletControlResponse walletEnable(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        apiAccessService.requireAdmin(adminToken);
        return walletControlService.enableWallet(currentUserService.actorOr("admin"));
    }
}
