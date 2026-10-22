package com.skillmanagement.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-user settings (Settings SRS §6.1). 1 row per user.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String language;

    private String timezone;

    private String dateFormat;

    @Builder.Default
    private boolean emailNotify = true;

    @Builder.Default
    private boolean inappNotify = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (language == null) language = "en";
        if (timezone == null) timezone = "Asia/Ho_Chi_Minh";
        if (dateFormat == null) dateFormat = "DD/MM/YYYY";
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
