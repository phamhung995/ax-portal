package com.furelise.skillmanagement.gamification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/** Composite key of {@link DailyCheckin}: one row per (user, date). */
@Data
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class DailyCheckinId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "checkin_date")
    private LocalDate checkinDate;
}
