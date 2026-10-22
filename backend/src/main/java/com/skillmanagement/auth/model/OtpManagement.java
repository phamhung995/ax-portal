package com.skillmanagement.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One-time password record. Only the hash is stored (OTP_MANAGEMENT per SRS).
 * Status: ACTIVE / VERIFIED / EXPIRED.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "otp_management")
public class OtpManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otpId;

    /** NULL for pre-registration or unknown users (keyed by email). */
    private Long userId;

    private String email;

    private String otpHash;

    private String otpType; // EMAIL_VERIFICATION / PASSWORD_RESET

    @Column(name = "generated_at", updatable = false)
    private Instant generatedAt;

    private Instant expiredAt;

    private Instant verifiedAt;

    private String status; // ACTIVE / VERIFIED / EXPIRED

    private String createdIp;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
