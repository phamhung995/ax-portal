package com.skillmanagement.skillrequest.repository;

import com.skillmanagement.skillrequest.model.SkillRequestVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRequestVoteRepository extends JpaRepository<SkillRequestVote, SkillRequestVote.SkillRequestVoteId> {

    boolean existsByRequestIdAndUserId(Long requestId, Long userId);

    List<SkillRequestVote> findByUserId(Long userId);

    void deleteByRequestIdAndUserId(Long requestId, Long userId);
}
