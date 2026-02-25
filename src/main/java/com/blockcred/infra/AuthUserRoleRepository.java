package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthUserRoleRepository extends JpaRepository<AuthUserRoleEntity, Long> {
    List<AuthUserRoleEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
