package com.furelise.skillmanagement.gamification.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One daily check-in. Streak = consecutive days ending on this date. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "daily_checkins")
public class DailyCheckin {

    @EmbeddedId
    private DailyCheckinId id;

    @Column(nullable = false)
    @Builder.Default
    private int streak = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
