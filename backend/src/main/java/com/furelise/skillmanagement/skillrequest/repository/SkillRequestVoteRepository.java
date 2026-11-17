package com.furelise.skillmanagement.skillrequest.repository;

import com.furelise.skillmanagement.skillrequest.model.SkillRequestVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRequestVoteRepository extends JpaRepository<SkillRequestVote, SkillRequestVote.SkillRequestVoteId> {

    boolean existsByRequestIdAndUserId(Long requestId, Long userId);

    /**
     * Q6 (BA §14, chốt 18/11): only votes from "verified" users count toward
     * the request score. There is no email-verified flag on the user table,
     * so the closest verified-equivalent the code actually has is an
     * ACTIVE, enabled account (a real account that can log in). Votes from
     * DEACTIVE / disabled accounts still exist (vote_count counts them) but
     * never move the score.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(v) FROM SkillRequestVote v JOIN User u ON u.id = v.userId "
                    + "WHERE v.requestId = :requestId AND u.enabled = true AND u.status = 'ACTIVE'")
    long countVerifiedByRequestId(@org.springframework.data.repository.query.Param("requestId") Long requestId);

    List<SkillRequestVote> findByUserId(Long userId);

    void deleteByRequestIdAndUserId(Long requestId, Long userId);

    /** Bulk delete all votes of a request (used when deleting the request). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SkillRequestVote v WHERE v.requestId = :requestId")
    void deleteByRequestId(@org.springframework.data.repository.query.Param("requestId") Long requestId);
}
