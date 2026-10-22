package com.skillmanagement.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Snapshot of a user record taken on every create/update (USER_HISTORY per SRS).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_history")
public class UserHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    private Long userId;

    private String name;

    private String email;

    private String roleName;

    private String status;

    private String registerId;

    private String createdIp;

    private String createdAppId;

    private String updatedIp;

    private String updatedAppId;

    private String updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Column(name = "history_created_at", updatable = false)
    private Instant historyCreatedAt;

    @PrePersist
    protected void onCreate() {
        if (historyCreatedAt == null) {
            historyCreatedAt = Instant.now();
        }
    }
}
