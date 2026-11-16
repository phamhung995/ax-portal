package com.furelise.skillmanagement.gamification.repository;

import com.furelise.skillmanagement.gamification.model.DailyCheckin;
import com.furelise.skillmanagement.gamification.model.DailyCheckinId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, DailyCheckinId> {

    /** The most recent check-in of a user (for the streak). */
    Optional<DailyCheckin> findFirstByIdUserIdOrderByIdCheckinDateDesc(Long userId);

    default Optional<DailyCheckin> findByUserAndDate(Long userId, LocalDate date) {
        return findById(new DailyCheckinId(userId, date));
    }
}
