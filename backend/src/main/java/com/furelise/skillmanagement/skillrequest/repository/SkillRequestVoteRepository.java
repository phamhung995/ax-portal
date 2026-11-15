package com.furelise.skillmanagement.skillrequest.repository;

import com.furelise.skillmanagement.skillrequest.model.SkillRequestVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRequestVoteRepository extends JpaRepository<SkillRequestVote, SkillRequestVote.SkillRequestVoteId> {

    boolean existsByRequestIdAndUserId(Long requestId, Long userId);

    List<SkillRequestVote> findByUserId(Long userId);

    void deleteByRequestIdAndUserId(Long requestId, Long userId);

    /** Bulk delete all votes of a request (used when deleting the request). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SkillRequestVote v WHERE v.requestId = :requestId")
    void deleteByRequestId(@org.springframework.data.repository.query.Param("requestId") Long requestId);
}
