package com.skillmanagement.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit record of every login attempt (LOGIN_MANAGEMENT per SRS).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "login_management")
public class LoginManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long loginId;

    private Long userId;

    private String email;

    private String loginIp;

    @Column(name = "login_at", updatable = false)
    private Instant loginAt;

    private String loginResult; // SUCCESS / FAILURE

    private String failureReason;

    private String appId;

    @PrePersist
    protected void onCreate() {
        if (loginAt == null) {
            loginAt = Instant.now();
        }
    }
}
