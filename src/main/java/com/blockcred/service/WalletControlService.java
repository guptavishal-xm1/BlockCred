package com.blockcred.service;

import com.blockcred.api.WalletControlResponse;
import com.blockcred.api.WalletStatusResponse;
import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;
import com.blockcred.infra.SystemControlEntity;
import com.blockcred.infra.SystemControlRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class WalletControlService {
    private static final String KEY_WALLET_ENABLED = "wallet_enabled";
    private static final String KEY_WALLET_DISABLE_REASON = "wallet_disable_reason";

    private final SystemControlRepository systemControlRepository;
    private final AuditService auditService;
    private final boolean defaultWalletEnabled;
    private final String keySource;
    private final String keyFilePath;

    public WalletControlService(
            SystemControlRepository systemControlRepository,
            AuditService auditService,
            @Value("${blockcred.wallet.enabled:true}") boolean defaultWalletEnabled,
            @Value("${blockcred.wallet.key-source:ENV}") String keySource,
            @Value("${blockcred.wallet.key-file-path:}") String keyFilePath
    ) {
        this.systemControlRepository = systemControlRepository;
        this.auditService = auditService;
        this.defaultWalletEnabled = defaultWalletEnabled;
        this.keySource = keySource;
        this.keyFilePath = keyFilePath;
    }

    @PostConstruct
    @Transactional
    void initializeDefaults() {
        if (systemControlRepository.findById(KEY_WALLET_ENABLED).isEmpty()) {
            setControl(KEY_WALLET_ENABLED, Boolean.toString(defaultWalletEnabled), "system", "startup default");
        }
        if (systemControlRepository.findById(KEY_WALLET_DISABLE_REASON).isEmpty()) {
            setControl(KEY_WALLET_DISABLE_REASON, "", "system", "startup default");
        }
    }

    @Transactional(readOnly = true)
    public boolean isWalletEnabled() {
        return Boolean.parseBoolean(controlValue(KEY_WALLET_ENABLED).orElse(Boolean.toString(defaultWalletEnabled)));
    }

    @Transactional
    public WalletControlResponse disableWallet(String actor, String reason) {
        setControl(KEY_WALLET_ENABLED, "false", actor, normalize(reason));
        setControl(KEY_WALLET_DISABLE_REASON, normalize(reason), actor, "disabled");
        auditService.log("WALLET_DISABLED", "SYSTEM", actor, normalize(reason), AuditSeverity.WARN, AuditCategory.SECURITY);
        WalletStatusResponse status = status();
        return new WalletControlResponse("DISABLED", status.enabled(), "Wallet disabled", actor, status.updatedAt());
    }

    @Transactional
    public WalletControlResponse enableWallet(String actor) {
        setControl(KEY_WALLET_ENABLED, "true", actor, "enabled");
        setControl(KEY_WALLET_DISABLE_REASON, "", actor, "enabled");
        auditService.log("WALLET_ENABLED", "SYSTEM", actor, "Wallet enabled", AuditSeverity.INFO, AuditCategory.SECURITY);
        WalletStatusResponse status = status();
        return new WalletControlResponse("ENABLED", status.enabled(), "Wallet enabled", actor, status.updatedAt());
    }

    @Transactional(readOnly = true)
    public WalletStatusResponse status() {
        SystemControlEntity enabled = systemControlRepository.findById(KEY_WALLET_ENABLED).orElse(null);
        SystemControlEntity reason = systemControlRepository.findById(KEY_WALLET_DISABLE_REASON).orElse(null);

        boolean walletEnabled = enabled == null
                ? defaultWalletEnabled
                : Boolean.parseBoolean(enabled.getControlValue());

        String updatedBy = enabled != null ? enabled.getUpdatedBy() : "system";
        Instant updatedAt = enabled != null ? enabled.getUpdatedAt() : Instant.now();
        String disableReason = reason == null || reason.getControlValue() == null || reason.getControlValue().isBlank()
                ? null
                : reason.getControlValue();

        return new WalletStatusResponse(
                walletEnabled,
                keySource.toUpperCase(Locale.ROOT),
                keyPresent(),
                disableReason,
                updatedBy,
                updatedAt
        );
    }

    public void requireWalletEnabledOrThrow() {
        if (!isWalletEnabled()) {
            String reason = controlValue(KEY_WALLET_DISABLE_REASON).orElse("");
            throw new WalletDisabledException(reason.isBlank() ? "Wallet currently disabled by admin" : reason);
        }
    }

    private Optional<String> controlValue(String key) {
        return systemControlRepository.findById(key).map(SystemControlEntity::getControlValue);
    }

    private void setControl(String key, String value, String updatedBy, String details) {
        SystemControlEntity entity = systemControlRepository.findById(key).orElseGet(SystemControlEntity::new);
        entity.setControlKey(key);
        entity.setControlValue(value == null ? "" : value);
        entity.setUpdatedBy(updatedBy);
        entity.setDetails(details);
        systemControlRepository.save(entity);
    }

    private boolean keyPresent() {
        String normalizedSource = keySource == null ? "" : keySource.trim().toUpperCase(Locale.ROOT);
        if ("FILE".equals(normalizedSource)) {
            if (keyFilePath == null || keyFilePath.isBlank()) {
                return false;
            }
            return Files.exists(Path.of(keyFilePath));
        }

        String env = System.getenv("BLOCKCRED_WALLET_KEY");
        return env != null && !env.isBlank();
    }

    private String normalize(String reason) {
        if (reason == null || reason.isBlank()) {
            return "No reason provided";
        }
        return reason.trim();
    }
}
