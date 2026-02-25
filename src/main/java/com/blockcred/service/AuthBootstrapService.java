package com.blockcred.service;

import com.blockcred.domain.AuthRole;
import com.blockcred.domain.AuthUserStatus;
import com.blockcred.infra.AuthUserEntity;
import com.blockcred.infra.AuthUserRepository;
import com.blockcred.infra.AuthUserRoleEntity;
import com.blockcred.infra.AuthUserRoleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthBootstrapService {
    private final AuthUserRepository authUserRepository;
    private final AuthUserRoleRepository authUserRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final boolean seedEnabled;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminDisplayName;
    private final String adminPassword;
    private final String issuerUsername;
    private final String issuerEmail;
    private final String issuerDisplayName;
    private final String issuerPassword;

    public AuthBootstrapService(
            AuthUserRepository authUserRepository,
            AuthUserRoleRepository authUserRoleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            @Value("${blockcred.auth.seed.enabled:false}") boolean seedEnabled,
            @Value("${blockcred.auth.seed.admin-username:admin}") String adminUsername,
            @Value("${blockcred.auth.seed.admin-email:admin@blockcred.local}") String adminEmail,
            @Value("${blockcred.auth.seed.admin-display-name:University Admin}") String adminDisplayName,
            @Value("${blockcred.auth.seed.admin-password:}") String adminPassword,
            @Value("${blockcred.auth.seed.issuer-username:issuer}") String issuerUsername,
            @Value("${blockcred.auth.seed.issuer-email:issuer@blockcred.local}") String issuerEmail,
            @Value("${blockcred.auth.seed.issuer-display-name:University Issuer}") String issuerDisplayName,
            @Value("${blockcred.auth.seed.issuer-password:}") String issuerPassword
    ) {
        this.authUserRepository = authUserRepository;
        this.authUserRoleRepository = authUserRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.seedEnabled = seedEnabled;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminDisplayName = adminDisplayName;
        this.adminPassword = adminPassword;
        this.issuerUsername = issuerUsername;
        this.issuerEmail = issuerEmail;
        this.issuerDisplayName = issuerDisplayName;
        this.issuerPassword = issuerPassword;
    }

    @PostConstruct
    @Transactional
    void seedUsers() {
        if (!seedEnabled) {
            return;
        }
        ensureUser(adminUsername, adminEmail, adminDisplayName, adminPassword, AuthRole.ADMIN);
        ensureUser(issuerUsername, issuerEmail, issuerDisplayName, issuerPassword, AuthRole.ISSUER);
    }

    private void ensureUser(String username, String email, String displayName, String password, AuthRole role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (authUserRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return;
        }
        passwordPolicyService.validate(password);

        AuthUserEntity user = new AuthUserEntity();
        user.setUsername(username.trim());
        user.setEmail(email == null || email.isBlank() ? username + "@blockcred.local" : email.trim());
        user.setDisplayName(displayName == null || displayName.isBlank() ? username.trim() : displayName.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(AuthUserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setForcePasswordChange(true);
        AuthUserEntity saved = authUserRepository.save(user);

        AuthUserRoleEntity userRole = new AuthUserRoleEntity();
        userRole.setUserId(saved.getId());
        userRole.setRole(role);
        authUserRoleRepository.save(userRole);
    }
}
