package com.furelise.skillmanagement.translation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One language value of a translation key.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "translations")
@IdClass(TranslationId.class)
public class Translation {

    @Id
    @Column(name = "key_id")
    private Long keyId;

    @Id
    @Column(length = 5)
    private String language;

    @Column(name = "\"value\"", nullable = false, length = 1000)
    private String value;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
