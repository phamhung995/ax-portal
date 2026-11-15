package com.furelise.skillmanagement.auth.repository;

import com.furelise.skillmanagement.auth.model.LoginManagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LoginRepository extends JpaRepository<LoginManagement, Long> {

    /**
     * Distinct user ids with at least one SUCCESS login within a window
     * (chart "active users" metric).
     */
    @Query("SELECT DISTINCT l.userId FROM LoginManagement l "
            + "WHERE l.loginResult = 'SUCCESS' AND l.loginAt BETWEEN :start AND :end AND l.userId IS NOT NULL")
    List<Long> findDistinctActiveUserIdsBetween(
            @Param("start") Instant start,
            @Param("end") Instant end);
}
