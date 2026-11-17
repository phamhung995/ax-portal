package com.furelise.skillmanagement.reputation.repository;

import com.furelise.skillmanagement.reputation.model.InstallEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstallEventRepository extends JpaRepository<InstallEvent, Long> {

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    long countByTargetTypeAndTargetIdAndSuccessTrue(String targetType, Long targetId);

    /**
     * True when an identical event was already recorded. NULL-safe on both
     * user_id and version: the DB unique key cannot dedupe NULLs (Postgres
     * treats them as distinct), so the service checks explicitly before
     * inserting — a companion retry is ignored, never double-counted.
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM InstallEvent e "
            + "WHERE e.targetType = :targetType AND e.targetId = :targetId "
            + "AND ((:userId IS NULL AND e.userId IS NULL) OR e.userId = :userId) "
            + "AND ((:version IS NULL AND e.version IS NULL) OR e.version = :version)")
    boolean existsDuplicate(@Param("targetType") String targetType,
                            @Param("targetId") Long targetId,
                            @Param("userId") Long userId,
                            @Param("version") String version);
}
