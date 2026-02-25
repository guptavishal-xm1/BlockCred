package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<CredentialEntity, Long> {
    Optional<CredentialEntity> findByCredentialId(String credentialId);
    Optional<CredentialEntity> findByHash(String hash);
}
