package com.shopassist.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered shopper. Carries both credentials and profile, so orders and
 * conversations hang directly off this table with no extra join.
 *
 * <p>The table is {@code app_users} rather than {@code users} because USER is a
 * reserved word in both MySQL 8 and H2.
 *
 * <p>{@link #id} is an internal surrogate key and must never leave the server.
 * Anything exposed over HTTP uses {@link #publicRef} instead.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_ref", nullable = false, length = 36, updatable = false)
    private String publicRef;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    /** BCrypt hash. Never expose this in a DTO, a log line, or a tool result. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void assignPublicRef() {
        if (publicRef == null) {
            publicRef = UUID.randomUUID().toString();
        }
        if (role == null) {
            role = UserRole.CUSTOMER;
        }
    }

    /** True while a lockout from repeated failed logins is still in effect. */
    public boolean isCurrentlyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
