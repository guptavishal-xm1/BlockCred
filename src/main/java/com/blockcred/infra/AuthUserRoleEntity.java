package com.blockcred.infra;

import com.blockcred.domain.AuthRole;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "auth_user_roles", indexes = {
        @Index(name = "idx_auth_user_roles_user_id", columnList = "userId"),
        @Index(name = "idx_auth_user_roles_user_role", columnList = "userId,role", unique = true)
})
public class AuthUserRoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuthRole role;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AuthRole getRole() {
        return role;
    }

    public void setRole(AuthRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
