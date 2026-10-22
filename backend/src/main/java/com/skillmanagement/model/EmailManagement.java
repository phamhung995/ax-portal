package com.skillmanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit record of OTP emails (EMAIL_MANAGEMENT per SRS).
 * Status: PENDING / SENT / FAILED.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "email_management")
public class EmailManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long emailId;

    private Long userId;

    private String email;

    private String emailType; // EMAIL_VERIFICATION / PASSWORD_RESET

    private String subject;

    private String status; // PENDING / SENT / FAILED

    private Instant sentAt;

    private String errorCode;

    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
