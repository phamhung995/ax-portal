package com.furelise.skillmanagement.gamification.repository;

import com.furelise.skillmanagement.gamification.model.PointsTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    Page<PointsTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Points earned of one type since a moment (daily caps). */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PointsTransaction t "
            + "WHERE t.userId = :userId AND t.type = :type AND t.createdAt >= :since")
    int sumAmountByUserIdAndTypeSince(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("since") Instant since);

    /** One reward per referenced object (e.g. +50 once per published skill). */
    boolean existsByTypeAndRefTypeAndRefId(String type, String refType, Long refId);
}
