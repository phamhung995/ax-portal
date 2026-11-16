package com.furelise.skillmanagement.gamification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Cached points balance of one user (the ledger is the source of truth). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_points")
public class UserPoints {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private int balance = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
